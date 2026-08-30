// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AttributeValueNode;
import org.jfxcore.compiler.ast.InlineArgumentSequenceNode;
import org.jfxcore.compiler.ast.LiteralValueNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.NodeDataKey;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.SyntaxNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.emit.EmitGetParentNode;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.DiagnosticInfo;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.type.AccessModifier;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.NameHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Materializes object operations after target conversion and constructor selection.
 */
public final class ValueEmitterFactory {

    private ValueEmitterFactory() {}

    /**
     * Tries to materialize the public default constructor.
     */
    public static EmitObjectNode newDefaultObject(ObjectNode objectNode) {
        TypeInstance type = TypeHelper.getTypeInstance(objectNode);
        return type.declaration().declaredConstructor()
            .filter(constructor -> constructor.accessModifier() == AccessModifier.PUBLIC)
            .map(constructor -> createObjectNode(objectNode, constructor, List.of()))
            .orElse(null);
    }

    /**
     * Selects an explicit named-argument constructor by property names, resolves all of its values
     * into retained target plans, and materializes only the selected candidate.
     */
    public static EmitObjectNode newObjectWithNamedParams(
            TransformContext context,
            ObjectNode objectNode,
            List<DiagnosticInfo> diagnostics) {
        int parentsUnderInitialization = getParentsUnderInitializationCount(context)
            + (objectNode.getNodeData(NodeDataKey.CONSTRUCTOR_ARGUMENT) == Boolean.TRUE ? 1 : 0);

        TypeInstance type = TypeHelper.getTypeInstance(objectNode);
        NamedArgsConstructor[] constructors = findNamedArgsConstructors(objectNode, diagnostics);
        List<DiagnosticInfo> constructorDiagnostics = new ArrayList<>();

        for (NamedArgsConstructor candidate : constructors) {
            List<TargetValueResolver.ValuePlan> argumentPlans = new ArrayList<>();
            boolean failed = false;

            for (int i = 0; i < candidate.parameters().size(); ++i) {
                NamedArgumentMetadata.Parameter parameter = candidate.parameters().get(i);
                PropertyNode property = objectNode.getProperties().stream()
                    .filter(item -> item.getName().equals(parameter.name()))
                    .findFirst()
                    .orElse(null);

                TargetValueResolver.ValueInput input;

                if (property == null) {
                    if (!parameter.isOptional()) {
                        failed = true;
                        break;
                    }

                    input = TargetValueResolver.ValueInput.of(
                        new LiteralValueNode(parameter.defaultValue(), SourceInfo.none()));
                } else {
                    input = createValueInput(
                        property,
                        i == candidate.parameters().size() - 1
                            && candidate.constructor().isVarArgs(),
                        parameter.type());
                    if (input == null) {
                        addArgumentDiagnostic(
                            constructorDiagnostics, candidate, parameter, property.getSourceInfo(),
                            null, null);
                        failed = true;
                        break;
                    }
                }

                TargetValueResolver.TargetContext target =
                    TargetValueResolver.TargetContext.constructorParameter(
                        type, parameter.name(), parameter.type(), type,
                        parentsUnderInitialization, input.sourceInfo());

                TargetValueResolver.ResolutionResult result =
                    TargetValueResolver.resolveSequence(context, input, target);

                if (result instanceof TargetValueResolver.ResolutionResult.Invalid invalid) {
                    throw invalid.diagnostic();
                }

                if (result instanceof TargetValueResolver.ResolutionResult.NotApplicable notApplicable) {
                    addArgumentDiagnostic(
                        constructorDiagnostics, candidate, parameter,
                        notApplicable.failure().sourceInfo(),
                        notApplicable.failure().valueType(),
                        notApplicable.failure().diagnostic());
                    failed = true;
                    break;
                }

                if (!(result instanceof TargetValueResolver.ResolutionResult.Applicable applicable)) {
                    throw new AssertionError();
                }

                if (applicable.plan().kind() != TargetValueResolver.PlanKind.VALUE) {
                    throw new IllegalStateException("A constructor argument must resolve to a value plan");
                }

                argumentPlans.add(applicable.plan());
            }

            if (!failed) {
                // Commit only after every semantic argument plan has been selected.
                List<ValueNode> arguments = argumentPlans.stream()
                    .map(TargetValueResolver.ValuePlan::lowerValue)
                    .toList();

                objectNode.getProperties().removeIf(property -> candidate.parameters().stream()
                    .anyMatch(parameter -> parameter.name().equals(property.getName())));

                return createObjectNode(objectNode, candidate.constructor(), arguments);
            }
        }

        if (!constructorDiagnostics.isEmpty()) {
            diagnostics.addAll(constructorDiagnostics);
        }

        return null;
    }

    public static int getParentsUnderInitializationCount(TransformContext context) {
        int depth = 0;
        var iterator = context.getParents().listIterator(context.getParents().size());

        while (iterator.hasPrevious()) {
            Node parent = iterator.previous();
            if (parent instanceof SyntaxNode) {
                continue;
            }

            if (parent.getNodeData(NodeDataKey.CONSTRUCTOR_ARGUMENT) != Boolean.TRUE) {
                break;
            }

            ++depth;
        }

        return depth;
    }

    static void adjustParentIndex(ValueNode node, int adjustment) {
        node.accept(new Visitor() {
            @Override
            protected Node onVisited(Node node) {
                if (node instanceof EmitGetParentNode getParentNode) {
                    getParentNode.setParentIndexAdjustment(adjustment);
                }

                return node;
            }
        });
    }

    private static @Nullable TargetValueResolver.ValueInput createValueInput(
            PropertyNode property, boolean varargs, TypeInstance parameterType) {
        if (property.getValues().size() == 1) {
            Node value = property.getValues().get(0);
            if (value instanceof InlineArgumentSequenceNode sequence) {
                return TargetValueResolver.ValueInput.structural(
                    sequence.getValues(), sequence.getSourceInfo());
            }

            return TargetValueResolver.ValueInput.of(value);
        }

        if (varargs || parameterType.isArray()) {
            return TargetValueResolver.ValueInput.structural(
                property.getValues(), SourceInfo.span(property.getValues()));
        }

        return null;
    }

    private static void addArgumentDiagnostic(
            List<DiagnosticInfo> diagnostics,
            NamedArgsConstructor constructor,
            NamedArgumentMetadata.Parameter parameter,
            SourceInfo sourceInfo,
            @Nullable TypeInstance sourceType,
            @Nullable MarkupException cause) {
        TypeInstance[] argumentTypes = constructor.parameters().stream()
            .map(NamedArgumentMetadata.Parameter::type)
            .toArray(TypeInstance[]::new);

        String[] argumentNames = constructor.parameters().stream()
            .map(NamedArgumentMetadata.Parameter::name)
            .toArray(String[]::new);

        String signature = NameHelper.getDisplaySignature(
            constructor.constructor(), argumentTypes, argumentNames);

        Diagnostic[] causes = cause == null
            ? new Diagnostic[0]
            : new Diagnostic[] {cause.getDiagnostic()};

        Diagnostic diagnostic = Diagnostic.newDiagnosticVariant(
            ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT,
            "named", causes, signature, parameter.name(),
            sourceType != null ? sourceType.javaName() : TypeInstance.StringType().javaName());

        diagnostics.add(new DiagnosticInfo(
            diagnostic, cause != null ? cause.getSourceInfo() : sourceInfo));
    }

    private static EmitObjectNode createObjectNode(
            ObjectNode objectNode,
            ConstructorDeclaration constructor,
            List<? extends ValueNode> arguments) {
        return EmitObjectNode
            .constructor(
                TypeHelper.getTypeInstance(objectNode), constructor,
                List.copyOf(arguments), objectNode.getSourceInfo())
            .children(new PropertyAssignmentSorter(objectNode, objectNode.getProperties()).sort())
            .backingField(findAndRemoveId(objectNode))
            .create();
    }

    private static NamedArgsConstructor[] findNamedArgsConstructors(
            ObjectNode objectNode, List<DiagnosticInfo> diagnostics) {
        List<NamedArgsConstructor> candidates = new ArrayList<>();
        TypeInstance type = TypeHelper.getTypeInstance(objectNode);

        for (ConstructorDeclaration constructor : type.declaration().constructors()) {
            if (constructor.accessModifier() != AccessModifier.PUBLIC) {
                continue;
            }

            List<NamedArgumentMetadata.Parameter> parameters = NamedArgumentMetadata.get(
                type, constructor, objectNode.getSourceInfo());

            if (!parameters.isEmpty()) {
                candidates.add(new NamedArgsConstructor(constructor, parameters));
            }
        }

        Map<Integer, List<NamedArgsConstructor>> constructorOrder =
            new TreeMap<>(Comparator.reverseOrder());

        for (NamedArgsConstructor candidate : candidates) {
            int matches = 0;

            for (NamedArgumentMetadata.Parameter parameter : candidate.parameters()) {
                if (parameter.isOptional() || objectNode.getProperties().stream()
                        .anyMatch(property -> property.getName().equals(parameter.name()))) {
                    ++matches;
                }
            }

            if (matches == candidate.parameters().size()) {
                constructorOrder.computeIfAbsent(matches, ignored -> new ArrayList<>()).add(candidate);
            } else {
                TypeInstance[] argumentTypes = candidate.parameters().stream()
                    .map(NamedArgumentMetadata.Parameter::type)
                    .toArray(TypeInstance[]::new);

                String[] argumentNames = candidate.parameters().stream()
                    .map(NamedArgumentMetadata.Parameter::name)
                    .toArray(String[]::new);

                diagnostics.add(new DiagnosticInfo(
                    Diagnostic.newDiagnosticVariant(
                        ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, "named",
                        NameHelper.getDisplaySignature(
                            candidate.constructor(), argumentTypes, argumentNames),
                        candidate.parameters().size(), matches), objectNode.getSourceInfo()));
            }
        }

        return constructorOrder.isEmpty()
            ? new NamedArgsConstructor[0]
            : constructorOrder.values().iterator().next().toArray(NamedArgsConstructor[]::new);
    }

    private static @Nullable String findAndRemoveId(ObjectNode node) {
        PropertyNode property = node.findIntrinsicProperty(Intrinsics.ID);
        if (property == null) {
            return null;
        }

        property.remove();
        Node value = property.getValues().size() == 1 ? property.getValues().get(0) : null;
        if (value instanceof AttributeValueNode attribute
                && attribute.getForm() == AttributeValueNode.Form.LITERAL) {
            value = attribute.getLiteral();
        }

        if (!(value instanceof LiteralValueNode literal)) {
            throw PropertyAssignmentErrors.propertyMustContainText(
                property.getSourceInfo(), TypeHelper.getTypeDeclaration(node), Intrinsics.ID.getName());
        }

        return literal.getText();
    }

    private record NamedArgsConstructor(
            ConstructorDeclaration constructor,
            List<NamedArgumentMetadata.Parameter> parameters) {

        private NamedArgsConstructor {
            Objects.requireNonNull(constructor);
            parameters = List.copyOf(parameters);
        }
    }
}

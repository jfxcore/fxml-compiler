// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.AttributeValueNode;
import org.jfxcore.compiler.ast.LiteralValueNode;
import org.jfxcore.compiler.ast.ContextNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.TemplateContentNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.ValueSourceKind;
import org.jfxcore.compiler.ast.emit.EmitEventHandlerNode;
import org.jfxcore.compiler.ast.emit.EmitInvokeGetterNode;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.emit.EmitPropertyPathNode;
import org.jfxcore.compiler.ast.emit.EmitPropertySetterNode;
import org.jfxcore.compiler.ast.emit.EmitSetFieldNode;
import org.jfxcore.compiler.ast.emit.EmitStaticPropertySetterNode;
import org.jfxcore.compiler.ast.emit.EmitTemplateContentNode;
import org.jfxcore.compiler.ast.emit.EmitUnwrapObservableNode;
import org.jfxcore.compiler.ast.emit.EmitterNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.transform.markup.util.TargetValueResolver;
import org.jfxcore.compiler.transform.markup.util.ValueEmitterFactory;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.PropertyInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * Replaces all instances of {@link PropertyNode} in the AST with nodes that represent property assignments
 * ({@link EmitPropertySetterNode}) or static property assignments ({@link EmitStaticPropertySetterNode}).
 */
public class PropertyAssignmentTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof PropertyNode propertyNode)) {
            return node;
        }

        if (propertyNode.isIntrinsic(Intrinsics.CONTEXT)) {
            ContextNode contextNode = propertyNode.getSingleValue(context).as(ContextNode.class);
            if (contextNode == null) {
                throw ParserErrors.invalidExpression(propertyNode.getSingleValue(context).getSourceInfo());
            }

            return new EmitSetFieldNode(
                contextNode.getField(),
                (ValueEmitterNode)contextNode.getValue(),
                contextNode.getSourceInfo());
        }

        if (propertyNode.isIntrinsic()) {
            throw GeneralErrors.unexpectedIntrinsic(node.getSourceInfo(), propertyNode.getMarkupName());
        }

        ValueEmitterNode parentNode = context.findParent(ValueEmitterNode.class);
        Resolver resolver = new Resolver(propertyNode.getSourceInfo());
        TypeInstance declaringType = TypeHelper.getTypeInstance(parentNode);
        PropertyInfo targetProperty = resolver.tryResolveProperty(
            declaringType, propertyNode.isAllowQualifiedName(), propertyNode.getNames());

        // A property assignment of the form foo.bar.baz="some value" must be resolved to a chain of
        // getter nodes until we arrive at the last path segment.
        if (targetProperty == null && propertyNode.getNames().length > 1
                && Arrays.stream(propertyNode.getNames()).allMatch(s -> Character.isLowerCase(s.charAt(0)))) {
            SourceInfo sourceInfo = propertyNode.getSourceInfo();
            String[] names = propertyNode.getNames();
            List<ValueEmitterNode> nodes = new ArrayList<>();

            for (int i = 0; i < names.length - 1; ++i) {
                targetProperty = resolver.tryResolveProperty(declaringType, false, names[i]);
                if (targetProperty == null) {
                    // If we fail to resolve the first segment, format the error message to include the
                    // entire chain of names. This makes for a better diagnostic in case the user meant
                    // to specify the name of a static property that couldn't be resolved.
                    String name = i == 0 ? propertyNode.getName() : names[i];
                    throw SymbolResolutionErrors.propertyNotFound(sourceInfo, declaringType.declaration(), name);
                }

                boolean hasGetter = targetProperty.getGetter() != null;

                ValueEmitterNode emitter = new EmitInvokeGetterNode(
                    targetProperty.getGetterOrPropertyGetter(),
                    hasGetter ? targetProperty.getType() : targetProperty.getObservableType(),
                    hasGetter ? ValueSourceKind.NONE : ValueSourceKind.READONLY,
                    true,
                    sourceInfo);

                if (!hasGetter) {
                    emitter = new EmitUnwrapObservableNode(emitter);
                }

                nodes.add(emitter);
            }

            return new EmitPropertyPathNode(
                nodes,
                new PropertyNode(
                    new String[] {names[names.length - 1]},
                    names[names.length - 1],
                    propertyNode.getValues(),
                    false,
                    false,
                    sourceInfo),
                sourceInfo);
        }

        if (targetProperty == null) {
            if (propertyNode.isAllowQualifiedName() && propertyNode.getNames().length > 1) {
                String[] names = propertyNode.getNames();
                String className = String.join(".", Arrays.copyOf(names, names.length - 1));
                TypeDeclaration type = resolver.tryResolveClassAgainstImports(className);

                if (type != null && TypeHelper.getTypeInstance(parentNode).subtypeOf(type)) {
                    throw SymbolResolutionErrors.propertyNotFound(
                        propertyNode.getSourceInfo(), className, names[names.length - 1]);
                }

                if (type == null) {
                    throw SymbolResolutionErrors.classNotFound(propertyNode.getSourceInfo(), className);
                }

                throw SymbolResolutionErrors.staticPropertyNotFound(
                    propertyNode.getSourceInfo(), type, names[names.length - 1]);
            }

            throw SymbolResolutionErrors.propertyNotFound(
                propertyNode.getSourceInfo(), declaringType.declaration(), propertyNode.getName());
        }

        if (propertyNode.getValues().isEmpty()) {
            throw PropertyAssignmentErrors.propertyCannotBeEmpty(
                propertyNode.getSourceInfo(), declaringType.declaration(), propertyNode.getMarkupName());
        }

        AttributeValueNode attributeValue = propertyNode.getValues().size() == 1
            ? propertyNode.getValues().get(0).as(AttributeValueNode.class)
            : null;

        if (attributeValue != null) {
            ValueAssignmentResolution resolution = resolveValueAssignment(
                context,
                TargetValueResolver.ValueInput.of(attributeValue),
                targetProperty,
                declaringType,
                propertyNode.getSourceInfo());

            if (resolution instanceof ValueAssignmentResolution.Assign assign) {
                return assign.node();
            }

            if (resolution instanceof ValueAssignmentResolution.Invalid invalid) {
                throw invalid.error();
            }

            if (resolution instanceof ValueAssignmentResolution.Error error
                    && error.diagnostic() != null) {
                if (attributeValue.getForm() != AttributeValueNode.Form.LITERAL
                        || error.diagnostic().getDiagnostic().getCode()
                            != org.jfxcore.compiler.diagnostic.ErrorCode.CONSTRUCTOR_NOT_FOUND) {
                    throw error.diagnostic();
                }
            }

            if (targetProperty.isReadOnly()) {
                throw PropertyAssignmentErrors.cannotModifyReadOnlyProperty(
                    attributeValue.getSourceInfo(), targetProperty);
            }

            if (attributeValue.getForm() == AttributeValueNode.Form.LITERAL) {
                LiteralValueNode literal = attributeValue.getLiteral();
                throw PropertyAssignmentErrors.cannotCoercePropertyValue(
                    literal.getSourceInfo(), targetProperty, literal.getText(), false);
            }

            String value = attributeValue.getSourceInfo().getText();
            throw PropertyAssignmentErrors.cannotCoercePropertyValue(
                attributeValue.getSourceInfo(), targetProperty,
                value != null ? value : attributeValue.format());
        }

        ValueAssignmentResolution resolution = resolveValueAssignment(
            context,
            TargetValueResolver.ValueInput.propertyContent(
                propertyNode.getValues(), propertyNode.getSourceInfo()),
            targetProperty,
            declaringType,
            propertyNode.getSourceInfo());

        if (resolution instanceof ValueAssignmentResolution.Assign assign) {
            return assign.node();
        }

        if (resolution instanceof ValueAssignmentResolution.Invalid invalid) {
            throw invalid.error();
        }

        if (resolution instanceof ValueAssignmentResolution.Error error
                && error.failure().diagnostic() != null) {
            throw error.failure().diagnostic();
        }

        if (propertyNode.getValues().size() > 1) {
            throw PropertyAssignmentErrors.propertyCannotHaveMultipleValues(
                propertyNode.getSourceInfo(), declaringType.declaration(), propertyNode.getMarkupName());
        }

        if (targetProperty.isReadOnly()) {
            throw PropertyAssignmentErrors.cannotModifyReadOnlyProperty(propertyNode.getSourceInfo(), targetProperty);
        }

        if (propertyNode.getValues().size() == 1) {
            if (propertyNode.getValues().get(0) instanceof LiteralValueNode literalNode) {
                throw PropertyAssignmentErrors.cannotCoercePropertyValue(
                    literalNode.getSourceInfo(), targetProperty, literalNode.getText(), false);
            }

            throw PropertyAssignmentErrors.incompatiblePropertyType(
                propertyNode.getValues().get(0).getSourceInfo(), targetProperty,
                TypeHelper.getTypeInstance(propertyNode.getValues().get(0)));
        }

        throw PropertyAssignmentErrors.incompatiblePropertyItems(propertyNode.getSourceInfo(), targetProperty);
    }

    private ValueAssignmentResolution resolveValueAssignment(
            TransformContext context,
            TargetValueResolver.ValueInput input,
            PropertyInfo propertyInfo,
            TypeInstance invokingType,
            SourceInfo assignmentSource) {
        int parentsUnderInitialization = ValueEmitterFactory.getParentsUnderInitializationCount(context);
        var target = TargetValueResolver.TargetContext.property(
            propertyInfo, invokingType, parentsUnderInitialization, assignmentSource);
        TargetValueResolver.ResolutionResult result = TargetValueResolver.resolveSequence(
            context, input, target);

        if (result instanceof TargetValueResolver.ResolutionResult.Applicable applicable) {
            TargetValueResolver.Lowered lowered = applicable.plan().lower();
            if (lowered instanceof TargetValueResolver.Lowered.Property property) {
                return new ValueAssignmentResolution.Assign(property.node());
            }

            if (lowered instanceof TargetValueResolver.Lowered.Value value) {
                return new ValueAssignmentResolution.Assign(
                    createSetter(propertyInfo, (ValueEmitterNode)value.node(), input.sourceInfo()));
            }
        } else if (result instanceof TargetValueResolver.ResolutionResult.Invalid invalid) {
            return new ValueAssignmentResolution.Invalid(invalid.diagnostic());
        }

        if (input.directValue() instanceof ValueNode valueNode) {
            ValueEmitterNode value = createEventHandlerNode(context, valueNode, propertyInfo.getType());
            if (value == null) {
                value = createTemplateContentNode(valueNode, propertyInfo.getType());
            }

            if (value != null) {
                return propertyInfo.isReadOnly()
                    ? targetConstraint(
                        input.directValue(), target, List.of(TypeHelper.getTypeInstance(value)),
                        PropertyAssignmentErrors.cannotModifyReadOnlyProperty(
                            assignmentSource, propertyInfo))
                    : new ValueAssignmentResolution.Assign(
                        createSetter(propertyInfo, value, input.sourceInfo()));
            }
        }

        TargetValueResolver.CandidateFailure failure =
            ((TargetValueResolver.ResolutionResult.NotApplicable)result).failure();
        return failure.diagnostic() != null
            ? new ValueAssignmentResolution.Error(failure)
            : new ValueAssignmentResolution.NotHandled();
    }

    private ValueAssignmentResolution targetConstraint(
            Node node,
            TargetValueResolver.TargetContext target,
            List<TypeInstance> sourceTypes,
            MarkupException diagnostic) {
        return new ValueAssignmentResolution.Error(new TargetValueResolver.CandidateFailure(
            TargetValueResolver.FailureKind.TARGET_CONSTRAINT,
            node.getSourceInfo(), target, sourceTypes, diagnostic));
    }

    private EmitterNode createSetter(
            PropertyInfo propertyInfo, ValueEmitterNode value, SourceInfo sourceInfo) {
        return propertyInfo.isStatic()
            ? new EmitStaticPropertySetterNode(
                propertyInfo.getDeclaringType(), propertyInfo, value, sourceInfo)
            : new EmitPropertySetterNode(propertyInfo, value, false, sourceInfo);
    }

    private ValueEmitterNode createEventHandlerNode(TransformContext context, ValueNode node, TypeInstance targetType) {
        if (targetType.subtypeOf(EventHandlerDecl()) && node instanceof LiteralValueNode literalNode) {
            return new EmitEventHandlerNode(
                context.getCodeBehindOrMarkupClass(),
                targetType.arguments().get(0),
                literalNode.getText().trim(),
                literalNode.getSourceInfo().getTrimmed());
        }

        return null;
    }

    private ValueEmitterNode createTemplateContentNode(ValueNode node, TypeInstance targetType) {
        if (node instanceof TemplateContentNode templateContentNode
                && Core.TemplateContentDecl() != null
                && targetType.subtypeOf(Core.TemplateContentDecl())) {
            return new EmitTemplateContentNode(
                targetType,
                templateContentNode.getItemType(),
                templateContentNode.getBindingContextClass(),
                (EmitObjectNode)templateContentNode.getContent(),
                node.getSourceInfo());
        }

        return null;
    }

    private sealed interface ValueAssignmentResolution {
        record NotHandled() implements ValueAssignmentResolution {}
        record Assign(EmitterNode node) implements ValueAssignmentResolution {}
        record Error(TargetValueResolver.CandidateFailure failure) implements ValueAssignmentResolution {
            MarkupException diagnostic() {
                return java.util.Objects.requireNonNull(failure.diagnostic());
            }
        }
        record Invalid(MarkupException error) implements ValueAssignmentResolution {}
    }

}

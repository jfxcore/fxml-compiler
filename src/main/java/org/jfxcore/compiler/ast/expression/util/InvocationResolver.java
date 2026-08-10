// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.expression.BindingOperator;
import org.jfxcore.compiler.ast.expression.ConstructorExpressionNode;
import org.jfxcore.compiler.ast.expression.ExpressionNode;
import org.jfxcore.compiler.ast.expression.FunctionExpressionNode;
import org.jfxcore.compiler.ast.expression.InvocationExpressionNode;
import org.jfxcore.compiler.ast.expression.PathExpressionNode;
import org.jfxcore.compiler.ast.text.PathNode;
import org.jfxcore.compiler.ast.text.PathSegmentNode;
import org.jfxcore.compiler.ast.text.TextSegmentNode;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.DiagnosticInfo;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.type.BehaviorDeclaration;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.AccessVerifier;
import org.jfxcore.compiler.util.Callable;
import org.jfxcore.compiler.util.MethodFinder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Resolves one neutral invocation into a method or constructor invocation plan.
 */
final class InvocationResolver extends AbstractFunctionEmitterFactory {

    record ResolvedInvocation(InvocationInfo invocation, boolean construction) {}

    private record Alternative(
        InvocationInfo invocation,
        boolean construction) {}

    private record ConstructorTarget(
        @Nullable ExpressionNode qualifier,
        TypeDeclaration type,
        PathNode sourceType) {}

    private record MemberTargetLookup(
        boolean qualifierResolved,
        @Nullable ConstructorTarget target) {}

    private final InvocationExpressionNode expression;

    InvocationResolver(
            InvocationExpressionNode expression,
            TypeInstance invokingType,
            @Nullable TypeInstance targetType) {
        super(invokingType, targetType);
        this.expression = expression;
    }

    ResolvedInvocation resolve(boolean bidirectional, boolean preferObservable) {
        MethodFinder.ResolvedCandidate selected = resolveCandidate(preferObservable);

        if (selected != null && selected.candidate().behavior() instanceof MethodDeclaration) {
            Alternative method = createMethodAlternative(bidirectional, preferObservable, selected);
            return new ResolvedInvocation(method.invocation(), false);
        }

        if (selected != null && selected.candidate().behavior() instanceof ConstructorDeclaration) {
            ConstructorExpressionNode constructorExpression = createConstructorExpression(preferObservable);
            InvocationInfo invocation = createConstructorInvocation(
                constructorExpression, preferObservable, bidirectional, selected);
            return new ResolvedInvocation(invocation, true);
        }

        return resolveFailure(bidirectional, preferObservable);
    }

    private @Nullable MethodFinder.ResolvedCandidate resolveCandidate(boolean preferObservable) {
        List<MethodFinder.InvocationCandidate> methodCandidates;
        List<MethodFinder.InvocationCandidate> constructorCandidates;

        try {
            methodCandidates = createMethodCandidates(preferObservable);
        } catch (MarkupException ignored) {
            methodCandidates = List.of();
        }

        try {
            constructorCandidates = createConstructorCandidates(preferObservable);
        } catch (MarkupException ignored) {
            constructorCandidates = List.of();
        }

        List<MethodFinder.InvocationCandidate> candidates = new ArrayList<>(
            methodCandidates.size() + constructorCandidates.size());
        candidates.addAll(methodCandidates);
        candidates.addAll(constructorCandidates);

        if (candidates.isEmpty()) {
            return null;
        }

        List<TypeInstance> argumentTypes = expression.getArguments().stream()
            .map(argument -> getArgumentType(argument, preferObservable))
            .toList();

        List<SourceInfo> argumentSources = expression.getArguments().stream()
            .map(Node::getSourceInfo)
            .toList();

        TypeInstance targetType = expression.getOperator().isBoolean() ? null : getTargetType();

        List<DiagnosticInfo> diagnostics = new ArrayList<>();

        List<MethodFinder.ResolvedCandidate> selected = MethodFinder.resolveInvocationCandidates(
            candidates, targetType, argumentTypes, argumentSources, diagnostics, expression.getSourceInfo());

        if (selected.isEmpty()) {
            return null;
        }

        boolean hasMethod = selected.stream().anyMatch(
            candidate -> candidate.candidate().behavior() instanceof MethodDeclaration);

        boolean hasConstructor = selected.stream().anyMatch(
            candidate -> candidate.candidate().behavior() instanceof ConstructorDeclaration);

        if (hasMethod && hasConstructor) {
            throw GeneralErrors.ambiguousMethodOrConstructorCall(
                expression.getTerminalSegment().getSourceInfo(),
                expression.getTerminalSegment().getText(),
                selected.stream()
                    .map(candidate -> candidate.candidate().behavior())
                    .toArray(BehaviorDeclaration[]::new));
        }

        if (selected.size() > 1) {
            BehaviorDeclaration behavior = selected.get(0).candidate().behavior();

            Diagnostic diagnostic = Diagnostic.newDiagnosticCauses(
                ErrorCode.AMBIGUOUS_METHOD_CALL,
                selected.stream()
                    .map(candidate -> candidate.candidate().behavior().longName())
                    .toArray(String[]::new),
                behavior.name());

            if (hasConstructor) {
                throw ObjectInitializationErrors.constructorNotFound(
                    expression.getSourceInfo(),
                    selected.get(0).resultType().declaration(),
                    new Diagnostic[] { diagnostic });
            }

            throw new MarkupException(
                expression.getPathTarget() != null
                    ? expression.getPathTarget().getSourceInfo()
                    : expression.getSourceInfo(),
                diagnostic);
        }

        return selected.get(0);
    }

    private List<MethodFinder.InvocationCandidate> createMethodCandidates(boolean preferObservable) {
        TypeDeclaration declaringType;
        List<TypeInstance> invocationContext;
        boolean staticInvocation;

        if (expression.getPathTarget() == null) {
            BindingEmitterInfo receiverInfo = expression.getReceiver().toEmitter(
                preferObservable ? BindingMode.UNIDIRECTIONAL : BindingMode.ONCE,
                getInvokingType(), null);

            TypeInstance receiverType = receiverInfo.getValueType();
            declaringType = receiverType.declaration();
            invocationContext = List.of(receiverType);
            staticInvocation = false;
        } else {
            PathExpressionNode path = expression.getPathTarget();
            List<PathSegmentNode> segments = path.getSegments();

            if (segments.size() == 1) {
                TypeInstance contextType = path.getBindingContext().getValueType();
                declaringType = contextType.declaration();
                invocationContext = List.of(contextType);
                staticInvocation = false;
            } else {
                int limit = segments.size() - 1;

                try {
                    TypeInstance receiverType = path.resolvePath(preferObservable, limit).getValueTypeInstance();
                    declaringType = receiverType.declaration();
                    invocationContext = List.of(receiverType);
                    staticInvocation = false;
                } catch (MarkupException ex) {
                    if (!path.getBindingContext().mayResolveAgainstImports()) {
                        throw ex;
                    }

                    String className = path.getSimplePath(limit);
                    Resolver resolver = new Resolver(expression.getTerminalSegment().getSourceInfo());
                    declaringType = resolver.tryResolveClassAgainstImports(className);
                    if (declaringType == null) {
                        declaringType = resolver.tryResolveNestedClass(
                            path.getBindingContext().getType().getTypeDeclaration(), className);
                    }

                    if (declaringType == null) {
                        throw ex;
                    }

                    invocationContext = List.of(getInvokingType());
                    staticInvocation = true;
                }
            }
        }

        List<TypeInstance> witnesses = expression.getTerminalSegment().getTypeArguments().stream()
            .map(PathNode::resolve)
            .toList();

        List<MethodFinder.InvocationCandidate> result = new ArrayList<>();

        for (MethodDeclaration method : declaringType.methods(expression.getTerminalSegment().getText())) {
            if (AccessVerifier.isAccessible(method, expression.getInvocationContext())) {
                result.add(new MethodFinder.InvocationCandidate(
                    method, invocationContext, witnesses, staticInvocation, null));
            }
        }

        return result;
    }

    private List<MethodFinder.InvocationCandidate> createConstructorCandidates(boolean preferObservable) {
        ConstructorExpressionNode constructorExpression = createConstructorExpression(preferObservable);
        if (constructorExpression == null) {
            return List.of();
        }

        BindingEmitterInfo qualifierInfo = constructorExpression.getQualifier() != null
            ? constructorExpression.getQualifier().toEmitter(
                preferObservable ? BindingMode.UNIDIRECTIONAL : BindingMode.ONCE,
                getInvokingType(), null)
            : null;

        TypeInstance owner = qualifierInfo != null ? qualifierInfo.getValueType() : null;
        SourceInfo typeSourceInfo = constructorExpression.getConstructedType().getSourceInfo();
        Resolver resolver = new Resolver(typeSourceInfo);
        TypeDeclaration constructedClass;

        if (owner == null) {
            constructedClass = resolver.resolveClassAgainstImports(
                constructorExpression.getConstructedType().formatText());

            if (!constructedClass.isStatic() && constructedClass.declaringType().isPresent()) {
                throw ObjectInitializationErrors.constructorNotFound(typeSourceInfo, constructedClass);
            }
        } else {
            String memberName = constructorExpression.getConstructedType().formatText();
            constructedClass = resolver.tryResolveNestedClass(owner.declaration(), memberName);

            if (constructedClass == null) {
                throw SymbolResolutionErrors.memberNotFound(typeSourceInfo, owner.declaration(), memberName);
            }

            if (constructedClass.isStatic() || constructedClass.declaringType().isEmpty()) {
                throw ObjectInitializationErrors.constructorNotFound(typeSourceInfo, constructedClass);
            }
        }

        AccessVerifier.verifyAccessible(constructedClass, expression.getInvocationContext(), typeSourceInfo);

        if (constructedClass.isAbstract()
                || constructedClass.isInterface()
                || constructedClass.isPrimitive()
                || constructedClass.isArray()) {
            throw ObjectInitializationErrors.constructorNotFound(typeSourceInfo, constructedClass);
        }

        List<TypeInstance> classArguments = constructorExpression.getClassArguments().stream()
            .map(PathNode::resolve)
            .toList();

        TypeInvoker invoker = new TypeInvoker(typeSourceInfo);

        TypeInstance constructedType = owner != null
            ? invoker.invokeType(owner, constructedClass, classArguments)
            : invoker.invokeType(constructedClass, classArguments);

        List<TypeInstance> invocationContext = getConstructionInvocationContext(constructedType);

        List<TypeInstance> witnesses = constructorExpression.getConstructorWitnesses().stream()
            .map(PathNode::resolve)
            .toList();

        return constructedClass.constructors().stream()
            .filter(constructor -> AccessVerifier.isAccessible(constructor, expression.getInvocationContext()))
            .map(constructor -> new MethodFinder.InvocationCandidate(
                constructor, invocationContext, witnesses, false, constructedType))
            .collect(Collectors.toList());
    }

    private static List<TypeInstance> getConstructionInvocationContext(TypeInstance constructedType) {
        var result = new ArrayList<TypeInstance>();
        addOwnerChain(result, constructedType);
        return result;
    }

    private static void addOwnerChain(List<TypeInstance> result, TypeInstance type) {
        TypeInstance owner = type.owner();
        if (owner != null) {
            addOwnerChain(result, owner);
        }

        result.add(type);
    }

    private ResolvedInvocation resolveFailure(boolean bidirectional, boolean preferObservable) {
        Alternative method = null;
        Alternative constructor = null;
        MarkupException methodError = null;
        MarkupException constructorError = null;

        try {
            method = createMethodAlternative(bidirectional, preferObservable);
        } catch (MarkupException ex) {
            methodError = ex;
        }

        ConstructorExpressionNode constructorExpression = null;

        try {
            constructorExpression = createConstructorExpression(preferObservable);

            if (constructorExpression != null) {
                InvocationInfo invocation = createConstructorInvocation(
                    constructorExpression, preferObservable, bidirectional);

                if (getTargetType() != null && !getTargetType().isAssignableFrom(invocation.type())) {
                    throw GeneralErrors.incompatibleReturnValue(
                        expression.getSourceInfo(), invocation.function().getBehavior(), getTargetType());
                }

                constructor = new Alternative(invocation, true);
            }
        } catch (MarkupException ex) {
            constructorError = ex;
        }

        if (method == null && constructor == null) {
            throw selectFailure(methodError, constructorError, constructorExpression != null);
        }

        if (method != null && constructor != null) {
            throw GeneralErrors.ambiguousMethodOrConstructorCall(
                expression.getTerminalSegment().getSourceInfo(),
                expression.getTerminalSegment().getText(),
                method.invocation().function().getBehavior(),
                constructor.invocation().function().getBehavior());
        }

        Alternative selected = method != null ? method : constructor;
        return new ResolvedInvocation(selected.invocation(), selected.construction());
    }

    private Alternative createMethodAlternative(boolean bidirectional, boolean preferObservable) {
        return createMethodAlternative(bidirectional, preferObservable, null);
    }

    private Alternative createMethodAlternative(
            boolean bidirectional,
            boolean preferObservable,
            @Nullable MethodFinder.ResolvedCandidate selected) {
        InvocationInfo invocation;

        if (expression.getPathTarget() != null) {
            FunctionExpressionNode function = new FunctionExpressionNode(
                expression.getInvocationContext(),
                expression.getPathTarget(),
                expression.getArguments(),
                expression.getInversePath(),
                expression.getSourceInfo());

            invocation = selected != null
                ? createInvocation(function, bidirectional, preferObservable, selected)
                : createInvocation(function, bidirectional, preferObservable);
        } else {
            invocation = createSelectedMethodInvocation(bidirectional, preferObservable, selected);
        }

        return new Alternative(invocation, false);
    }

    private InvocationInfo createSelectedMethodInvocation(
            boolean bidirectional,
            boolean preferObservable,
            @Nullable MethodFinder.ResolvedCandidate selected) {
        BindingEmitterInfo receiverInfo = expression.getReceiver().toEmitter(
            preferObservable ? BindingMode.UNIDIRECTIONAL : BindingMode.ONCE,
            getInvokingType(), null);

        TypeInstance receiverType = receiverInfo.getValueType();
        TextSegmentNode target = expression.getSelectedTarget();
        MethodDeclaration method;
        List<TypeInstance> invocationContext;
        TypeInstance[] parameterTypes;
        TypeInstance returnType;

        if (selected != null) {
            method = (MethodDeclaration)selected.candidate().behavior();
            invocationContext = selected.candidate().invocationContext();
            parameterTypes = selected.parameterTypes().toArray(TypeInstance[]::new);
            returnType = selected.resultType();
        } else {
            List<TypeInstance> witnesses = target.getTypeArguments().stream()
                .map(PathNode::resolve)
                .toList();

            List<TypeInstance> argumentTypes = expression.getArguments().stream()
                .map(argument -> getArgumentType(argument, preferObservable))
                .toList();

            List<SourceInfo> argumentSources = expression.getArguments().stream()
                .map(Node::getSourceInfo)
                .toList();

            invocationContext = List.of(receiverType);
            List<DiagnosticInfo> diagnostics = new ArrayList<>();
            MethodFinder finder = new MethodFinder(invocationContext, receiverType.declaration());

            method = finder.findMethod(
                target.getText(), false, getTargetType(), witnesses,
                argumentTypes, argumentSources, diagnostics, expression.getSourceInfo());

            if (method == null && getTargetType() != null && expression.getOperator().isBoolean()) {
                diagnostics.clear();

                method = finder.findMethod(
                    target.getText(), false, null, witnesses,
                    argumentTypes, argumentSources, diagnostics, expression.getSourceInfo());
            }

            if (method == null) {
                if (diagnostics.size() == 1) {
                    DiagnosticInfo diagnostic = diagnostics.get(0);
                    throw new MarkupException(diagnostic.getSourceInfo(), diagnostic.getDiagnostic());
                }

                if (!diagnostics.isEmpty()) {
                    throw BindingSourceErrors.cannotBindFunction(
                        expression.getSourceInfo(), diagnostics.stream()
                            .map(DiagnosticInfo::getDiagnostic).toArray(Diagnostic[]::new));
                }

                throw SymbolResolutionErrors.memberNotFound(
                    target.getSourceInfo(), receiverType.declaration(), target.getText());
            }

            TypeInvoker invoker = new TypeInvoker(expression.getSourceInfo());
            parameterTypes = invoker.invokeParameterTypes(method, invocationContext, witnesses);
            returnType = invoker.invokeReturnType(method, invocationContext, witnesses);
        }

        AccessVerifier.verifyAccessible(method, expression.getInvocationContext(), target.getSourceInfo());

        ObservableDependencyKind receiverDependency = getArgumentDependencyKind(receiverInfo);

        Callable callable = new Callable(
            invocationContext,
            List.of(receiverInfo.getValue()),
            receiverDependency,
            method,
            expression.getSourceInfo());

        PreparedArguments prepared = prepareArguments(
            method, parameterTypes, expression.getArguments(), bidirectional,
            preferObservable, receiverDependency != ObservableDependencyKind.NONE,
            expression.getSourceInfo());

        Callable inverse = null;

        if (bidirectional) {
            if (prepared.values().size() != 1) {
                throw BindingSourceErrors.invalidBidirectionalMethodParamCount(expression.getSourceInfo());
            }

            Node argument = expression.getArguments().get(0);
            if (!(argument instanceof PathExpressionNode)) {
                throw BindingSourceErrors.invalidBidirectionalMethodParamKind(argument.getSourceInfo());
            }

            if (expression.getInversePath() != null) {
                class ReturnValueNode extends AbstractNode implements ValueEmitterNode {
                    private final ResolvedTypeNode type = new ResolvedTypeNode(returnType, target.getSourceInfo());
                    ReturnValueNode() { super(target.getSourceInfo()); }
                    @Override public void emit(BytecodeEmitContext context) {}
                    @Override public ResolvedTypeNode getType() { return type; }
                    @Override public ReturnValueNode deepClone() { return this; }
                }

                PathExpressionNode inversePath = expression.getInversePath();
                List<PathNode> inverseTypeNodes = inversePath.getSegments()
                    .get(inversePath.getSegments().size() - 1).getTypeArguments();

                inverse = findInverseCallable(
                    inversePath,
                    parameterTypes[0],
                    inverseTypeNodes.stream().map(PathNode::resolve).toList(),
                    List.of(new ReturnValueNode()),
                    preferObservable);
            } else {
                inverse = findInverseFunctionViaAnnotation(
                    callable, parameterTypes[0], returnType, target.getSourceInfo());
            }
        }

        return new InvocationInfo(
            prepared.observable(), returnType, callable, inverse, prepared.values());
    }

    private @Nullable ConstructorExpressionNode createConstructorExpression(boolean preferObservable) {
        TextSegmentNode terminal = expression.getTerminalSegment();
        if (terminal.isObservableSelector()) {
            return null;
        }

        ConstructorTarget target = resolveConstructorTarget(preferObservable);
        if (target == null) {
            return null;
        }

        List<PathNode> flattened = terminal.getTypeArguments();
        int classCount = new TypeInvoker(terminal.getSourceInfo()).getTypeParameterCount(target.type());
        int split = flattened.isEmpty() ? 0 : Math.min(classCount, flattened.size());
        List<PathNode> classArguments = flattened.subList(0, split);
        List<PathNode> constructorWitnesses = flattened.subList(split, flattened.size());

        return new ConstructorExpressionNode(
            expression.getInvocationContext(),
            target.qualifier(),
            expression.getInversePath(),
            constructorWitnesses,
            target.sourceType(),
            classArguments,
            expression.getArguments(),
            expression.getSourceInfo());
    }

    private @Nullable ConstructorTarget resolveConstructorTarget(boolean preferObservable) {
        Resolver resolver = new Resolver(expression.getTerminalSegment().getSourceInfo());

        if (expression.getPathTarget() != null) {
            PathExpressionNode path = expression.getPathTarget();
            List<PathSegmentNode> segments = path.getSegments();

            if (segments.size() > 1
                    || segments.size() == 1 && path.getBindingContext().isExplicitReceiver()) {
                List<PathSegmentNode> qualifierSegments = segments.subList(0, segments.size() - 1);

                PathExpressionNode qualifier = new PathExpressionNode(
                    BindingOperator.IDENTITY,
                    path.getBindingContext(),
                    qualifierSegments,
                    qualifierSegments.isEmpty()
                        ? path.getBindingContext().getSourceInfo()
                        : SourceInfo.span(
                            qualifierSegments.get(0).getSourceInfo(),
                            qualifierSegments.get(qualifierSegments.size() - 1).getSourceInfo()));

                MemberTargetLookup lookup = lookupMemberConstructorTarget(
                    qualifier, expression.getTerminalSegment(), resolver, preferObservable);

                if (lookup.qualifierResolved()) {
                    return lookup.target();
                }
            }

            if (path.getBindingContext().mayResolveAgainstImports()
                    && segments.stream().allMatch(segment ->
                        segment instanceof TextSegmentNode && !segment.isObservableSelector())
                    && segments.stream().limit(segments.size() - 1L)
                        .allMatch(segment -> segment.getTypeArguments().isEmpty())) {
                String className = segments.stream()
                    .map(PathSegmentNode::getText)
                    .collect(Collectors.joining("."));

                TypeDeclaration type = resolver.tryResolveClassAgainstImports(className);
                if (type == null) {
                    type = resolver.tryResolveNestedClass(
                        expression.getInvocationContext(), className);
                }

                if (type != null) {
                    return new ConstructorTarget(null, type, sourceTypePath(segments));
                }
            }

            return null;
        }

        return lookupMemberConstructorTarget(
            expression.getReceiver(), expression.getSelectedTarget(),
            resolver, preferObservable).target();
    }

    private MemberTargetLookup lookupMemberConstructorTarget(
            ExpressionNode qualifier,
            TextSegmentNode terminal,
            Resolver resolver,
            boolean preferObservable) {
        try {
            BindingEmitterInfo qualifierInfo = qualifier.toEmitter(
                preferObservable ? BindingMode.UNIDIRECTIONAL : BindingMode.ONCE,
                getInvokingType(), null);

            TypeDeclaration type = resolver.tryResolveNestedClass(
                qualifierInfo.getValueType().declaration(), terminal.getText());

            if (type == null || type.isStatic() || type.declaringType().isEmpty()) {
                return new MemberTargetLookup(true, null);
            }

            return new MemberTargetLookup(true, new ConstructorTarget(
                qualifier, type, sourceTypePath(List.of(terminal))));
        } catch (MarkupException ignored) {
            return new MemberTargetLookup(false, null);
        }
    }

    private PathNode sourceTypePath(List<PathSegmentNode> sourceSegments) {
        List<PathSegmentNode> result = sourceSegments.stream().map(segment -> {
            TextSegmentNode text = (TextSegmentNode)segment;

            return (PathSegmentNode)new TextSegmentNode(
                text.isObservableSelector(),
                text.getValue().deepClone(),
                List.of(),
                text.getSelectorSourceInfo(),
                null,
                text.getValue().getSourceInfo());
        }).toList();

        return new PathNode(
            null, result, List.of(),
            SourceInfo.span(
                result.get(0).getSourceInfo(),
                result.get(result.size() - 1).getSourceInfo()));
    }

    private MarkupException selectFailure(
            @Nullable MarkupException methodError,
            @Nullable MarkupException constructorError,
            boolean constructorTargetExisted) {
        if (constructorError != null && constructorTargetExisted
                && (methodError == null || methodError.getDiagnostic().getCode() == ErrorCode.MEMBER_NOT_FOUND)) {
            return constructorError;
        }

        if (methodError != null && constructorError == null) {
            return methodError;
        }

        if (constructorError != null && methodError == null) {
            return constructorError;
        }

        if (methodError != null) {
            return BindingSourceErrors.cannotBindFunction(
                expression.getSourceInfo(),
                new Diagnostic[] { methodError.getDiagnostic(), constructorError.getDiagnostic() });
        }

        return SymbolResolutionErrors.memberNotFound(
            expression.getTerminalSegment().getSourceInfo(),
            getInvocationHost(),
            expression.getTerminalSegment().getText());
    }

    private TypeDeclaration getInvocationHost() {
        if (expression.getReceiver() != null) {
            return getArgumentType(expression.getReceiver(), false).declaration();
        }

        PathExpressionNode path = expression.getPathTarget();
        List<PathSegmentNode> segments = path.getSegments();

        if (segments.size() > 1) {
            try {
                return path.resolvePath(false, segments.size() - 1).getValueTypeInstance().declaration();
            } catch (MarkupException ignored) {
                if (path.getBindingContext().mayResolveAgainstImports()) {
                    String className = path.getSimplePath(segments.size() - 1);
                    Resolver resolver = new Resolver(expression.getTerminalSegment().getSourceInfo());
                    TypeDeclaration type = resolver.tryResolveClassAgainstImports(className);
                    if (type != null) {
                        return type;
                    }
                }
            }
        }

        return path.getBindingContext().getType().getTypeDeclaration();
    }
}

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
import org.jfxcore.compiler.ast.expression.TargetTypeNotApplicableException;
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
import org.jfxcore.compiler.util.ApplicableInvocationCandidate;
import org.jfxcore.compiler.util.Callable;
import org.jfxcore.compiler.util.InvocationCandidate;
import org.jfxcore.compiler.util.MethodFinder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Resolves an invocation into a method or constructor invocation plan.
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

    private record CandidateSelection(
        @Nullable ApplicableInvocationCandidate candidate,
        List<DiagnosticInfo> diagnostics) {}

    private final InvocationExpressionNode expression;
    private final @Nullable TypeInstance targetType;
    private boolean staticInvocation;

    InvocationResolver(
            InvocationExpressionNode expression,
            TypeInstance invokingType,
            @Nullable TypeInstance targetType) {
        super(invokingType);
        this.expression = expression;
        this.targetType = targetType;
    }

    ResolvedInvocation emitSelected(
            ApplicableInvocationCandidate selected,
            boolean bidirectional,
            boolean preferObservable) {
        if (selected.candidate().behavior() instanceof MethodDeclaration) {
            Alternative method = createMethodAlternative(bidirectional, preferObservable, selected);
            return new ResolvedInvocation(method.invocation(), false);
        }

        if (selected.candidate().behavior() instanceof ConstructorDeclaration) {
            ConstructorExpressionNode constructorExpression = createConstructorExpression(preferObservable);

            InvocationInfo invocation = createConstructorInvocation(
                Objects.requireNonNull(constructorExpression), preferObservable, bidirectional, selected);

            return new ResolvedInvocation(invocation, true);
        }

        throw new AssertionError(selected.candidate().behavior().getClass().getName());
    }

    ApplicableInvocationCandidate resolveCandidate(boolean preferObservable) {
        List<InvocationCandidate> methodCandidates;
        List<InvocationCandidate> constructorCandidates;
        MarkupException methodError = null;
        MarkupException constructorError = null;

        try {
            methodCandidates = createMethodCandidates(preferObservable);
        } catch (MarkupException ex) {
            methodCandidates = List.of();
            methodError = ex;
        }

        try {
            constructorCandidates = createConstructorCandidates(preferObservable);
        } catch (MarkupException ex) {
            constructorCandidates = List.of();
            constructorError = ex;
        }

        List<InvocationCandidate> candidates = new ArrayList<>(methodCandidates.size() + constructorCandidates.size());
        candidates.addAll(methodCandidates);
        candidates.addAll(constructorCandidates);

        CandidateSelection selection = selectCandidate(
            preferAccessibleCandidates(candidates), preferObservable, true, targetType);

        if (selection.candidate() == null && targetType != null && expression.getOperator().isBoolean()) {
            selection = selectCandidate(preferAccessibleCandidates(candidates), preferObservable, true, null);
        }

        if (selection.candidate() == null) {
            boolean targetMismatch = targetType != null
                && hasTargetIndependentCandidate(preferAccessibleCandidates(candidates), preferObservable);

            try {
                throwCandidateFailure(
                    selection.diagnostics(), methodCandidates, constructorCandidates,
                    methodError, constructorError);
            } catch (MarkupException ex) {
                throw targetMismatch ? new TargetTypeNotApplicableException(ex) : ex;
            }
        }

        verifyAccessible(selection.candidate());
        return selection.candidate();
    }

    ApplicableInvocationCandidate resolveMethodCandidate(boolean preferObservable) {
        List<InvocationCandidate> candidates = createMethodCandidates(preferObservable);

        CandidateSelection selection = selectCandidate(
            preferAccessibleCandidates(candidates), preferObservable, false, targetType);

        if (selection.candidate() == null && targetType != null && expression.getOperator().isBoolean()) {
            selection = selectCandidate(preferAccessibleCandidates(candidates), preferObservable, false, null);
        }

        if (selection.candidate() == null) {
            boolean targetMismatch = targetType != null
                && hasTargetIndependentCandidate(preferAccessibleCandidates(candidates), preferObservable);

            try {
                throwMethodFailure(selection.diagnostics());
            } catch (MarkupException ex) {
                throw targetMismatch ? new TargetTypeNotApplicableException(ex) : ex;
            }
        }

        verifyAccessible(selection.candidate());
        return selection.candidate();
    }

    private CandidateSelection selectCandidate(
            List<InvocationCandidate> candidates,
            boolean preferObservable,
            boolean mixedCategories,
            @Nullable TypeInstance targetType) {
        if (candidates.isEmpty()) {
            return new CandidateSelection(null, List.of());
        }

        List<TypeInstance> argumentTypes = expression.getArguments().stream()
            .map(argument -> resolveArgumentType(argument, preferObservable))
            .toList();

        List<SourceInfo> argumentSources = expression.getArguments().stream()
            .map(Node::getSourceInfo)
            .toList();

        List<DiagnosticInfo> diagnostics = new ArrayList<>();

        List<ApplicableInvocationCandidate> selected = MethodFinder.resolveInvocationCandidates(
            candidates, targetType, argumentTypes, argumentSources, diagnostics, invocationSourceInfo());

        if (selected.isEmpty()) {
            return new CandidateSelection(null, List.copyOf(diagnostics));
        }

        boolean hasMethod = selected.stream().anyMatch(
            candidate -> candidate.candidate().behavior() instanceof MethodDeclaration);

        boolean hasConstructor = selected.stream().anyMatch(
            candidate -> candidate.candidate().behavior() instanceof ConstructorDeclaration);

        if (mixedCategories && hasMethod && hasConstructor) {
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

        return new CandidateSelection(selected.get(0), List.of());
    }

    /**
     * Tests invocation applicability without a return target. A non-empty result proves that the
     * target-aware failure was caused only by the requested return type, even when target-free
     * resolution would itself be ambiguous.
     */
    private boolean hasTargetIndependentCandidate(List<InvocationCandidate> candidates, boolean preferObservable) {
        if (candidates.isEmpty()) {
            return false;
        }

        List<TypeInstance> argumentTypes = expression.getArguments().stream()
            .map(argument -> resolveArgumentType(argument, preferObservable))
            .toList();

        List<SourceInfo> argumentSources = expression.getArguments().stream()
            .map(Node::getSourceInfo)
            .toList();

        return !MethodFinder.resolveInvocationCandidates(
            candidates, null, argumentTypes, argumentSources, null, invocationSourceInfo()).isEmpty();
    }

    private List<InvocationCandidate> preferAccessibleCandidates(List<InvocationCandidate> candidates) {
        List<InvocationCandidate> accessible = candidates.stream()
            .filter(candidate -> AccessVerifier.isAccessible(candidate.behavior(), expression.getInvocationContext()))
            .toList();

        return accessible.isEmpty() ? candidates : accessible;
    }

    private List<InvocationCandidate> createMethodCandidates(boolean preferObservable) {
        TypeDeclaration declaringType;
        List<TypeInstance> invocationContext;
        boolean staticInvocation;

        if (expression.getPathTarget() == null) {
            ExpressionNode receiver = Objects.requireNonNull(expression.getReceiver());
            TypeInstance receiverType = getExpressionValueType(receiver, preferObservable);
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
                        throw BindingSourceErrors.bindingContextNotApplicable(
                            path.getBindingContext().getSourceInfo());
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

        this.staticInvocation = staticInvocation;

        List<InvocationCandidate> result = new ArrayList<>();

        for (MethodDeclaration method : declaringType.methods(expression.getTerminalSegment().getText())) {
            result.add(new InvocationCandidate(method, invocationContext, witnesses, false, null));
        }

        return result;
    }

    private List<InvocationCandidate> createConstructorCandidates(boolean preferObservable) {
        ConstructorExpressionNode constructorExpression = createConstructorExpression(preferObservable);
        if (constructorExpression == null) {
            return List.of();
        }

        TypeInstance owner = constructorExpression.getQualifier() != null
            ? getExpressionValueType(constructorExpression.getQualifier(), preferObservable)
            : null;

        SourceInfo typeSourceInfo = constructorExpression.getConstructedType().getSourceInfo();
        Resolver resolver = new Resolver(typeSourceInfo);
        TypeDeclaration constructedClass;

        if (owner == null) {
            constructedClass = resolver.resolveClassAgainstImports(
                constructorExpression.getConstructedType().format());

            if (!constructedClass.isStatic() && constructedClass.declaringType().isPresent()) {
                throw ObjectInitializationErrors.constructorNotFound(typeSourceInfo, constructedClass);
            }
        } else {
            String memberName = constructorExpression.getConstructedType().format();
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
            .map(constructor -> new InvocationCandidate(
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

    private Alternative createMethodAlternative(
            boolean bidirectional,
            boolean preferObservable,
            ApplicableInvocationCandidate selected) {
        InvocationInfo invocation;

        if (expression.getPathTarget() != null) {
            FunctionExpressionNode function = new FunctionExpressionNode(
                expression.getInvocationContext(),
                expression.getPathTarget(),
                expression.getArguments(),
                expression.getInversePath(),
                expression.getSourceInfo());

            invocation = createInvocation(function, bidirectional, preferObservable, selected);
        } else {
            invocation = createSelectedMethodInvocation(bidirectional, preferObservable, selected);
        }

        return new Alternative(invocation, false);
    }

    private InvocationInfo createSelectedMethodInvocation(
            boolean bidirectional,
            boolean preferObservable,
            ApplicableInvocationCandidate selected) {
        ExpressionNode receiver = Objects.requireNonNull(expression.getReceiver());
        BindingEmitterInfo receiverInfo = receiver.resolve(
            preferObservable ? BindingMode.UNIDIRECTIONAL : BindingMode.ONCE,
            getInvokingType(), null).toEmitter();

        TextSegmentNode target = Objects.requireNonNull(expression.getSelectedTarget());
        MethodDeclaration method = (MethodDeclaration)selected.candidate().behavior();
        List<TypeInstance> invocationContext = selected.candidate().invocationContext();
        TypeInstance[] parameterTypes = selected.parameterTypes().toArray(TypeInstance[]::new);
        TypeInstance returnType = selected.resultType();

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

    @Nullable ConstructorExpressionNode createConstructorExpression(boolean preferObservable) {
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
            expression.getReceiver(), Objects.requireNonNull(expression.getSelectedTarget()),
            resolver, preferObservable).target();
    }

    private MemberTargetLookup lookupMemberConstructorTarget(
            ExpressionNode qualifier,
            TextSegmentNode terminal,
            Resolver resolver,
            boolean preferObservable) {
        try {
            TypeDeclaration type = resolver.tryResolveNestedClass(
                getExpressionValueType(qualifier, preferObservable).declaration(),
                terminal.getText());

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

    private void verifyAccessible(ApplicableInvocationCandidate selected) {
        SourceInfo invocationSource = expression.getPathTarget() != null
            ? expression.getPathTarget().getSourceInfo()
            : expression.getTerminalSegment().getSourceInfo();

        if (staticInvocation
                && selected.candidate().behavior() instanceof MethodDeclaration method
                && !method.isStatic()) {
            throw SymbolResolutionErrors.instanceMemberReferencedFromStaticContext(
                invocationSource, method);
        }

        AccessVerifier.verifyAccessible(
            selected.candidate().behavior(),
            expression.getInvocationContext(),
            expression.getTerminalSegment().getSourceInfo());
    }

    private void throwCandidateFailure(
            List<DiagnosticInfo> diagnostics,
            List<InvocationCandidate> methodCandidates,
            List<InvocationCandidate> constructorCandidates,
            @Nullable MarkupException methodError,
            @Nullable MarkupException constructorError) {
        if (!diagnostics.isEmpty()) {
            if (methodCandidates.isEmpty() && !constructorCandidates.isEmpty()) {
                TypeInstance resultType = Objects.requireNonNull(constructorCandidates.get(0).resultType());
                TypeDeclaration constructedType = resultType.declaration();

                throw ObjectInitializationErrors.constructorNotFound(
                    expression.getSourceInfo(), constructedType,
                    diagnostics.stream()
                        .map(DiagnosticInfo::getDiagnostic).toArray(Diagnostic[]::new));
            }

            throwMethodFailure(diagnostics);
        }

        if (constructorError != null && methodError == null) {
            throw invocationError(constructorError);
        }

        if (methodError != null && constructorError == null) {
            throw invocationError(methodError);
        }

        if (methodError != null) {
            throw BindingSourceErrors.cannotBindFunction(
                invocationSourceInfo(),
                new Diagnostic[] {methodError.getDiagnostic(), constructorError.getDiagnostic()});
        }

        throw SymbolResolutionErrors.memberNotFound(
            invocationSourceInfo(),
            getInvocationHost(), expression.getTerminalSegment().getText());
    }

    private void throwMethodFailure(List<DiagnosticInfo> diagnostics) {
        if (diagnostics.size() == 1) {
            DiagnosticInfo diagnostic = diagnostics.get(0);
            throw new MarkupException(diagnostic.getSourceInfo(), diagnostic.getDiagnostic());
        }

        if (!diagnostics.isEmpty()) {
            throw BindingSourceErrors.cannotBindFunction(
                invocationSourceInfo(), diagnostics.stream()
                    .map(DiagnosticInfo::getDiagnostic)
                    .toArray(Diagnostic[]::new));
        }

        throw SymbolResolutionErrors.memberNotFound(
            invocationSourceInfo(),
            getInvocationHost(), expression.getTerminalSegment().getText());
    }

    private SourceInfo invocationSourceInfo() {
        if (expression.getPathTarget() == null) {
            return expression.getSelectedTarget().getSourceInfo();
        }

        PathExpressionNode path = expression.getPathTarget();
        SourceInfo start = path.getBindingContext().isExplicitReceiver()
            ? path.getBindingContext().getSourceInfo()
            : path.getSegments().get(0).getSourceInfo();

        return SourceInfo.span(start, path.getSegments().get(path.getSegments().size() - 1).getSourceInfo());
    }

    private MarkupException invocationError(MarkupException error) {
        if (error.getDiagnostic().getCode() != ErrorCode.MEMBER_NOT_FOUND) {
            return error;
        }

        MarkupException result = new MarkupException(invocationSourceInfo(), error.getDiagnostic(), error);
        result.getProperties().putAll(error.getProperties());
        return result;
    }

    TypeDeclaration getInvocationHost() {
        if (expression.getReceiver() != null) {
            return resolveArgumentType(expression.getReceiver(), false).declaration();
        }

        PathExpressionNode path = Objects.requireNonNull(expression.getPathTarget());
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

    private TypeInstance getExpressionValueType(ExpressionNode expression, boolean preferObservable) {
        return expression.resolve(
            preferObservable ? BindingMode.UNIDIRECTIONAL : BindingMode.ONCE,
            getInvokingType(), null).getTypeInfo().valueType();
    }
}

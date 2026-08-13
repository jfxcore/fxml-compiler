// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ValueSourceKind;
import org.jfxcore.compiler.ast.emit.EmitInvariantPathNode;
import org.jfxcore.compiler.ast.emit.EmitCompiledExpressionNode;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.emit.EmitMethodArgumentNode;
import org.jfxcore.compiler.ast.emit.EmitObservablePathNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingOperator;
import org.jfxcore.compiler.ast.expression.BindingTypeInfo;
import org.jfxcore.compiler.ast.expression.CompiledExpressionNode;
import org.jfxcore.compiler.ast.expression.ConstructorExpressionNode;
import org.jfxcore.compiler.ast.expression.ExpressionAnalysisContext;
import org.jfxcore.compiler.ast.expression.ExpressionNode;
import org.jfxcore.compiler.ast.expression.ExpressionResolution;
import org.jfxcore.compiler.ast.expression.FunctionExpressionNode;
import org.jfxcore.compiler.ast.expression.InvocationExpressionNode;
import org.jfxcore.compiler.ast.expression.LiteralExpressionNode;
import org.jfxcore.compiler.ast.expression.MemberAccessExpressionNode;
import org.jfxcore.compiler.ast.expression.PathExpressionNode;
import org.jfxcore.compiler.ast.expression.path.ExpressionSegment;
import org.jfxcore.compiler.ast.expression.path.ResolvedPath;
import org.jfxcore.compiler.ast.expression.path.Segment;
import org.jfxcore.compiler.ast.text.PathNode;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.DiagnosticInfo;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.transform.markup.util.MarkupExtensionInfo;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.AccessVerifier;
import org.jfxcore.compiler.util.ApplicableInvocationCandidate;
import org.jfxcore.compiler.util.InvocationCandidate;
import org.jfxcore.compiler.util.MethodFinder;
import org.jfxcore.compiler.util.NameHelper;
import java.util.ArrayList;
import java.util.List;

import static org.jfxcore.compiler.type.KnownSymbols.*;

public final class ExpressionResolver {

    private final TypeInstance invokingType;

    private ExpressionResolver(TypeInstance invokingType) {
        this.invokingType = invokingType;
    }

    public static ExpressionResolution resolve(
            ExpressionNode expression,
            BindingMode bindingMode,
            TypeInstance invokingType,
            @Nullable TypeInstance targetType) {
        return new ExpressionResolver(invokingType).resolveImpl(expression, bindingMode, targetType);
    }

    static ExpressionResolution resolveArgument(
            ExpressionNode expression,
            BindingMode bindingMode,
            TypeInstance invokingType,
            @Nullable TypeInstance targetType) {
        ExpressionResolver resolver = new ExpressionResolver(invokingType);
        return expression instanceof PathExpressionNode path
            ? resolver.resolvePath(path, bindingMode, true)
            : resolver.resolveImpl(expression, bindingMode, targetType);
    }

    private ExpressionResolution resolveImpl(
            ExpressionNode expression,
            BindingMode bindingMode,
            @Nullable TypeInstance targetType) {
        if (expression instanceof PathExpressionNode path) {
            return resolvePath(path, bindingMode);
        }

        if (expression instanceof LiteralExpressionNode literal) {
            return resolveLiteral(literal);
        }

        if (expression instanceof CompiledExpressionNode compiled) {
            return resolveCompiled(compiled, bindingMode);
        }

        if (expression instanceof MemberAccessExpressionNode member) {
            return resolveMemberAccess(member, bindingMode);
        }

        if (expression instanceof FunctionExpressionNode function) {
            return resolveFunction(function, bindingMode, targetType);
        }

        if (expression instanceof ConstructorExpressionNode constructor) {
            return resolveConstructor(constructor, bindingMode);
        }

        if (expression instanceof InvocationExpressionNode invocation) {
            return resolveInvocation(invocation, bindingMode, targetType);
        }

        throw GeneralErrors.expressionNotApplicable(expression.getSourceInfo(), false);
    }

    private ExpressionResolution resolveLiteral(LiteralExpressionNode expression) {
        TypeInstance type = expression.getLiteralType();
        SourceInfo sourceInfo = expression.getSourceInfo();
        BindingTypeInfo typeInfo = new BindingTypeInfo(
            type, type, null, ValueSourceKind.NONE,
            ObservableDependencyKind.get(type.declaration()),
            null, "literal", false, false, type.equals(TypeInstance.nullType()), sourceInfo);

        return new ExpressionResolution(
            typeInfo, () -> new EmitLiteralNode(type, expression.getLiteral(), sourceInfo));
    }

    private ExpressionResolution resolvePath(PathExpressionNode expression, BindingMode mode) {
        return resolvePath(expression, mode, false);
    }

    private ExpressionResolution resolvePath(
            PathExpressionNode expression,
            BindingMode mode,
            boolean allowDirectContentSource) {
        boolean preferObservable = mode.isObservable();

        if (preferObservable) {
            ResolvedPath observablePath = expression.resolvePath(true);

            if (mode == BindingMode.BIDIRECTIONAL) {
                validateObservablePath(observablePath, expression.getOperator(), mode, expression.getSourceInfo());
            }

            if (!observablePath.isInvariant() && (allowDirectContentSource || !isDirectContentSource(observablePath))) {
                BindingTypeInfo info = observablePathInfo(
                    observablePath, expression.getOperator(), mode, expression.getSourceInfo());

                return new ExpressionResolution(
                    info, () -> emitObservablePath(
                        expression.getOperator(), observablePath, mode, expression.getSourceInfo()));
            }
        }

        ResolvedPath path = expression.resolvePath(false);
        TypeInstance valueType = expression.getOperator().evaluateType(path.getValueTypeInstance());
        Segment last = path.get(path.size() - 1);

        BindingTypeInfo info = new BindingTypeInfo(
            valueType, valueType, null, ValueSourceKind.NONE,
            path.getObservableDependencyKind(), last.getDeclaringType(), last.getDisplayName(),
            false, false, invariantPathMayBeNull(expression.getOperator(), path),
            expression.getSourceInfo());

        return new ExpressionResolution(
            info,
            () -> emitInvariantPath(expression.getOperator(), path, expression.getSourceInfo()));
    }

    private ExpressionResolution resolveMemberAccess(
            MemberAccessExpressionNode expression, BindingMode mode) {
        ExpressionResolution receiver = resolveImpl(
            expression.getReceiver(),
            mode.isObservable()
                ? BindingMode.UNIDIRECTIONAL
                : BindingMode.ONCE,
            null);

        ResolvedPath path = ResolvedPath.parse(
            new ExpressionSegment(expression.getReceiver(), receiver), expression.getSegments(), false,
            mode.isObservable(), expression.getSourceInfo());

        if (mode == BindingMode.BIDIRECTIONAL) {
            validateObservablePath(path, expression.getOperator(), mode, expression.getSourceInfo());
        }

        if (mode.isObservable() && !path.isInvariant() && !isDirectContentSource(path)) {
            BindingTypeInfo info = observablePathInfo(
                path, expression.getOperator(), mode, expression.getSourceInfo());

            return new ExpressionResolution(
                info, () -> emitObservablePath(
                    expression.getOperator(), path, mode, expression.getSourceInfo()));
        }

        TypeInstance valueType = expression.getOperator().evaluateType(path.getValueTypeInstance());
        Segment last = path.get(path.size() - 1);

        BindingTypeInfo info = new BindingTypeInfo(
            valueType, valueType, null, ValueSourceKind.NONE,
            path.getObservableDependencyKind(), last.getDeclaringType(), last.getDisplayName(),
            false, false, invariantPathMayBeNull(expression.getOperator(), path),
            expression.getSourceInfo());

        return new ExpressionResolution(
            info, () -> emitInvariantPath(
                expression.getOperator(), path, expression.getSourceInfo()));
    }

    private ExpressionResolution resolveCompiled(CompiledExpressionNode expression, BindingMode mode) {
        if (mode.isContent() || mode.isReverse()) {
            throw GeneralErrors.expressionNotApplicable(expression.getSourceInfo(), false);
        }

        if (mode.isBidirectional()) {
            throw BindingSourceErrors.expressionNotInvertible(expression.getSourceInfo());
        }

        var context = new ExpressionAnalysisContext(mode, invokingType);
        TypeInstance valueType = context.analyze(expression.getRoot());
        context.allocateInputSlots();

        if (context.getParameterSlots() > 255) {
            throw GeneralErrors.expressionTooComplex(expression.getSourceInfo());
        }

        boolean observable = mode.isObservable() && context.getInputs().stream()
            .map(ExpressionAnalysisContext.Input::resolution)
            .filter(java.util.Objects::nonNull)
            .anyMatch(input -> input.getTypeInfo().isObservableArgument());

        TypeInstance emittedType = observable
            ? new Resolver(expression.getSourceInfo()).getObservableClass(valueType)
            : valueType;

        BindingTypeInfo info = new BindingTypeInfo(
            emittedType, valueType, observable ? emittedType : null,
            observable ? ValueSourceKind.get(emittedType.declaration()) : ValueSourceKind.NONE,
            ObservableDependencyKind.get(emittedType.declaration()),
            expression.getInvocationContext(), expression.getSourceName(),
            true, false, !observable && !valueType.isPrimitive(), expression.getSourceInfo());

        return new ExpressionResolution(info, () -> {
            TypeDeclaration[] parameterTypes = context.getInputs().stream()
                .map(ExpressionAnalysisContext.Input::parameterType)
                .toArray(TypeDeclaration[]::new);

            String helperName = NameHelper.getUniqueName("eval", expression);

            List<EmitMethodArgumentNode> arguments = new CompiledExpressionEmitterFactory()
                .createArguments(context.getInputs());

            ValueEmitterNode body = expression.getRoot().toEmitter(context);

            return new EmitCompiledExpressionNode(
                expression.getInvocationContext(), helperName, valueType, parameterTypes,
                body, arguments, observable, expression.getSourceInfo());
        });
    }

    private ExpressionResolution resolveFunction(
            FunctionExpressionNode expression,
            BindingMode mode,
            @Nullable TypeInstance targetType) {
        boolean preferObservable = mode.isObservable();
        boolean bidirectional = mode == BindingMode.BIDIRECTIONAL;
        TypeInstance effectiveTargetType = mode == BindingMode.REVERSE ? null : targetType;

        var invocation = new InvocationExpressionNode(
            expression.getInvocationContext(), expression.getPath().getOperator(),
            expression.getPath(), expression.getArguments(), expression.getInversePath(),
            expression.getSourceInfo());

        ApplicableInvocationCandidate selected = new InvocationResolver(
            invocation, invokingType, effectiveTargetType)
            .resolveMethodCandidate(preferObservable);

        if (bidirectional) {
            validateBidirectionalArguments(invocation);
        }

        BindingTypeInfo info = getInvocationInfo(invocation, selected, false, mode);

        return new ExpressionResolution(info, () -> {
            if (info.valueSourceType() != null) {
                ValueEmitterNode result = new ObservableFunctionEmitterFactory(
                    expression, invokingType, selected).newInstance(bidirectional);

                if (result == null) {
                    throw new IllegalStateException("Resolved observable function emitted an invariant value");
                }

                return result;
            }

            return new SimpleFunctionEmitterFactory(
                expression, invokingType, selected, preferObservable).newInstance();
        });
    }

    private ExpressionResolution resolveInvocation(
            InvocationExpressionNode expression,
            BindingMode mode,
            @Nullable TypeInstance targetType) {
        boolean preferObservable = mode.isObservable();
        boolean bidirectional = mode == BindingMode.BIDIRECTIONAL;
        TypeInstance effectiveTargetType = mode == BindingMode.REVERSE ? null : targetType;
        InvocationResolver resolver = new InvocationResolver(expression, invokingType, effectiveTargetType);
        ApplicableInvocationCandidate selected = resolver.resolveCandidate(preferObservable);

        boolean construction = selected.candidate().behavior() instanceof ConstructorDeclaration;
        if (construction && mode.isContent()) {
            throw GeneralErrors.expressionNotApplicable(expression.getSourceInfo(), false);
        }

        if (construction && mode.isReverse()) {
            throw BindingSourceErrors.expressionNotInvertible(expression.getSourceInfo());
        }

        if (bidirectional) {
            validateBidirectionalArguments(expression);
        }

        BindingTypeInfo info = getInvocationInfo(expression, selected, construction, mode);

        return new ExpressionResolution(info, () -> {
            if (info.valueSourceType() != null) {
                ValueEmitterNode result = new ObservableInvocationEmitterFactory(
                    expression, invokingType, selected).newInstance(bidirectional);

                if (result == null) {
                    throw new IllegalStateException("Resolved observable invocation emitted an invariant value");
                }

                return result;
            }

            return new SimpleInvocationEmitterFactory(expression, invokingType, selected).newInstance();
        });
    }

    private BindingTypeInfo getInvocationInfo(
            InvocationExpressionNode expression,
            ApplicableInvocationCandidate selected,
            boolean construction,
            BindingMode mode) {
        boolean preferObservable = mode.isObservable();
        boolean observable = preferObservable
            && (receiverIsObservable(expression, selected, construction)
                || expression.getArguments().stream().anyMatch(this::isObservableArgument));

        TypeInstance rawValueType = selected.resultType();
        if (mode == BindingMode.BIDIRECTIONAL && observable && !expression.getOperator().isInvertible(rawValueType)) {
            throw BindingSourceErrors.expressionNotInvertible(expression.getSourceInfo());
        }

        TypeInstance valueType = expression.getOperator().evaluateType(rawValueType);
        TypeInstance emittedType = observable
            ? mode == BindingMode.BIDIRECTIONAL
                ? new Resolver(expression.getSourceInfo()).getObservableClass(rawValueType)
                : observableOperatorType(
                    new Resolver(expression.getSourceInfo()).getObservableClass(rawValueType),
                    rawValueType, expression.getOperator(), expression.getSourceInfo())
            : valueType;

        TypeDeclaration sourceType = construction
            ? rawValueType.declaration()
            : selected.candidate().behavior().declaringType();

        String sourceName = construction
            ? rawValueType.declaration().simpleName()
            : selected.candidate().behavior().name();

        return new BindingTypeInfo(
            emittedType, valueType, observable ? emittedType : null,
            observable ? ValueSourceKind.get(emittedType.declaration()) : ValueSourceKind.NONE,
            ObservableDependencyKind.get(emittedType.declaration()),
            sourceType, sourceName, true, false,
            !observable && !construction && !valueType.isPrimitive(), expression.getSourceInfo());
    }

    private boolean receiverIsObservable(
            InvocationExpressionNode expression,
            ApplicableInvocationCandidate selected,
            boolean construction) {
        if (construction) {
            ConstructorExpressionNode constructor = new InvocationResolver(
                expression, invokingType, null).createConstructorExpression(true);

            return constructor != null && constructor.getQualifier() != null
                && resolveImpl(constructor.getQualifier(), BindingMode.UNIDIRECTIONAL, null)
                    .getTypeInfo().isObservableArgument();
        }

        if (selected.candidate().behavior().isStatic()) {
            return false;
        }

        if (expression.getReceiver() != null) {
            return resolveImpl(expression.getReceiver(), BindingMode.UNIDIRECTIONAL, null)
                .getTypeInfo().isObservableArgument();
        }

        PathExpressionNode path = expression.getPathTarget();
        int limit = path.getSegments().size() - 1;

        if (limit == 0) {
            return path.getBindingContext().toSegment().hasObservableDependency();
        }

        if (selected.candidate().staticInvocation()) {
            return false;
        }

        return path.resolvePath(true, limit).isObservable();
    }

    private ExpressionResolution resolveConstructor(ConstructorExpressionNode expression, BindingMode mode) {
        if (mode.isContent()) {
            throw GeneralErrors.expressionNotApplicable(expression.getSourceInfo(), false);
        }

        if (mode.isReverse()) {
            throw BindingSourceErrors.expressionNotInvertible(expression.getSourceInfo());
        }

        boolean preferObservable = mode.isObservable();

        ExpressionResolution qualifier = expression.getQualifier() != null
            ? resolveImpl(
                expression.getQualifier(),
                preferObservable ? BindingMode.UNIDIRECTIONAL : BindingMode.ONCE,
                null)
            : null;

        TypeInstance owner = qualifier != null ? qualifier.getTypeInfo().valueType() : null;
        SourceInfo typeSourceInfo = expression.getConstructedType().getSourceInfo();
        Resolver resolver = new Resolver(typeSourceInfo);
        TypeDeclaration constructedClass;

        if (owner == null) {
            constructedClass = resolver.resolveClassAgainstImports(expression.getConstructedType().format());

            if (!constructedClass.isStatic() && constructedClass.declaringType().isPresent()) {
                throw ObjectInitializationErrors.constructorNotFound(typeSourceInfo, constructedClass);
            }
        } else {
            String memberName = expression.getConstructedType().format();
            constructedClass = resolver.tryResolveNestedClass(owner.declaration(), memberName);

            if (constructedClass == null) {
                throw SymbolResolutionErrors.memberNotFound(
                    typeSourceInfo, owner.declaration(), memberName);
            }

            if (constructedClass.isStatic() || constructedClass.declaringType().isEmpty()) {
                throw ObjectInitializationErrors.constructorNotFound(typeSourceInfo, constructedClass);
            }
        }

        AccessVerifier.verifyAccessible(constructedClass, expression.getInvocationContext(), typeSourceInfo);

        if (constructedClass.isAbstract() || constructedClass.isInterface()
                || constructedClass.isPrimitive() || constructedClass.isArray()) {
            throw ObjectInitializationErrors.constructorNotFound(typeSourceInfo, constructedClass);
        }

        List<TypeInstance> classArguments = expression.getClassArguments().stream()
            .map(PathNode::resolve)
            .toList();

        TypeInvoker invoker = new TypeInvoker(typeSourceInfo);
        TypeInstance constructedType = owner != null
            ? invoker.invokeType(owner, constructedClass, classArguments)
            : invoker.invokeType(constructedClass, classArguments);

        List<TypeInstance> invocationContext = constructionInvocationContext(constructedType);
        List<TypeInstance> witnesses = expression.getConstructorWitnesses().stream()
            .map(PathNode::resolve)
            .toList();

        List<TypeInstance> argumentTypes = expression.getArguments().stream()
            .map(argument -> argumentValueType(argument, preferObservable))
            .toList();

        List<SourceInfo> argumentSources = expression.getArguments().stream()
            .map(Node::getSourceInfo)
            .toList();

        List<InvocationCandidate> candidates = constructedClass.constructors().stream()
            .map(constructor -> new InvocationCandidate(
                constructor, invocationContext, witnesses, false, constructedType))
            .toList();

        List<InvocationCandidate> accessibleCandidates = candidates.stream()
            .filter(candidate -> AccessVerifier.isAccessible(
                candidate.behavior(), expression.getInvocationContext()))
            .toList();

        if (!accessibleCandidates.isEmpty()) {
            candidates = accessibleCandidates;
        }

        List<DiagnosticInfo> diagnostics = new ArrayList<>();
        List<ApplicableInvocationCandidate> selected = MethodFinder.resolveInvocationCandidates(
            candidates, null, argumentTypes, argumentSources, diagnostics, expression.getSourceInfo());

        if (selected.size() != 1) {
            Diagnostic[] causes = diagnostics.stream()
                .map(DiagnosticInfo::getDiagnostic)
                .toArray(Diagnostic[]::new);

            throw causes.length == 0
                ? ObjectInitializationErrors.constructorNotFound(expression.getSourceInfo(), constructedClass)
                : ObjectInitializationErrors.constructorNotFound(expression.getSourceInfo(), constructedClass, causes);
        }

        AccessVerifier.verifyAccessible(
            selected.get(0).candidate().behavior(),
            expression.getInvocationContext(), expression.getSourceInfo());

        boolean observable = preferObservable
            && (qualifier != null && qualifier.getTypeInfo().isObservableArgument()
                || expression.getArguments().stream().anyMatch(this::isObservableArgument));

        TypeInstance emittedType = observable
            ? new Resolver(expression.getSourceInfo()).getObservableClass(constructedType)
            : constructedType;

        BindingTypeInfo info = new BindingTypeInfo(
            emittedType, constructedType, observable ? emittedType : null,
            observable ? ValueSourceKind.get(emittedType.declaration()) : ValueSourceKind.NONE,
            ObservableDependencyKind.get(emittedType.declaration()),
            constructedType.declaration(), constructedType.declaration().simpleName(),
            true, false, false, expression.getSourceInfo());

        ApplicableInvocationCandidate resolvedCandidate = selected.get(0);

        if (mode == BindingMode.BIDIRECTIONAL) {
            validateBidirectionalArguments(expression.getArguments(), expression.getSourceInfo());
        }

        return new ExpressionResolution(info, () -> {
            if (observable) {
                ValueEmitterNode result = new ObservableConstructorEmitterFactory(
                    expression, invokingType, resolvedCandidate).newInstance(mode == BindingMode.BIDIRECTIONAL);

                if (result == null) {
                    throw new IllegalStateException("Resolved observable constructor emitted an invariant value");
                }

                return result;
            }

            return new SimpleConstructorEmitterFactory(expression, invokingType, resolvedCandidate).newInstance();
        });
    }

    private BindingTypeInfo observablePathInfo(
            ResolvedPath path,
            BindingOperator operator,
            BindingMode mode,
            SourceInfo sourceInfo) {
        TypeInstance rawValueType = path.getValueTypeInstance();
        TypeInstance valueType = operator.evaluateType(rawValueType);
        boolean compiledPath = isCompiledPath(path);

        TypeInstance pathType = compiledPath
            ? compiledPathType(path, sourceInfo)
            : path.getTypeInstance();

        TypeInstance emittedType = mode == BindingMode.BIDIRECTIONAL
            ? pathType
            : observableOperatorType(pathType, rawValueType, operator, sourceInfo);

        boolean exposesValueSource = path.getValueSourceKind() != ValueSourceKind.NONE
            || ObservableDependencyKind.get(rawValueType.declaration()) != ObservableDependencyKind.CONTENT
            && !rawValueType.subtypeOf(CollectionDecl())
            && !rawValueType.subtypeOf(MapDecl());

        Segment last = path.get(path.size() - 1);

        return new BindingTypeInfo(
            emittedType, valueType, exposesValueSource ? emittedType : null,
            exposesValueSource ? ValueSourceKind.get(emittedType.declaration()) : ValueSourceKind.NONE,
            path.getObservableDependencyKind(), last.getDeclaringType(), last.getDisplayName(),
            false, compiledPath, observablePathMayBeNull(path, mode), sourceInfo);
    }

    private TypeInstance observableOperatorType(
            TypeInstance observableType,
            TypeInstance valueType,
            BindingOperator operator,
            SourceInfo sourceInfo) {
        if (operator == BindingOperator.IDENTITY
                || operator == BindingOperator.BOOLIFY
                && (valueType.equals(BooleanDecl()) || valueType.equals(booleanDecl()))) {
            return observableType;
        }

        return new TypeInvoker(sourceInfo).invokeType(ObservableValueDecl(), List.of(TypeInstance.BooleanType()));
    }

    private TypeInstance compiledPathType(ResolvedPath path, SourceInfo sourceInfo) {
        TypeDeclaration finalType = path.getValueSourceKind() != ValueSourceKind.NONE
            ? path.getTypeInstance().declaration()
            : path.getValueTypeInstance().declaration();

        if (finalType.equals(booleanDecl()) || finalType.subtypeOf(ObservableBooleanValueDecl())) {
            return TypeInstance.of(BooleanPropertyDecl());
        }

        if (finalType.equals(intDecl()) || finalType.equals(shortDecl())
                || finalType.equals(byteDecl()) || finalType.equals(charDecl())
                || finalType.subtypeOf(ObservableIntegerValueDecl())) {
            return TypeInstance.of(IntegerPropertyDecl());
        }

        if (finalType.equals(longDecl()) || finalType.subtypeOf(ObservableLongValueDecl())) {
            return TypeInstance.of(LongPropertyDecl());
        }

        if (finalType.equals(floatDecl()) || finalType.subtypeOf(ObservableFloatValueDecl())) {
            return TypeInstance.of(FloatPropertyDecl());
        }

        if (finalType.equals(doubleDecl()) || finalType.subtypeOf(ObservableDoubleValueDecl())) {
            return TypeInstance.of(DoublePropertyDecl());
        }

        return new TypeInvoker(sourceInfo).invokeType(
            PropertyDecl(), List.of(path.getValueTypeInstance().boxed()));
    }

    private boolean isCompiledPath(ResolvedPath path) {
        int leadingInvariantSegments = 0;
        while (leadingInvariantSegments < path.size()
                && !path.get(leadingInvariantSegments).hasObservableDependency()) {
            ++leadingInvariantSegments;
        }

        int trailingSegments = path.size() - leadingInvariantSegments;
        return trailingSegments > 0 && (trailingSegments > 1
            || path.get(leadingInvariantSegments).getObservableDependencyKind()
                != ObservableDependencyKind.VALUE);
    }

    private boolean invariantPathMayBeNull(BindingOperator operator, ResolvedPath path) {
        return !operator.isBoolean() && pathMayBeNull(path, path.size());
    }

    private boolean observablePathMayBeNull(ResolvedPath path, BindingMode mode) {
        int leadingInvariantSegments = 0;
        while (leadingInvariantSegments < path.size()
                && !path.get(leadingInvariantSegments).hasObservableDependency()) {
            ++leadingInvariantSegments;
        }

        if (leadingInvariantSegments == 0 || isCompiledPath(path)) {
            return false;
        }

        boolean wrapsLeadingValue = leadingInvariantSegments > 1 && mode != BindingMode.BIDIRECTIONAL;
        return !wrapsLeadingValue && pathMayBeNull(path, leadingInvariantSegments);
    }

    private boolean pathMayBeNull(ResolvedPath path, int endIndex) {
        for (int i = 0; i < endIndex; ++i) {
            Segment segment = path.get(i);

            if (segment.hasValueSource()) {
                if (!segment.getValueTypeInstance().isPrimitive()) {
                    return true;
                }
            } else if (!segment.getTypeInstance().isPrimitive() && segment.isNullable()) {
                return true;
            }
        }

        return false;
    }

    private boolean isDirectContentSource(ResolvedPath path) {
        return path.fold().getGroups().length == 1
            && path.getValueSourceKind() == ValueSourceKind.NONE
            && ObservableDependencyKind.get(path.getValueTypeInstance().declaration())
                == ObservableDependencyKind.CONTENT;
    }

    private ValueEmitterNode emitInvariantPath(
            BindingOperator operator, ResolvedPath path, SourceInfo sourceInfo) {
        ValueEmitterNode value = new EmitInvariantPathNode(
            path.toValueEmitters(sourceInfo), sourceInfo);
        return operator.toEmitter(value, BindingMode.ONCE);
    }

    private ValueEmitterNode emitObservablePath(
            BindingOperator operator, ResolvedPath path, BindingMode mode, SourceInfo sourceInfo) {
        ValueEmitterNode value = new EmitObservablePathNode(
            path, mode == BindingMode.BIDIRECTIONAL, sourceInfo);

        return operator.toEmitter(
            value, mode == BindingMode.BIDIRECTIONAL ? BindingMode.BIDIRECTIONAL : BindingMode.UNIDIRECTIONAL);
    }

    private void validateObservablePath(
            ResolvedPath path, BindingOperator operator, BindingMode mode, SourceInfo sourceInfo) {
        Segment last = path.get(path.size() - 1);

        if (mode == BindingMode.BIDIRECTIONAL && path.getValueSourceKind() != ValueSourceKind.WRITABLE) {
            MarkupException ex = last.getDeclaringType() == null
                ? BindingSourceErrors.invalidBidirectionalBindingSource(
                    sourceInfo, last.getValueTypeInstance(), false)
                : BindingSourceErrors.invalidBidirectionalBindingSource(
                    sourceInfo, last.getDeclaringType(), last.getDisplayName());
            ex.getProperties().put("sourceType", path.getValueTypeInstance());
            throw ex;
        }

        if (mode == BindingMode.BIDIRECTIONAL && !operator.isInvertible(path.getValueTypeInstance())) {
            throw BindingSourceErrors.expressionNotInvertible(sourceInfo);
        }
    }

    private boolean isObservableArgument(Node argument) {
        return argument instanceof ExpressionNode expression
            && resolveImpl(expression, BindingMode.UNIDIRECTIONAL, null).getTypeInfo().isObservableArgument();
    }

    private void validateBidirectionalArguments(InvocationExpressionNode expression) {
        validateBidirectionalArguments(expression.getArguments(), expression.getSourceInfo());
    }

    private void validateBidirectionalArguments(List<Node> arguments, SourceInfo sourceInfo) {
        if (arguments.size() != 1) {
            throw BindingSourceErrors.invalidBidirectionalMethodParamCount(sourceInfo);
        }

        Node argument = arguments.get(0);
        if (!(argument instanceof PathExpressionNode)) {
            throw BindingSourceErrors.invalidBidirectionalMethodParamKind(argument.getSourceInfo());
        }
    }

    private TypeInstance argumentValueType(Node argument, boolean preferObservable) {
        if (argument instanceof ExpressionNode expression) {
            return resolveImpl(
                expression,
                preferObservable ? BindingMode.UNIDIRECTIONAL : BindingMode.ONCE,
                null).getTypeInfo().valueType();
        }

        if (argument instanceof ValueEmitterNode) {
            MarkupExtensionInfo extension = MarkupExtensionInfo.of(argument);

            if (extension instanceof MarkupExtensionInfo.Supplier supplier) {
                return supplier.providedTypes().size() == 1
                    ? supplier.providedTypes().get(0)
                    : TypeInstance.ofUnion(supplier.providedTypes());
            }

            if (extension instanceof MarkupExtensionInfo.PropertyConsumer) {
                throw ObjectInitializationErrors.invalidMarkupExtensionUsage(argument.getSourceInfo());
            }

            return TypeHelper.getTypeInstance(argument);
        }

        throw GeneralErrors.expressionNotApplicable(argument.getSourceInfo(), false);
    }

    private List<TypeInstance> constructionInvocationContext(TypeInstance constructedType) {
        var result = new ArrayList<TypeInstance>();
        addOwnerChain(result, constructedType);
        return result;
    }

    private void addOwnerChain(List<TypeInstance> result, TypeInstance type) {
        TypeInstance owner = type.owner();
        if (owner != null) {
            addOwnerChain(result, owner);
        }

        result.add(type);
    }
}

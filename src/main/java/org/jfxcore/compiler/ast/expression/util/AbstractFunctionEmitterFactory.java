// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.emit.EmitApplyMarkupExtensionNode;
import org.jfxcore.compiler.ast.emit.EmitInvariantPathNode;
import org.jfxcore.compiler.ast.emit.EmitMethodArgumentNode;
import org.jfxcore.compiler.ast.emit.EmitObservablePathNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingContextNode;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.expression.ConstructorExpressionNode;
import org.jfxcore.compiler.ast.expression.ExpressionNode;
import org.jfxcore.compiler.ast.expression.ExpressionResolution;
import org.jfxcore.compiler.ast.expression.FunctionExpressionNode;
import org.jfxcore.compiler.ast.expression.PathExpressionNode;
import org.jfxcore.compiler.ast.expression.path.InconvertibleArgumentException;
import org.jfxcore.compiler.ast.expression.path.ResolvedPath;
import org.jfxcore.compiler.ast.text.PathNode;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.DiagnosticInfo;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.transform.markup.util.MarkupExtensionInfo;
import org.jfxcore.compiler.type.AnnotationDeclaration;
import org.jfxcore.compiler.type.BehaviorDeclaration;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.ApplicableInvocationCandidate;
import org.jfxcore.compiler.util.Callable;
import org.jfxcore.compiler.util.MethodFinder;
import org.jfxcore.compiler.util.NameHelper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

import static org.jfxcore.compiler.type.KnownSymbols.*;

abstract class AbstractFunctionEmitterFactory {

    private final TypeInstance invokingType;

    protected AbstractFunctionEmitterFactory(TypeInstance invokingType) {
        this.invokingType = invokingType;
    }

    protected final TypeInstance getInvokingType() {
        return invokingType;
    }

    protected InvocationInfo createInvocation(
            FunctionExpressionNode functionExpression,
            boolean bidirectional,
            boolean preferObservable,
            ApplicableInvocationCandidate selected) {
        PathExpressionNode methodPath = functionExpression.getPath();
        List<TypeInstance> witnesses = methodPath.getSegments()
            .get(methodPath.getSegments().size() - 1)
            .getTypeArguments()
            .stream()
            .map(PathNode::resolve)
            .toList();

        List<Node> methodArguments = functionExpression.getArguments();
        Callable function = createSelectedMethodCallable(methodPath, selected, preferObservable);
        TypeInstance[] paramTypes = selected.parameterTypes().toArray(TypeInstance[]::new);
        TypeInstance returnType = selected.resultType();

        Callable inverseFunction = null;

        PreparedArguments preparedArguments = prepareArguments(
            function.getBehavior(), paramTypes, methodArguments, bidirectional, preferObservable,
            function.getReceiverDependencyKind() != ObservableDependencyKind.NONE, functionExpression.getSourceInfo());

        List<EmitMethodArgumentNode> argumentValues = preparedArguments.values();
        boolean observableFunction = preparedArguments.observable();

        if (bidirectional) {
            var inversePath = functionExpression.getInversePath();
            if (inversePath != null) {
                // Synthetic node to represent the value of the return type
                class ReturnValueNode extends AbstractNode implements ValueEmitterNode {
                    final ResolvedTypeNode type = new ResolvedTypeNode(returnType, methodPath.getSourceInfo());
                    public ReturnValueNode() { super(methodPath.getSourceInfo()); }
                    @Override public void emit(BytecodeEmitContext context) {}
                    @Override public ValueEmitterNode deepClone() { return null; }
                    @Override public ResolvedTypeNode getType() {
                        return type;
                    }
                }

                inverseFunction = findInverseCallable(
                    inversePath, paramTypes[0], witnesses,
                    List.of(new ReturnValueNode()), preferObservable);
            } else {
                inverseFunction = findInverseFunctionViaAnnotation(
                    function, paramTypes[0], returnType, methodPath.getSourceInfo());
            }
        }

        return new InvocationInfo(
            observableFunction, returnType, function, inverseFunction, argumentValues);
    }

    protected InvocationInfo createConstructorInvocation(
            ConstructorExpressionNode expression,
            boolean preferObservable,
            boolean bidirectional,
            ApplicableInvocationCandidate selected) {
        BindingEmitterInfo qualifierInfo = expression.getQualifier() != null
            ? expression.getQualifier().resolve(
                preferObservable ? BindingMode.UNIDIRECTIONAL : BindingMode.ONCE,
                invokingType, null).toEmitter()
            : null;

        SourceInfo typeSourceInfo = expression.getConstructedType().getSourceInfo();
        ConstructorDeclaration constructor = (ConstructorDeclaration)selected.candidate().behavior();
        List<TypeInstance> invocationContext = selected.candidate().invocationContext();
        TypeInstance[] parameterTypes = selected.parameterTypes().toArray(TypeInstance[]::new);
        TypeInstance invocationType = selected.resultType();

        ObservableDependencyKind receiverDependencyKind = qualifierInfo != null
            ? getArgumentDependencyKind(qualifierInfo)
            : ObservableDependencyKind.NONE;

        Callable callable = new Callable(
            invocationContext, qualifierInfo != null ? List.of(qualifierInfo.getValue()) : List.of(),
            receiverDependencyKind, constructor, expression.getSourceInfo());

        PreparedArguments preparedArguments = prepareArguments(
            constructor, parameterTypes, expression.getArguments(), bidirectional, preferObservable,
            receiverDependencyKind != ObservableDependencyKind.NONE, expression.getSourceInfo());

        Callable inverseFunction = null;

        if (bidirectional) {
            PathExpressionNode inversePath = expression.getInversePath();
            if (inversePath != null) {
                class ReturnValueNode extends AbstractNode implements ValueEmitterNode {
                    final ResolvedTypeNode type = new ResolvedTypeNode(invocationType, typeSourceInfo);
                    ReturnValueNode() { super(typeSourceInfo); }
                    @Override public void emit(BytecodeEmitContext context) {}
                    @Override public ValueEmitterNode deepClone() { return null; }
                    @Override public ResolvedTypeNode getType() { return type; }
                }

                List<TypeInstance> inverseWitnesses = inversePath.getSegments()
                    .get(inversePath.getSegments().size() - 1)
                    .getTypeArguments()
                    .stream()
                    .map(PathNode::resolve)
                    .toList();

                inverseFunction = findInverseCallable(
                    inversePath,
                    parameterTypes[0],
                    inverseWitnesses,
                    List.of(new ReturnValueNode()),
                    preferObservable);
            } else {
                inverseFunction = findInverseFunctionViaAnnotation(
                    callable,
                    parameterTypes[0],
                    invocationType,
                    typeSourceInfo);
            }
        }

        return new InvocationInfo(
            preparedArguments.observable(), invocationType, callable,
            inverseFunction, preparedArguments.values());
    }

    protected final PreparedArguments prepareArguments(
            BehaviorDeclaration behavior,
            TypeInstance[] parameterTypes,
            Collection<? extends Node> sourceArguments,
            boolean bidirectional,
            boolean preferObservable,
            boolean observable,
            SourceInfo invocationSourceInfo) {
        Queue<Node> arguments = new ArrayDeque<>(sourceArguments);
        boolean varargs = behavior.isVarArgs();

        if (!varargs && arguments.size() != parameterTypes.length
                || varargs && arguments.size() < parameterTypes.length - 1) {
            throw GeneralErrors.numFunctionArgumentsMismatch(
                SourceInfo.span(arguments),
                NameHelper.getDisplaySignature(behavior, parameterTypes),
                parameterTypes.length,
                arguments.size());
        }

        List<EmitMethodArgumentNode> argumentValues = new ArrayList<>();

        for (int i = 0; i < parameterTypes.length; ++i) {
            EmitMethodArgumentNode argumentValue;

            if (i < parameterTypes.length - 1 || !varargs) {
                Node argument = arguments.remove();

                try {
                    argumentValue = createSingleFunctionArgumentValue(
                        argument, parameterTypes[i], bidirectional, preferObservable);
                } catch (InconvertibleArgumentException ex) {
                    if (ex.getCause() instanceof MarkupException markupException) {
                        throw markupException;
                    }

                    throw GeneralErrors.cannotAssignFunctionArgument(
                        argument.getSourceInfo(),
                        behavior.displaySignature(false, false),
                        i, ex.getTypeName());
                }
            } else if (arguments.isEmpty()) {
                argumentValue = EmitMethodArgumentNode.newVariadic(
                    parameterTypes[i].componentType(), List.of(), invocationSourceInfo);
            } else {
                argumentValue = createVariadicFunctionArgumentValue(
                    behavior, new ArrayList<>(arguments), parameterTypes[i], i,
                    bidirectional, preferObservable);
            }

            argumentValues.add(argumentValue);
            observable |= argumentValue.isObservable();
        }

        return new PreparedArguments(argumentValues, observable);
    }

    private EmitMethodArgumentNode createVariadicFunctionArgumentValue(
            BehaviorDeclaration method, List<Node> arguments, TypeInstance paramType,
            int paramIndex, boolean bidirectional, boolean preferObservable) {
        SourceInfo sourceInfo = SourceInfo.span(
            arguments.get(0).getSourceInfo(),
            arguments.get(arguments.size() - 1).getSourceInfo());

        try {
            TypeInstance componentType = paramType.componentType();
            List<EmitMethodArgumentNode> values = new ArrayList<>();

            for (Node argument : arguments) {
                values.add(createSingleFunctionArgumentValue(argument, componentType, bidirectional, preferObservable));
            }

            return EmitMethodArgumentNode.newVariadic(componentType, values, sourceInfo);
        } catch (InconvertibleArgumentException ex) {
            if (ex.getCause() instanceof MarkupException) {
                throw (MarkupException)ex.getCause();
            }

            throw GeneralErrors.cannotAssignFunctionArgument(
                sourceInfo, method.displaySignature(false, false), paramIndex, ex.getTypeName());
        }
    }

    protected final EmitMethodArgumentNode createSingleFunctionArgumentValue(
            Node argument, TypeInstance paramType, boolean bidirectional, boolean preferObservable) {
        SourceInfo sourceInfo = argument.getSourceInfo();

        if (argument instanceof ExpressionNode expressionArg) {
            try {
                BindingMode mode = preferObservable
                    ? bidirectional ? BindingMode.BIDIRECTIONAL : BindingMode.UNIDIRECTIONAL
                    : BindingMode.ONCE;

                ExpressionResolution resolution = ExpressionResolver.resolveArgument(
                    expressionArg, mode, invokingType, paramType);

                BindingEmitterInfo emitterInfo = resolution.toEmitter();

                return EmitMethodArgumentNode.newScalar(
                    paramType, emitterInfo.getValue(),
                    resolution.getTypeInfo().argumentDependencyKind(), sourceInfo);
            } catch (MarkupException ex) {
                throw new InconvertibleArgumentException(argument.getClass().getName(), ex);
            }
        }

        if (argument instanceof ValueEmitterNode valueEmitterArg) {
            var extensionInfo = MarkupExtensionInfo.of(valueEmitterArg);

            if (extensionInfo instanceof MarkupExtensionInfo.Supplier supplierInfo) {
                return EmitMethodArgumentNode.newScalar(
                    paramType,
                    new EmitApplyMarkupExtensionNode.Supplier(
                        valueEmitterArg, supplierInfo.markupExtensionInterface(), null,
                        paramType, supplierInfo.returnType(), null),
                    ObservableDependencyKind.NONE, sourceInfo);
            }

            if (extensionInfo instanceof MarkupExtensionInfo.PropertyConsumer) {
                throw new InconvertibleArgumentException(argument.getClass().getName(),
                    ObjectInitializationErrors.invalidMarkupExtensionUsage(sourceInfo));
            }

            return EmitMethodArgumentNode.newScalar(
                paramType, valueEmitterArg, ObservableDependencyKind.NONE, sourceInfo);
        }

        throw new InconvertibleArgumentException(argument.getClass().getName());
    }

    private Callable createSelectedMethodCallable(
            PathExpressionNode pathExpression,
            ApplicableInvocationCandidate selected,
            boolean preferObservable) {
        MethodDeclaration method = (MethodDeclaration)selected.candidate().behavior();
        ResolvedPath resolvedPath = null;

        if (!method.isStatic() && pathExpression.getSegments().size() > 1) {
            resolvedPath = pathExpression.resolvePath(preferObservable, pathExpression.getSegments().size() - 1);
        }

        ReceiverInfo receiverInfo = getMethodReceiverInfo(pathExpression, resolvedPath, method, preferObservable);

        return new Callable(
            selected.candidate().invocationContext(),
            receiverInfo.emitters(),
            receiverInfo.dependencyKind(),
            method,
            pathExpression.getSourceInfo());
    }

    protected final Callable findInverseCallable(
            PathExpressionNode pathExpression,
            @Nullable TypeInstance returnType,
            List<TypeInstance> typeWitnesses,
            Collection<Node> arguments,
            boolean preferObservable) {
        return findCallable(
            pathExpression, returnType, typeWitnesses, arguments,
            preferObservable, true, true);
    }

    private Callable findCallable(
            PathExpressionNode pathExpression,
            @Nullable TypeInstance returnType,
            List<TypeInstance> typeWitnesses,
            Collection<Node> arguments,
            boolean preferObservable,
            boolean maybeInstanceMethod,
            boolean allowConstructor) {
        String methodName;
        TypeDeclaration declaringClass;
        ResolvedPath resolvedPath = null;
        List<TypeInstance> invocationContext = List.of(invokingType);
        boolean isConstructor = false;

        if (pathExpression.getSegments().size() > 1) {
            // If we assume that the path points to a method, we limit path resolution to all but the
            // last segment of the path (since method names are not part of the resolvable path).
            int limit = pathExpression.getSegments().size() - 1;
            methodName = pathExpression.getSegments().get(limit).getText();
            String className = null;

            try {
                if (maybeInstanceMethod) {
                    resolvedPath = pathExpression.resolvePath(preferObservable, limit);
                    className = resolvedPath.getValueTypeInstance().javaName();
                    invocationContext = List.of(resolvedPath.getValueTypeInstance());
                }
            } catch (MarkupException ignored) {
                maybeInstanceMethod = false;
            }

            if (!maybeInstanceMethod) {
                className = pathExpression.getSimplePath(limit);

                // If we don't have a valid path expression, the only other possible interpretation would be
                // a static method call. Since a static method call is not resolved by a path expression, we
                // check that only the default binding context selector is used.
                if (!pathExpression.getBindingContext().mayResolveAgainstImports()) {
                    throw BindingSourceErrors.bindingContextNotApplicable(pathExpression.getBindingContext().getSourceInfo());
                }
            }

            var resolver = new Resolver(pathExpression.getSourceInfo());
            declaringClass = resolver.tryResolveClassAgainstImports(className);
            if (declaringClass == null) {
                declaringClass = resolver.tryResolveNestedClass(
                    pathExpression.getBindingContext().getType().getTypeDeclaration(), className);
            }

            if (declaringClass == null && allowConstructor) {
                className = pathExpression.getSimplePath();
                declaringClass = resolver.tryResolveClass(className);
                isConstructor = true;
            }

            if (declaringClass == null) {
                throw SymbolResolutionErrors.memberNotFound(
                    pathExpression.getSourceInfo(),
                    pathExpression.getBindingContext().getType().getTypeDeclaration(),
                    methodName);
            }
        } else {
            methodName = pathExpression.getSegments().get(0).getText();
            TypeInstance contextType = pathExpression.getBindingContext().getValueType();
            declaringClass = contextType.declaration();
            invocationContext = List.of(contextType);
        }

        List<TypeInstance> argumentTypes = arguments.stream()
            .map(arg -> resolveArgumentType(arg, preferObservable))
            .collect(Collectors.toList());

        List<SourceInfo> argumentsSourceInfo = arguments.stream()
            .map(Node::getSourceInfo).collect(Collectors.toList());

        List<DiagnosticInfo> diagnostics = new ArrayList<>();

        // First we try to match the identifier against methods.
        // If applicable methods are found, we choose the most specific method.
        if (!isConstructor) {
            var methodFinder = new MethodFinder(invocationContext, declaringClass);
            MethodDeclaration method = methodFinder.findMethod(
                methodName, false, returnType, typeWitnesses, argumentTypes,
                argumentsSourceInfo, diagnostics, pathExpression.getSourceInfo());

            // If we didn't find a method with the specified return type, relax the search to
            // include any return type if the path has a boolean operator.
            if (method == null && returnType != null && pathExpression.getOperator().isBoolean()) {
                method = methodFinder.findMethod(
                    methodName, false, null, typeWitnesses, argumentTypes,
                    argumentsSourceInfo, diagnostics, pathExpression.getSourceInfo());
            }

            if (method != null) {
                if (!maybeInstanceMethod && !method.isStatic()) {
                    throw SymbolResolutionErrors.instanceMemberReferencedFromStaticContext(
                        pathExpression.getSourceInfo(), method);
                }

                ReceiverInfo receiverInfo = getMethodReceiverInfo(
                    pathExpression, resolvedPath, method, preferObservable);

                return new Callable(
                    invocationContext,
                    receiverInfo.emitters(),
                    receiverInfo.dependencyKind(),
                    method, pathExpression.getSourceInfo());
            }
        }

        // Only inverseMethod has constructor-name semantics. Ordinary calls never enter this branch.
        if (allowConstructor && pathExpression.getBindingContext().mayResolveAgainstImports()) {
            var resolver = new Resolver(pathExpression.getSourceInfo());
            TypeDeclaration ctorClass = resolver.tryResolveClass(pathExpression.getSimplePath());
            if (ctorClass == null) {
                ctorClass = resolver.tryResolveClassAgainstImports(methodName);
            }

            if (ctorClass != null && (ctorClass.isStatic() || ctorClass.declaringType().isEmpty())) {
                ConstructorDeclaration constructor = new MethodFinder(List.of(invokingType), ctorClass).findConstructor(
                    typeWitnesses, argumentTypes, argumentsSourceInfo,
                    diagnostics, pathExpression.getSourceInfo());

                if (constructor != null) {
                    TypeInstance constructedType = new TypeInvoker(pathExpression.getSourceInfo()).invokeType(ctorClass);
                    if (returnType != null && !constructedType.subtypeOf(returnType)) {
                        throw GeneralErrors.incompatibleReturnValue(
                            pathExpression.getSourceInfo(), constructor, returnType);
                    }

                    return new Callable(
                        List.of(constructedType), List.of(), ObservableDependencyKind.NONE,
                        constructor, pathExpression.getSourceInfo());
                }
            }
        }

        // At this point, we've tried to find a method that is applicable for the arguments and failed.
        // If we were looking for an instance method, we try again, but only look for static methods.
        if (maybeInstanceMethod && resolvedPath == null) {
            return findCallable(
                pathExpression, returnType, typeWitnesses, arguments,
                preferObservable, false, allowConstructor);
        }

        if (diagnostics.size() == 1) {
            throw new MarkupException(diagnostics.get(0).getSourceInfo(), diagnostics.get(0).getDiagnostic());
        }

        if (!diagnostics.isEmpty()) {
            throw BindingSourceErrors.cannotBindFunction(
                pathExpression.getSourceInfo(),
                diagnostics.stream().map(DiagnosticInfo::getDiagnostic).toArray(Diagnostic[]::new));
        }

        throw SymbolResolutionErrors.memberNotFound(pathExpression.getSourceInfo(), declaringClass, methodName);
    }

    protected final TypeInstance resolveArgumentType(Node argument, boolean preferObservable) {
        if (argument instanceof ExpressionNode expression) {
            return ExpressionResolver.resolveArgument(
                expression,
                preferObservable ? BindingMode.UNIDIRECTIONAL : BindingMode.ONCE,
                invokingType,
                null).getTypeInfo().valueType();
        }

        if (argument instanceof ValueEmitterNode) {
            var extensionInfo = MarkupExtensionInfo.of(argument);

            if (extensionInfo instanceof MarkupExtensionInfo.Supplier supplierInfo) {
                return supplierInfo.providedTypes().size() > 1
                    ? TypeInstance.ofUnion(supplierInfo.providedTypes())
                    : supplierInfo.providedTypes().get(0);
            }

            if (extensionInfo instanceof MarkupExtensionInfo.PropertyConsumer) {
                throw ObjectInitializationErrors.invalidMarkupExtensionUsage(argument.getSourceInfo());
            }

            return TypeHelper.getTypeInstance(argument);
        }

        throw GeneralErrors.expressionNotApplicable(argument.getSourceInfo(), false);
    }

    private ReceiverInfo getMethodReceiverInfo(PathExpressionNode pathExpression,
                                               ResolvedPath resolvedPath,
                                               MethodDeclaration method,
                                               boolean preferObservable) {
        if (resolvedPath != null) {
            if (preferObservable && resolvedPath.isObservable()) {
                return new ReceiverInfo(
                    List.of(new EmitObservablePathNode(resolvedPath, false, pathExpression.getSourceInfo())),
                    getReceiverDependencyKind(resolvedPath));
            }

            return new ReceiverInfo(
                List.of(new EmitInvariantPathNode(
                    resolvedPath.toValueEmitters(pathExpression.getSourceInfo()),
                    pathExpression.getSourceInfo())),
                ObservableDependencyKind.NONE);
        }

        if (!method.isStatic()) {
            BindingContextNode bindingSource = pathExpression.getBindingContext();
            var result = new ArrayList<ValueEmitterNode>(1);
            var segment = bindingSource.toSegment();

            if (preferObservable && segment.getObservableDependencyKind() != ObservableDependencyKind.NONE) {
                result.add(segment.toEmitter(true, bindingSource.getSourceInfo()));
                return new ReceiverInfo(result, segment.getObservableDependencyKind());
            }

            result.add(segment.toValueEmitter(false, bindingSource.getSourceInfo()));
            return new ReceiverInfo(result, ObservableDependencyKind.NONE);
        }

        return new ReceiverInfo(List.of(), ObservableDependencyKind.NONE);
    }

    protected final Callable findInverseFunctionViaAnnotation(
            Callable method, TypeInstance argumentType, TypeInstance returnType, SourceInfo sourceInfo) {
        AnnotationDeclaration annotation = method.getBehavior()
            .annotation(Markup.InverseMethodAnnotationName)
            .orElse(null);

        if (annotation == null) {
            throw BindingSourceErrors.methodNotInvertible(sourceInfo, method.getBehavior());
        }

        String methodName = annotation.getString("value");
        if (methodName == null) {
            throw BindingSourceErrors.invalidInverseMethodAnnotationValue(sourceInfo, method.getBehavior());
        }

        TypeDeclaration declaringClass = method.getBehavior().declaringType();
        List<DiagnosticInfo> diagnostics = new ArrayList<>();

        // TODO: Do we need a way to specify type witnesses for inverse methods?
        MethodDeclaration foundMethod = new MethodFinder(method.getInvocationContext(), declaringClass).findMethod(
            methodName, false, argumentType, List.of(), List.of(returnType), List.of(sourceInfo), diagnostics, sourceInfo);

        if (!diagnostics.isEmpty()) {
            throw BindingSourceErrors.invalidInverseMethod(
                sourceInfo, method.getBehavior(),
                diagnostics.stream().map(DiagnosticInfo::getDiagnostic).toArray(Diagnostic[]::new));
        }

        return new Callable(
            method.getInvocationContext(),
            method.getReceiver(),
            method.getReceiverDependencyKind(),
            foundMethod,
            sourceInfo);
    }

    protected final ObservableDependencyKind getArgumentDependencyKind(BindingEmitterInfo emitterInfo) {
        if (emitterInfo.getObservableDependencyKind() != ObservableDependencyKind.NONE) {
            return emitterInfo.getObservableDependencyKind();
        }

        return emitterInfo.getValueSourceType() != null
            ? ObservableDependencyKind.VALUE
            : ObservableDependencyKind.NONE;
    }

    private ObservableDependencyKind getReceiverDependencyKind(ResolvedPath resolvedPath) {
        if (!resolvedPath.isObservable()) {
            return ObservableDependencyKind.NONE;
        }

        return resolvedPath.getObservableDependencyKind() != ObservableDependencyKind.NONE
            ? resolvedPath.getObservableDependencyKind()
            : ObservableDependencyKind.VALUE;
    }

    private record ReceiverInfo(
        List<ValueEmitterNode> emitters,
        ObservableDependencyKind dependencyKind) {}

    protected record PreparedArguments(
        List<EmitMethodArgumentNode> values,
        boolean observable) {}

    protected record InvocationInfo(
        boolean observable,
        TypeInstance type,
        Callable function,
        Callable inverseFunction,
        List<EmitMethodArgumentNode> arguments) {}
}

// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.emit.EmitApplyMarkupExtensionNode;
import org.jfxcore.compiler.ast.emit.EmitInvariantPathNode;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.emit.EmitMethodArgumentNode;
import org.jfxcore.compiler.ast.emit.EmitObservablePathNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingContextNode;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.expression.ExpressionNode;
import org.jfxcore.compiler.ast.expression.FunctionExpressionNode;
import org.jfxcore.compiler.ast.expression.Operator;
import org.jfxcore.compiler.ast.expression.PathExpressionNode;
import org.jfxcore.compiler.ast.expression.path.InconvertibleArgumentException;
import org.jfxcore.compiler.ast.expression.path.ResolvedPath;
import org.jfxcore.compiler.ast.text.NumberNode;
import org.jfxcore.compiler.ast.text.PathNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.DiagnosticInfo;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
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
import org.jfxcore.compiler.util.Callable;
import org.jfxcore.compiler.util.MethodFinder;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.NumberUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;

import static org.jfxcore.compiler.type.KnownSymbols.*;

abstract class AbstractFunctionEmitterFactory {

    private final TypeInstance invokingType;
    private final TypeInstance targetType;
    private final Map<InvocationInfoKey, InvocationInfo> invocationCache = new HashMap<>();

    protected AbstractFunctionEmitterFactory(TypeInstance invokingType, @Nullable TypeInstance targetType) {
        this.invokingType = invokingType;
        this.targetType = targetType;
    }

    protected InvocationInfo createInvocation(
            FunctionExpressionNode functionExpression, boolean bidirectional, boolean preferObservable) {
        return createInvocation(functionExpression, bidirectional, preferObservable, targetType);
    }

    private InvocationInfo createInvocation(
            FunctionExpressionNode functionExpression,
            boolean bidirectional,
            boolean preferObservable,
            @Nullable TypeInstance targetType) {
        var key = new InvocationInfoKey(functionExpression, bidirectional, preferObservable, targetType);
        var cachedInvocationInfo = invocationCache.get(key);
        if (cachedInvocationInfo != null) {
            return cachedInvocationInfo;
        }

        PathExpressionNode methodPath = functionExpression.getPath();
        List<TypeInstance> witnesses = methodPath.getSegments()
            .get(methodPath.getSegments().size() - 1)
            .getWitnesses()
            .stream()
            .map(PathNode::resolve)
            .toList();

        Queue<Node> methodArguments = new ArrayDeque<>(functionExpression.getArguments());
        Callable function = findFunction(methodPath, targetType, witnesses, methodArguments, preferObservable);
        Callable inverseFunction = null;

        boolean isVarArgs = function.getBehavior().isVarArgs();
        TypeInvoker invoker = new TypeInvoker(functionExpression.getSourceInfo());
        TypeInstance[] paramTypes = invoker.invokeParameterTypes(function.getBehavior(), function.getInvocationContext(), witnesses);
        TypeInstance returnType = invoker.invokeReturnType(function.getBehavior(), function.getInvocationContext(), witnesses);
        List<EmitMethodArgumentNode> argumentValues = new ArrayList<>();
        boolean observableFunction = function.getReceiverDependencyKind() != ObservableDependencyKind.NONE;

        if (!isVarArgs && methodArguments.size() != paramTypes.length
                || isVarArgs && methodArguments.size() < paramTypes.length) {
            throw GeneralErrors.numFunctionArgumentsMismatch(
                SourceInfo.span(methodArguments),
                NameHelper.getDisplaySignature(function.getBehavior(), paramTypes),
                paramTypes.length,
                methodArguments.size());
        }

        for (int i = 0; i < paramTypes.length; ++i) {
            EmitMethodArgumentNode argumentValue;

            if (i < paramTypes.length - 1 || !isVarArgs) {
                Node argument = methodArguments.remove();

                try {
                    argumentValue = createSingleFunctionArgumentValue(
                        argument, paramTypes[i], bidirectional, preferObservable);
                } catch (InconvertibleArgumentException ex) {
                    if (ex.getCause() instanceof MarkupException) {
                        throw (MarkupException)ex.getCause();
                    }

                    throw GeneralErrors.cannotAssignFunctionArgument(
                        argument.getSourceInfo(), function.getBehavior().displaySignature(false, false),
                        i, ex.getTypeName());
                }
            } else {
                argumentValue = createVariadicFunctionArgumentValue(
                    function.getBehavior(), new ArrayList<>(methodArguments), paramTypes[i],
                    i, bidirectional, preferObservable);
            }

            argumentValues.add(argumentValue);

            if (!observableFunction && argumentValue.isObservable()) {
                observableFunction = true;
            }
        }

        if (bidirectional) {
            if (argumentValues.size() != 1) {
                throw BindingSourceErrors.invalidBidirectionalMethodParamCount(functionExpression.getSourceInfo());
            } else {
                Node argNode = functionExpression.getArguments().get(0);
                if (!(argNode instanceof PathExpressionNode)) {
                    throw BindingSourceErrors.invalidBidirectionalMethodParamKind(argNode.getSourceInfo());
                }
            }

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

                inverseFunction = findFunction(
                    inversePath, paramTypes[0], witnesses, List.of(new ReturnValueNode()), preferObservable);
            } else {
                inverseFunction = findInverseFunctionViaAnnotation(
                    function, paramTypes[0], returnType, methodPath.getSourceInfo());
            }
        }

        var result = new InvocationInfo(
            observableFunction, returnType, function, inverseFunction, argumentValues);

        invocationCache.put(key, result);

        return result;
    }

    private EmitMethodArgumentNode createVariadicFunctionArgumentValue(
            BehaviorDeclaration method, List<Node> arguments, TypeInstance paramType,
            int paramIndex, boolean bidirectional, boolean preferObservable) {
        SourceInfo sourceInfo = SourceInfo.span(
            arguments.get(0).getSourceInfo(), arguments.get(arguments.size() - 1).getSourceInfo());

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

    private EmitMethodArgumentNode createSingleFunctionArgumentValue(
            Node argument, TypeInstance paramType, boolean bidirectional, boolean preferObservable) {
        SourceInfo sourceInfo = argument.getSourceInfo();

        if (argument instanceof NumberNode numberArg) {
            TypeInstance numberType;
            Number value;

            try {
                numberType = NumberUtil.parseType(numberArg.getText());
                value = NumberUtil.parse(numberArg.getText());
            } catch (NumberFormatException ex) {
                throw new InconvertibleArgumentException(NumberName);
            }

            if (!paramType.isAssignableFrom(numberType)) {
                throw new InconvertibleArgumentException(NumberName);
            }

            return EmitMethodArgumentNode.newScalar(
                paramType, new EmitLiteralNode(paramType, value, sourceInfo),
                ObservableDependencyKind.NONE, sourceInfo);
        }

        if (argument instanceof TextNode textArg) {
            if (!paramType.isAssignableFrom(TypeInstance.StringType())) {
                throw new InconvertibleArgumentException(StringName);
            }

            return EmitMethodArgumentNode.newScalar(
                paramType,
                new EmitLiteralNode(paramType, textArg.getText(), sourceInfo),
                ObservableDependencyKind.NONE,
                sourceInfo);
        }

        if (argument instanceof ExpressionNode) {
            EmitterFactory factory;

            if (argument instanceof FunctionExpressionNode funcExpressionArg) {
                InvocationInfo invocationInfo = createInvocation(funcExpressionArg, false, preferObservable, paramType);
                if (invocationInfo.observable()) {
                    factory = new ObservableFunctionEmitterFactory(funcExpressionArg, invokingType, paramType);
                } else {
                    factory = new SimpleFunctionEmitterFactory(funcExpressionArg, invokingType, paramType);
                }
            } else if (argument instanceof PathExpressionNode pathExpressionArg) {
                Keyword keyword = Keyword.of(pathExpressionArg.getSimplePath());
                if (keyword != null) {
                    if (pathExpressionArg.getOperator() != Operator.IDENTITY) {
                        throw ParserErrors.unexpectedExpression(pathExpressionArg.getSourceInfo());
                    }

                    return keyword.newEmitter(paramType, sourceInfo);
                }

                if (preferObservable && pathExpressionArg.resolvePath(true).isObservable()) {
                    factory = new ObservablePathEmitterFactory(pathExpressionArg);
                } else {
                    factory = new SimplePathEmitterFactory(pathExpressionArg);
                }
            } else {
                throw GeneralErrors.expressionNotApplicable(sourceInfo, false);
            }

            try {
                BindingEmitterInfo emitterInfo;

                if (factory instanceof ObservablePathEmitterFactory observablePathFactory) {
                    emitterInfo = observablePathFactory.newInstance(bidirectional, true);
                } else if (factory instanceof ObservableEmitterFactory observableFactory) {
                    emitterInfo = observableFactory.newInstance(bidirectional);
                } else {
                    emitterInfo = factory.newInstance();
                }

                if (emitterInfo == null) {
                    if (argument instanceof PathExpressionNode pathExpressionArg) {
                        emitterInfo = new SimplePathEmitterFactory(pathExpressionArg).newInstance();
                    } else {
                        throw new AssertionError();
                    }
                }

                return EmitMethodArgumentNode.newScalar(
                    paramType, emitterInfo.getValue(), getArgumentDependencyKind(emitterInfo), sourceInfo);
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

    private Callable findFunction(
            PathExpressionNode pathExpression,
            @Nullable TypeInstance returnType,
            List<TypeInstance> typeWitnesses,
            Collection<Node> arguments,
            boolean preferObservable) {
        return findFunction(pathExpression, returnType, typeWitnesses, arguments, preferObservable, true);
    }

    private Callable findFunction(
            PathExpressionNode pathExpression,
            @Nullable TypeInstance returnType,
            List<TypeInstance> typeWitnesses,
            Collection<Node> arguments,
            boolean preferObservable,
            boolean maybeInstanceMethod) {
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
                if (!pathExpression.getBindingContext().getSelector().isDefault()) {
                    throw BindingSourceErrors.bindingContextNotApplicable(pathExpression.getBindingContext().getSourceInfo());
                }
            }

            var resolver = new Resolver(pathExpression.getSourceInfo());
            declaringClass = resolver.tryResolveClassAgainstImports(className);
            if (declaringClass == null) {
                declaringClass = resolver.tryResolveNestedClass(
                    pathExpression.getBindingContext().getType().getTypeDeclaration(), className);
            }

            if (declaringClass == null) {
                className = pathExpression.getSimplePath();
                declaringClass = resolver.tryResolveClass(className);
                isConstructor = true;

                if (declaringClass == null) {
                    throw SymbolResolutionErrors.memberNotFound(
                        pathExpression.getSourceInfo(),
                        pathExpression.getBindingContext().getType().getTypeDeclaration(),
                        className);
                }
            }
        } else {
            methodName = pathExpression.getSimplePath();
            declaringClass = pathExpression.getBindingContext().getType().getTypeDeclaration();
            invocationContext = List.of(pathExpression.getBindingContext().getType().getTypeInstance());
        }

        List<TypeInstance> argumentTypes = arguments.stream()
            .map(arg -> getArgumentType(arg, preferObservable))
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

        // If no applicable methods were found, we treat the identifier as the name of a class and
        // see if there is a constructor that accepts our arguments.
        var resolver = new Resolver(pathExpression.getSourceInfo());
        TypeDeclaration ctorClass = resolver.tryResolveClass(pathExpression.getSimplePath());
        if (ctorClass == null) {
            ctorClass = resolver.tryResolveClassAgainstImports(methodName);
        }

        if (ctorClass != null) {
            ConstructorDeclaration constructor = new MethodFinder(List.of(invokingType), ctorClass).findConstructor(
                typeWitnesses,
                argumentTypes,
                argumentsSourceInfo,
                diagnostics,
                pathExpression.getSourceInfo());

            if (constructor != null) {
                if (returnType != null && !new TypeInvoker(pathExpression.getSourceInfo()).invokeReturnType(
                        constructor, List.of(invokingType)).subtypeOf(returnType)) {
                    throw GeneralErrors.incompatibleReturnValue(
                        pathExpression.getSourceInfo(), constructor, returnType);
                }

                return new Callable(
                    List.of(invokingType), List.of(), ObservableDependencyKind.NONE,
                    constructor, pathExpression.getSourceInfo());
            }
        }

        // At this point, we've tried to find a method that is applicable for the arguments and failed.
        // If we were looking for an instance method, we try again, but only look for static methods.
        if (maybeInstanceMethod && resolvedPath == null) {
            return findFunction(pathExpression, returnType, typeWitnesses, arguments, preferObservable, false);
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

    private TypeInstance getArgumentType(Node argument, boolean preferObservable) {
        if (argument instanceof FunctionExpressionNode funcExpressionArg) {
            return createInvocation(funcExpressionArg, false, preferObservable, null).type();
        } else if (argument instanceof PathExpressionNode pathExpressionArg) {
            Operator operator = pathExpressionArg.getOperator();
            if (operator == Operator.NOT || operator == Operator.BOOLIFY) {
                return TypeInstance.booleanType();
            }

            Keyword keyword = Keyword.of(pathExpressionArg.getSimplePath());
            return keyword != null
                ? keyword.getType()
                : pathExpressionArg.resolvePath(preferObservable).getValueTypeInstance();
        } else if (argument instanceof TextNode) {
            if (argument instanceof NumberNode numberNode) {
                return NumberUtil.parseType(numberNode.getText());
            } else {
                return TypeInstance.StringType();
            }
        } else if (argument instanceof ValueEmitterNode) {
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

    private Callable findInverseFunctionViaAnnotation(
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

    private ObservableDependencyKind getArgumentDependencyKind(BindingEmitterInfo emitterInfo) {
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

    private enum Keyword {
        NULL,
        TRUE,
        FALSE;

        TypeInstance getType() {
            return switch (this) {
                case NULL -> TypeInstance.nullType();
                case TRUE, FALSE -> TypeInstance.booleanType();
            };
        }

        EmitMethodArgumentNode newEmitter(TypeInstance paramType, SourceInfo sourceInfo) {
            Object literal = null;

            if (this == NULL) {
                if (paramType.isPrimitive()) {
                    throw new InconvertibleArgumentException(NullTypeDecl().name());
                }
            } else {
                if (paramType.isAssignableFrom(TypeInstance.booleanType())) {
                    literal = this == TRUE;
                } else {
                    throw new InconvertibleArgumentException(BooleanName);
                }
            }

            return EmitMethodArgumentNode.newScalar(
                paramType, new EmitLiteralNode(paramType, literal, sourceInfo),
                ObservableDependencyKind.NONE, sourceInfo);
        }

        static @Nullable AbstractFunctionEmitterFactory.Keyword of(String text) {
            return switch (text) {
                case "null" -> NULL;
                case "true" -> TRUE;
                case "false" -> FALSE;
                default -> null;
            };
        }
    }

    private record InvocationInfoKey(
        FunctionExpressionNode functionExpression,
        boolean bidirectional,
        boolean preferObservable,
        @Nullable TypeInstance targetType) {}

    private record ReceiverInfo(
        List<ValueEmitterNode> emitters,
        ObservableDependencyKind dependencyKind) {}

    protected record InvocationInfo(
        boolean observable,
        TypeInstance type,
        Callable function,
        Callable inverseFunction,
        List<EmitMethodArgumentNode> arguments) { }
}

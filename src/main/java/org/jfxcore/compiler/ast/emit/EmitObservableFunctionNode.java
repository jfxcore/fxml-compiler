// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.GeneratorEmitterNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.generate.Generator;
import org.jfxcore.compiler.generate.ObservableFunctionGenerator;
import org.jfxcore.compiler.type.BehaviorDeclaration;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.AccessVerifier;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Callable;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class EmitObservableFunctionNode
        extends AbstractNode
        implements ValueEmitterNode, GeneratorEmitterNode, ParentStackInfo, NullableInfo {

    private final Callable function;
    private final Callable inverseFunction;
    private final List<EmitMethodArgumentNode> arguments;
    private final ResolvedTypeNode type;
    private final TypeDeclaration invocationContext;
    private transient String compiledClassName;

    public EmitObservableFunctionNode(
            TypeInstance type,
            Callable function,
            @Nullable Callable inverseFunction,
            Collection<? extends EmitMethodArgumentNode> arguments,
            TypeDeclaration invocationContext,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.type = new ResolvedTypeNode(checkNotNull(type), sourceInfo);
        this.function = checkNotNull(function);
        this.inverseFunction = inverseFunction;
        this.arguments = new ArrayList<>(checkNotNull(arguments));
        this.invocationContext = checkNotNull(invocationContext);
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public boolean needsParentStack() {
        return true;
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        acceptChildren(function.getReceiver(), visitor, ValueEmitterNode.class);
        if (inverseFunction != null) {
            acceptChildren(inverseFunction.getReceiver(), visitor, ValueEmitterNode.class);
        }
        acceptChildren(arguments, visitor, EmitMethodArgumentNode.class);
    }

    @Override
    public List<Generator> emitGenerators(BytecodeEmitContext context) {
        String cachedClassName = getClassCache().get(this);
        if (cachedClassName != null) {
            compiledClassName = cachedClassName;
            return Collections.emptyList();
        }

        AccessVerifier.verifyAccessible(function.getBehavior(), invocationContext, function.getSourceInfo());

        if (inverseFunction != null) {
            AccessVerifier.verifyAccessible(inverseFunction.getBehavior(), invocationContext, inverseFunction.getSourceInfo());
        }

        var generator = new ObservableFunctionGenerator(
            ensureAccessible(function), ensureAccessible(inverseFunction), arguments, type.getTypeInstance());

        compiledClassName = generator.getClassName();
        getClassCache().put(this, compiledClassName);

        return List.of(generator);
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        TypeDeclaration compiledClass = context.getNestedClasses().find(compiledClassName);

        if (!AccessVerifier.isAccessible(function.getBehavior(), compiledClass)) {
            AccessVerifier.verifyAccessible(function.getBehavior(), invocationContext, function.getSourceInfo());
        }

        if (inverseFunction != null && !AccessVerifier.isAccessible(inverseFunction.getBehavior(), compiledClass)) {
            AccessVerifier.verifyAccessible(inverseFunction.getBehavior(), invocationContext, inverseFunction.getSourceInfo());
        }

        code.anew(compiledClass)
            .dup()
            .aload(context.getRuntimeContextLocal())
            .invoke(compiledClass.requireDeclaredConstructor(context.getRuntimeContextClass()));
    }

    /**
     * If the method invocation targets a protected method of a base class of the invocation context
     * class (which is the root class of the FXML document), then the generated nested class doesn't
     * have access to the protected method if it is located in a different package.
     * We can fix this specific scenario by creating a bridge method in the root class which calls
     * the protected method of its base class, and referencing the bridge method from the generated
     * nested class.
     */
    private Callable ensureAccessible(Callable function) {
        if (function == null) {
            return null;
        }

        var receiver = function.getReceiver();
        var behavior = function.getBehavior();
        var sourceInfo = function.getSourceInfo();

        if (!AccessVerifier.isNestedAccessible(behavior, invocationContext)) {
            if (receiver.size() == 1 && equalsInvocationContext(receiver.get(0))) {
                function = new Callable(function.getInvocationContext(), receiver, emitBridgeMethod(behavior), sourceInfo);
            } else {
                AccessVerifier.verifyNestedAccessible(behavior, invocationContext, sourceInfo);
            }
        }

        return function;
    }

    private boolean equalsInvocationContext(ValueEmitterNode node) {
        if (node instanceof EmitGetParentNode getParentNode) {
            return getParentNode.getType().getTypeInstance().equals(invocationContext);
        }

        if (node instanceof EmitGetRootNode getRootNode) {
            return getRootNode.getType().getTypeInstance().equals(invocationContext);
        }

        return false;
    }

    private MethodDeclaration emitBridgeMethod(BehaviorDeclaration behavior) {
        String methodName = NameHelper.getMangledMethodName("bridge$" +
            (behavior instanceof ConstructorDeclaration ctor
                ? ctor.declaringType().simpleName()
                : behavior.name()));

        return invocationContext
            .declaredMethod(methodName, behavior.parameters().stream()
                .map(BehaviorDeclaration.Parameter::type)
                .toArray(TypeDeclaration[]::new))
            .orElseGet(() -> {
                List<BehaviorDeclaration.Parameter> params = behavior.parameters();
                TypeDeclaration returnType = behavior instanceof ConstructorDeclaration constructor ?
                    constructor.declaringType() : ((MethodDeclaration) behavior).returnType();

                MethodDeclaration bridgeMethod = invocationContext
                    .createMethod(methodName, returnType, params.stream()
                        .map(BehaviorDeclaration.Parameter::type)
                        .toArray(TypeDeclaration[]::new))
                    .setModifiers(Modifier.FINAL);

                var code = new Bytecode(bridgeMethod);

                if (behavior instanceof ConstructorDeclaration constructor) {
                    bridgeMethod.setModifiers(Modifier.STATIC);

                    code.anew(constructor.declaringType())
                        .dup();
                } else {
                    code.aload(0);
                }

                for (int i = 0, slots = 1; i < params.size(); slots += params.get(i).type().slots(), ++i) {
                    code.load(params.get(i).type(), slots);
                }

                code.invoke(behavior)
                    .ret(returnType);

                return bridgeMethod.setCode(code);
            });
    }

    @Override
    public EmitObservableFunctionNode deepClone() {
        return new EmitObservableFunctionNode(
            type.getTypeInstance(), function.deepClone(), inverseFunction.deepClone(),
            deepClone(arguments), invocationContext, getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitObservableFunctionNode that = (EmitObservableFunctionNode)o;
        return Objects.equals(function, that.function)
            && Objects.equals(inverseFunction, that.inverseFunction)
            && invocationContext.equals(that.invocationContext)
            && arguments.equals(that.arguments)
            && type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(function, inverseFunction, arguments, invocationContext, type);
    }

    @SuppressWarnings("unchecked")
    private Map<EmitObservableFunctionNode, String> getClassCache() {
        return (Map<EmitObservableFunctionNode, String>)CompilationContext.getCurrent()
            .computeIfAbsent(EmitObservableFunctionNode.class, key -> new HashMap<EmitObservableFunctionNode, String>());
    }
}

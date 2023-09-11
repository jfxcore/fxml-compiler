// Copyright (c) 2022, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CannotCompileException;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.MethodInfo;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.GeneratorEmitterNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.generate.Generator;
import org.jfxcore.compiler.generate.ObservableFunctionGenerator;
import org.jfxcore.compiler.util.AccessVerifier;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Callable;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.ExceptionHelper;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.jfxcore.compiler.util.Descriptors.function;

public class EmitObservableFunctionNode
        extends AbstractNode
        implements ValueEmitterNode, GeneratorEmitterNode, ParentStackInfo, NullableInfo {

    private final Callable function;
    private final Callable inverseFunction;
    private final List<EmitMethodArgumentNode> arguments;
    private final ResolvedTypeNode type;
    private final CtClass invocationContext;
    private transient String compiledClassName;

    public EmitObservableFunctionNode(
            TypeInstance type,
            Callable function,
            @Nullable Callable inverseFunction,
            Collection<? extends EmitMethodArgumentNode> arguments,
            CtClass invocationContext,
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
        acceptChildren(function.getReceiver(), visitor);
        if (inverseFunction != null) {
            acceptChildren(inverseFunction.getReceiver(), visitor);
        }
        acceptChildren(arguments, visitor);
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
            ensureAccessible(context, function), ensureAccessible(context, inverseFunction), arguments);

        compiledClassName = generator.getClassName();
        getClassCache().put(this, compiledClassName);

        return List.of(generator);
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        CtClass compiledClass = context.getNestedClasses().find(compiledClassName);

        if (!AccessVerifier.isAccessible(function.getBehavior(), compiledClass, function.getSourceInfo())) {
            AccessVerifier.verifyAccessible(function.getBehavior(), invocationContext, function.getSourceInfo());
        }

        if (inverseFunction != null &&
                !AccessVerifier.isAccessible(inverseFunction.getBehavior(), compiledClass, inverseFunction.getSourceInfo())) {
            AccessVerifier.verifyAccessible(inverseFunction.getBehavior(), invocationContext, inverseFunction.getSourceInfo());
        }

        code.anew(compiledClass)
            .dup()
            .aload(context.getRuntimeContextLocal())
            .invokespecial(
                compiledClass,
                MethodInfo.nameInit,
                function(CtClass.voidType, context.getRuntimeContextClass()));
    }

    /**
     * If the method invocation targets a protected method of a base class of the invocation context
     * class (which is the root class of the FXML document), then the generated nested class doesn't
     * have access to the protected method if it is located in a different package.
     * We can fix this specific scenario by creating a bridge method in the root class which calls
     * the protected method of its base class, and referencing the bridge method from the generated
     * nested class.
     */
    private Callable ensureAccessible(BytecodeEmitContext context, Callable function) {
        if (function == null) {
            return null;
        }

        var receiver = function.getReceiver();
        var behavior = function.getBehavior();
        var sourceInfo = function.getSourceInfo();

        if (!AccessVerifier.isNestedAccessible(behavior, invocationContext, sourceInfo)) {
            if (receiver.size() == 1 && equalsInvocationContext(receiver.get(0))) {
                function = new Callable(receiver, emitBridgeMethod(context, behavior), sourceInfo);
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

    private CtMethod emitBridgeMethod(BytecodeEmitContext context, CtBehavior behavior) {
        String methodName = NameHelper.getMangledMethodName(
            "bridge$" + (behavior instanceof CtConstructor ctor ?
                ctor.getDeclaringClass().getSimpleName() : behavior.getName()));

        try {
            return invocationContext.getDeclaredMethod(methodName, behavior.getParameterTypes());
        } catch (NotFoundException e) {
            try {
                CtClass[] paramTypes = behavior.getParameterTypes();
                CtClass returnType = behavior instanceof CtConstructor constructor ?
                    constructor.getDeclaringClass() : ((CtMethod)behavior).getReturnType();

                CtMethod bridgeMethod = new CtMethod(returnType, methodName, paramTypes, invocationContext);
                bridgeMethod.setModifiers(Modifier.FINAL);
                invocationContext.addMethod(bridgeMethod);

                int slotCount = Bytecode.getSlotCount(bridgeMethod.getSignature());
                var ctx = new BytecodeEmitContext(context, invocationContext, slotCount, -1);
                var code = ctx.getOutput();

                if (behavior instanceof CtConstructor constructor) {
                    bridgeMethod.setModifiers(Modifier.STATIC);

                    code.anew(constructor.getDeclaringClass())
                        .dup();
                } else {
                    code.aload(0);
                }

                for (int i = 0, slots = 1; i < paramTypes.length; slots += TypeHelper.getSlots(paramTypes[i]), ++i) {
                    code.ext_load(paramTypes[i], slots);
                }

                code.ext_invoke(behavior)
                    .ext_return(returnType);

                bridgeMethod.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
                bridgeMethod.getMethodInfo().rebuildStackMap(invocationContext.getClassPool());
                return bridgeMethod;
            } catch (NotFoundException ex) {
                throw SymbolResolutionErrors.classNotFound(SourceInfo.none(), ex.getMessage());
            } catch (BadBytecode | CannotCompileException ex) {
                throw ExceptionHelper.unchecked(ex);
            }
        }
    }

    @Override
    public EmitObservableFunctionNode deepClone() {
        return new EmitObservableFunctionNode(
            type.getTypeInstance(), function.deepClone(), inverseFunction.deepClone(),
            deepClone(arguments), invocationContext, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitObservableFunctionNode that = (EmitObservableFunctionNode)o;
        return Objects.equals(function, that.function)
            && Objects.equals(inverseFunction, that.inverseFunction)
            && TypeHelper.equals(invocationContext, that.invocationContext)
            && arguments.equals(that.arguments)
            && type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(function, inverseFunction, arguments, TypeHelper.hashCode(invocationContext), type);
    }

    @SuppressWarnings("unchecked")
    private Map<EmitObservableFunctionNode, String> getClassCache() {
        return (Map<EmitObservableFunctionNode, String>)CompilationContext.getCurrent()
            .computeIfAbsent(EmitObservableFunctionNode.class, key -> new HashMap<EmitObservableFunctionNode, String>());
    }

}

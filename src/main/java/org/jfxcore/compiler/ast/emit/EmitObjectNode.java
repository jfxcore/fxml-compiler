// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.Modifier;
import javassist.bytecode.MethodInfo;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.generate.RuntimeContextGenerator;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.jfxcore.compiler.util.Descriptors.*;

/**
 * Emits opcodes to construct a new object instance and places it on top of the operand stack.
 */
public class EmitObjectNode extends ReferenceableNode {

    public enum CreateKind {
        NONE,
        LOAD_LOCAL,
        CONSTRUCTOR,
        VALUE_OF
    }

    private final CtConstructor constructor;
    private final List<ValueNode> arguments;
    private final List<Node> children;
    private final CreateKind createKind;
    private ResolvedTypeNode type;

    public EmitObjectNode(
            @Nullable String fieldName,
            TypeInstance type,
            @Nullable CtConstructor constructor,
            Collection<? extends ValueNode> arguments,
            Collection<? extends Node> children,
            CreateKind createKind,
            SourceInfo sourceInfo) {
        this(null, fieldName, type, constructor, arguments, children, createKind, sourceInfo);
    }

    public EmitObjectNode(
            @Nullable ReferenceableNode referencedNode,
            @Nullable String fieldName,
            TypeInstance type,
            @Nullable CtConstructor constructor,
            Collection<? extends ValueNode> arguments,
            Collection<? extends Node> children,
            CreateKind createKind,
            SourceInfo sourceInfo) {
        super(referencedNode, fieldName, sourceInfo);
        this.type = new ResolvedTypeNode(checkNotNull(type), sourceInfo);
        this.constructor = constructor;
        this.arguments = new ArrayList<>(checkNotNull(arguments));
        this.children = new ArrayList<>(checkNotNull(children));
        this.createKind = checkNotNull(createKind);
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    public @Nullable CtConstructor getConstructor() {
        return constructor;
    }

    public List<ValueNode> getArguments() {
        return arguments;
    }

    public List<Node> getChildren() {
        return children;
    }

    public boolean addsToParentStack() {
        return children.stream().anyMatch(RuntimeContextHelper::needsParentStack)
            || arguments.stream().anyMatch(RuntimeContextHelper::needsParentStack);
    }

    /**
     * Creates a new node that loads the value emitted by the current node.
     * <p>
     * When this method is called, the current node will store its emitted value in a local variable
     * during bytecode generation instead of pushing it to the operand stack. The new node will then
     * load the stored value.
     * <p>
     * The purpose of this mechanism is to make sure that runtime objects referenced by other runtime
     * objects (for example in bindings) are created before being accessed. In this scenario, the
     * current node will create the runtime object in the <i>preamble</i> phase, while the new node returned
     * by this method is used to load the runtime object during property assignments, bindings, etc.
     */
    @Override
    public EmitObjectNode convertToLocalReference() {
        if (!isEmitInPreamble()) {
            throw new UnsupportedOperationException();
        }

        EmitObjectNode node = new EmitObjectNode(
            this,
            null,
            type.getTypeInstance(),
            null,
            Collections.emptyList(),
            children,
            CreateKind.LOAD_LOCAL,
            getSourceInfo());

        this.children.clear();
        return node;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();

        if (isEmitInPreamble()) {
            boolean parentStack = addsToParentStack();

            code.aload(0);

            if (parentStack) {
                emitPushNode(context);
            }

            emitCore(type.getJvmType(), context);

            if (parentStack) {
                emitPopNode(context);
            }

            code.dup_x1()
                .putfield(context.getLocalMarkupClass(), getId(), type.getJvmType());

            storeLocal(code, type.getJvmType());
        } else {
            boolean parentStack = addsToParentStack() && type.getTypeInstance().subtypeOf(Classes.ObjectType());

            emitCore(type.getJvmType(), context);

            if (parentStack) {
                emitPushNode(context);
            }

            for (Node child : children) {
                context.emit(child);
            }

            if (parentStack) {
                emitPopNode(context);
            }
        }
    }

    private void emitPushNode(BytecodeEmitContext context) {
        context.getOutput()
            .dup()
            .aload(context.getRuntimeContextLocal())
            .dup_x1()
            .pop()
            .invokevirtual(
                context.getRuntimeContextClass(),
                RuntimeContextGenerator.PUSH_PARENT_METHOD,
                function(CtClass.voidType, Classes.ObjectType()));
    }

    private void emitPopNode(BytecodeEmitContext context) {
        context.getOutput()
            .aload(context.getRuntimeContextLocal())
            .invokevirtual(
                context.getRuntimeContextClass(),
                RuntimeContextGenerator.POP_PARENT_METHOD,
                function(CtClass.voidType));
    }

    private void emitCore(CtClass typeClass, BytecodeEmitContext context) {
        switch (createKind) {
            case NONE:
                context.getOutput().aload(0);
                break;
            case LOAD_LOCAL:
                getReferencedNode().loadLocal(context.getOutput());
                context.getOutput().ext_autoconv(getSourceInfo(), TypeHelper.getJvmType(getReferencedNode()), typeClass);
                break;
            case CONSTRUCTOR:
                emitInvokeConstructor(typeClass, context);
                break;
            case VALUE_OF:
                emitInvokeValueOf(typeClass, context.getOutput());
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    private void emitInvokeConstructor(CtClass type, BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        TypeInstance[] paramTypes = new Resolver(getSourceInfo()).getParameterTypes(constructor, Collections.emptyList());
        boolean varargs = Modifier.isVarArgs(constructor.getModifiers());

        code.anew(type)
            .dup();

        for (int i = 0; i < paramTypes.length; ++i) {
            boolean lastParam = i == paramTypes.length - 1;

            if (varargs && lastParam) {
                TypeInstance componentType = paramTypes[i].getComponentType();
                TypeInstance argType = TypeHelper.getTypeInstance(arguments.get(i));

                if (arguments.size() > paramTypes.length || !argType.subtypeOf(paramTypes[i])) {
                    code.newarray(componentType.jvmType(), arguments.size() - paramTypes.length + 1);

                    for (int j = i; j < arguments.size(); ++j) {
                        argType = TypeHelper.getTypeInstance(arguments.get(j));

                        code.dup()
                            .iconst(j - i);

                        context.emit(arguments.get(j));

                        code.ext_autoconv(arguments.get(i).getSourceInfo(), argType.jvmType(), componentType.jvmType())
                            .ext_arraystore(argType.jvmType());
                    }
                } else {
                    context.emit(arguments.get(i));
                    code.ext_autoconv(arguments.get(i).getSourceInfo(), argType.jvmType(), componentType.jvmType());
                }
            } else {
                context.emit(arguments.get(i));
                code.ext_autoconv(arguments.get(i).getSourceInfo(), TypeHelper.getJvmType(arguments.get(i)),
                                  paramTypes[i].jvmType());
            }
        }

        code.invokespecial(type, MethodInfo.nameInit, constructor.getSignature());
    }

    private void emitInvokeValueOf(CtClass type, Bytecode code) {
        EmitLiteralNode literalNode = (EmitLiteralNode)arguments.get(0);
        CtClass literalType = literalNode.getType().getJvmType();

        if (literalType.equals(Classes.StringType())) {
            code.ldc(literalNode.getLiteral(String.class));
        } else if (literalType == CtClass.charType) {
            code.iconst(literalNode.getLiteral(Character.class));
        } else if (TypeHelper.isNumericPrimitive(literalType)) {
            code.ext_const(literalType, literalNode.getLiteral(Number.class));
        } else {
            throw new IllegalArgumentException();
        }

        code.invokestatic(type, "valueOf", function(type, literalType));
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        type = (ResolvedTypeNode)type.accept(visitor);
        acceptChildren(arguments, visitor);
        acceptChildren(children, visitor);
    }

    @Override
    public EmitObjectNode deepClone() {
        return new EmitObjectNode(
            getReferencedNode(),
            getId(),
            type.getTypeInstance(),
            constructor,
            deepClone(arguments),
            deepClone(children),
            createKind,
            getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitObjectNode that = (EmitObjectNode)o;
        return Objects.equals(getId(), that.getId()) &&
            TypeHelper.equals(constructor, that.constructor) &&
            arguments.equals(that.arguments) &&
            children.equals(that.children) &&
            createKind == that.createKind &&
            type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), TypeHelper.hashCode(constructor), arguments, children, createKind, type);
    }

}

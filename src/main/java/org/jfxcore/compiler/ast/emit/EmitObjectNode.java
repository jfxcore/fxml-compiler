// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.MethodInfo;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.NodeDataKey;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.generate.RuntimeContextGenerator;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import org.jfxcore.compiler.util.TypeInvoker;
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
        VALUE_OF,
        FACTORY
    }

    public static class ConstructorBuilder {
        private final TypeInstance type;
        private final CtConstructor constructor;
        private final Collection<? extends ValueNode> arguments;
        private final SourceInfo sourceInfo;
        private Collection<? extends Node> children = Collections.emptyList();
        private String fieldName;

        private ConstructorBuilder(
                TypeInstance type,
                CtConstructor constructor,
                Collection<? extends ValueNode> arguments,
                SourceInfo sourceInfo) {
            this.type = type;
            this.constructor = constructor;
            this.arguments = arguments;
            this.sourceInfo = sourceInfo;
        }

        public ConstructorBuilder children(Collection<? extends Node> children) {
            this.children = children;
            return this;
        }

        public ConstructorBuilder backingField(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public EmitObjectNode create() {
            for (ValueNode argument : arguments) {
                if (argument instanceof ObjectNode objectNode) {
                    objectNode.setNodeData(NodeDataKey.CONSTRUCTOR_ARGUMENT, true);
                }
            }

            return new EmitObjectNode(
                fieldName,
                type,
                constructor,
                arguments,
                children,
                EmitObjectNode.CreateKind.CONSTRUCTOR,
                sourceInfo);
        }
    }

    public static class ValueOfBuilder {
        private final TypeInstance type;
        private final SourceInfo sourceInfo;
        private final CtMethod method;
        private ValueEmitterNode value;
        private Collection<? extends Node> children = Collections.emptyList();
        private String fieldName;

        private ValueOfBuilder(TypeInstance type, CtMethod method, SourceInfo sourceInfo) {
            this.type = type;
            this.method = method;
            this.sourceInfo = sourceInfo;
        }

        public ValueOfBuilder value(ValueEmitterNode value) {
            this.value = value;
            return this;
        }

        public ValueOfBuilder textValue(String value) {
            this.value = new EmitLiteralNode(TypeInstance.StringType(), value, sourceInfo);
            return this;
        }

        public ValueOfBuilder children(Collection<? extends Node> children) {
            this.children = children;
            return this;
        }

        public ValueOfBuilder backingField(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public EmitObjectNode create() {
            return new EmitObjectNode(
                fieldName,
                type,
                method,
                List.of(value),
                children,
                CreateKind.VALUE_OF,
                sourceInfo);
        }
    }

    public static class FactoryBuilder {
        private final TypeInstance type;
        private final SourceInfo sourceInfo;
        private final CtMethod factoryMethod;
        private Collection<? extends Node> children = Collections.emptyList();
        private String fieldName;

        private FactoryBuilder(TypeInstance type, CtMethod factoryMethod, SourceInfo sourceInfo) {
            this.type = type;
            this.factoryMethod = factoryMethod;
            this.sourceInfo = sourceInfo;
        }

        public FactoryBuilder children(Collection<? extends Node> children) {
            this.children = children;
            return this;
        }

        public FactoryBuilder backingField(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public EmitObjectNode create() {
            return new EmitObjectNode(
                fieldName,
                type,
                factoryMethod,
                Collections.emptyList(),
                children,
                CreateKind.FACTORY,
                sourceInfo);
        }
    }

    public static class RootObjectBuilder {
        private final TypeInstance type;
        private final SourceInfo sourceInfo;
        private Collection<? extends Node> children = Collections.emptyList();

        private RootObjectBuilder(TypeInstance type, SourceInfo sourceInfo) {
            this.type = type;
            this.sourceInfo = sourceInfo;
        }

        public RootObjectBuilder children(Collection<? extends Node> children) {
            this.children = children;
            return this;
        }

        public EmitObjectNode create() {
            return new EmitObjectNode(
                null,
                type,
                null,
                Collections.emptyList(),
                children,
                CreateKind.NONE,
                sourceInfo);
        }
    }

    /**
     * Emits an object by invoking a constructor.
     */
    public static ConstructorBuilder constructor(
            TypeInstance type, CtConstructor constructor, List<? extends ValueNode> arguments, SourceInfo sourceInfo) {
        return new ConstructorBuilder(type, constructor, arguments, sourceInfo);
    }

    /**
     * Emits an object by invoking a T::valueOf(?)->T method.
     */
    public static ValueOfBuilder valueOf(TypeInstance type, CtMethod valueOfMethod, SourceInfo sourceInfo) {
        return new ValueOfBuilder(type, valueOfMethod, sourceInfo);
    }

    /**
     * Emits an object by invoking a parameterless factory method.
     */
    public static FactoryBuilder factory(TypeInstance type, CtMethod factoryMethod, SourceInfo sourceInfo) {
        return new FactoryBuilder(type, factoryMethod, sourceInfo);
    }

    /**
     * Emits an object by loading an existing object from a local.
     */
    public static EmitObjectNode loadLocal(
            TypeInstance type, ReferenceableNode referencedNode, SourceInfo sourceInfo) {
        return new EmitObjectNode(
            referencedNode,
            null,
            type,
            null,
            Collections.emptyList(),
            Collections.emptyList(),
            EmitObjectNode.CreateKind.LOAD_LOCAL,
            sourceInfo);
    }

    /**
     * Emits an object by loading the root object.
     */
    public static RootObjectBuilder loadRoot(TypeInstance type, SourceInfo sourceInfo) {
        return new RootObjectBuilder(type, sourceInfo);
    }

    private final CtBehavior constructorOrFactoryMethod;
    private final List<ValueNode> arguments;
    private final List<Node> children;
    private final CreateKind createKind;
    private ResolvedTypeNode type;

    private EmitObjectNode(
            @Nullable String fieldName,
            TypeInstance type,
            @Nullable CtBehavior constructorOrFactoryMethod,
            Collection<? extends ValueNode> arguments,
            Collection<? extends Node> children,
            CreateKind createKind,
            SourceInfo sourceInfo) {
        this(null, fieldName, type, constructorOrFactoryMethod, arguments, children, createKind, sourceInfo);
    }

    private EmitObjectNode(
            @Nullable ReferenceableNode referencedNode,
            @Nullable String fieldName,
            TypeInstance type,
            @Nullable CtBehavior constructorOrFactoryMethod,
            Collection<? extends ValueNode> arguments,
            Collection<? extends Node> children,
            CreateKind createKind,
            SourceInfo sourceInfo) {
        super(referencedNode, fieldName, sourceInfo);
        this.type = new ResolvedTypeNode(checkNotNull(type), sourceInfo);
        this.constructorOrFactoryMethod = constructorOrFactoryMethod;
        this.arguments = new ArrayList<>(checkNotNull(arguments));
        this.children = new ArrayList<>(checkNotNull(children));
        this.createKind = checkNotNull(createKind);
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    public List<Node> getChildren() {
        return children;
    }

    public boolean addsToParentStack() {
        return !(Classes.Markup.isAvailable() && type.getTypeInstance().subtypeOf(Classes.Markup.MarkupExtensionType()))
            && (children.stream().anyMatch(RuntimeContextHelper::needsParentStack)
                || arguments.stream().anyMatch(RuntimeContextHelper::needsParentStack));
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
                emitInvokeValueOf(typeClass, context);
                break;
            case FACTORY:
                emitInvokeFactoryMethod(context.getOutput());
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    private void emitInvokeConstructor(CtClass type, BytecodeEmitContext context) {
        Bytecode code = context.getOutput();

        TypeInstance[] paramTypes = new TypeInvoker(getSourceInfo())
            .invokeParameterTypes(constructorOrFactoryMethod, Collections.emptyList());

        boolean varargs = Modifier.isVarArgs(constructorOrFactoryMethod.getModifiers());

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

                        code.ext_castconv(arguments.get(i).getSourceInfo(), argType.jvmType(), componentType.jvmType())
                            .ext_arraystore(componentType.jvmType());
                    }
                } else {
                    context.emit(arguments.get(i));
                    code.ext_castconv(arguments.get(i).getSourceInfo(), argType.jvmType(), paramTypes[i].jvmType());
                }
            } else {
                context.emit(arguments.get(i));
                code.ext_castconv(arguments.get(i).getSourceInfo(), TypeHelper.getJvmType(arguments.get(i)),
                                  paramTypes[i].jvmType());
            }
        }

        code.invokespecial(type, MethodInfo.nameInit, constructorOrFactoryMethod.getSignature());
    }

    private void emitInvokeValueOf(CtClass type, BytecodeEmitContext context) {
        CtClass argType = unchecked(() -> constructorOrFactoryMethod.getParameterTypes()[0]);
        context.emit(arguments.get(0));
        context.getOutput()
            .ext_castconv(getSourceInfo(), TypeHelper.getJvmType(arguments.get(0)), argType)
            .invokestatic(type, constructorOrFactoryMethod.getName(), function(type, argType));
    }

    private void emitInvokeFactoryMethod(Bytecode code) {
        code.invokestatic(
            constructorOrFactoryMethod.getDeclaringClass(),
            constructorOrFactoryMethod.getName(),
            constructorOrFactoryMethod.getSignature());
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        type = (ResolvedTypeNode)type.accept(visitor);
        acceptChildren(arguments, visitor, ValueNode.class);
        acceptChildren(children, visitor, Node.class);
    }

    @Override
    public EmitObjectNode deepClone() {
        return new EmitObjectNode(
            getReferencedNode(),
            getId(),
            type.getTypeInstance(),
            constructorOrFactoryMethod,
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
            TypeHelper.equals(constructorOrFactoryMethod, that.constructorOrFactoryMethod) &&
            arguments.equals(that.arguments) &&
            children.equals(that.children) &&
            createKind == that.createKind &&
            type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            getId(), TypeHelper.hashCode(constructorOrFactoryMethod), arguments, children, createKind, type);
    }
}

// Copyright (c) 2025, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.Types;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.PropertyInfo;
import java.util.Objects;

import static org.jfxcore.compiler.generate.RuntimeContextGenerator.*;
import static org.jfxcore.compiler.type.Types.*;
import static org.jfxcore.compiler.type.Types.Markup.*;

public class EmitApplyMarkupExtensionNode extends AbstractNode implements ValueNode, EmitterNode, ParentStackInfo {

    private final TypeDeclaration markupExtensionInterface;
    private final PropertyInfo propertyInfo;
    private final TypeInstance returnType;
    private final String targetName;
    private ValueEmitterNode markupExtensionNode;
    private ResolvedTypeNode targetType;

    public EmitApplyMarkupExtensionNode(
            ValueEmitterNode markupExtensionNode,
            TypeDeclaration markupExtensionInterface,
            String targetName,
            TypeInstance targetType,
            TypeInstance returnType,
            @Nullable PropertyInfo propertyInfo) {
        super(markupExtensionNode.getSourceInfo());
        this.targetType = new ResolvedTypeNode(checkNotNull(targetType), markupExtensionNode.getSourceInfo());
        this.returnType = checkNotNull(returnType);
        this.targetName = targetName;
        this.markupExtensionInterface = checkNotNull(markupExtensionInterface);
        this.markupExtensionNode = checkNotNull(markupExtensionNode);
        this.propertyInfo = propertyInfo;

        if (returnType.equals(TypeInstance.voidType())) {
            Objects.requireNonNull(propertyInfo, "propertyInfo");
        }
    }

    @Override
    public boolean needsParentStack() {
        return true;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();

        markupExtensionNode.emit(context);

        Local extensionLocal = code.acquireLocal(false);
        code.astore(extensionLocal);

        emitSetPropertyInfo(context);

        if (this instanceof Supplier) {
            code.aload(extensionLocal)
                .aload(context.getRuntimeContextLocal())
                .invoke(markupExtensionInterface.requireDeclaredMethod("get", MarkupContextDecl()));

            if (!returnType.isPrimitive()) {
                code.checkcast(returnType.declaration());
            }

            code.castconv(returnType.declaration(), targetType.getTypeDeclaration());
        } else {
            TypeDeclaration acceptParamType =
                markupExtensionInterface.subtypeOf(MarkupExtension.PropertyConsumerDecl())
                    ? Types.PropertyDecl()
                    : Types.ReadOnlyPropertyDecl();

            Local propertyLocal = code.acquireLocal(false);

            code.dup()
                .invoke(Objects.requireNonNull(propertyInfo.getPropertyGetter()))
                .astore(propertyLocal)
                .aload(extensionLocal)
                .aload(propertyLocal)
                .aload(context.getRuntimeContextLocal())
                .invoke(markupExtensionInterface.requireDeclaredMethod("accept", acceptParamType, MarkupContextDecl()));

            code.releaseLocal(propertyLocal);
        }

        code.releaseLocal(extensionLocal);
    }

    private void emitSetPropertyInfo(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        Local beanLocal = null;

        if (propertyInfo != null) {
            beanLocal = code.acquireLocal(false);
            code.dup()
                .astore(beanLocal);
        }

        code.aload(context.getRuntimeContextLocal());

        emitTargetType(code);

        if (beanLocal != null) {
            code.aload(beanLocal)
                .releaseLocal(beanLocal);
        } else {
            code.aconst_null();
        }

        if (targetName != null) {
            code.ldc(targetName);
        } else {
            code.aconst_null();
        }

        code.invoke(context.getRuntimeContextClass()
                           .requireDeclaredMethod(SET_TARGET_INFO, ClassDecl(), ObjectDecl(), StringDecl()));
    }

    private void emitTargetType(Bytecode code) {
        if (targetType.getTypeDeclaration().isPrimitive()) {
            switch (targetType.getTypeDeclaration().name()) {
                case "boolean" -> code.getstatic(BooleanDecl().requireField("TYPE"));
                case "int" -> code.getstatic(IntegerDecl().requireField("TYPE"));
                case "long" -> code.getstatic(LongDecl().requireField("TYPE"));
                case "float" -> code.getstatic(FloatDecl().requireField("TYPE"));
                case "double" -> code.getstatic(DoubleDecl().requireField("TYPE"));
                case "short" -> code.getstatic(ShortDecl().requireField("TYPE"));
                case "char" -> code.getstatic(CharacterDecl().requireField("TYPE"));
                case "byte" -> code.getstatic(ByteDecl().requireField("TYPE"));
                default -> throw new AssertionError();
            }
        } else {
            code.ldc(targetType.getTypeDeclaration());
        }
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        markupExtensionNode = (ValueEmitterNode)markupExtensionNode.accept(visitor);
        targetType = (ResolvedTypeNode)targetType.accept(visitor);
    }

    @Override
    public ResolvedTypeNode getType() {
        return targetType;
    }

    @Override
    public EmitApplyMarkupExtensionNode deepClone() {
        return new EmitApplyMarkupExtensionNode(
            markupExtensionNode.deepClone(), markupExtensionInterface, targetName,
            targetType.getTypeInstance(), returnType, propertyInfo);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof EmitApplyMarkupExtensionNode other
            && markupExtensionNode.equals(other.markupExtensionNode)
            && markupExtensionInterface.equals(other.markupExtensionInterface)
            && Objects.equals(targetName, other.targetName)
            && targetType.equals(other.targetType)
            && returnType.equals(other.returnType)
            && Objects.equals(propertyInfo, other.propertyInfo);
    }

    TypeDeclaration getMarkupExtensionInterface() { return markupExtensionInterface; }
    PropertyInfo getPropertyInfo() { return propertyInfo; }
    TypeInstance getReturnType() { return returnType; }
    TypeInstance getTargetType() { return targetType.getTypeInstance(); }
    String getTargetName() { return targetName;}
    ValueEmitterNode getMarkupExtensionNode() { return markupExtensionNode; }

    /**
     * Specialized version of {@link EmitApplyMarkupExtensionNode} that implements {@link ValueEmitterNode},
     * used by markup extensions that produce a value and place it on the top of the operand stack.
     */
    public static class Supplier extends EmitApplyMarkupExtensionNode implements ValueEmitterNode {
        public Supplier(ValueEmitterNode markupExtensionNode, TypeDeclaration markupExtensionInterface, String targetName,
                        TypeInstance targetType, TypeInstance returnType, @Nullable PropertyInfo propertyInfo) {
            super(markupExtensionNode, markupExtensionInterface, targetName, targetType, returnType, propertyInfo);
        }

        @Override
        public Supplier deepClone() {
            return new Supplier(getMarkupExtensionNode(), getMarkupExtensionInterface(), getTargetName(),
                                getTargetType(), getReturnType(), getPropertyInfo());
        }
    }
}

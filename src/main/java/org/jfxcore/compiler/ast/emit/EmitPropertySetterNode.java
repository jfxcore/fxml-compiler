// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.PropertyInfo;
import java.util.Objects;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * Emits opcodes to set a property value.
 */
public class EmitPropertySetterNode extends AbstractNode implements EmitterNode {

    private final PropertyInfo propertyInfo;
    private final boolean content;
    private ValueNode value;

    public EmitPropertySetterNode(PropertyInfo propertyInfo, ValueNode value, boolean content, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.propertyInfo = checkNotNull(propertyInfo);
        this.value = checkNotNull(value);
        this.content = content;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        if (!(value instanceof ValueEmitterNode)) {
            context.emit(value);
        } else if (content) {
            emitAddValues(context);
        } else if (propertyInfo.getSetter() != null) {
            emitInvokeSetter(context);
        } else {
            emitInvokePropertySetter(context);
        }
    }

    private void emitInvokeSetter(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        MethodDeclaration method = Objects.requireNonNull(propertyInfo.getSetter());
        TypeDeclaration valueType = TypeHelper.getTypeDeclaration(value);
        TypeDeclaration targetType = method.parameters().get(0).type();

        code.dup();
        context.emit(value);
        code.castconv(valueType, targetType)
            .invoke(method);
    }

    private void emitInvokePropertySetter(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        TypeDeclaration valueType = TypeHelper.getTypeDeclaration(value);
        TypeDeclaration targetType = propertyInfo.getType().declaration();
        MethodDeclaration method = propertyInfo.getSetterOrPropertyGetter();
        TypeDeclaration declaringClass = new Resolver(getSourceInfo()).getWritableClass(method.returnType(), false);

        context.emit(value);

        Local valueLocal = code.acquireLocal(valueType);
        code.store(valueType, valueLocal);

        code.dup()
            .invoke(method)
            .load(valueType, valueLocal)
            .castconv(valueType, targetType);

        if (targetType.isPrimitive()) {
            code.invoke(declaringClass.requireDeclaredMethod("set", targetType));
        } else {
            code.invoke(declaringClass.requireDeclaredMethod("setValue", ObjectDecl()));
        }

        code.releaseLocal(valueLocal);
    }

    private void emitAddValues(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();

        context.emit(value);

        Local local = code.acquireLocal(false);
        code.astore(local);

        code.aload(local)
            .ifnonnull(() -> {
                if (propertyInfo.getGetter() != null) {
                    code.dup()
                        .invoke(propertyInfo.getGetter())
                        .aload(local);
                } else {
                    code.dup()
                        .invoke(Objects.requireNonNull(propertyInfo.getPropertyGetter()));

                    if (propertyInfo.getType().subtypeOf(CollectionDecl()) &&
                            !propertyInfo.getObservableType().subtypeOf(CollectionDecl())) {
                        code.invoke(ObservableValueDecl().requireDeclaredMethod("getValue"))
                            .checkcast(CollectionDecl());
                    }
                    else if (propertyInfo.getType().subtypeOf(MapDecl()) &&
                                !propertyInfo.getObservableType().subtypeOf(MapDecl())) {
                        code.invoke(ObservableValueDecl().requireDeclaredMethod("getValue"))
                            .checkcast(MapDecl());
                    }

                    code.aload(local);
                }

                if (propertyInfo.getType().subtypeOf(CollectionDecl())) {
                    code.invoke(CollectionDecl().requireDeclaredMethod("addAll", CollectionDecl()))
                        .pop();
                } else if (propertyInfo.getType().subtypeOf(MapDecl())) {
                    code.invoke(MapDecl().requireDeclaredMethod("putAll", MapDecl()));
                } else {
                    throw new IllegalArgumentException(propertyInfo.getType().toString());
                }
            });
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        value = (ValueNode)value.accept(visitor);
    }

    @Override
    public EmitPropertySetterNode deepClone() {
        return new EmitPropertySetterNode(propertyInfo, value.deepClone(), content, getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitPropertySetterNode that = (EmitPropertySetterNode)o;
        return propertyInfo.equals(that.propertyInfo) && value.equals(that.value) && content == that.content;
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyInfo, value, content);
    }
}

// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import javassist.NotFoundException;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;

import static org.jfxcore.compiler.util.Classes.BooleanType;
import static org.jfxcore.compiler.util.Classes.DoubleType;
import static org.jfxcore.compiler.util.Classes.FloatType;
import static org.jfxcore.compiler.util.Classes.IntegerType;
import static org.jfxcore.compiler.util.Classes.LongType;
import static org.jfxcore.compiler.util.Classes.NumberType;
import static org.jfxcore.compiler.util.Classes.ObjectType;
import static org.jfxcore.compiler.util.Classes.ObservableBooleanValueType;
import static org.jfxcore.compiler.util.Classes.ObservableDoubleValueType;
import static org.jfxcore.compiler.util.Classes.ObservableFloatValueType;
import static org.jfxcore.compiler.util.Classes.ObservableIntegerValueType;
import static org.jfxcore.compiler.util.Classes.ObservableLongValueType;
import static org.jfxcore.compiler.util.Classes.ObservableObjectValueType;
import static org.jfxcore.compiler.util.Descriptors.function;

/**
 * Wraps a non-observable value into an instance of {@link javafx.beans.value.ObservableValue}.
 */
public class EmitWrapValueNode extends AbstractNode implements ValueEmitterNode, NullableInfo {

    private EmitterNode child;
    private ResolvedTypeNode type;

    public EmitWrapValueNode(EmitterNode child) {
        super(child.getSourceInfo());
        this.child = child;
        this.type = new ResolvedTypeNode(
            new Resolver(child.getSourceInfo()).getObservableClass(TypeHelper.getTypeInstance(child)),
            child.getSourceInfo());
    }

    @Override
    public TypeNode getType() {
        return type;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        TypeInstance requestedType = type.getTypeInstance();
        TypeInstance actualType = TypeHelper.getWidenedNumericType(TypeHelper.getTypeInstance(child));

        context.emit(child);

        if (requestedType.subtypeOf(ObservableBooleanValueType())) {
            code.ext_autoconv(getSourceInfo(), actualType.jvmType(), BooleanType());
            invokestaticSafe(code, ObservableBooleanValueType(), "observableBooleanValue",
                             function(ObservableBooleanValueType(), BooleanType()));
        } else if (requestedType.subtypeOf(ObservableIntegerValueType())) {
            if (actualType.isPrimitive()) {
                code.ext_numericconv(getSourceInfo(), actualType.jvmType(), IntegerType());
            }

            invokestaticSafe(code, ObservableIntegerValueType(), "observableIntegerValue",
                             function(ObservableIntegerValueType(), NumberType()));
        } else if (requestedType.subtypeOf(ObservableLongValueType())) {
            if (actualType.isPrimitive()) {
                code.ext_numericconv(getSourceInfo(), actualType.jvmType(), LongType());
            }

            invokestaticSafe(code, ObservableLongValueType(), "observableLongValue",
                             function(ObservableLongValueType(), NumberType()));
        } else if (requestedType.subtypeOf(ObservableFloatValueType())) {
            if (actualType.isPrimitive()) {
                code.ext_numericconv(getSourceInfo(), actualType.jvmType(), FloatType());
            }

            invokestaticSafe(code, ObservableFloatValueType(), "observableFloatValue",
                              function(ObservableFloatValueType(), NumberType()));
        } else if (requestedType.subtypeOf(ObservableDoubleValueType())) {
            if (actualType.isPrimitive()) {
                code.ext_numericconv(getSourceInfo(), actualType.jvmType(), DoubleType());
            }

            invokestaticSafe(code, ObservableDoubleValueType(), "observableDoubleValue",
                             function(ObservableDoubleValueType(), NumberType()));
        } else {
            invokestaticSafe(code, ObservableObjectValueType(), "observableObjectValue",
                             function(ObservableObjectValueType(), ObjectType()));
        }
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        type = (ResolvedTypeNode)type.accept(visitor);
        child = (EmitterNode)child.accept(visitor);
    }

    @Override
    public EmitWrapValueNode deepClone() {
        return new EmitWrapValueNode(child.deepClone());
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    /**
     * Check whether the method actually exists, as it is exclusive to the JFXcore runtime
     */
    private void invokestaticSafe(Bytecode code, CtClass type, String name, String desc) {
        try {
            type.getMethod(name, desc);
        } catch (NotFoundException ex) {
            throw BindingSourceErrors.bindingNotSupported(getSourceInfo());
        }

        code.invokestatic(type, name, desc);
    }

}

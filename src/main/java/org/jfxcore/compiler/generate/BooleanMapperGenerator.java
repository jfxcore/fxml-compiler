// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.function.BiConsumer;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;
import static org.jfxcore.compiler.util.ExceptionHelper.*;

public class BooleanMapperGenerator extends ClassGenerator {

    private final TypeInstance typeInstance =
        new Resolver(SourceInfo.none()).getTypeInstance(BooleanBindingType());

    private final String className;
    private final CtClass valueType;
    private final boolean invert;

    public BooleanMapperGenerator(CtClass valueType, boolean invert) {
        this.valueType = valueType;
        this.invert = invert;

        String name;
        CtClass boxedType = TypeHelper.getBoxedType(valueType);

        if (unchecked(SourceInfo.none(), () -> boxedType.subtypeOf(BooleanType()))) {
            name = "Boolean";
        } else if (unchecked(SourceInfo.none(), () -> boxedType.subtypeOf(FloatType()))) {
            name = "Float";
        } else if (unchecked(SourceInfo.none(), () -> boxedType.subtypeOf(DoubleType()))) {
            name = "Double";
        } else if (unchecked(SourceInfo.none(), () -> boxedType.subtypeOf(CharacterType()))) {
            name = "Character";
        } else if (unchecked(SourceInfo.none(), () -> boxedType.subtypeOf(NumberType()))) {
            name = "Number";
        } else {
            name = "Object";
        }

        this.className = NameHelper.getMangledClassName(name + (invert ? "ToInvBoolean" : "ToBoolean"));
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public TypeInstance getTypeInstance() {
        return typeInstance;
    }

    @Override
    public void emitClass(BytecodeEmitContext context) throws Exception {
        generatedClass = context.getNestedClasses().create(getClassName());
        generatedClass.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.setSuperclass(BooleanBindingType());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField field =new CtField(ObservableValueType(), "observable", generatedClass);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addField(field);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        super.emitCode(context);

        CtConstructor constructor = new CtConstructor(new CtClass[] {ObservableValueType()}, generatedClass);
        SharedMethodImpls.createBehavior(context, generatedClass, constructor, 2, code -> {
            // super()
            code.aload(0)
                .invokespecial(generatedClass.getSuperclass(), MethodInfo.nameInit, constructor());

            // this.observable = $1
            code.aload(0)
                .aload(1)
                .putfield(generatedClass, "observable", ObservableValueType());

            // bind($1)
            code.aload(0)
                .newarray(ObservableType(), 1)
                .dup()
                .iconst(0)
                .aload(1)
                .ext_arraystore(ObservableType())
                .invokevirtual(generatedClass.getSuperclass(), "bind", "([Ljavafx/beans/Observable;)V")
                .vreturn();
        });

        CtMethod method = new CtMethod(CtClass.booleanType, "computeValue", new CtClass[0], generatedClass);
        SharedMethodImpls.createBehavior(context, generatedClass, method, 1, code -> {
            BiConsumer<Runnable, Runnable> op = code::ifeq;

            code.aload(0)
                .getfield(generatedClass, "observable", ObservableValueType())
                .invokeinterface(ObservableValueType(), "getValue", function(ObjectType()));

            if (valueType.subtypeOf(BooleanType())) {
                code.checkcast(BooleanType())
                    .ext_castconv(SourceInfo.none(), BooleanType(), CtClass.booleanType);
            } else if (valueType.subtypeOf(FloatType())) {
                code.checkcast(FloatType())
                    .ext_castconv(SourceInfo.none(), FloatType(), CtClass.floatType)
                    .fconst(0)
                    .fcmpl();
            } else if (valueType.subtypeOf(DoubleType())) {
                code.checkcast(DoubleType())
                    .ext_castconv(SourceInfo.none(), DoubleType(), CtClass.doubleType)
                    .dconst(0)
                    .dcmpl();
            } else if (valueType.subtypeOf(CharacterType())) {
                code.checkcast(CharacterType())
                    .ext_castconv(SourceInfo.none(), CharacterType(), CtClass.intType);
            } else if (valueType.subtypeOf(NumberType())) {
                code.checkcast(NumberType())
                    .ext_castconv(SourceInfo.none(), NumberType(), CtClass.intType);
            } else {
                op = code::ifnull;
            }

            op.accept(() -> code.iconst(invert ? 1 : 0), () -> code.iconst(invert ? 0 : 1));

            code.ireturn();
        });
    }

}

// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;

import static org.jfxcore.compiler.type.KnownSymbols.*;

public class PushListenerGenerator extends ClassGenerator {

    private static final String CLASS_NAME_BASE = "PushListener";
    private static final String TARGET_FIELD = "target";
    private static final String SOURCE_FIELD = "source";

    public static final String CLASS_NAME = NameHelper.getMangledClassName(CLASS_NAME_BASE);

    private final String className;
    private final TypeDeclaration targetType;
    private final TypeDeclaration sourceType;
    private final MethodDeclaration getMethod;
    private final MethodDeclaration setMethod;

    private ConstructorDeclaration constructor;
    private MethodDeclaration invalidatedMethod;
    private FieldDeclaration targetField;
    private FieldDeclaration sourceField;

    public PushListenerGenerator(TypeDeclaration type) {
        if (type.equals(booleanDecl())) {
            className = NameHelper.getMangledClassName("Boolean" + CLASS_NAME_BASE);
            targetType = ObservableBooleanValueDecl();
            sourceType = BooleanPropertyDecl();
            getMethod = ObservableBooleanValueDecl().requireDeclaredMethod("get");
            setMethod = WritableBooleanValueDecl().requireDeclaredMethod("set", booleanDecl());
        } else if (type.equals(intDecl())) {
            className = NameHelper.getMangledClassName("Int" + CLASS_NAME_BASE);
            targetType = ObservableIntegerValueDecl();
            sourceType = IntegerPropertyDecl();
            getMethod = ObservableIntegerValueDecl().requireDeclaredMethod("get");
            setMethod = WritableIntegerValueDecl().requireDeclaredMethod("set", intDecl());
        } else if (type.equals(longDecl())) {
            className = NameHelper.getMangledClassName("Long" + CLASS_NAME_BASE);
            targetType = ObservableLongValueDecl();
            sourceType = LongPropertyDecl();
            getMethod = ObservableLongValueDecl().requireDeclaredMethod("get");
            setMethod = WritableLongValueDecl().requireDeclaredMethod("set", longDecl());
        } else if (type.equals(floatDecl())) {
            className = NameHelper.getMangledClassName("Float" + CLASS_NAME_BASE);
            targetType = ObservableFloatValueDecl();
            sourceType = FloatPropertyDecl();
            getMethod = ObservableFloatValueDecl().requireDeclaredMethod("get");
            setMethod = WritableFloatValueDecl().requireDeclaredMethod("set", floatDecl());
        } else if (type.equals(doubleDecl())) {
            className = NameHelper.getMangledClassName("Double" + CLASS_NAME_BASE);
            targetType = ObservableDoubleValueDecl();
            sourceType = DoublePropertyDecl();
            getMethod = ObservableDoubleValueDecl().requireDeclaredMethod("get");
            setMethod = WritableDoubleValueDecl().requireDeclaredMethod("set", doubleDecl());
        } else {
            className = NameHelper.getMangledClassName(CLASS_NAME_BASE);
            targetType = ObservableValueDecl();
            sourceType = PropertyDecl();
            getMethod = ObservableValueDecl().requireDeclaredMethod("getValue");
            setMethod = WritableValueDecl().requireDeclaredMethod("setValue", ObjectDecl());
        }
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public TypeInstance getTypeInstance() {
        return TypeInstance.of(InvalidationListenerDecl());
    }

    @Override
    public TypeDeclaration emitClass(BytecodeEmitContext context) {
        return super.emitClass(context)
            .addInterface(InvalidationListenerDecl())
            .setModifiers(Modifier.PRIVATE | Modifier.FINAL);
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {
        targetField = createField(TARGET_FIELD, targetType).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        sourceField = createField(SOURCE_FIELD, sourceType).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) {
        super.emitMethods(context);

        constructor = createConstructor(targetType, sourceType);
        invalidatedMethod = createMethod("invalidated", voidDecl(), ObservableDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        constructor.setCode(new Bytecode(constructor)
            .aload(0)
            .invoke(requireSuperClass().requireDeclaredConstructor())
            .aload(0)
            .aload(1)
            .putfield(targetField)
            .aload(0)
            .aload(2)
            .putfield(sourceField)
            .aload(2)
            .aload(1)
            .invoke(getMethod)
            .invoke(setMethod)
            .aload(2)
            .anew(WeakInvalidationListenerDecl())
            .dup()
            .aload(0)
            .invoke(WeakInvalidationListenerDecl().requireDeclaredConstructor(InvalidationListenerDecl()))
            .invoke(ObservableDecl().requireDeclaredMethod("addListener", InvalidationListenerDecl()))
            .vreturn());

        invalidatedMethod.setCode(new Bytecode(invalidatedMethod)
            .aload(0)
            .getfield(sourceField)
            .aload(0)
            .getfield(targetField)
            .invoke(getMethod)
            .invoke(setMethod)
            .vreturn());
    }
}

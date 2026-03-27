// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;

import static org.jfxcore.compiler.generate.SharedMethodImpls.*;
import static org.jfxcore.compiler.type.KnownSymbols.*;

public abstract class ValueWrapperGenerator extends ClassGenerator {

    public static ClassGenerator newInstance(TypeDeclaration valueType) {
        TypeInvoker invoker = new TypeInvoker(SourceInfo.none());
        TypeDeclaration primitiveType = valueType.primitive().orElse(null);

        if (booleanDecl().equals(primitiveType)) {
            return new ValueWrapperGenerator(
                    invoker.invokeType(ObservableBooleanValueDecl()), booleanDecl(), BooleanDecl()) {
                @Override public String getClassName() {
                    return NameHelper.getMangledClassName("BooleanConstant");
                }
            };
        } else if (intDecl().equals(primitiveType)) {
            return new ValueWrapperGenerator(
                    invoker.invokeType(ObservableIntegerValueDecl()), intDecl(), IntegerDecl()) {
                @Override public String getClassName() {
                    return NameHelper.getMangledClassName("IntegerConstant");
                }
            };
        } else if (longDecl().equals(primitiveType)) {
            return new ValueWrapperGenerator(
                    invoker.invokeType(ObservableLongValueDecl()), longDecl(), LongDecl()) {
                @Override public String getClassName() {
                    return NameHelper.getMangledClassName("LongConstant");
                }
            };
        } else if (floatDecl().equals(primitiveType)) {
            return new ValueWrapperGenerator(
                    invoker.invokeType(ObservableFloatValueDecl()), floatDecl(), FloatDecl()) {
                @Override public String getClassName() {
                    return NameHelper.getMangledClassName("FloatConstant");
                }
            };
        } else if (doubleDecl().equals(primitiveType)) {
            return new ValueWrapperGenerator(
                    invoker.invokeType(ObservableDoubleValueDecl()), doubleDecl(), DoubleDecl()) {
                @Override public String getClassName() {
                    return NameHelper.getMangledClassName("DoubleConstant");
                }
            };
        }

        return new ValueWrapperGenerator(
                invoker.invokeType(ObservableObjectValueDecl()), null, ObjectDecl()) {
            @Override public String getClassName() {
                return NameHelper.getMangledClassName("ObjectConstant");
            }
        };
    }

    private static final String BOXED_FIELD = "value";
    private static final String PRIMITIVE_FIELD = "primitiveValue";
    private static final String INVALIDATION_LISTENER_FIELD = "invalidationListener";
    private static final String CHANGE_LISTENER_FIELD = "changeListener";

    private final TypeInstance observableType;
    private final TypeDeclaration primitiveType;
    private final TypeDeclaration boxedType;

    private ConstructorDeclaration primitiveConstructor;
    private ConstructorDeclaration boxedConstructor;
    private MethodDeclaration getMethod;
    private MethodDeclaration getValueMethod;
    private MethodDeclaration intValueMethod;
    private MethodDeclaration longValueMethod;
    private MethodDeclaration floatValueMethod;
    private MethodDeclaration doubleValueMethod;

    ValueWrapperGenerator(TypeInstance observableType, TypeDeclaration primitiveType, TypeDeclaration boxedType) {
        this.observableType = observableType;
        this.primitiveType = primitiveType;
        this.boxedType = boxedType;
    }

    @Override
    public TypeInstance getTypeInstance() {
        return observableType;
    }

    @Override
    public TypeDeclaration emitClass(BytecodeEmitContext context) {
        return super.emitClass(context)
            .setModifiers(Modifier.PRIVATE | Modifier.FINAL)
            .addInterface(observableType.declaration());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {
        createField(BOXED_FIELD, boxedType).setModifiers(Modifier.PRIVATE | Modifier.FINAL);

        if (primitiveType != null) {
            createField(PRIMITIVE_FIELD, primitiveType).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        }

        createField(INVALIDATION_LISTENER_FIELD, InvalidationListenerDecl()).setModifiers(Modifier.PRIVATE);
        createField(CHANGE_LISTENER_FIELD, ChangeListenerDecl()).setModifiers(Modifier.PRIVATE);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) {
        super.emitMethods(context);

        if (primitiveType != null) {
            primitiveConstructor = createConstructor(primitiveType);
        }

        boxedConstructor = createConstructor(boxedType);

        getMethod = createMethod("get", primitiveType != null ? primitiveType : boxedType)
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        getValueMethod = createMethod("getValue", boxedType).setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        TypeDeclaration invalidationListenerType = InvalidationListenerDecl();
        TypeDeclaration changeListenerType = ChangeListenerDecl();

        createListenerMethods(this, INVALIDATION_LISTENER_FIELD, invalidationListenerType);
        createListenerMethods(this, CHANGE_LISTENER_FIELD, changeListenerType);

        if (primitiveType != null && primitiveType.isNumericPrimitive()) {
            intValueMethod = createMethod("intValue", intDecl()).setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            longValueMethod = createMethod("longValue", longDecl()).setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            floatValueMethod = createMethod("floatValue", floatDecl()).setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            doubleValueMethod = createMethod("doubleValue", doubleDecl()).setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        }
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        if (primitiveType != null) {
            emitPrimitiveConstructor(primitiveConstructor);
        }

        emitBoxedConstructor(boxedConstructor);
        emitGetValueMethod(getValueMethod);
        emitGetMethod(getMethod);

        if (primitiveType != null && primitiveType.isNumericPrimitive()) {
            emitConversionMethods();
        }
    }

    private void emitPrimitiveConstructor(ConstructorDeclaration constructor) {
        Bytecode code = new Bytecode(constructor);

        code.aload(0)
            .invoke(requireSuperClass().requireDeclaredConstructor())
            .aload(0)
            .load(primitiveType, 1)
            .putfield(requireDeclaredField(PRIMITIVE_FIELD))
            .aload(0)
            .load(primitiveType, 1)
            .autoconv(primitiveType, boxedType)
            .putfield(requireDeclaredField(BOXED_FIELD))
            .vreturn();

        constructor.setCode(code);
    }

    private void emitBoxedConstructor(ConstructorDeclaration constructor) {
        Bytecode code = new Bytecode(constructor);

        code.aload(0)
            .invoke(requireSuperClass().requireDeclaredConstructor())
            .aload(0)
            .load(boxedType, 1)
            .putfield(requireDeclaredField(BOXED_FIELD));

        if (primitiveType != null) {
            code.aload(0)
                .aload(1)
                .autoconv(boxedType, primitiveType)
                .putfield(requireDeclaredField(PRIMITIVE_FIELD));
        }

        code.vreturn();
        constructor.setCode(code);
    }

    private void emitGetMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        code.aload(0)
            .getfield(requireDeclaredField(primitiveType != null ? PRIMITIVE_FIELD : BOXED_FIELD))
            .ret(primitiveType != null ? primitiveType : boxedType);

        method.setCode(code);
    }

    private void emitGetValueMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        code.aload(0)
            .getfield(requireDeclaredField(BOXED_FIELD))
            .ret(boxedType);

        method.setCode(code);
    }

    private void emitConversionMethods() {
        Bytecode code = new Bytecode(intValueMethod);
        code
            .aload(0)
            .getfield(requireDeclaredField(PRIMITIVE_FIELD))
            .autoconv(primitiveType, intDecl())
            .ireturn();

        intValueMethod.setCode(code);

        code = new Bytecode(longValueMethod);
        code
            .aload(0)
            .getfield(requireDeclaredField(PRIMITIVE_FIELD))
            .autoconv(primitiveType, longDecl())
            .lreturn();

        longValueMethod.setCode(code);

        code = new Bytecode(floatValueMethod);
        code
            .aload(0)
            .getfield(requireDeclaredField(PRIMITIVE_FIELD))
            .autoconv(primitiveType, floatDecl())
            .freturn();

        floatValueMethod.setCode(code);

        code = new Bytecode(doubleValueMethod);
        code
            .aload(0)
            .getfield(requireDeclaredField(PRIMITIVE_FIELD))
            .autoconv(primitiveType, doubleDecl())
            .dreturn();

        doubleValueMethod.setCode(code);
    }
}

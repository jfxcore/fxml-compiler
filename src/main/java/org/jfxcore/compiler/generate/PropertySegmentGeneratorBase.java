// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.expression.path.FoldedGroup;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.jfxcore.compiler.type.TypeSymbols.*;

abstract class PropertySegmentGeneratorBase extends SegmentGeneratorBase {

    boolean isNumeric;
    TypeDeclaration valueClass;
    MethodDeclaration getValueMethod;
    MethodDeclaration setValueMethod;
    MethodDeclaration getMethod;
    MethodDeclaration setMethod;
    MethodDeclaration intValueMethod;
    MethodDeclaration longValueMethod;
    MethodDeclaration floatValueMethod;
    MethodDeclaration doubleValueMethod;
    MethodDeclaration addInvalidationListenerMethod;
    MethodDeclaration removeInvalidationListenerMethod;
    MethodDeclaration addChangeListenerMethod;
    MethodDeclaration removeChangeListenerMethod;
    MethodDeclaration getBeanMethod;
    MethodDeclaration getNameMethod;

    private MethodDeclaration bindMethod;
    private MethodDeclaration unbindMethod;
    private MethodDeclaration isBoundMethod;
    private MethodDeclaration bindBidirectionalMethod;
    private MethodDeclaration unbindBidirectionalMethod;
    private final TypeInstance type;
    private final TypeDeclaration superClass;
    private final List<TypeDeclaration> interfaces;

    PropertySegmentGeneratorBase(SourceInfo sourceInfo, FoldedGroup[] groups, int segment) {
        super(sourceInfo, groups, segment);

        interfaces = new ArrayList<>();

        TypeDeclaration type = groups[groups.length - 1].getObservableType();
        if (type == null) {
            type = groups[groups.length - 1].getValueType();
        }

        TypeDeclaration finalType = type;

        if (type.equals(booleanDecl()) || finalType.subtypeOf(ObservableBooleanValueDecl())) {
            superClass = BooleanPropertyDecl();
            valueClass = booleanDecl();
        } else if (finalType.equals(intDecl())
                || finalType.equals(shortDecl())
                || finalType.equals(byteDecl())
                || finalType.equals(charDecl())
                || finalType.subtypeOf(ObservableIntegerValueDecl())) {
            superClass = IntegerPropertyDecl();
            valueClass = finalType.subtypeOf(ObservableIntegerValueDecl()) ? intDecl() : finalType;
            isNumeric = true;
        } else if (finalType.equals(longDecl()) || finalType.subtypeOf(ObservableLongValueDecl())) {
            superClass = LongPropertyDecl();
            valueClass = longDecl();
            isNumeric = true;
        } else if (finalType.equals(floatDecl()) || finalType.subtypeOf(ObservableFloatValueDecl())) {
            superClass = FloatPropertyDecl();
            valueClass = floatDecl();
            isNumeric = true;
        } else if (finalType.equals(doubleDecl()) || finalType.subtypeOf(ObservableDoubleValueDecl())) {
            superClass = DoublePropertyDecl();
            valueClass = doubleDecl();
            isNumeric = true;
        } else {
            superClass = ObjectDecl();
            interfaces.add(PropertyDecl());
            valueClass = groups[groups.length - 1].getValueType();
        }

        if (!superClass.equals(ObjectDecl())) {
            this.type = TypeInstance.of(superClass);
        } else {
            TypeInvoker invoker = new TypeInvoker(sourceInfo);
            this.type = invoker.invokeType(
                PropertyDecl(),
                List.of(invoker.invokeType(valueClass.boxed())));
        }
    }

    @Override
    public TypeInstance getTypeInstance() {
        return type;
    }

    @Override
    public String getClassName() {
        return NameHelper.getMangledClassName(groups[segment].getName());
    }

    @Override
    public TypeDeclaration emitClass(BytecodeEmitContext context) {
        TypeDeclaration generatedClass = super.emitClass(context)
            .setSuperClass(superClass)
            .setModifiers(Modifier.PRIVATE | Modifier.FINAL);

        for (TypeDeclaration itf : interfaces) {
            generatedClass.addInterface(itf);
        }

        groups[segment].setCompiledClass(generatedClass);

        return generatedClass;
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) {
        super.emitMethods(context);

        getValueMethod = createMethod("getValue", ObjectDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        setValueMethod = createMethod("setValue", voidDecl(), ObjectDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        addInvalidationListenerMethod = createMethod("addListener", voidDecl(), InvalidationListenerDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        removeInvalidationListenerMethod = createMethod("removeListener", voidDecl(), InvalidationListenerDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        addChangeListenerMethod = createMethod("addListener", voidDecl(), ChangeListenerDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        removeChangeListenerMethod = createMethod("removeListener", voidDecl(), ChangeListenerDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        bindMethod = createMethod("bind", voidDecl(), ObservableValueDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        unbindMethod = createMethod("unbind", voidDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        isBoundMethod = createMethod("isBound", booleanDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        bindBidirectionalMethod = createMethod("bindBidirectional", voidDecl(), PropertyDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        unbindBidirectionalMethod = createMethod("unbindBidirectional", voidDecl(), PropertyDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        getBeanMethod = createMethod("getBean", ObjectDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        getNameMethod = createMethod("getName", StringDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        if (valueClass.isPrimitive()) {
            getMethod = createMethod("get", widenShortInt(valueClass))
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            setMethod = createMethod("set", voidDecl(), valueClass)
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        }

        if (isNumeric) {
            intValueMethod = createMethod("intValue", intDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            longValueMethod = createMethod("longValue", longDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            floatValueMethod = createMethod("floatValue", floatDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            doubleValueMethod = createMethod("doubleValue", doubleDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        }
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        emitNotSupportedMethod(bindMethod);
        emitNotSupportedMethod(unbindMethod);
        emitNotSupportedMethod(bindBidirectionalMethod);
        emitNotSupportedMethod(unbindBidirectionalMethod);
        emitIsBoundMethod(isBoundMethod);
    }

    protected void emitNotSupportedMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        code.anew(UnsupportedOperationExceptionDecl())
            .dup()
            .invoke(UnsupportedOperationExceptionDecl().requireDeclaredConstructor())
            .athrow();

        method.setCode(code);
    }

    private void emitIsBoundMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        code.iconst(0)
            .ireturn();

        method.setCode(code);
    }

    static TypeDeclaration widenShortInt(TypeDeclaration type) {
        return switch (type.name()) {
            case "short", "byte", "char" -> intDecl();
            case ShortName, ByteName, CharacterName -> IntegerDecl();
            default -> type;
        };
    }
}

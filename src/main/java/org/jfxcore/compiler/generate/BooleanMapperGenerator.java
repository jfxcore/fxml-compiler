// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.BooleanConversionHelper;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;

import static org.jfxcore.compiler.type.KnownSymbols.*;

public class BooleanMapperGenerator extends ClassGenerator {

    private final TypeInstance typeInstance =
        new TypeInvoker(SourceInfo.none()).invokeType(BooleanBindingDecl());

    private final String className;
    private final TypeDeclaration valueType;
    private final boolean invert;

    public BooleanMapperGenerator(TypeDeclaration valueType, boolean invert) {
        this.valueType = valueType;
        this.invert = invert;

        String name;
        TypeDeclaration boxedType = valueType.boxed();

        if (boxedType.subtypeOf(BooleanDecl())) {
            name = "Boolean";
        } else if (boxedType.subtypeOf(FloatDecl())) {
            name = "Float";
        } else if (boxedType.subtypeOf(DoubleDecl())) {
            name = "Double";
        } else if (boxedType.subtypeOf(CharacterDecl())) {
            name = "Character";
        } else if (boxedType.subtypeOf(NumberDecl())) {
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
    public TypeDeclaration emitClass(BytecodeEmitContext context) {
        return super.emitClass(context)
            .setModifiers(Modifier.PRIVATE | Modifier.FINAL)
            .setSuperClass(BooleanBindingDecl());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {
        createField("observable", ObservableValueDecl()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        SharedMethodImpls.createBehavior(createConstructor(ObservableValueDecl()), code -> {
            // super()
            code.aload(0)
                .invoke(requireSuperClass().requireDeclaredConstructor());

            // this.observable = $1
            code.aload(0)
                .aload(1)
                .putfield(requireDeclaredField("observable"));

            // bind($1)
            code.aload(0)
                .newarray(ObservableDecl(), 1)
                .dup()
                .iconst(0)
                .aload(1)
                .arraystore(ObservableDecl())
                .invoke(requireSuperClass().requireDeclaredMethod("bind", ObservableDecl().arrayType(1)))
                .vreturn();
        });

        SharedMethodImpls.createBehavior(createMethod("computeValue", booleanDecl()), code -> {
            code.aload(0)
                .getfield(requireDeclaredField("observable"))
                .invoke(ObservableValueDecl().requireDeclaredMethod("getValue"));

            if (!valueType.equals(ObjectDecl())) {
                code.checkcast(valueType);
            }

            BooleanConversionHelper.emit(code, valueType, invert);
            code.ireturn();
        });
    }
}

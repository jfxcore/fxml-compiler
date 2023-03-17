// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.ExceptionHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.ArrayList;
import java.util.List;

public abstract class ClassGenerator implements Generator {

    protected CtClass clazz;

    private CtMethod forceInitMethod;

    public abstract String getClassName();

    public abstract TypeInstance getTypeInstance();

    public abstract void emitClass(BytecodeEmitContext context) throws Exception;

    @Override
    @SuppressWarnings("unchecked")
    public boolean consume(BytecodeEmitContext context) {
        String className = getClassName();
        List<String> classNames = (List<String>) CompilationContext.getCurrent().computeIfAbsent(
            ClassGenerator.class, key -> new ArrayList<>());

        if (classNames.contains(className)) {
            return false;
        }

        classNames.add(className);
        return true;
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        forceInitMethod = new CtMethod(CtClass.voidType, "forceInit", new CtClass[0], clazz);
        forceInitMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL | Modifier.STATIC);
        clazz.addMethod(forceInitMethod);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        Bytecode code = new Bytecode(clazz, 0);
        code.vreturn();
        forceInitMethod.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        forceInitMethod.getMethodInfo().rebuildStackMap(clazz.getClassPool());
    }

    public static CtClass emit(BytecodeEmitContext context, ClassGenerator generator) {
        ExceptionHelper.unchecked(SourceInfo.none(), () -> {
            generator.emitClass(context);
            generator.emitFields(context);
            generator.emitMethods(context);
            generator.emitCode(context);
        });

        return context.getNestedClasses().find(generator.getClassName());
    }

}

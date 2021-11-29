// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.util.Bytecode;

public abstract class GeneratorBase implements Generator {

    CtClass clazz;

    private CtMethod forceInitMethod;

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

}

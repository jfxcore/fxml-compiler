// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import javassist.CtClass;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.util.ExceptionHelper;
import org.jfxcore.compiler.util.TypeInstance;

public interface Generator {

    String getClassName();

    TypeInstance getTypeInstance();

    void emitClass(BytecodeEmitContext context) throws Exception;

    void emitFields(BytecodeEmitContext context) throws Exception;

    void emitMethods(BytecodeEmitContext context) throws Exception;

    void emitCode(BytecodeEmitContext context) throws Exception;

    static CtClass emit(BytecodeEmitContext context, Generator generator) {
        ExceptionHelper.unchecked(SourceInfo.none(), () -> {
            generator.emitClass(context);
            generator.emitFields(context);
            generator.emitMethods(context);
            generator.emitCode(context);
        });

        return context.getNestedClasses().find(generator.getClassName());
    }

}

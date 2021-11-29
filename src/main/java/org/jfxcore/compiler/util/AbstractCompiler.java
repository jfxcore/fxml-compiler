// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.NotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public abstract class AbstractCompiler {

    protected void flushModifiedClasses(CompilationContext context)
            throws URISyntaxException, CannotCompileException, IOException, NotFoundException {
        for (Map.Entry<String, Path> entry : context.getModifiedClasses().entrySet()) {
            String className = entry.getKey();
            CtClass ctclass = context.getClassPool().get(className);
            String packageName = ctclass.getPackageName();
            Path location = entry.getValue() != null ?
                entry.getValue() : Paths.get(context.getClassPool().find(className).toURI());

            if (packageName != null) {
                int packages = packageName.length() - packageName.replace(".", "").length() + 1;
                location = FileUtil.removeLastN(location, packages + 1);
            }

            ctclass.writeFile(location.toString());
        }

        context.getModifiedClasses().clear();
    }

}

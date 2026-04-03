// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class AbstractCompiler {

    static {
        // If this flag is set to true, file handles for JAR files are sometimes not released.
        ClassPool.cacheOpenedJarFile = false;
    }

    private final Set<Path> searchPath;

    protected AbstractCompiler(Set<Path> searchPath) {
        this.searchPath = Objects.requireNonNull(searchPath, "searchPath must not be null");
    }

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

    protected ClassPool newClassPool() {
        var classPool = new ClassPool(true);

        for (Path path : searchPath) {
            try {
                classPool.appendClassPath(path.toAbsolutePath().toString());
            } catch (NotFoundException ex) {
                throw GeneralErrors.internalError("Search path dependency not found: " + ex.getMessage());
            }
        }

        return classPool;
    }
}

// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.ClassPool;
import javassist.CtClass;
import org.jfxcore.compiler.Logger;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class CompilationContext extends HashMap<Object, Object> {

    private static CompilationContext current;

    private final CompilationSource compilationSource;
    private final Map<String, Path> modifiedClasses = new HashMap<>();

    private List<String> imports;
    private ClassPool classPool;

    public CompilationContext(CompilationSource sourceFile) {
        compilationSource = sourceFile;
    }

    public static boolean isCurrent() {
        return current != null;
    }

    public static CompilationContext getCurrent() {
        if (current == null) {
            throw new IllegalStateException();
        }

        return current;
    }

    static synchronized void setCurrent(CompilationContext context) {
        if (context != null) {
            if (current != null) {
                throw new IllegalStateException();
            }

            current = context;
        } else {
            current = null;
        }
    }

    public abstract Logger getLogger();

    public CompilationSource getCompilationSource() {
        if (compilationSource == null) {
            throw new IllegalStateException();
        }

        return compilationSource;
    }

    public void addModifiedClass(CtClass ctclass) {
        modifiedClasses.put(ctclass.getName(), null);
    }

    public void addModifiedClass(CtClass ctclass, Path outDir) {
        modifiedClasses.put(ctclass.getName(), outDir);
    }

    public Map<String, Path> getModifiedClasses() {
        return modifiedClasses;
    }

    public ClassPool getClassPool() {
        return classPool;
    }

    public void setClassPool(ClassPool classPool) {
        this.classPool = classPool;
    }

    public List<String> getImports() {
        return imports;
    }

    public void setImports(List<String> imports) {
        this.imports = imports;
    }

}

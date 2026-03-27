// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.ClassPool;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.KnownSymbols;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompilationContext extends HashMap<Object, Object> {

    public static final String USE_SHARED_IMPLEMENTATION = "CompilationContext.useSharedImplementation";

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

    public boolean useSharedImplementation() {
        return get(CompilationContext.USE_SHARED_IMPLEMENTATION) instanceof Boolean value
            ? value
            : KnownSymbols.Markup.isAvailable();
    }

    public CompilationSource getCompilationSource() {
        if (compilationSource == null) {
            throw new IllegalStateException();
        }

        return compilationSource;
    }

    public void addModifiedClass(TypeDeclaration ctclass) {
        modifiedClasses.put(ctclass.jvmType().getName(), null);
    }

    public void addModifiedClass(TypeDeclaration ctclass, Path outDir) {
        modifiedClasses.put(ctclass.jvmType().getName(), outDir);
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

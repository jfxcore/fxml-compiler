// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.runner;

import org.jfxcore.compiler.ClassGenerator;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClassGeneratorRunner extends AbstractCompilerRunner {

    private final Object instance;
    private final Method addFileSourceMethod;
    private final Method processMethod;

    public ClassGeneratorRunner(Set<Path> searchPath, RunnerLogger logger) throws ReflectiveOperationException {
        super(ClassGenerator.class.getName(), searchPath, logger);

        instance = getClassLoader()
            .findClass(ClassGenerator.class)
            .getConstructor(Set.class, getCompilerLoggerClass())
            .newInstance(searchPath, newCompilerLogger());

        addFileSourceMethod = instance.getClass().getMethod("addFileSource", Path.class, Path.class);
        processMethod = instance.getClass().getMethod("process");
    }

    public void addFileSources(Map<Path, List<Path>> markupFilesPerSourceDirectory) {
        for (var entry : markupFilesPerSourceDirectory.entrySet()) {
            Path sourceDir = entry.getKey();
            List<Path> sourceFiles = entry.getValue();

            for (Path sourceFile : sourceFiles) {
                addFileSource(sourceDir, sourceFile);
            }
        }
    }

    private void addFileSource(Path sourceDir, Path sourceFile) {
        invoke(() -> addFileSourceMethod.invoke(instance, sourceDir, sourceFile));
    }

    public List<CompilationUnitWrapper> process() {
        return invoke(() -> {
            @SuppressWarnings("unchecked")
            List<Object> result = (List<Object>)processMethod.invoke(instance);
            return result.stream().map(CompilationUnitWrapper::new).toList();
        });
    }
}

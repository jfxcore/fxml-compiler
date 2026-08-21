// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.runner;

import org.jfxcore.compiler.MarkupCompiler;
import org.jfxcore.compiler.util.CompilationUnitDescriptor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

public final class MarkupCompilerRunner extends AbstractCompilerRunner {

    private final Object instance;
    private final Method compileMethod;
    private final Method isCompiledFileMethod;
    private final Method loadDescriptorMethod;

    public MarkupCompilerRunner(Set<Path> searchPath, RunnerLogger logger) throws ReflectiveOperationException {
        super(MarkupCompiler.class.getName(), searchPath, logger);

        instance = getClassLoader()
            .findClass(MarkupCompiler.class)
            .getConstructor(Set.class, getCompilerLoggerClass())
            .newInstance(searchPath, newCompilerLogger());

        compileMethod = instance.getClass().getMethod("compile", Set.class);
        isCompiledFileMethod = instance.getClass().getMethod("isCompiledFile", Path.class);
        loadDescriptorMethod = getClassLoader().findClass(CompilationUnitDescriptor.class)
                                               .getMethod("readFrom", Path.class);
    }

    public CompilationUnitDescriptorWrapper loadDescriptor(Path file) {
        return invoke(() -> new CompilationUnitDescriptorWrapper(loadDescriptorMethod.invoke(null, file)));
    }

    public boolean isCompiledFile(Path classFile) {
        return (boolean)invoke(() -> isCompiledFileMethod.invoke(instance, classFile));
    }

    public void compile(Set<CompilationUnitDescriptorWrapper> descriptors) {
        invoke(() -> {
            Set<Object> targetDescriptors = descriptors.stream()
                .map(CompilationUnitDescriptorWrapper::getTarget)
                .collect(Collectors.toUnmodifiableSet());

            compileMethod.invoke(instance, targetDescriptors);
            return null;
        });
    }
}

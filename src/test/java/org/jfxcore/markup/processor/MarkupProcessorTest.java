// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.markup.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class MarkupProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    public void MarkupClass_Without_Explicit_Superclass_Fails_Compilation() throws IOException {
        CompilationResult result = compile("SampleControl", """
            package sample;

            import javafx.scene.layout.Pane;
            import org.jfxcore.markup.embed.Markup;

            @Markup(\"\"\"
                <Pane/>
            \"\"\")
            public class SampleControl {
            }
            """);

        assertFalse(result.success(), result.formatDiagnostics());
        assertTrue(
            result.hasErrorContaining("must extend the generated base class"),
            result.formatDiagnostics());
    }

    @Test
    public void MarkupClass_With_Explicit_Superclass_Compiles() throws IOException {
        CompilationResult result = compile("SampleControl", """
            package sample;

            import javafx.scene.layout.Pane;
            import org.jfxcore.markup.embed.Markup;

            @Markup(\"\"\"
                <Pane/>
            \"\"\")
            public class SampleControl extends SampleControlBase {
            }
            """);

        assertTrue(result.success(), result.formatDiagnostics());
        assertTrue(
            Files.exists(result.generatedSourcesDir().resolve(Path.of("sample", "SampleControlBase.java"))),
            result.formatDiagnostics());
        assertTrue(
            Files.exists(result.classesDir().resolve(Path.of("sample", "SampleControl.class"))),
            result.formatDiagnostics());
        assertTrue(
            Files.exists(result.classesDir().resolve(Path.of("sample", "SampleControlBase.class"))),
            result.formatDiagnostics());
    }

    private CompilationResult compile(String simpleClassName, String source) throws IOException {
        Path sourceDir = tempDir.resolve("src");
        Path generatedSourcesDir = tempDir.resolve("generated");
        Path classesDir = tempDir.resolve("classes");
        Path intermediateDir = tempDir.resolve("intermediate");
        Path sourceFile = sourceDir.resolve(Path.of("sample", simpleClassName + ".java"));
        String classPath = System.getProperty("java.class.path");

        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(generatedSourcesDir);
        Files.createDirectories(classesDir);
        Files.createDirectories(intermediateDir);
        Files.writeString(sourceFile, source);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = List.of(
                "-d", classesDir.toString(),
                "-s", generatedSourcesDir.toString(),
                "-classpath", classPath + File.pathSeparator + classesDir,
                "--module-path", classPath,
                "--add-modules", "javafx.base,javafx.graphics,javafx.controls",
                "--release", System.getProperty("java.specification.version"),
                "-A" + ProcessorOptions.SOURCE_DIRS_OPT + "=" + sourceDir,
                "-A" + ProcessorOptions.SEARCH_PATH_OPT + "=" + classPath,
                "-A" + ProcessorOptions.INTERMEDIATE_BUILD_DIR_OPT + "=" + intermediateDir);

            JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics, options, null,
                fileManager.getJavaFileObjects(sourceFile.toFile()));

            task.setProcessors(List.of(new MarkupProcessor()));
            return new CompilationResult(task.call(), diagnostics.getDiagnostics(), generatedSourcesDir, classesDir);
        }
    }

    private record CompilationResult(
            boolean success,
            List<Diagnostic<? extends JavaFileObject>> diagnostics,
            Path generatedSourcesDir,
            Path classesDir) {

        boolean hasErrorContaining(String text) {
            return diagnostics.stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(diagnostic -> diagnostic.getMessage(Locale.ROOT))
                .anyMatch(message -> message.contains(text));
        }

        String formatDiagnostics() {
            return diagnostics.stream()
                .map(diagnostic -> "%s: %s".formatted(diagnostic.getKind(), diagnostic.getMessage(Locale.ROOT)))
                .collect(Collectors.joining(System.lineSeparator()));
        }
    }
}

// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import org.jfxcore.compiler.util.CompilationUnitDescriptor;
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
            import org.jfxcore.markup.ComponentView;

            @ComponentView(""\"
                <Pane/>
            ""\")
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
            import org.jfxcore.markup.ComponentView;

            @ComponentView(""\"
                <Pane/>
            ""\")
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

    @Test
    public void MarkupClass_With_Blank_ComponentView_Value_Fails_Compilation() throws IOException {
        CompilationResult result = compile("SampleControl", """
            package sample;

            import org.jfxcore.markup.ComponentView;

            @ComponentView("   ")
            public class SampleControl extends SampleControlBase {
            }
            """);

        assertFalse(result.success(), result.formatDiagnostics());
        assertTrue(
            result.hasErrorContaining("value must not be empty"),
            result.formatDiagnostics());
    }

    @Test
    public void ComponentView_On_Interface_Fails_Compilation() throws IOException {
        CompilationResult result = compile("SampleControl", """
            package sample;

            import org.jfxcore.markup.ComponentView;

            @ComponentView("<Pane/>")
            public interface SampleControl {
            }
            """);

        assertFalse(result.success(), result.formatDiagnostics());
        assertTrue(
            result.hasErrorContaining("can only be used on classes"),
            result.formatDiagnostics());
    }

    @Test
    public void ComponentView_On_Nested_Class_Fails_Compilation() throws IOException {
        CompilationResult result = compile("Outer", """
            package sample;

            import org.jfxcore.markup.ComponentView;

            public class Outer {
                @ComponentView("<Pane/>")
                static class Inner {
                }
            }
            """);

        assertFalse(result.success(), result.formatDiagnostics());
        assertTrue(
            result.hasErrorContaining("can only be used on top-level classes"),
            result.formatDiagnostics());
    }

    @Test
    public void MarkupClass_With_Explicit_Superclass_Writes_Descriptor_File() throws IOException {
        CompilationResult result = compile("SampleControl", """
            package sample;

            import javafx.scene.layout.Pane;
            import org.jfxcore.markup.ComponentView;

            @ComponentView(""\"
                <Pane/>
            ""\")
            public class SampleControl extends SampleControlBase {
            }
            """);

        assertTrue(result.success(), result.formatDiagnostics());

        Path descriptorFile = result.intermediateDir().resolve(Path.of("sample", "SampleControlBase.fxmd"));
        assertTrue(Files.exists(descriptorFile), result.formatDiagnostics());

        CompilationUnitDescriptor descriptor = CompilationUnitDescriptor.readFrom(descriptorFile);
        assertEquals("sample.SampleControlBase", descriptor.markupClass().fullName());
        assertEquals(
            result.sourceDir().resolve(Path.of("sample", "SampleControl.java")),
            descriptor.absoluteSourceFile());
        assertTrue(descriptor.sourceText().contains("<Pane/>"));
    }

    @Test
    public void Package_Private_MarkupClass_With_Explicit_Superclass_Compiles() throws IOException {
        CompilationResult result = compile("SampleControl", """
            package sample;

            import javafx.scene.layout.Pane;
            import org.jfxcore.markup.ComponentView;

            @ComponentView(""\"
                <Pane/>
            ""\")
            class SampleControl extends SampleControlBase {
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

    @Test
    public void MarkupClass_Outside_Configured_SourceDirs_Fails_Compilation() throws IOException {
        CompilationResult result = compile(
            "SampleControl",
            """
            package sample;

            import javafx.scene.layout.Pane;
            import org.jfxcore.markup.ComponentView;

            @ComponentView(""\"
                <Pane/>
            ""\")
            public class SampleControl extends SampleControlBase {
            }
            """,
            tempDir.resolve("other-src"));

        assertFalse(result.success(), result.formatDiagnostics());
        assertTrue(
            result.hasErrorContaining("not contained in one of the recognized source directories"),
            result.formatDiagnostics());
    }

    private CompilationResult compile(String simpleClassName, String source) throws IOException {
        return compile(simpleClassName, source, tempDir.resolve("src"));
    }

    private CompilationResult compile(String simpleClassName, String source, Path configuredSourceDir) throws IOException {
        Path sourceDir = tempDir.resolve("src");
        Path generatedSourcesDir = tempDir.resolve("generated");
        Path classesDir = tempDir.resolve("classes");
        Path intermediateDir = tempDir.resolve("fxml");
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
                "-A" + ProcessorOptions.SOURCE_DIRS_OPT + "=" + configuredSourceDir,
                "-A" + ProcessorOptions.SEARCH_PATH_OPT + "=" + classPath,
                "-A" + ProcessorOptions.INTERMEDIATE_BUILD_DIR_OPT + "=" + intermediateDir);

            JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics, options, null,
                fileManager.getJavaFileObjects(sourceFile.toFile()));

            task.setProcessors(List.of(new MarkupProcessor()));
            return new CompilationResult(
                task.call(), diagnostics.getDiagnostics(), sourceDir, generatedSourcesDir, classesDir, intermediateDir);
        }
    }

    private record CompilationResult(
            boolean success,
            List<Diagnostic<? extends JavaFileObject>> diagnostics,
            Path sourceDir,
            Path generatedSourcesDir,
            Path classesDir,
            Path intermediateDir) {

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

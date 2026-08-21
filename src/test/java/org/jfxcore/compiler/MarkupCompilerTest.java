// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.Logger;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.resource.EmbeddedResource;
import org.jfxcore.compiler.util.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class MarkupCompilerTest {

    @TempDir
    Path tempDir;

    @Test
    public void Generator_Returns_Utf8_Resources_From_Utf16_Fxml_File() throws IOException {
        Path sourceRoot = tempDir.resolve("src");
        Path sourceFile = Path.of("sample", "View.fxml");
        Path absoluteSourceFile = sourceRoot.resolve(sourceFile);
        String source = """
            <?xml version="1.0" encoding="UTF-16"?>
            <?import javafx.scene.control.*?>
            <?resource greeting.txt:
                Gr\u00fc\u00dfe
            ?>
            <?resource unused.txt:unused?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"/>
        """;

        Files.createDirectories(absoluteSourceFile.getParent());
        Files.write(absoluteSourceFile, withPrefix(
            new byte[] {(byte)0xff, (byte)0xfe},
            source.getBytes(StandardCharsets.UTF_16LE)));

        ClassGenerator generator = new ClassGenerator(Set.of(), new SilentLogger());
        assertTrue(generator.addFileSource(sourceRoot.toAbsolutePath(), sourceFile));
        CompilationUnit unit = generator.process().get(0);
        EmbeddedResource resource = unit.embeddedResources().get(0);

        assertEquals(2, unit.embeddedResources().size());
        assertEquals("sample/View$greeting.txt", resource.logicalPath());
        assertEquals(sourceFile, resource.declaringSource());
        assertEquals("greeting.txt", resource.logicalName());
        assertArrayEquals("Gr\u00fc\u00dfe".getBytes(StandardCharsets.UTF_8), resource.content());
        assertEquals("sample/View$unused.txt", unit.embeddedResources().get(1).logicalPath());

        Path classesDir = tempDir.resolve("classes");
        compileGeneratedSource(unit, classesDir);
        new MarkupCompiler(Set.of(classesDir), new SilentLogger()).compile(Set.of(unit.descriptor()));

        assertFalse(Files.exists(classesDir.resolve(Path.of("sample", "View$greeting.txt"))));
        assertFalse(Files.exists(classesDir.resolve(Path.of("sample", "View$unused.txt"))));
    }

    @Test
    public void Empty_Descriptor_Set_Compiles_Without_Output() {
        assertDoesNotThrow(() -> new MarkupCompiler(Set.of(), new SilentLogger()).compile(Set.of()));
    }

    @Test
    public void Compiler_Rejects_Resource_That_Matches_An_Emitted_Nested_Class() throws IOException {
        Path sourceRoot = tempDir.resolve("src");
        Path sourceFile = Path.of("sample", "View.fxml");
        Path absoluteSourceFile = sourceRoot.resolve(sourceFile);
        String source = """
            <?import javafx.scene.control.*?>
            <?resource __FX$RuntimeContext.class:value?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   stylesheets="@__FX$RuntimeContext.class"/>
            """;

        Files.createDirectories(absoluteSourceFile.getParent());
        Files.writeString(absoluteSourceFile, source);

        ClassGenerator generator = new ClassGenerator(Set.of(), new SilentLogger());
        assertTrue(generator.addFileSource(sourceRoot, sourceFile));
        CompilationUnit unit = generator.process().get(0);
        assertEquals("sample/View$__FX$RuntimeContext.class", unit.embeddedResources().get(0).logicalPath());

        Path classesDir = tempDir.resolve("classes");
        compileGeneratedSource(unit, classesDir);

        MarkupException exception = assertThrows(MarkupException.class, () ->
            new MarkupCompiler(Set.of(classesDir), new SilentLogger()).compile(Set.of(unit.descriptor())));

        assertEquals(ErrorCode.RESOURCE_FILE_COLLISION, exception.getDiagnostic().getCode());
        assertEquals(absoluteSourceFile, exception.getSourceFile().toPath());
        assertFalse(Files.exists(classesDir.resolve(Path.of("sample", "View$__FX$RuntimeContext.class"))));
    }

    private void compileGeneratedSource(CompilationUnit unit, Path classesDir) throws IOException {
        String className = unit.descriptor().markupClass().fullName();
        Path sourceFile = tempDir.resolve("generated").resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classesDir);
        Files.writeString(sourceFile, unit.generatedSourceText());

        var compiler = ToolProvider.getSystemJavaCompiler();
        List<Diagnostic<? extends JavaFileObject>> diagnostics = new ArrayList<>();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            boolean success = compiler.getTask(
                null,
                fileManager,
                diagnostics::add,
                Arrays.asList(
                    "-d", classesDir.toString(),
                    "-classpath", System.getProperty("java.class.path") + File.pathSeparator + classesDir,
                    "--module-path", System.getProperty("java.class.path"),
                    "--add-modules", "javafx.base,javafx.graphics,javafx.controls",
                    "--release", System.getProperty("java.specification.version")),
                null,
                fileManager.getJavaFileObjects(sourceFile.toFile()))
                .call();

            assertTrue(success, () -> diagnostics.isEmpty()
                ? "Generated source compilation failed"
                : diagnostics.get(0).getMessage(Locale.ROOT));
        }
    }

    private byte[] withPrefix(byte[] prefix, byte[] content) {
        byte[] result = new byte[prefix.length + content.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(content, 0, result, prefix.length, content.length);
        return result;
    }

    private static final class SilentLogger implements Logger {
        @Override public void fine(String message) {}
        @Override public void info(String message) {}
    }
}

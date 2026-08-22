// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import org.jfxcore.compiler.diagnostic.Logger;
import org.jfxcore.compiler.resource.EmbeddedResource;
import org.jfxcore.compiler.util.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ClassGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    public void Process_Returns_All_Resources_In_Logical_Path_Order() throws IOException {
        Path sourceRoot = tempDir.resolve("src");
        Path sourceFile = Path.of("sample", "View.fxml");
        writeSource(sourceRoot, sourceFile, fxml("""
            <?resource z.txt:last?>
            <?resource a.txt:first?>
            """));

        ClassGenerator generator = new ClassGenerator(Set.of(), new SilentLogger());
        assertTrue(generator.addFileSource(sourceRoot, sourceFile));

        CompilationUnit unit = generator.process().get(0);
        assertEquals(
            List.of("sample/View$e085c95e$a.txt", "sample/View$f7b56fcd$z.txt"),
            unit.embeddedResources().stream().map(EmbeddedResource::logicalPath).toList());
        assertArrayEquals("first".getBytes(StandardCharsets.UTF_8), unit.embeddedResources().get(0).content());
        assertArrayEquals("last".getBytes(StandardCharsets.UTF_8), unit.embeddedResources().get(1).content());
    }

    @Test
    public void Process_Orders_Units_By_Portable_Source_Path() throws IOException {
        Path sourceRoot = tempDir.resolve("src");
        Path first = Path.of("a", "First.fxml");
        Path second = Path.of("z", "Second.fxml");
        writeSource(sourceRoot, first, fxml(""));
        writeSource(sourceRoot, second, fxml(""));

        ClassGenerator generator = new ClassGenerator(Set.of(), new SilentLogger());
        assertTrue(generator.addFileSource(sourceRoot, second));
        assertTrue(generator.addFileSource(sourceRoot, first));

        List<CompilationUnit> units = generator.process();
        assertEquals(List.of(first, second), units.stream().map(unit -> unit.descriptor().sourceFile()).toList());
        assertTrue(units.stream().allMatch(unit -> unit.embeddedResources().isEmpty()));
    }

    @Test
    public void Skipped_Document_Contributes_No_Unit_Or_Resource() throws IOException {
        Path sourceRoot = tempDir.resolve("src");
        Path sourceFile = Path.of("sample", "Skipped.fxml");
        writeSource(sourceRoot, sourceFile, """
            <?resource unused.txt:unused?>
            <Label/>
            """);

        ClassGenerator generator = new ClassGenerator(Set.of(), new SilentLogger());
        assertFalse(generator.addFileSource(sourceRoot, sourceFile));
        assertTrue(generator.process().isEmpty());
    }

    private void writeSource(Path sourceRoot, Path sourceFile, String source) throws IOException {
        Path absoluteSourceFile = sourceRoot.resolve(sourceFile);
        Files.createDirectories(absoluteSourceFile.getParent());
        Files.writeString(absoluteSourceFile, source);
    }

    private String fxml(String declarations) {
        return """
            <?import javafx.scene.control.*?>
            %s
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"/>
            """.formatted(declarations);
    }

    private static final class SilentLogger implements Logger {
        @Override public void fine(String message) {}
        @Override public void info(String message) {}
    }
}

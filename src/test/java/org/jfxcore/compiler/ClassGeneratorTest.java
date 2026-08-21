// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.Logger;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.resource.EmbeddedResource;
import org.jfxcore.compiler.util.CompilationUnit;
import org.jfxcore.compiler.util.QualifiedName;
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
            List.of("sample/View$a.txt", "sample/View$z.txt"),
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
    public void Process_Rejects_Case_Only_Collision_Deterministically() throws IOException {
        CollisionResult forward = generateCollidingRoots(false);
        CollisionResult reverse = generateCollidingRoots(true);

        assertEquals(ErrorCode.RESOURCE_FILE_COLLISION, forward.exception().getDiagnostic().getCode());
        assertEquals(forward.exception().getMessage(), reverse.exception().getMessage());
        assertEquals(forward.sourceFile(), forward.exception().getSourceFile().toPath());
        assertEquals(reverse.sourceFile(), reverse.exception().getSourceFile().toPath());
    }

    @Test
    public void Process_Rejects_Resource_That_Matches_Known_Top_Level_Class() throws IOException {
        Path sourceRoot = tempDir.resolve("src");
        Path resourceOwner = Path.of("sample", "View.fxml");
        Path classOwner = Path.of("sample", "Z.fxml");
        writeSource(sourceRoot, resourceOwner, fxml("<?resource Helper.class:value?>"));
        writeSource(sourceRoot, classOwner, fxmlWithClassName("View$Helper"));

        ClassGenerator generator = new ClassGenerator(Set.of(), new SilentLogger());
        assertTrue(generator.addFileSource(sourceRoot, resourceOwner));
        assertTrue(generator.addFileSource(sourceRoot, classOwner));

        MarkupException exception = assertThrows(MarkupException.class, generator::process);
        assertEquals(ErrorCode.RESOURCE_FILE_COLLISION, exception.getDiagnostic().getCode());
        assertEquals(sourceRoot.resolve(resourceOwner), exception.getSourceFile().toPath());
        assertTrue(exception.getMessage().contains("sample/View$Helper.class"));
    }

    @Test
    public void Embedded_Source_Collision_Uses_The_Selected_Source_And_Offset() {
        Path firstRoot = tempDir.resolve("root-a");
        Path secondRoot = tempDir.resolve("root-z");
        Path sourceFile = Path.of("sample", "View.java");
        Location firstOffset = new Location(4, 2);
        Location secondOffset = new Location(9, 3);

        ClassGenerator generator = new ClassGenerator(Set.of(), new SilentLogger());
        generator.addEmbeddedSource(
            firstRoot, sourceFile, fxml("<?resource Item.txt:first?>"), List.of(),
            QualifiedName.of("sample.View"), firstOffset);
        generator.addEmbeddedSource(
            secondRoot, sourceFile, fxml("<?resource item.txt:second?>"), List.of(),
            QualifiedName.of("sample.View"), secondOffset);

        MarkupException exception = assertThrows(MarkupException.class, generator::process);
        assertEquals(firstRoot.resolve(sourceFile), exception.getSourceFile().toPath());
        assertEquals(firstOffset, exception.getSourceOffset());
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

    private CollisionResult generateCollidingRoots(boolean reverse) throws IOException {
        Path firstRoot = tempDir.resolve(reverse ? "reverse-root-a" : "forward-root-a");
        Path secondRoot = tempDir.resolve(reverse ? "reverse-root-z" : "forward-root-z");
        Path sourceFile = Path.of("sample", "View.fxml");
        writeSource(firstRoot, sourceFile, fxml("<?resource Item.txt:first?>"));
        writeSource(secondRoot, sourceFile, fxml("<?resource item.txt:second?>"));

        ClassGenerator generator = new ClassGenerator(Set.of(), new SilentLogger());
        if (reverse) {
            assertTrue(generator.addFileSource(secondRoot, sourceFile));
            assertTrue(generator.addFileSource(firstRoot, sourceFile));
        } else {
            assertTrue(generator.addFileSource(firstRoot, sourceFile));
            assertTrue(generator.addFileSource(secondRoot, sourceFile));
        }

        MarkupException exception = assertThrows(MarkupException.class, generator::process);
        return new CollisionResult(exception, firstRoot.resolve(sourceFile));
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

    private String fxmlWithClassName(String className) {
        return fxml("").replace(
            "/>", " fx:subclass=\"sample.Z\" fx:className=\"" + className + "\"/>");
    }

    private record CollisionResult(MarkupException exception, Path sourceFile) {}

    private static final class SilentLogger implements Logger {
        @Override public void fine(String message) {}
        @Override public void info(String message) {}
    }
}

// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.parse.EmbeddingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CompilationUnitDescriptorTest {

    @TempDir
    Path tempDir;

    @Test
    public void WriteTo_Is_Deterministic_And_RoundTrips_Property_Characters() throws IOException {
        CompilationUnitDescriptor descriptor = new CompilationUnitDescriptor(
            new EmbeddingContext(
                List.of("java.util.List", "sample.Gr\u00fc\u00dfe"),
                QualifiedName.of("sample.EmbeddingHost"),
                new Location(4, 7)),
            QualifiedName.of("sample.Generated"),
            tempDir.resolve("source root"),
            Path.of("source file.fxml"),
            " <Pane text=\"Gr\u00fc\u00dfe: \\ value\"/>\r\n# next line\n");

        Path firstDirectory = tempDir.resolve("first");
        Path secondDirectory = tempDir.resolve("second");
        descriptor.writeTo(firstDirectory);
        descriptor.writeTo(secondDirectory);

        Path relativeDescriptorPath = Path.of("sample", "Generated.fxmd");
        Path firstFile = firstDirectory.resolve(relativeDescriptorPath);
        Path secondFile = secondDirectory.resolve(relativeDescriptorPath);
        assertArrayEquals(Files.readAllBytes(firstFile), Files.readAllBytes(secondFile));

        List<String> lines = Files.readAllLines(firstFile, StandardCharsets.US_ASCII);
        assertEquals("#FXML compilation unit descriptor", lines.get(0));
        assertTrue(lines.stream().skip(1).noneMatch(line -> line.startsWith("#")));

        List<String> keys = lines.stream()
            .skip(1)
            .map(line -> line.substring(0, line.indexOf('=')))
            .toList();
        assertEquals(keys.stream().sorted().toList(), keys);

        CompilationUnitDescriptor restored = CompilationUnitDescriptor.readFrom(firstFile.toAbsolutePath());
        assertEquals(descriptor.embeddingContext(), restored.embeddingContext());
        assertEquals(descriptor.markupClass(), restored.markupClass());
        assertEquals(descriptor.sourceRoot().toAbsolutePath(), restored.sourceRoot());
        assertEquals(descriptor.sourceFile(), restored.sourceFile());
        assertEquals(descriptor.sourceText(), restored.sourceText());
    }
}

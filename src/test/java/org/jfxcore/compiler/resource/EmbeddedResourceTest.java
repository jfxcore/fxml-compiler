// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.resource;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.parse.ResourceInstructionParser;
import org.jfxcore.compiler.runner.CompilationUnitWrapper;
import org.jfxcore.compiler.util.CompilationUnit;
import org.jfxcore.compiler.util.CompilationUnitDescriptor;
import org.jfxcore.compiler.util.QualifiedName;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EmbeddedResourceTest {

    @Test
    public void CompilationUnit_Is_Sorted_Snapshot_And_Passes_Content_Through() {
        byte[] firstBytes = {1, 2};
        EmbeddedResource second = new EmbeddedResource(
            new byte[] {3}, "second.txt", Path.of("z", "View.fxml"), SourceInfo.none());
        EmbeddedResource first = new EmbeddedResource(
            firstBytes, "first.txt", Path.of("a", "View.fxml"), SourceInfo.none());
        List<EmbeddedResource> sourceResources = new ArrayList<>(List.of(second, first));

        CompilationUnit unit = new CompilationUnit(descriptor(), sourceResources, "source");
        sourceResources.clear();

        List<String> actual = unit.embeddedResources().stream()
            .map(EmbeddedResource::logicalPath)
            .toList();
        assertEquals(
            List.of("a/View$9fa3b9aa$first.txt", "z/View$353bf5eb$second.txt"),
            actual);
        assertSame(first, unit.embeddedResources().get(0));
        assertSame(firstBytes, unit.embeddedResources().get(0).content());

        byte[] returned = unit.embeddedResources().get(0).content();
        returned[0] = 8;
        assertEquals(8, firstBytes[0]);
        assertSame(returned, unit.embeddedResources().get(0).content());
        assertThrows(UnsupportedOperationException.class, () -> unit.embeddedResources().clear());

        CompilationUnitWrapper wrapper = new CompilationUnitWrapper(unit);
        assertSame(firstBytes, wrapper.embeddedResources().get(0).content());
        assertThrows(UnsupportedOperationException.class, () -> wrapper.embeddedResources().clear());
    }

    @Test
    public void CompilationUnit_Without_Resources_Uses_An_Immutable_Empty_List() {
        CompilationUnit unit = new CompilationUnit(descriptor(), List.of(), "source");

        assertTrue(unit.embeddedResources().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> unit.embeddedResources().add(
            resource(Path.of("sample", "View.fxml"), " item.txt:value")));
    }

    @Test
    public void Logical_Path_Hashes_Resource_Names_Case_Insensitively() {
        EmbeddedResource upper = resource(Path.of("sample", "View.fxml"), " Item.txt:first");
        EmbeddedResource lower = resource(Path.of("sample", "View.fxml"), " item.txt:second");

        assertEquals("sample/View$6d85ccf1$Item.txt", upper.logicalPath());
        assertEquals("sample/View$6d85ccf1$item.txt", lower.logicalPath());
    }

    private EmbeddedResource resource(Path sourceFile, String data) {
        return new ResourceInstructionParser(data, sourceFile).parse();
    }

    private CompilationUnitDescriptor descriptor() {
        return new CompilationUnitDescriptor(
            null,
            QualifiedName.of("sample.View"),
            Path.of("").toAbsolutePath(),
            Path.of("sample", "View.fxml"),
            "");
    }

}

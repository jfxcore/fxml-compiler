// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.resource;

import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.parse.ResourceInstructionParser;
import org.jfxcore.compiler.runner.CompilationUnitWrapper;
import org.jfxcore.compiler.util.CompilationUnit;
import org.jfxcore.compiler.util.CompilationUnitDescriptor;
import org.jfxcore.compiler.util.CompilerOutputTracker;
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
            new byte[] {3}, "second.txt", Path.of("z", "View.fxml"), SourceInfo.none(), SourceInfo.none());
        EmbeddedResource first = new EmbeddedResource(
            firstBytes, "first.txt", Path.of("a", "View.fxml"), SourceInfo.none(), SourceInfo.none());
        List<EmbeddedResource> sourceResources = new ArrayList<>(List.of(second, first));

        CompilationUnit unit = new CompilationUnit(descriptor(), sourceResources, "source");
        sourceResources.clear();

        List<String> actual = unit.embeddedResources().stream()
            .map(EmbeddedResource::logicalPath)
            .toList();
        assertEquals(List.of("a/View$first.txt", "z/View$second.txt"), actual);
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
    public void Registry_Accepts_The_Same_Declaration_Twice() {
        EmbeddedResource resource = resource(Path.of("sample", "View.fxml"), " item.txt:value");
        CompilerOutputTracker tracker = new CompilerOutputTracker();

        tracker.registerResource(resource);
        assertDoesNotThrow(() -> tracker.registerResource(resource));
    }

    @Test
    public void Collector_Rejects_CaseOnly_And_GeneratedClass_Collisions() {
        CompilerOutputTracker caseTracker = new CompilerOutputTracker();
        caseTracker.registerResource(resource(Path.of("sample", "View.fxml"), " Item.txt:first"));
        MarkupException caseCollision = assertThrows(MarkupException.class,
            () -> caseTracker.registerResource(resource(Path.of("sample", "View.fxml"), " item.txt:second")));

        CompilerOutputTracker classTracker = new CompilerOutputTracker();
        classTracker.registerResource(resource(Path.of("sample", "View.fxml"), " Helper.class:value"));
        MarkupException classCollision = assertThrows(MarkupException.class,
            () -> classTracker.registerClass(QualifiedName.of("sample.View$Helper")));

        assertEquals(ErrorCode.RESOURCE_FILE_COLLISION, caseCollision.getDiagnostic().getCode());
        assertEquals(ErrorCode.RESOURCE_FILE_COLLISION, classCollision.getDiagnostic().getCode());
    }

    @Test
    public void Collector_Rejects_Distinct_Declarations_From_The_Same_Reported_Owner() {
        CompilerOutputTracker tracker = new CompilerOutputTracker();
        tracker.registerResource(resource(Path.of("sample", "View.fxml"), " item.txt:first"));

        MarkupException collision = assertThrows(MarkupException.class,
            () -> tracker.registerResource(resource(Path.of("sample", "View.fxml"), " item.txt:second")));

        assertEquals(ErrorCode.RESOURCE_FILE_COLLISION, collision.getDiagnostic().getCode());
    }

    @Test
    public void Collector_Collision_Diagnostic_Is_Independent_Of_Registration_Order() {
        EmbeddedResource upper = resource(Path.of("sample", "View.fxml"), " Item.txt:first");
        EmbeddedResource lower = resource(Path.of("sample", "View.fxml"), " item.txt:second");

        assertEquals(collisionMessage(upper, lower), collisionMessage(lower, upper));
    }

    @Test
    public void Collector_Rejects_NonPortable_Logical_Paths() {
        CompilerOutputTracker tracker = new CompilerOutputTracker();

        for (String path : List.of("", "/root.txt", "a\\b.txt", "a/../b.txt", "a//b.txt")) {
            assertThrows(IllegalArgumentException.class, () -> tracker.registerClass(path, "sample.View"));
        }
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

    private String collisionMessage(EmbeddedResource first, EmbeddedResource second) {
        CompilerOutputTracker tracker = new CompilerOutputTracker();
        tracker.registerResource(first);
        return assertThrows(MarkupException.class, () -> tracker.registerResource(second)).getMessage();
    }
}

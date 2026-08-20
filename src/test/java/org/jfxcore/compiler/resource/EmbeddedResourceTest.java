// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.resource;

import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.parse.ResourceInstructionParser;
import org.jfxcore.compiler.runner.CompilationResultWrapper;
import org.jfxcore.compiler.util.CompilationResult;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EmbeddedResourceTest {

    @Test
    public void CompilationResult_Is_Sorted_And_Passes_Content_Through() {
        byte[] firstBytes = {1, 2};
        EmbeddedResource second = new EmbeddedResource(
            new byte[] {3}, "second.txt", Path.of("z", "View.fxml"), SourceInfo.none(), SourceInfo.none());
        EmbeddedResource first = new EmbeddedResource(
            firstBytes, "first.txt", Path.of("a", "View.fxml"), SourceInfo.none(), SourceInfo.none());

        CompilationResult result = new CompilationResult(List.of(second, first));
        List<String> actual = result.getEmbeddedResources().stream()
            .map(EmbeddedResource::logicalPath)
            .toList();
        assertEquals(List.of("a/View$first.txt", "z/View$second.txt"), actual);
        assertSame(first, result.getEmbeddedResources().get(0));
        assertSame(firstBytes, result.getEmbeddedResources().get(0).content());

        byte[] returned = result.getEmbeddedResources().get(0).content();
        returned[0] = 8;
        assertEquals(8, firstBytes[0]);
        assertSame(returned, result.getEmbeddedResources().get(0).content());
        assertThrows(UnsupportedOperationException.class, () -> result.getEmbeddedResources().clear());

        CompilationResultWrapper wrapper = new CompilationResultWrapper(result);
        assertSame(firstBytes, wrapper.getEmbeddedResources().get(0).content());
        assertThrows(UnsupportedOperationException.class, () -> wrapper.getEmbeddedResources().clear());
    }

    @Test
    public void Collector_Deduplicates_One_Declaration() {
        EmbeddedResource resource = resource(Path.of("sample", "View.fxml"), " item.txt:value");
        EmbeddedResourceCollector collector = new EmbeddedResourceCollector();

        collector.request(resource);
        collector.request(resource);

        assertEquals(1, collector.getMaterializedResources().size());
        assertSame(resource, collector.getMaterializedResources().get(0));
        assertEquals("sample/View$item.txt", collector.getMaterializedResources().get(0).logicalPath());
        assertSame(resource.content(), collector.getMaterializedResources().get(0).content());
    }

    @Test
    public void Collector_Rejects_CaseOnly_And_GeneratedClass_Collisions() {
        EmbeddedResourceCollector caseCollector = new EmbeddedResourceCollector();
        caseCollector.request(resource(Path.of("sample", "View.fxml"), " Item.txt:first"));
        MarkupException caseCollision = assertThrows(MarkupException.class,
            () -> caseCollector.request(resource(Path.of("sample", "View.fxml"), " item.txt:second")));

        EmbeddedResourceCollector classCollector = new EmbeddedResourceCollector();
        classCollector.request(resource(Path.of("sample", "View.fxml"), " Helper.class:value"));
        MarkupException classCollision = assertThrows(MarkupException.class,
            () -> classCollector.reserveClass("sample/View$Helper.class", "sample.View$Helper"));

        assertEquals(ErrorCode.RESOURCE_FILE_COLLISION, caseCollision.getDiagnostic().getCode());
        assertEquals(ErrorCode.RESOURCE_FILE_COLLISION, classCollision.getDiagnostic().getCode());
    }

    @Test
    public void Collector_Rejects_Distinct_Declarations_From_The_Same_Reported_Owner() {
        EmbeddedResourceCollector collector = new EmbeddedResourceCollector();
        collector.request(resource(Path.of("sample", "View.fxml"), " item.txt:first"));

        MarkupException collision = assertThrows(MarkupException.class,
            () -> collector.request(resource(Path.of("sample", "View.fxml"), " item.txt:second")));

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
        EmbeddedResourceCollector collector = new EmbeddedResourceCollector();

        for (String path : List.of("", "/root.txt", "a\\b.txt", "a/../b.txt", "a//b.txt")) {
            assertThrows(IllegalArgumentException.class, () -> collector.reserveClass(path, "sample.View"));
        }
    }

    private EmbeddedResource resource(Path sourceFile, String data) {
        return new ResourceInstructionParser(data, sourceFile).parse();
    }

    private String collisionMessage(EmbeddedResource first, EmbeddedResource second) {
        EmbeddedResourceCollector collector = new EmbeddedResourceCollector();
        collector.request(first);
        return assertThrows(MarkupException.class, () -> collector.request(second)).getMessage();
    }
}

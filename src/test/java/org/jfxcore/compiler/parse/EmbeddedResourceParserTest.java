// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.ast.AttributeValueNode;
import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.resource.EmbeddedResource;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EmbeddedResourceParserTest {

    @Test
    public void Resources_Are_Collected_Before_Inside_And_After_The_Root() {
        DocumentNode document = parse(Path.of("views", "Test.fxml"), """
            <?resource before.txt:before?>
            <Test xmlns="http://javafx.com/javafx">
                <?resource inside.txt:inside?>
            </Test>
            <?resource after.txt:after?>
        """);

        assertEquals(
            List.of("before.txt", "inside.txt", "after.txt"),
            document.getResources().stream().map(EmbeddedResource::logicalName).toList());
    }

    @Test
    public void Document_Name_And_Resource_Path_Use_The_Same_Source_Base() {
        DocumentNode fxml = parse(Path.of("views", "My.View.fxml"),
            "<?resource item.txt:value?><Test xmlns=\"http://javafx.com/javafx\"/>");

        DocumentNode embeddedJava = parse(Path.of("views", "MyView.java"),
            "<?resource item.txt:value?><Test xmlns=\"http://javafx.com/javafx\"/>");

        assertEquals("My.View", fxml.getDocumentName());
        assertEquals("views/My.View$fad70b48$item.txt", fxml.getResources().get(0).logicalPath());
        assertEquals("MyView", embeddedJava.getDocumentName());
        assertEquals("views/MyView$cc9829f$item.txt", embeddedJava.getResources().get(0).logicalPath());
    }

    @Test
    public void DeepClone_Preserves_Document_Name_And_Declaration_Identity() {
        DocumentNode document = parse(Path.of("views", "Test.fxml"),
            "<?resource item.txt:item?><Test xmlns=\"http://javafx.com/javafx\"/>");

        DocumentNode clone = document.deepClone();

        assertEquals(document.getDocumentName(), clone.getDocumentName());
        assertSame(document.getResources().get(0), clone.getResources().get(0));
        assertThrows(UnsupportedOperationException.class, () -> document.getResources().clear());
    }

    @Test
    public void Exact_And_CaseOnly_Duplicate_Declarations_Are_Rejected() {
        for (String duplicate : List.of("item.txt", "Item.txt")) {
            MarkupException exception = assertThrows(MarkupException.class, () -> parse(
                Path.of("views", "Test.fxml"), """
                    <?resource item.txt:first?>
                    <?resource %s:second?>
                    <Test xmlns="http://javafx.com/javafx"/>
                """.formatted(duplicate)));

            assertEquals(ErrorCode.DUPLICATE_RESOURCE_DECLARATION, exception.getDiagnostic().getCode());
        }
    }

    private DocumentNode parse(Path sourceFile, String source) {
        return new FxmlParser(sourceFile, source, null).parseDocument();
    }

    private AttributeValueNode getAttribute(PropertyNode property) {
        assertNotNull(property);
        return assertInstanceOf(AttributeValueNode.class, property.getValues().get(0));
    }
}

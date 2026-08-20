// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.CompilationScope;
import org.jfxcore.compiler.util.CompilationSource;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.ProcessingInstruction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class XmlReaderTest {

    @Test
    public void ProcessingInstruction_Retains_Exact_Data_And_Normalizes_Resource_Newlines() {
        String data = " foo.txt:\r\n\tvalue\r\n  ";
        String source = "<?resource" + data + "?>\r\n<Root/>";
        var context = new CompilationContext(new CompilationSource.InMemory(source));

        try (var ignored = new CompilationScope(context)) {
            ProcessingInstruction instruction = assertInstanceOf(
                ProcessingInstruction.class,
                new XmlReader(source, Map.of()).getDocument().getFirstChild());

            SourceMappedText mapped = assertInstanceOf(
                SourceMappedText.class,
                instruction.getUserData(XmlReader.PI_DATA_SOURCE_MAPPED_TEXT_KEY));

            assertEquals(data, instruction.getData());
            assertEquals(data, mapped.getText());
            assertEquals(source.indexOf(data), mapped.getSourceInfo(0, 0).getStart().getColumn());

            var resource = new ResourceInstructionParser(
                mapped,
                Path.of("Root.fxml"),
                (SourceInfo)instruction.getUserData(XmlReader.SOURCE_INFO_KEY)).parse();

            assertEquals("value", new String(resource.content(), StandardCharsets.UTF_8));
            assertEquals("foo.txt", resource.nameSourceInfo().toOriginal().getText());
        }
    }

    @Test
    public void Attribute_Retains_Mapped_Lexer_Input_User_Data() {
        String source = "<Root value=\"a&amp;b\"/>";
        Attr attribute = new XmlReader(source, Map.of()).getDocument().getDocumentElement().getAttributeNode("value");
        SourceMappedText input = assertInstanceOf(SourceMappedText.class, attribute.getUserData(XmlReader.ATTR_VALUE_SOURCE_MAPPED_TEXT_KEY));
        int start = source.indexOf("a&amp;b");

        assertEquals("a&b", attribute.getValue());
        assertEquals("a&b", input.getText());
        SourceInfo sourceInfo = input.getSourceInfo(0, 3);
        assertEquals(new SourceInfo(0, start, 0, start + 3), sourceInfo);
        assertEquals(new SourceInfo(0, start, 0, start + 7), sourceInfo.toOriginal());
        assertEquals(sourceInfo, attribute.getUserData(XmlReader.ATTR_VALUE_SOURCE_INFO_KEY));
    }

    @Test
    public void Invalid_Numeric_Reference_Highlights_Complete_Raw_Spelling() {
        String source = "<Root value=\"x&#x110000;y\"/>";
        var context = new CompilationContext(new CompilationSource.InMemory(source));

        try (var ignored = new CompilationScope(context)) {
            MarkupException exception = assertThrows(MarkupException.class, () -> new XmlReader(source, Map.of()));
            assertEquals("&#x110000;", exception.getSourceInfo().getText());
        }
    }
}

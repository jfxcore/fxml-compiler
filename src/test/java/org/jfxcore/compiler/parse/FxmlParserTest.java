// Copyright (c) 2022, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.junit.jupiter.api.Test;
import org.jfxcore.compiler.TestBase;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
public class FxmlParserTest extends TestBase {

    @Test
    public void CDataSection_Is_Not_Processed() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Test xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    CDATA section: <![CDATA[ < > & ]]>.
                </Test>
            """).parseDocument();

        assertEquals("CDATA section:  < > & .", ((ObjectNode)document.getRoot()).getTextContent().getText());
    }

    @Test
    public void CDataStart_Can_Appear_Within_CDataSection() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Test xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    CDATA section: <![CDATA[ <![CDATA[ ]]>.
                </Test>
            """).parseDocument();

        assertEquals("CDATA section:  <![CDATA[ .", ((ObjectNode)document.getRoot()).getTextContent().getText());
    }

    @Test
    public void CDataEnd_Is_Escaped_With_CDataSection() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Test xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    CDATA section: <![CDATA[<![CDATA[...]]><![CDATA[]]]]><![CDATA[>]]>
                </Test>
            """).parseDocument();

        assertEquals("CDATA section: <![CDATA[...]]>", ((ObjectNode)document.getRoot()).getTextContent().getText());
    }

    @Test
    public void FxNamespace_Is_Not_Required() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" prefWidth="10"/>
            """).parseDocument();

        //noinspection ConstantConditions
        assertEquals("10", ((ObjectNode)document.getRoot()).findProperty("prefWidth").getTextValue(null));
    }

    @Test
    public void Unknown_Namespace_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" foo:prefWidth="10"/>
            """).parseDocument());

        assertEquals(ErrorCode.UNKNOWN_NAMESPACE, ex.getDiagnostic().getCode());
    }

    @Test
    public void ProcessingInstructions_Are_Parsed_Correctly() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <?import javafx.scene.layout.*?>
                <?import javafx.scene.control.Label?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"/>
            """).parseDocument();

        assertTrue(document.getImports().contains("javafx.scene.layout.*"));
        assertTrue(document.getImports().contains("javafx.scene.control.Label"));
    }

    @Test
    public void Unescape_Character_Entity_References() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       gt="&gt;"
                       lt="&lt;"
                       quot="&quot;"
                       amp="&amp;"
                       apos="&apos;">
                    <num1>&#100;</num1>
                    <num2>&#xff;</num2>
                </Label>
            """).parseDocument();

        assertEquals(">", getPropertyText(document, "gt"));
        assertEquals("<", getPropertyText(document, "lt"));
        assertEquals("\"", getPropertyText(document, "quot"));
        assertEquals("&", getPropertyText(document, "amp"));
        assertEquals("'", getPropertyText(document, "apos"));
        assertEquals(String.valueOf((char)100), getElementText(document, "num1"));
        assertEquals(String.valueOf((char)255), getElementText(document, "num2"));
    }

    @Test
    public void Quoted_Text_Includes_All_Whitespace() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text1=" foo"
                       text2="  foo  "
                       text3="bar  "
                       text4="foo&#x0a;bar"
                       text5="  foo&#x0a;bar  "/>
            """).parseDocument();

        assertEquals(" foo", getPropertyText(document, "text1"));
        assertEquals("  foo  ", getPropertyText(document, "text2"));
        assertEquals("bar  ", getPropertyText(document, "text3"));
        assertEquals("foo\nbar", getPropertyText(document, "text4"));
        assertEquals("  foo\nbar  ", getPropertyText(document, "text5"));
    }

    @Test
    public void Escaped_OpenCurly_Is_Not_Processed() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text1="{}foo"
                       text2="  {} bar "/>
            """).parseDocument();

        assertEquals("foo", getPropertyText(document, "text1"));
        assertEquals(" bar ", getPropertyText(document, "text2"));
    }

    private String getElementText(DocumentNode document, String elementName) {
        //noinspection OptionalGetWithoutIsPresent
        return document
            .getRoot().as(ObjectNode.class)
            .getChildren().stream()
            .filter(e -> ((ObjectNode)e).getType().getMarkupName().equals(elementName))
            .findFirst().get().as(ObjectNode.class)
            .getTextContent()
            .getText();
    }

    private String getPropertyText(DocumentNode document, String propertyName) {
        //noinspection OptionalGetWithoutIsPresent
        return document
            .getRoot().as(ObjectNode.class)
            .getProperties().stream()
            .filter(p -> p.getName().equals(propertyName))
            .findFirst().get()
            .getValues().get(0).as(TextNode.class)
            .getText();
    }

}

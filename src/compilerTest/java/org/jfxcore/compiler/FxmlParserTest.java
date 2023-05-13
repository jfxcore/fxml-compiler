// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.parse.FxmlParseAbortException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javafx.scene.layout.Pane;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class FxmlParserTest extends CompilerTestBase {

    @Test
    public void JavaFX_Namespace_Can_Contain_Trailing_Slash() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx/" id="foo"/>
        """);

        assertEquals("foo", root.getId());
    }

    @Test
    public void JavaFX_Namespace_Can_Contain_SubPath() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx/21" id="foo"/>
        """);

        assertEquals("foo", root.getId());
    }

    @Test
    public void FXML_Namespace_Can_Contain_Trailing_Slash() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx/" xmlns:fx="http://jfxcore.org/fxml/2.0/">
                <GridPane fx:id="foo"/>
            </GridPane>
        """);

        assertEquals("foo", root.getChildren().get(0).getId());
    }

    @Test
    public void Unknown_FX_Namespace_Aborts_Parsing() {
        assertThrows(
                FxmlParseAbortException.class,
                () -> compileAndRun("""
                <GridPane xmlns:fx="http://javafx.com/fxml"/>
            """));
    }

    @Test
    public void Missing_Xmlns_Aborts_Parsing() {
        assertThrows(
            FxmlParseAbortException.class,
            () -> compileAndRun("""
                <GridPane xmlns:fx="http://javafx.com/fxml/2.0"/>
            """));
    }

    @Test
    public void Unknown_Xmlns_Aborts_Parsing() {
        assertThrows(
            FxmlParseAbortException.class,
            () -> compileAndRun("""
                <GridPane xmlns="http://doesnotexist.com/javafx" xmlns:fx="http://javafx.com/fxml/2.0"/>
            """));
    }

    @Test
    public void Unmatched_Tags_Throws_Exception() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <GridPane xmlns="http://javafx.com/javafx">
            </Button>
        """));

        assertEquals(ErrorCode.UNMATCHED_TAG, ex.getDiagnostic().getCode());
    }

}

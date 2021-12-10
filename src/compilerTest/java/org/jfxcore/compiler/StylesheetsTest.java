// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class StylesheetsTest extends CompilerTestBase {

    @Test
    public void Internal_Stylesheet_Is_Applied_Correctly() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <stylesheets>
                    <fx:stylesheet>
                        .label {
                            -fx-pref-width: 5.0;
                        }
                    </fx:stylesheet>
                </stylesheets>
                <Label/>
            </Pane>
        """);

        //noinspection unused
        Scene unused = new Scene(root);
        root.applyCss();
        Label label = (Label)root.getChildren().get(0);
        assertEquals(5.0, label.getPrefWidth(), 0.001);
    }

    @Test
    public void Multiple_Internal_Stylesheets_Are_Applied_Correctly() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <stylesheets>
                    <fx:stylesheet>
                        .label {
                            -fx-pref-width: 5.0;
                        }
                    </fx:stylesheet>
                    <fx:stylesheet>
                        .label {
                            -fx-pref-height: 7.0;
                        }
                    </fx:stylesheet>
                </stylesheets>
                <Label/>
            </Pane>
        """);

        //noinspection unused
        Scene unused = new Scene(root);
        root.applyCss();
        Label label = (Label)root.getChildren().get(0);
        assertEquals(2, root.getStylesheets().size());
        assertEquals(5.0, label.getPrefWidth(), 0.001);
        assertEquals(7.0, label.getPrefHeight(), 0.001);
    }

    @Test
    public void Complex_Internal_Stylesheet_Is_Compiled_Correctly() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <stylesheets>
                    <fx:stylesheet>
                        .table-view:constrained-resize .filler,
                        .tree-table-view:constrained-resize .filler {
                            -fx-border-color:
                                derive(-fx-base, 80%)
                                linear-gradient(to bottom, derive(-fx-base,80%) 20%, derive(-fx-base,-10%) 90%)
                                derive(-fx-base, 10%)
                                linear-gradient(to bottom, derive(-fx-base,80%) 20%, derive(-fx-base,-10%) 90%),
                                /* Outer border: */
                                transparent -fx-table-header-border-color -fx-table-header-border-color -fx-table-header-border-color;
                            -fx-border-insets: 0 1 1 1, 0 0 0 0;
                        }
                    </fx:stylesheet>
                </stylesheets>
                <Label/>
            </Pane>
        """);

        //noinspection unused
        Scene unused = new Scene(root);
        root.applyCss();
    }

    @Test
    public void Internal_Stylesheet_Works_With_External_Stylesheet() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <stylesheets>
                    <fx:stylesheet>
                        .label {
                            -fx-pref-width: 5.0;
                        }
                    </fx:stylesheet>
                    path/to/external_stylesheet1.css
                    path/to/external_stylesheet2.css
                </stylesheets>
                <Label/>
            </Pane>
        """);

        //noinspection unused
        Scene unused = new Scene(root);
        root.applyCss();
        Label label = (Label)root.getChildren().get(0);
        assertEquals(5.0, label.getPrefWidth(), 0.001);
        assertEquals(3, root.getStylesheets().size());
        assertTrue(root.getStylesheets().get(0).startsWith("data:"));
        assertEquals("path/to/external_stylesheet1.css", root.getStylesheets().get(1));
        assertEquals("path/to/external_stylesheet2.css", root.getStylesheets().get(2));
    }

    @Test
    public void Stylesheet_For_Incompatible_Property_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <prefWidth>
                    <fx:stylesheet>
                        .text { -fx-pref-width: 5.0; }
                    </fx:stylesheet>
                </prefWidth>
            </Pane>
        """));

        assertEquals(ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, ex.getDiagnostic().getCode());
    }

    @Test
    @Disabled
    public void Stylesheet_With_Invalid_Rule_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <stylesheets>
                    <fx:stylesheet>
                        grid-pane?selected {
                            -fx-stroke-line-join: miter;
                        }
                    </fx:stylesheet>
                </stylesheets>
            </Pane>
        """));

        assertEquals(ErrorCode.STYLESHEET_ERROR, ex.getDiagnostic().getCode());
    }

    @Test
    public void Stylesheet_With_Invalid_Declaration_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <stylesheets>
                    <fx:stylesheet>
                        grid-pane:selected {
                            -fx-stroke-line-join: foo bar baz;
                        }
                    </fx:stylesheet>
                </stylesheets>
            </Pane>
        """));

        assertEquals(ErrorCode.STYLESHEET_ERROR, ex.getDiagnostic().getCode());
    }

    @Test
    @Disabled
    public void Stylesheet_Without_Rule_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <stylesheets>
                    <fx:stylesheet>
                        -fx-stroke-line-join: miter;
                    </fx:stylesheet>
                </stylesheets>
                <Label/>
            </Pane>
        """));

        assertEquals(ErrorCode.STYLESHEET_ERROR, ex.getDiagnostic().getCode());
    }

    @Test
    public void Stylesheet_With_Unmatched_Brackets_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <stylesheets>
                    <fx:stylesheet>
                        .label { -fx-pref-width: 5.0;
                    </fx:stylesheet>
                </stylesheets>
            </Pane>
        """));

        assertEquals(ErrorCode.STYLESHEET_ERROR, ex.getDiagnostic().getCode());
    }

    @Test
    public void Inline_Style_Is_Applied_Correctly() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <style>
                    -fx-pref-width: 5.0;
                    -fx-pref-height: 7.0;
                </style>
            </Pane>
        """);

        //noinspection unused
        Scene unused = new Scene(root);
        root.applyCss();
        assertEquals(5.0, root.getPrefWidth(), 0.001);
        assertEquals(7.0, root.getPrefHeight(), 0.001);
    }

}

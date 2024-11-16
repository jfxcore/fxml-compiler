// Copyright (c) 2021, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class MarkupExtensionTest extends CompilerTestBase {

    @Test
    public void Instantiation_With_MarkupExtension_Syntax() {
        GridPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <fx:define>
                    <Background fx:id="a" fills="{BackgroundFill fill=#ff0000; radii={fx:null}; insets=1,2,3.5,.4}"/>
                    <Background fx:id="b" fills="{BackgroundFill fill=red; radii=EMPTY; insets=1,2,3.5,.4}"/>
                    <Background fx:id="c" fills="{BackgroundFill fill=RED; radii=$CornerRadii.EMPTY; insets=1,2,3.5,.4}"/>
                </fx:define>
            </GridPane>
        """);

        class Test {
            static void test(Background background) {
                BackgroundFill fill = background.getFills().get(0);
                assertEquals(Color.RED, fill.getFill());
                assertEquals(CornerRadii.EMPTY, fill.getRadii());
                assertEquals(1, fill.getInsets().getTop(), 0.001);
                assertEquals(2, fill.getInsets().getRight(), 0.001);
                assertEquals(3.5, fill.getInsets().getBottom(), 0.001);
                assertEquals(.4, fill.getInsets().getLeft(), 0.001);
            }
        }

        Test.test((Background)root.getProperties().get("a"));
        Test.test((Background)root.getProperties().get("b"));
        Test.test((Background)root.getProperties().get("c"));
    }

    @Test
    public void Missing_CloseCurly_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   text="{fx:foo bar"/>
        """));

        assertEquals(ErrorCode.EXPECTED_TOKEN, ex.getDiagnostic().getCode());
        assertTrue(ex.getDiagnostic().getMessage().contains("}"));
        assertCodeHighlight("\"", ex);
    }

    @Test
    public void URLExtension_With_Relative_Location_Is_Evaluated_Correctly() {
        Label root = compileAndRun("""
            <?import javafx.fxml.*?>
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   text="{fx:url image.jpg}"/>
        """);

        assertTrue(root.getText().endsWith("org/jfxcore/compiler/classes/image.jpg"));
    }

    @Test
    public void URLExtension_With_Root_Location_Is_Evaluated_Correctly() {
        Label root = compileAndRun("""
            <?import javafx.fxml.*?>
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   text="{fx:url /org/jfxcore/compiler/classes/image.jpg}"/>
        """);

        assertTrue(root.getText().endsWith("org/jfxcore/compiler/classes/image.jpg"));
    }

    @Test
    public void URLExtension_With_Quoted_Path_Is_Evaluated_Correctly() {
        Label root = compileAndRun("""
            <?import javafx.fxml.*?>
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   text="{fx:url '/org/jfxcore/compiler/classes/image with   spaces.jpg'}"/>
        """);

        assertTrue(root.getText().endsWith("org/jfxcore/compiler/classes/image%20with%20%20%20spaces.jpg"));
    }

    @Test
    public void URLExtension_Can_Be_Added_To_String_Collection() {
        Label root = compileAndRun("""
            <?import javafx.fxml.*?>
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <stylesheets>
                    <fx:url>image.jpg</fx:url>
                </stylesheets>
            </Label>
        """);

        assertTrue(root.getStylesheets().stream().anyMatch(s -> s.endsWith("org/jfxcore/compiler/classes/image.jpg")));
    }

    @Test
    public void URLExtension_Cannot_Be_Assigned_To_Incompatible_Property() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.fxml.*?>
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   prefWidth="{fx:url image.jpg}"/>
        """));

        assertEquals(ErrorCode.INCOMPATIBLE_PROPERTY_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("{fx:url image.jpg}", ex);
    }

    @Test
    public void Unsuitable_Extension_Parameter_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.fxml.*?>
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   text="{fx:url ${foo}}"/>
        """));

        assertEquals(ErrorCode.PROPERTY_MUST_CONTAIN_TEXT, ex.getDiagnostic().getCode());
        assertCodeHighlight("${foo}", ex);
    }

    @Test
    public void Nonexistent_Resource_Throws_RuntimeException() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> compileAndRun("""
            <?import javafx.fxml.*?>
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   text="{fx:url foobarbaz.jpg}"/>
        """));

        assertTrue(ex.getMessage().startsWith("Resource not found"));
    }

    @SuppressWarnings("unused")
    public static class TypeIntrinsicTestPane extends Pane {
        private Class<?> wildcardClass;
        private Class<? extends Node> wildcardClassUpperBound;

        public Class<?> getWildcardClass() { return wildcardClass; }
        public void setWildcardClass(Class<?> clazz) { wildcardClass = clazz; }

        public Class<? extends Node> getWildcardClassUpperBound() { return wildcardClassUpperBound; }
        public void setWildcardClassUpperBound(Class<? extends Node> clazz) { wildcardClassUpperBound = clazz; }

        public Class<? extends String> getWildcardClassIncompatibleUpperBound() { return null; }
        public void setWildcardClassIncompatibleUpperBound(Class<? extends String> clazz) {}
    }

    @Test
    public void Type_Can_Be_Assigned_To_Wildcard() {
        TypeIntrinsicTestPane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <TypeIntrinsicTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   wildcardClass="{fx:type TextField}"/>
        """);

        assertEquals(TextField.class, root.getWildcardClass());
    }

    @Test
    public void Type_Can_Be_Assigned_To_Wildcard_With_Upper_Bound() {
        TypeIntrinsicTestPane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <TypeIntrinsicTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   wildcardClassUpperBound="{fx:type TextField}"/>
        """);

        assertEquals(TextField.class, root.getWildcardClassUpperBound());
    }

    @Test
    public void Type_Is_Assigned_To_Wildcard_With_Incompatible_Upper_Bound_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <TypeIntrinsicTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   wildcardClassIncompatibleUpperBound="{fx:type TextField}"/>
        """));

        assertEquals(ErrorCode.INCOMPATIBLE_PROPERTY_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("{fx:type TextField}", ex);
    }

    @Test
    public void Null_Can_Be_Assigned_To_ReferenceType() {
        Label root = compileAndRun("""
            <?import javafx.fxml.*?>
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   text="{fx:null}"/>
        """);

        assertNull(root.getText());
    }

    @Test
    public void Null_Cannot_Be_Assigned_To_PrimitiveType() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.fxml.*?>
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   prefWidth="{fx:null}"/>
        """));

        assertEquals(ErrorCode.INCOMPATIBLE_PROPERTY_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("{fx:null}", ex);
    }

}

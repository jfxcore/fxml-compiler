// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.TestCompiler;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class MarkupExtensionTest {

    @Test
    public void URLExtension_With_Relative_Location_Is_Evaluated_Correctly() {
        Label root = TestCompiler.newInstance(
            this, "URLExtension_With_Relative_Location_Is_Evaluated_Correctly", """
                <?import javafx.fxml.*?>
                <?import javafx.scene.control.*?>
                <Label xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                       text="{fx:url image.jpg}"/>
            """);

        assertTrue(root.getText().endsWith("org/jfxcore/compiler/classes/image.jpg"));
    }

    @Test
    public void URLExtension_With_Root_Location_Is_Evaluated_Correctly() {
        Label root = TestCompiler.newInstance(
            this, "URLExtension_With_Root_Location_Is_Evaluated_Correctly", """
                <?import javafx.fxml.*?>
                <?import javafx.scene.control.*?>
                <Label xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                       text="{fx:url /org/jfxcore/compiler/classes/image.jpg}"/>
            """);

        assertTrue(root.getText().endsWith("org/jfxcore/compiler/classes/image.jpg"));
    }

    @Test
    public void URLExtension_With_Quoted_Path_Is_Evaluated_Correctly() {
        Label root = TestCompiler.newInstance(
            this, "URLExtension_With_Quoted_Path_Is_Evaluated_Correctly", """
                <?import javafx.fxml.*?>
                <?import javafx.scene.control.*?>
                <Label xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                       text="{fx:url '/org/jfxcore/compiler/classes/image with   spaces.jpg'}"/>
            """);

        assertTrue(root.getText().endsWith("org/jfxcore/compiler/classes/image%20with%20%20%20spaces.jpg"));
    }

    @Test
    public void URLExtension_Can_Be_Added_To_String_Collection() {
        Label root = TestCompiler.newInstance(
            this, "URLExtension_Can_Be_Added_To_String_Collection", """
                <?import javafx.fxml.*?>
                <?import javafx.scene.control.*?>
                <Label xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <stylesheets>
                        <fx:url>image.jpg</fx:url>
                    </stylesheets>
                </Label>
            """);

        assertTrue(root.getStylesheets().stream().anyMatch(s -> s.endsWith("org/jfxcore/compiler/classes/image.jpg")));
    }

    @Test
    public void URLExtension_Cannot_Be_Assigned_To_Incompatible_Property() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "URLExtension_Cannot_Be_Assigned_To_Incompatible_Property", """
                <?import javafx.fxml.*?>
                <?import javafx.scene.control.*?>
                <Label xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                       prefWidth="{fx:url image.jpg}"/>
            """));

        assertEquals(ErrorCode.INCOMPATIBLE_PROPERTY_TYPE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Unsuitable_Extension_Parameter_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Unsuitable_Extension_Parameter_Fails", """
                <?import javafx.fxml.*?>
                <?import javafx.scene.control.*?>
                <Label xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                       text="{fx:url {fx:bind foo}}"/>
            """));

        assertEquals(ErrorCode.PROPERTY_MUST_CONTAIN_TEXT, ex.getDiagnostic().getCode());
    }

    @Test
    public void Nonexistent_Resource_Throws_RuntimeException() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> TestCompiler.newInstance(
            this, "Nonexistent_Resource_Throws_RuntimeException", """
                <?import javafx.fxml.*?>
                <?import javafx.scene.control.*?>
                <Label xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
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
        TypeIntrinsicTestPane root = TestCompiler.newInstance(
            this, "Type_Can_Be_Assigned_To_Wildcard", """
                <?import javafx.scene.control.*?>
                <?import org.jfxcore.compiler.MarkupExtensionTest.*?>
                <TypeIntrinsicTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                       wildcardClass="{fx:type TextField}"/>
            """);

        assertEquals(TextField.class, root.getWildcardClass());
    }

    @Test
    public void Type_Can_Be_Assigned_To_Wildcard_With_Upper_Bound() {
        TypeIntrinsicTestPane root = TestCompiler.newInstance(
            this, "Type_Can_Be_Assigned_To_Wildcard_With_Upper_Bound", """
                <?import javafx.scene.control.*?>
                <?import org.jfxcore.compiler.MarkupExtensionTest.*?>
                <TypeIntrinsicTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                       wildcardClassUpperBound="{fx:type TextField}"/>
            """);

        assertEquals(TextField.class, root.getWildcardClassUpperBound());
    }

    @Test
    public void Type_Is_Assigned_To_Wildcard_With_Incompatible_Upper_Bound_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Type_Is_Assigned_To_Wildcard_With_Incompatible_Upper_Bound_Fails", """
                <?import javafx.scene.control.*?>
                <?import org.jfxcore.compiler.MarkupExtensionTest.*?>
                <TypeIntrinsicTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                       wildcardClassIncompatibleUpperBound="{fx:type TextField}"/>
            """));

        assertEquals(ErrorCode.INCOMPATIBLE_PROPERTY_TYPE, ex.getDiagnostic().getCode());
    }

}
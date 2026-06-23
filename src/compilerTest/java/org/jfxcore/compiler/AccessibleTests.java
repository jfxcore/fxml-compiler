// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.accesstest.PublicTestPane;
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
public class AccessibleTests extends CompilerTestBase {

    @SuppressWarnings("unused")
    static class InaccessiblePane extends Pane {}

    @Test
    public void Pane_Is_Not_Accessible() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import org.jfxcore.compiler.accesstest.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <PackagePrivateTestPane/>
            </GridPane>
        """));

        assertEquals(ErrorCode.CLASS_NOT_ACCESSIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("PackagePrivateTestPane", ex);
    }

    @SuppressWarnings("unused")
    public static class InaccessibleConstant {
        private static final double VALUE = 1.0;
    }

    @Test
    public void NonPublic_Constant_Is_Not_Accessible() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <prefWidth>
                    <InaccessibleConstant fx:constant="VALUE"/>
                </prefWidth>
            </GridPane>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_ACCESSIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("VALUE", ex);
    }

    @Test
    public void ValueOf_Method_Is_Not_Accessible() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import org.jfxcore.compiler.accesstest.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <fx:define>
                    <InaccessibleValueOf fx:value="foo"/>
                </fx:define>
            </GridPane>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_ACCESSIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("fx:value=\"foo\"", ex);
    }

    @Test
    public void Bind_Once_To_Private_Method_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import org.jfxcore.compiler.accesstest.*?>
            <PublicTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            prefWidth="$privateMethod(123)"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("privateMethod", ex);
    }

    @Test
    public void Bind_Once_To_PackagePrivate_Method_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import org.jfxcore.compiler.accesstest.*?>
            <PublicTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            prefWidth="$packagePrivateMethod(123)"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_ACCESSIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("packagePrivateMethod", ex);
    }

    @Test
    public void Bind_Once_To_Protected_Method_With_Constant_Argument_Succeeds() {
        PublicTestPane root = compileAndRun("""
            <?import org.jfxcore.compiler.accesstest.*?>
            <PublicTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            prefWidth="$protectedMethod(123)"/>
        """);

        assertEquals(123, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Once_To_Protected_With_Observable_Argument_Method_Succeeds() {
        PublicTestPane root = compileAndRun("""
            <?import org.jfxcore.compiler.accesstest.*?>
            <PublicTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="$protectedMethod(prefHeight)"/>
        """);

        assertEquals(-1, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Once_To_Nested_Protected_Method_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import org.jfxcore.compiler.accesstest.*?>
            <PublicTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            prefWidth="$Nested.protectedMethod(123)"/>
        """));

        assertEquals(ErrorCode.INSTANCE_MEMBER_REFERENCED_FROM_STATIC_CONTEXT, ex.getDiagnostic().getCode());
        assertCodeHighlight("Nested.protectedMethod", ex);
    }

    @Test
    public void Bind_Once_To_Nested_Static_Protected_Method_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import org.jfxcore.compiler.accesstest.*?>
            <PublicTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            prefWidth="$Nested.staticProtectedMethod(123)"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_ACCESSIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("Nested.staticProtectedMethod", ex);
    }

    @Test
    public void Bind_Unidirectional_To_PackagePrivate_Method_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import org.jfxcore.compiler.accesstest.*?>
            <PublicTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            prefWidth="${packagePrivateMethod(123)}"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_ACCESSIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("packagePrivateMethod", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Protected_Method_With_Constant_Argument_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import org.jfxcore.compiler.accesstest.*?>
            <PublicTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            prefWidth="${protectedMethod(123)}"/>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("protectedMethod(123)", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Protected_Method_With_Observable_Argument_Succeeds() {
        PublicTestPane root = compileAndRun("""
            <?import org.jfxcore.compiler.accesstest.*?>
            <PublicTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            prefWidth="${protectedMethod(prefHeight)}"/>
        """);

        root.setPrefHeight(5);
        assertEquals(5, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Bidirectional_To_Protected_Method_With_Observable_Argument_Succeeds() {
        PublicTestPane root = compileAndRun("""
            <?import org.jfxcore.compiler.accesstest.*?>
            <PublicTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            prefWidth="#{protectedMethod(prefHeight)}"/>
        """);

        root.setPrefHeight(5);
        assertEquals(5, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Bidirectional_To_Protected_Method_With_Inaccessible_InverseMethod_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import org.jfxcore.compiler.accesstest.*?>
            <PublicTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            prefWidth="#{protectedMethod(prefHeight);
                                         inverseMethod=InaccessibleInverseMethod.protectedMethod}"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_ACCESSIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("InaccessibleInverseMethod.protectedMethod", ex);
    }

    @SuppressWarnings("unused")
    public static class TestPane2 extends Pane {
        final StringProperty labelText1 = new SimpleStringProperty("foo");
        private final StringProperty labelText2 = new SimpleStringProperty("bar");
        StringProperty labelText2Property() { return labelText2; }
    }

    @Test
    public void Bind_To_PackagePrivate_Property_In_Same_Package() {
        TestPane2 root = compileAndRun("""
            <?import javafx.scene.control.Label?>
            <TestPane2 xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Label text="${labelText1}"/>
                <Label text="${labelText2}"/>
            </TestPane2>
        """);

        assertFieldAccess(root, TestPane2.class.getName(), "labelText1", "Ljavafx/beans/property/StringProperty;");
        assertNotFieldAccess(root, TestPane2.class.getName(), "labelText2", "Ljavafx/beans/property/StringProperty;");
        assertMethodCall(root, "labelText2Property");

        assertEquals("foo", ((Label)root.getChildren().get(0)).getText());
        assertEquals("bar", ((Label)root.getChildren().get(1)).getText());
    }

    @Test
    public void Bind_To_PackagePrivate_Field_In_Different_Package() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.Label?>
            <?import org.jfxcore.compiler.accesstest.*?>
            <PropertyTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Label text="${labelText1}"/>
            </PropertyTestPane>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_ACCESSIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("labelText1", ex);
    }

    @Test
    public void Bind_To_PackagePrivate_Method_In_Different_Package() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.Label?>
            <?import org.jfxcore.compiler.accesstest.*?>
            <PropertyTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Label text="${labelText2}"/>
            </PropertyTestPane>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_ACCESSIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("labelText2", ex);
    }
}

// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class BindingSourceTest extends CompilerTestBase {

    @Test
    public void Bind_Once_To_Local_Control_With_Indexed_Parent_Selector() {
        Pane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Pane fx:id="pane" prefWidth="123">
                    <Pane prefWidth="{fx:once parent[2]/pane.prefWidth}"/>
                </Pane>
            </Pane>
        """);

        assertEquals(123, ((Pane)root.getChildren().get(0)).getPrefWidth(), 0.001);
        assertEquals(-1, ((Pane)((Pane)root.getChildren().get(0)).getChildren().get(0)).getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Once_To_Parent_Property_With_Typed_Parent_Selector_Does_Not_Apply_Latest_Value() {
        BindingPathTest.TestPane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
            <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                      prefWidth="123">
                <Label prefWidth="{fx:once parent[TestPane]/prefWidth}"/>
            </TestPane>
        """);

        Label label = (Label)root.getChildren().get(0);
        assertEquals(-1, label.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_Local_Control_With_Parent_Selector() {
        Pane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Pane fx:id="pane" prefWidth="123">
                    <Pane prefWidth="{fx:bind parent[2]/pane.prefWidth}"/>
                </Pane>
            </Pane>
        """);

        assertEquals(123.0, ((Pane)root.getChildren().get(0)).getPrefWidth(), 0.001);
        assertEquals(123.0, ((Pane)((Pane)root.getChildren().get(0)).getChildren().get(0)).getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_Parent_Property_Applies_Latest_Value() {
        BindingPathTest.TestPane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
            <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                      prefWidth="123">
                <Label prefWidth="{fx:bind parent[TestPane]/prefWidth}"/>
            </TestPane>
        """);

        // In this example, the TestPane.prefWidth property is assigned AFTER TestPane.children,
        // but the unidirectional binding will apply the latest value as soon as TestPane.prefWidth
        // has been set.
        Label label = (Label)root.getChildren().get(0);
        assertEquals(123, label.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_Parent_Property_By_Index() {
        BindingPathTest.TestPane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
            <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                      prefWidth="123">
                <Pane>
                    <Label prefWidth="{fx:bind parent[2]/prefWidth}"/>
                </Pane>
            </TestPane>
        """);

        Label label = (Label)((Pane)root.getChildren().get(0)).getChildren().get(0);
        assertEquals(123, label.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_Parent_Property_By_Type_And_Index() {
        BindingPathTest.TestPane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
            <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                      prefWidth="123">
                <Pane>
                    <Label prefWidth="{fx:bind parent[Pane:2]/prefWidth}"/>
                </Pane>
            </TestPane>
        """);

        Label label = (Label)((Pane)root.getChildren().get(0)).getChildren().get(0);
        assertEquals(123, label.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_Parent_Property_By_Default_Index() {
        BindingPathTest.TestPane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
            <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                      prefWidth="123">
                <Label prefWidth="{fx:bind parent/prefWidth}"/>
            </TestPane>
        """);

        Label label = (Label)root.getChildren().get(0);
        assertEquals(123, label.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_Parent0_Refers_To_Current_Node() {
        BindingPathTest.TestPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
            <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                  <Pane prefWidth="123" prefHeight="{fx:bind parent[0]/prefWidth}"/>
            </TestPane>
        """);

        Pane pane = (Pane)root.getChildren().get(0);
        assertEquals(123, pane.getPrefWidth(), 0.001);
        assertEquals(123, pane.getPrefHeight(), 0.001);
    }

    @Test
    public void Bind_To_Invalid_Parent_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
            <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                      prefWidth="123">
                <Label prefWidth="{fx:once parent[Button]/prefWidth}"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.PARENT_TYPE_NOT_FOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_To_Parent_Index_Out_Of_Bounds_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
            <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Label prefWidth="{fx:once parent[-1]/prefWidth}"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.PARENT_INDEX_OUT_OF_BOUNDS, ex.getDiagnostic().getCode());

        ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
            <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Label prefWidth="{fx:bind parent[2]/prefWidth}"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.PARENT_INDEX_OUT_OF_BOUNDS, ex.getDiagnostic().getCode());
    }

    @Test
    public void Parent_Of_Non_Node_Is_Correctly_Determined() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.geometry.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml" prefWidth="123">
                <rotationAxis>
                    <Point3D x="{fx:once parent/prefWidth}" y="0" z="0"/>
                </rotationAxis>
            </Pane>
        """);

        assertEquals(123, root.getRotationAxis().getX(), 0.001);
    }

    @Test
    public void Parent_With_Invalid_Identifier_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Label text="{fx:bind parent/0123}"/>
            </Pane>
        """));

        assertEquals(ErrorCode.EXPECTED_IDENTIFIER, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Parent_With_This_Syntax() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Button graphic="{fx:once parent/this}"/>
            </Pane>
        """);

        assertSame(root, ((Button)root.getChildren().get(0)).getGraphic());
    }

}

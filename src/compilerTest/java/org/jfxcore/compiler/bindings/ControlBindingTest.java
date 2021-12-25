// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
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
public class ControlBindingTest extends CompilerTestBase {

    @Test
    public void Bind_Once_To_IncompatibleType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Button fx:id="btn" text="foo"/>
                <Label prefWidth="{fx:once btn.text}"/>
            </Pane>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("{fx:once btn.text}", ex);
    }

    @Test
    public void Bind_Once_To_Local_Control() {
        Pane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Button fx:id="btn" text="foo"/>
                <Label text="{fx:once btn.text}"/>
                <Label>
                    <text>
                        <fx:once path="btn.text"/>
                    </text>
                </Label>
            </Pane>
        """);

        assertFalse(((Label)root.getChildren().get(1)).textProperty().isBound());
        assertEquals("foo", ((Label)root.getChildren().get(1)).getText());

        assertFalse(((Label)root.getChildren().get(2)).textProperty().isBound());
        assertEquals("foo", ((Label)root.getChildren().get(2)).getText());
    }

    @Test
    public void Bind_Once_To_Local_Control_Cat2Type() {
        Pane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Button fx:id="btn" prefWidth="123"/>
                <Label prefWidth="{fx:once btn.prefWidth}"/>
            </Pane>
        """);

        assertFalse(((Label)root.getChildren().get(1)).prefWidthProperty().isBound());
        assertEquals(123, ((Label)root.getChildren().get(1)).getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Once_To_Same_Control() {
        Pane root = compileAndRun("""
            <?import javafx.scene.shape.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Rectangle fill="red" stroke="{fx:once parent[0]/fill}"/>
            </Pane>
        """);

        Rectangle rect = (Rectangle)root.getChildren().get(0);
        assertEquals(rect.getFill(), rect.getStroke());
    }

    @SuppressWarnings("unused")
    public static final class NonNode {
        private final DoubleProperty prop1 = new SimpleDoubleProperty();
        private final DoubleProperty prop2 = new SimpleDoubleProperty();
        public DoubleProperty prop1Property() { return prop1; }
        public DoubleProperty prop2Property() { return prop2; }
    }

    @Test
    public void Bind_Once_To_NonNode() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <properties>
                    <NonNode fx:id="prop" prop1="123.0" prop2="{fx:once parent[0]/prop1}"/>
                </properties>
            </Pane>
        """);

        NonNode child = (NonNode)root.getProperties().get("prop");
        assertEquals(child.prop1.get(), child.prop2.get(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_IncompatibleType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Button fx:id="btn" text="foo"/>
                <Label prefWidth="{fx:bind btn.text}"/>
            </Pane>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("{fx:bind btn.text}", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Multiple_Local_Controls() {
        Pane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                  prefWidth="{fx:bind btn.prefWidth}">
                <Pane fx:id="pane" prefWidth="{fx:bind prefWidth}">
                    <Pane prefWidth="{fx:bind pane.prefWidth}">
                        <Button fx:id="btn" prefWidth="123"/>
                    </Pane>
                </Pane>
            </Pane>
        """);

        assertEquals(123.0, root.getPrefWidth(), 0.001);
        assertEquals(123.0, ((Pane)root.getChildren().get(0)).getPrefWidth(), 0.001);
        assertEquals(123.0, ((Pane)((Pane)root.getChildren().get(0)).getChildren().get(0)).getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Bidirectional_To_IncompatibleType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Button fx:id="btn" text="foo"/>
                <Label prefWidth="{fx:sync btn.text}"/>
            </Pane>
        """));

        assertEquals(ErrorCode.SOURCE_TYPE_MISMATCH, ex.getDiagnostic().getCode());
        assertCodeHighlight("{fx:sync btn.text}", ex);
    }

    @Test
    public void Bind_Bidirectional_To_Local_Control() {
        Pane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Label fx:id="lbl" text="foo"/>
                <Label text="{fx:sync lbl.text}"/>
            </Pane>
        """);

        Label label0 = (Label)root.getChildren().get(1);
        Label label1 = (Label)root.getChildren().get(1);

        assertEquals("foo", label1.getText());
        label1.setText("bar");
        assertEquals("bar", label0.getText());
        label0.setText("baz");
        assertEquals("baz", label1.getText());
    }

    @Test
    public void Bind_Bidirectional_To_Multiple_Local_Controls() {
        Pane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                  prefWidth="{fx:sync btn.prefWidth}">
                <Pane fx:id="pane" prefWidth="{fx:sync prefWidth}">
                    <Pane prefWidth="{fx:sync pane.prefWidth}">
                        <Button fx:id="btn" prefWidth="123"/>
                    </Pane>
                </Pane>
            </Pane>
        """);

        assertEquals(123.0, root.getPrefWidth(), 0.001);
        assertEquals(123.0, ((Pane)root.getChildren().get(0)).getPrefWidth(), 0.001);
        assertEquals(123.0, ((Pane)((Pane)root.getChildren().get(0)).getChildren().get(0)).getPrefWidth(), 0.001);
    }

}

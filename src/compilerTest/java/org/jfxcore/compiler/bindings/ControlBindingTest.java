// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import javafx.beans.NamedArg;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.Reflection;
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
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
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
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
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
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
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
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Rectangle fill="red" stroke="{fx:once self/fill}"/>
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
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <properties>
                    <NonNode fx:id="prop" prop1="123.0" prop2="{fx:once self/prop1}"/>
                </properties>
            </Pane>
        """);

        NonNode child = (NonNode)root.getProperties().get("prop");
        assertEquals(child.prop1.get(), child.prop2.get(), 0.001);
    }

    @SuppressWarnings("unused")
    public static class NodeUnderInitialization extends Pane {
        public NodeUnderInitialization(@NamedArg("arg1") double param) { test.set(param); }
        public NodeUnderInitialization(@NamedArg("arg2") Node param) { getChildren().add(param); }

        private final DoubleProperty test = new SimpleDoubleProperty(this, "test");
        public DoubleProperty testProperty() { return test; }

        public static double function(double value) { return value; }
    }

    @Test
    public void Bind_Once_To_Node_Under_Initialization_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <NodeUnderInitialization arg1="{fx:once self/test}"/>
            </Pane>
        """));

        assertEquals(ErrorCode.CANNOT_REFERENCE_NODE_UNDER_INITIALIZATION, ex.getDiagnostic().getCode());
        assertCodeHighlight("{fx:once self/test}", ex);
    }

    @Test
    public void Bind_Once_To_Node_Under_Initialization_In_FunctionExpression_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <NodeUnderInitialization arg1="{fx:once NodeUnderInitialization.function(self/test)}"/>
            </Pane>
        """));

        assertEquals(ErrorCode.CANNOT_REFERENCE_NODE_UNDER_INITIALIZATION, ex.getDiagnostic().getCode());
        assertCodeHighlight("{fx:once NodeUnderInitialization.function(self/test)}", ex);
    }

    @Test
    public void Bind_Once_To_Parent_Node_Under_Initialization_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <NodeUnderInitialization>
                    <arg2>
                        <NodeUnderInitialization arg1="{fx:once parent[0]/test}"/>
                    </arg2>
                </NodeUnderInitialization>
            </Pane>
        """));

        assertEquals(ErrorCode.CANNOT_REFERENCE_NODE_UNDER_INITIALIZATION, ex.getDiagnostic().getCode());
        assertCodeHighlight("{fx:once parent[0]/test}", ex);
    }

    @Test
    public void Bind_Once_To_Nested_Parent_Node_Under_Initialization_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <NodeUnderInitialization>
                    <arg2>
                        <NodeUnderInitialization>
                            <arg2>
                                <NodeUnderInitialization arg1="{fx:once parent[1]/test}"/>
                            </arg2>
                        </NodeUnderInitialization>
                    </arg2>
                </NodeUnderInitialization>
            </Pane>
        """));

        assertEquals(ErrorCode.CANNOT_REFERENCE_NODE_UNDER_INITIALIZATION, ex.getDiagnostic().getCode());
        assertCodeHighlight("{fx:once parent[1]/test}", ex);
    }

    @Test
    public void Bind_Property_Once_To_Parent_Node_Under_Initialization_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <NodeUnderInitialization>
                    <arg2>
                        <Pane prefWidth="{fx:once parent[0]/test}"/>
                    </arg2>
                </NodeUnderInitialization>
            </Pane>
        """));

        assertEquals(ErrorCode.CANNOT_REFERENCE_NODE_UNDER_INITIALIZATION, ex.getDiagnostic().getCode());
        assertCodeHighlight("prefWidth=\"{fx:once parent[0]/test}\"", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Parent_Works_When_GrandParent_Is_Under_Initialization() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <NodeUnderInitialization>
                    <arg2>
                        <NodeUnderInitialization arg1="123">
                            <Pane fx:id="testNode" prefWidth="{fx:bind parent[0]/test}"/>
                        </NodeUnderInitialization>
                    </arg2>
                </NodeUnderInitialization>
            </Pane>
        """);

        assertMethodCall(root, methods -> methods.stream().anyMatch(method -> method.getName().equals("bind")));
        assertEquals(123.0, Reflection.<Pane>getFieldValue(root, "testNode").getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_IncompatibleType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
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
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
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
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Button fx:id="btn" text="foo"/>
                <Label prefWidth="{fx:bindBidirectional btn.text}"/>
            </Pane>
        """));

        assertEquals(ErrorCode.SOURCE_TYPE_MISMATCH, ex.getDiagnostic().getCode());
        assertCodeHighlight("{fx:bindBidirectional btn.text}", ex);
    }

    @Test
    public void Bind_Bidirectional_To_Local_Control() {
        Pane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Label fx:id="lbl" text="foo"/>
                <Label text="{fx:bindBidirectional lbl.text}"/>
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
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                  prefWidth="{fx:bindBidirectional btn.prefWidth}">
                <Pane fx:id="pane" prefWidth="{fx:bindBidirectional prefWidth}">
                    <Pane prefWidth="{fx:bindBidirectional pane.prefWidth}">
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

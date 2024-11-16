// Copyright (c) 2021, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import javafx.beans.NamedArg;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
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
                <Label prefWidth="$btn.text"/>
            </Pane>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("$btn.text", ex);
    }

    @Test
    public void Bind_Once_To_Local_Control() {
        Pane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Button fx:id="btn" text="foo"/>
                <Label text="$btn.text"/>
            </Pane>
        """);

        assertFalse(((Label)root.getChildren().get(1)).textProperty().isBound());
        assertEquals("foo", ((Label)root.getChildren().get(1)).getText());
    }

    @Test
    public void Bind_Once_To_Local_Control_Cat2Type() {
        Pane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Button fx:id="btn" prefWidth="123"/>
                <Label prefWidth="$btn.prefWidth"/>
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
                <Rectangle fill="red" stroke="$self/fill"/>
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
                    <NonNode fx:id="prop" prop1="123.0" prop2="$self/prop1"/>
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
                <NodeUnderInitialization arg1="$self/test"/>
            </Pane>
        """));

        assertEquals(ErrorCode.CANNOT_REFERENCE_NODE_UNDER_INITIALIZATION, ex.getDiagnostic().getCode());
        assertCodeHighlight("$self/test", ex);
    }

    @Test
    public void Bind_Once_To_Node_Under_Initialization_In_FunctionExpression_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <NodeUnderInitialization arg1="$NodeUnderInitialization.function(self/test)"/>
            </Pane>
        """));

        assertEquals(ErrorCode.CANNOT_REFERENCE_NODE_UNDER_INITIALIZATION, ex.getDiagnostic().getCode());
        assertCodeHighlight("$NodeUnderInitialization.function(self/test)", ex);
    }

    @Test
    public void Bind_Once_To_Parent_Node_Under_Initialization_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <NodeUnderInitialization>
                    <arg2>
                        <NodeUnderInitialization arg1="$parent[0]/test"/>
                    </arg2>
                </NodeUnderInitialization>
            </Pane>
        """));

        assertEquals(ErrorCode.CANNOT_REFERENCE_NODE_UNDER_INITIALIZATION, ex.getDiagnostic().getCode());
        assertCodeHighlight("$parent[0]/test", ex);
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
                                <NodeUnderInitialization arg1="$parent[1]/test"/>
                            </arg2>
                        </NodeUnderInitialization>
                    </arg2>
                </NodeUnderInitialization>
            </Pane>
        """));

        assertEquals(ErrorCode.CANNOT_REFERENCE_NODE_UNDER_INITIALIZATION, ex.getDiagnostic().getCode());
        assertCodeHighlight("$parent[1]/test", ex);
    }

    @Test
    public void Bind_Property_Once_To_Parent_Node_Under_Initialization_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <NodeUnderInitialization>
                    <arg2>
                        <Pane prefWidth="$parent[0]/test"/>
                    </arg2>
                </NodeUnderInitialization>
            </Pane>
        """));

        assertEquals(ErrorCode.CANNOT_REFERENCE_NODE_UNDER_INITIALIZATION, ex.getDiagnostic().getCode());
        assertCodeHighlight("prefWidth=\"$parent[0]/test\"", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Parent_Works_When_GrandParent_Is_Under_Initialization() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <NodeUnderInitialization>
                    <arg2>
                        <NodeUnderInitialization arg1="123">
                            <Pane fx:id="testNode" prefWidth="${parent[0]/test}"/>
                        </NodeUnderInitialization>
                    </arg2>
                </NodeUnderInitialization>
            </Pane>
        """);

        assertMethodCall(root, methods -> methods.stream().anyMatch(method -> method.getName().equals("bind")));
        assertEquals(123.0, Reflection.<Pane>getFieldValue(root, "testNode").getPrefWidth(), 0.001);
    }

    @SuppressWarnings("unused")
    public static class NodeUnderInitializationWithAlternativeProperty extends Pane {
        public NodeUnderInitializationWithAlternativeProperty() {}
        public NodeUnderInitializationWithAlternativeProperty(@NamedArg("content") Node content) {}

        private final ObjectProperty<Node> content = new SimpleObjectProperty<>(this, "content");
        public ObjectProperty<Node> contentProperty() { return content; }
    }

    @Test
    public void Bind_Once_To_Parent_Works_When_Alternative_Property_Is_Available() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <NodeUnderInitializationWithAlternativeProperty>
                    <content>
                        <Pane prefWidth="${parent[0]/prefHeight}"/>
                    </content>
                </NodeUnderInitializationWithAlternativeProperty>
            </Pane>
        """);

        assertMethodCall(root, methods -> methods.stream().anyMatch(m -> m.getName().equals("prefWidthProperty")));
        assertMethodCall(root, methods -> methods.stream().anyMatch(m -> m.getName().equals("prefHeightProperty")));
        assertNewExpr(root, ctors -> ctors.stream()
            .anyMatch(c -> c.getLongName().equals(NodeUnderInitializationWithAlternativeProperty.class.getName() + "()")));
    }

    @Test
    public void Bind_Unidirectional_To_IncompatibleType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Button fx:id="btn" text="foo"/>
                <Label prefWidth="${btn.text}"/>
            </Pane>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("${btn.text}", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Multiple_Local_Controls() {
        Pane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                  prefWidth="${btn.prefWidth}">
                <Pane fx:id="pane" prefWidth="${prefWidth}">
                    <Pane prefWidth="${pane.prefWidth}">
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
                <Label prefWidth="#{btn.text}"/>
            </Pane>
        """));

        assertEquals(ErrorCode.SOURCE_TYPE_MISMATCH, ex.getDiagnostic().getCode());
        assertCodeHighlight("#{btn.text}", ex);
    }

    @Test
    public void Bind_Bidirectional_To_Local_Control() {
        Pane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Label fx:id="lbl" text="foo"/>
                <Label text="#{lbl.text}"/>
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
                  prefWidth="#{btn.prefWidth}">
                <Pane fx:id="pane" prefWidth="#{prefWidth}">
                    <Pane prefWidth="#{pane.prefWidth}">
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

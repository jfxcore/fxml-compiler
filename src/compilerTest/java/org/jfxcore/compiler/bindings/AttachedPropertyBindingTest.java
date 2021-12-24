// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"HttpUrlsUsage", "DuplicatedCode"})
@ExtendWith(TestExtension.class)
public class AttachedPropertyBindingTest extends CompilerTestBase {

    public static class TextSource {
        public static StringProperty textProperty(Node node) {
            StringProperty property = (StringProperty)node.getProperties().get("text");
            if (property == null) {
                property = new SimpleStringProperty(node, "text");
                node.getProperties().put("text", property);
            }

            return property;
        }

        public static String getText(Node node) { return textProperty(node).get(); }
        public static void setText(Node node, String value) { textProperty(node).set(value); }
    }

    @Test
    public void Bind_Once_To_Attached_StringProperty() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Pane fx:id="pane" TextSource.text="foo"/>
                <Label text="{fx:once pane.(TextSource.text)}"/>
            </Pane>
        """);

        Label label = (Label)root.getChildren().get(1);
        assertEquals("foo", label.getText());
    }

    @Test
    public void Bind_Once_To_Fully_Qualified_Attached_StringProperty() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Pane fx:id="pane" TextSource.text="foo"/>
                <Label text="{fx:once pane.(org.jfxcore.compiler.bindings.AttachedPropertyBindingTest.TextSource.text)}"/>
            </Pane>
        """);

        Label label = (Label)root.getChildren().get(1);
        assertEquals("foo", label.getText());
    }

    @Test
    public void Bind_Once_To_Partially_Qualified_Attached_StringProperty() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.compiler.bindings.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Pane fx:id="pane" TextSource.text="foo"/>
                <Label text="{fx:once pane.(AttachedPropertyBindingTest.TextSource.text)}"/>
            </Pane>
        """);

        Label label = (Label)root.getChildren().get(1);
        assertEquals("foo", label.getText());
    }

    @Test
    public void Bind_Unidirectional_To_Attached_StringProperty() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Pane fx:id="pane"/>
                <Label text="{fx:bind pane.(TextSource.text)}"/>
            </Pane>
        """);

        Pane pane = (Pane)root.getChildren().get(0);
        Label label = (Label)root.getChildren().get(1);
        assertNull(label.getText());

        TextSource.setText(pane, "foo");
        assertEquals("foo", label.getText());

        TextSource.setText(pane, "bar");
        assertEquals("bar", label.getText());
    }

    @Test
    public void Bind_Bidirectional_To_Attached_StringProperty() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Pane fx:id="pane" TextSource.text="foo"/>
                <Label text="{fx:sync pane.(TextSource.text)}"/>
            </Pane>
        """);

        Pane pane = (Pane)root.getChildren().get(0);
        Label label = (Label)root.getChildren().get(1);
        assertEquals("foo", label.getText());

        TextSource.setText(pane, "bar");
        assertEquals("bar", label.getText());

        label.setText("baz");
        assertEquals("baz", TextSource.getText(pane));
    }

    @Test
    public void Bind_Observable_AttachedProperty_To_ObservableValue() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Label fx:id="lbl" text="foo"/>
                <Pane TextSource.text="{fx:bind lbl.text}"/>
            </Pane>
        """);

        Label label = (Label)root.getChildren().get(0);
        Pane pane = (Pane)root.getChildren().get(1);
        assertEquals("foo", TextSource.getText(pane));

        label.setText("bar");
        assertEquals("bar", TextSource.getText(pane));

        label.setText("baz");
        assertEquals("baz", TextSource.getText(pane));
    }

    @Test
    public void Bind_Fully_Qualified_Observable_AttachedProperty_To_ObservableValue() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Label fx:id="lbl" text="foo"/>
                <Pane org.jfxcore.compiler.bindings.AttachedPropertyBindingTest.TextSource.text="{fx:bind lbl.text}"/>
            </Pane>
        """);

        Label label = (Label)root.getChildren().get(0);
        Pane pane = (Pane)root.getChildren().get(1);
        assertEquals("foo", TextSource.getText(pane));

        label.setText("bar");
        assertEquals("bar", TextSource.getText(pane));

        label.setText("baz");
        assertEquals("baz", TextSource.getText(pane));
    }

    @Test
    public void BindBidirectional_Observable_AttachedProperty_To_StringProperty() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Label fx:id="lbl" text="foo"/>
                <Pane TextSource.text="{fx:sync lbl.text}"/>
            </Pane>
        """);

        Label label = (Label)root.getChildren().get(0);
        Pane pane = (Pane)root.getChildren().get(1);
        assertEquals("foo", TextSource.getText(pane));

        TextSource.setText(pane, "bar");
        assertEquals("bar", label.getText());

        label.setText("baz");
        assertEquals("baz", TextSource.getText(pane));
    }

    @Test
    public void Nonexistent_Source_AttachedProperty_Cannot_Be_Resolved() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Pane fx:id="pane" TextSource.text="foo"/>
                <Label text="{fx:once pane.(TextSource.nonexistent)}"/>
            </Pane>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void Nonexistent_Target_AttachedProperty_Cannot_Be_Resolved() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Label fx:id="lbl" text="foo"/>
                <Pane TextSource.nonexistent="{fx:bind lbl.text}"/>
            </Pane>
        """));

        assertEquals(ErrorCode.PROPERTY_NOT_FOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void Nested_AttachedProperty_Path_Is_Not_Allowed() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Pane fx:id="pane"/>
                <Label text="{fx:once pane.(TextSource.(TextSource.text))}"/>
            </Pane>
        """));

        assertEquals(ErrorCode.INVALID_EXPRESSION, ex.getDiagnostic().getCode());
    }

    @SuppressWarnings("unused")
    public static class LabelSource {
        private static Label value;
        public static Label getLabel(Node node) { return (Label)node.getProperties().get("label"); }
        public static void setLabel(Node node, Label value) { node.getProperties().put("label", value); }
    }

    @SuppressWarnings("unused")
    public static class ObservableLabelSource {
        public static ObjectProperty<Label> labelProperty(Node node) {
            @SuppressWarnings("unchecked")
            ObjectProperty<Label> property = (ObjectProperty<Label>)node.getProperties().get("label");
            if (property == null) {
                property = new SimpleObjectProperty<>(node, "label");
                node.getProperties().put("label", property);
            }

            return property;
        }

        public static Label getLabel(Node node) { return labelProperty(node).get(); }
        public static void setLabel(Node node, Label value) { labelProperty(node).set(value); }
    }

    @Test
    public void Bind_Unidirectional_To_Chained_Attached_Property() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Pane fx:id="pane">
                    <LabelSource.label>
                        <Label text="foo"/>
                    </LabelSource.label>
                </Pane>
                <Label text="{fx:bind pane.(LabelSource.label).text}"/>
            </Pane>
        """);

        Pane pane = (Pane)root.getChildren().get(0);
        Label label = (Label)root.getChildren().get(1);
        assertEquals("foo", label.getText());

        LabelSource.getLabel(pane).setText("bar");
        assertEquals("bar", label.getText());
    }

    @Test
    public void Bind_Unidirectional_To_Chained_Observable_Attached_Property() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <Pane fx:id="pane">
                    <ObservableLabelSource.label>
                        <Label text="foo"/>
                    </ObservableLabelSource.label>
                </Pane>
                <Label text="{fx:bind pane.(ObservableLabelSource.label).text}"/>
            </Pane>
        """);

        Pane pane = (Pane)root.getChildren().get(0);
        Label label = (Label)root.getChildren().get(1);
        assertEquals("foo", label.getText());

        ObservableLabelSource.getLabel(pane).setText("bar");
        assertEquals("bar", label.getText());

        ObservableLabelSource.setLabel(pane, new Label("baz"));
        assertEquals("baz", label.getText());
    }

}

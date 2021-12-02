// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.Supplier;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.jfxcore.compiler.util.TestCompiler;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"HttpUrlsUsage", "DuplicatedCode"})
@ExtendWith(TestExtension.class)
public class CoercionTest {

    @Test
    public void AttributeValue_Is_Coerced_To_String() {
        Button root = TestCompiler.newInstance(this, "AttributeValue_Is_Coerced_To_String", """
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        text="Hello!"/>
            """);
        assertEquals("Hello!", root.getText());
    }

    @Test
    public void AttributeValue_Is_Coerced_To_String_And_Preserves_Whitespace() {
        Button root = TestCompiler.newInstance(this, "AttributeValue_Is_Coerced_To_String_And_Preserves_Whitespace", """
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        text="  Hello!  "/>
            """);
        assertEquals("  Hello!  ", root.getText());
    }

    @Test
    public void AttributeValue_Is_Coerced_To_Number() {
        Button root = TestCompiler.newInstance(this, "AttributeValue_Is_Coerced_To_Number", """
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        prefWidth="123.5"/>
            """);
        assertEquals(123.5, root.getPrefWidth(), 0.001);
    }

    @Test
    public void AttributeValue_Is_Coerced_To_InfiniteDouble() {
        Button root = TestCompiler.newInstance(this, "AttributeValue_Is_Coerced_To_InfiniteDouble", """
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        prefWidth="-Infinity"/>
            """);
        assertEquals(Double.NEGATIVE_INFINITY, root.getPrefWidth(), 0.001);
    }

    @Test
    public void AttributeValue_Is_Coerced_To_Boolean() {
        Button root = TestCompiler.newInstance(this, "AttributeValue_Is_Coerced_To_Boolean", """
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        managed="true" visible="false"/>
            """);
        assertTrue(root.isManaged());
        assertFalse(root.isVisible());
    }

    @Test
    public void AttributeValue_Is_Coerced_To_Enum() {
        GridPane root = TestCompiler.newInstance(this, "AttributeValue_Is_Coerced_To_Enum", """
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          alignment="CENTER"/>
            """);
        assertEquals(Pos.CENTER, root.getAlignment());
    }

    @Test
    public void AttributeValue_Of_Chained_Property_Is_Coerced_To_Enum() {
        ListView<?> root = TestCompiler.newInstance(this, "AttributeValue_Of_Chained_Property_Is_Coerced_To_Enum", """
                <?import javafx.scene.control.*?>
                <ListView xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                    selectionModel.selectionMode="MULTIPLE"/>
            """);
        assertEquals(SelectionMode.MULTIPLE, root.getSelectionModel().getSelectionMode());
    }

    @Test
    public void AttributeValue_Is_Coerced_To_Static_Field_Of_TargetType() {
        GridPane root = TestCompiler.newInstance(this, "AttributeValue_Is_Coerced_To_Static_Field_Of_TargetType", """
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefWidth="POSITIVE_INFINITY"/>
            """);

        assertEquals(Double.POSITIVE_INFINITY, root.getPrefWidth());
    }

    @SuppressWarnings("unused")
    public static class StaticFieldClass<T> extends GridPane {
        public static final Supplier<String> SUPPLIER = () -> null;
        private Supplier<T> supplier;
        public Supplier<T> getSupplier() { return supplier; }
        public void setSupplier(Supplier<T> supplier) { this.supplier = supplier; }
    }

    @Test
    public void AttributeValue_Is_Coerced_To_Static_Field_Of_Raw_GenericClass() {
        GridPane root = TestCompiler.newInstance(
            this, "AttributeValue_Is_Coerced_To_Static_Field_Of_Raw_GenericClass", """
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.CoercionTest.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <StaticFieldClass supplier="SUPPLIER"/>
                </GridPane>
            """);

        assertSame(StaticFieldClass.SUPPLIER, ((StaticFieldClass<?>)root.getChildren().get(0)).getSupplier());
    }

    @Test
    public void AttributeValue_Is_Coerced_To_Static_Field_Of_Typed_GenericClass() {
        GridPane root = TestCompiler.newInstance(
            this, "AttributeValue_Is_Coerced_To_Static_Field_Of_Typed_GenericClass", """
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.CoercionTest.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <StaticFieldClass fx:typeArguments="String" supplier="SUPPLIER"/>
                </GridPane>
            """);

        assertSame(StaticFieldClass.SUPPLIER, ((StaticFieldClass<?>)root.getChildren().get(0)).getSupplier());
    }

    @Test
    public void AttributeValue_Cannot_Be_Coerced_To_Static_Field_Of_Incompatibly_Typed_GenericClass() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "AttributeValue_Cannot_Be_Coerced_To_Static_Field_Of_Incompatibly_Typed_GenericClass", """
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.CoercionTest.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <StaticFieldClass fx:typeArguments="Boolean" supplier="SUPPLIER"/>
                </GridPane>
            """));

        assertEquals(ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, ex.getDiagnostic().getCode());
    }

    @Test
    public void AttributeValue_Is_Coerced_To_Insets() {
        GridPane root = TestCompiler.newInstance(this, "AttributeValue_Is_Coerced_To_Insets", """
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml" padding="1"/>
            """);

        assertEquals(1, root.getPadding().getLeft(), 0.001);
        assertEquals(1, root.getPadding().getTop(), 0.001);
        assertEquals(1, root.getPadding().getRight(), 0.001);
        assertEquals(1, root.getPadding().getBottom(), 0.001);
    }

    @Test
    public void AttributeValue_Comma_Separated_List_Is_Coerced_To_Insets() {
        GridPane root = TestCompiler.newInstance(this, "AttributeValue_Comma_Separated_List_Is_Coerced_To_Insets", """
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml" padding="1,2,3,4"/>
            """);

        assertEquals(1, root.getPadding().getTop(), 0.001);
        assertEquals(2, root.getPadding().getRight(), 0.001);
        assertEquals(3, root.getPadding().getBottom(), 0.001);
        assertEquals(4, root.getPadding().getLeft(), 0.001);
    }

    @Test
    public void ElementValue_Is_Coerced_To_String() {
        Button root = TestCompiler.newInstance(this, "ElementValue_Is_Coerced_To_String", """
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <text>Hello!</text>
                </Button>
            """);
        assertEquals("Hello!", root.getText());
    }

    @Test
    public void ElementValue_Is_Coerced_To_String_And_Removes_Insinificant_Whitespace() {
        Button root = TestCompiler.newInstance(this, "ElementValue_Is_Coerced_To_String_And_Removes_Insignificant_Whitespace", """
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <text>
                        foo
                            bar
                        baz
                    </text>
                </Button>
            """);
        assertEquals("foo\n    bar\nbaz", root.getText());
    }

    @Test
    public void ElementValue_Is_Coerced_To_Number() {
        Button root = TestCompiler.newInstance(this, "ElementValue_Is_Coerced_To_Number", """
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <prefWidth>123.5</prefWidth>
                </Button>
            """);
        assertEquals(123.5, root.getPrefWidth(), 0.001);
    }

    @Test
    public void ElementValue_Is_Coerced_To_InfiniteDouble() {
        Button root = TestCompiler.newInstance(this, "ElementValue_Is_Coerced_To_InfiniteDouble", """
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <prefWidth>-Infinity</prefWidth>
                </Button>
            """);
        assertEquals(Double.NEGATIVE_INFINITY, root.getPrefWidth(), 0.001);
    }

    @Test
    public void ElementValue_Is_Coerced_To_Boolean() {
        Button root = TestCompiler.newInstance(this, "ElementValue_Is_Coerced_To_Boolean", """
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <managed>true</managed>
                    <visible>false</visible>
                </Button>
            """);
        assertTrue(root.isManaged());
        assertFalse(root.isVisible());
    }

    @Test
    public void ElementValue_Is_Coerced_To_Enum() {
        GridPane root = TestCompiler.newInstance(this, "ElementValue_Is_Coerced_To_Enum", """
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <alignment>
                        CENTER
                    </alignment>
                </GridPane>
            """);
        assertEquals(Pos.CENTER, root.getAlignment());
    }

    @Test
    public void ElementValue_Of_Chained_Property_Is_Coerced_To_Enum() {
        ListView<?> root = TestCompiler.newInstance(this, "ElementValue_Of_Chained_Property_Is_Coerced_To_Enum", """
                <?import javafx.scene.control.*?>
                <ListView xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <selectionModel.selectionMode>MULTIPLE</selectionModel.selectionMode>
                </ListView>
            """);
        assertEquals(SelectionMode.MULTIPLE, root.getSelectionModel().getSelectionMode());
    }

    @Test
    public void Comma_Separated_String_Is_Coerced_To_Collection() {
        GridPane root = TestCompiler.newInstance(this, "Comma_Separated_String_Is_Coerced_To_Collection", """
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          styleClass="style1, style2, style3"/>
            """);
        assertTrue(root.getStyleClass().contains("style1"));
        assertTrue(root.getStyleClass().contains("style2"));
        assertTrue(root.getStyleClass().contains("style3"));
    }

    @Test
    public void Comma_Separated_String_Is_Coerced_In_Collection_Initializer() {
        GridPane root = TestCompiler.newInstance(this, "Comma_Separated_String_Is_Coerced_In_Collection_Initializer", """
                <?import java.util.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <fx:define>
                        <ArrayList fx:id="list">
                            style1, style2, style3
                        </ArrayList>
                    </fx:define>
                </GridPane>
            """);

        List<?> list = (List<?>)root.getProperties().get("list");
        assertTrue(list.contains("style1"));
        assertTrue(list.contains("style2"));
        assertTrue(list.contains("style3"));
    }

    @Test
    public void Comma_Separated_String_Initializer_For_DoubleList_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () ->
            TestCompiler.newInstance(this, "Comma_Separated_String_Initializer_For_DoubleList_Fails", """
                <?import java.util.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <fx:define>
                        <ArrayList fx:id="list" fx:typeArguments="java.lang.Double">
                            style1, style2, style3
                        </ArrayList>
                    </fx:define>
                </GridPane>
            """));

        assertEquals(ErrorCode.CANNOT_ADD_ITEM_INCOMPATIBLE_TYPE, ex.getDiagnostic().getCode());
    }

    @Test
    public void StaticProperty_PropertyText_Is_Coerced_To_Insets() {
        VBox root = TestCompiler.newInstance(this, "StaticProperty_PropertyText_Is_Coerced_To_Insets", """
                <?import javafx.scene.layout.*?>
                <VBox xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <Pane VBox.margin="10"/>
                    <Pane VBox.margin="10, 20,30, 40"/>
                </VBox>
            """);

        assertEquals(2, root.getChildren().size());
        var margin = VBox.getMargin(root.getChildren().get(0));
        assertEquals(10, margin.getTop(), 0.001);
        assertEquals(10, margin.getRight(), 0.001);
        assertEquals(10, margin.getBottom(), 0.001);
        assertEquals(10, margin.getLeft(), 0.001);

        margin = VBox.getMargin(root.getChildren().get(1));
        assertEquals(10, margin.getTop(), 0.001);
        assertEquals(20, margin.getRight(), 0.001);
        assertEquals(30, margin.getBottom(), 0.001);
        assertEquals(40, margin.getLeft(), 0.001);
    }

    @Test
    public void StaticProperty_ElementText_Is_Coerced_To_Insets() {
        VBox root = TestCompiler.newInstance(this, "StaticProperty_ElementText_Is_Coerced_To_Insets", """
                <?import javafx.scene.layout.*?>
                <VBox xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <Pane><VBox.margin>10</VBox.margin></Pane>
                    <Pane><VBox.margin>10,20,30, 40</VBox.margin></Pane>
                </VBox>
            """);

        assertEquals(2, root.getChildren().size());
        var margin = VBox.getMargin(root.getChildren().get(0));
        assertEquals(10, margin.getTop(), 0.001);
        assertEquals(10, margin.getRight(), 0.001);
        assertEquals(10, margin.getBottom(), 0.001);
        assertEquals(10, margin.getLeft(), 0.001);

        margin = VBox.getMargin(root.getChildren().get(1));
        assertEquals(10, margin.getTop(), 0.001);
        assertEquals(20, margin.getRight(), 0.001);
        assertEquals(30, margin.getBottom(), 0.001);
        assertEquals(40, margin.getLeft(), 0.001);
    }

    @Test
    public void StaticProperty_PropertyText_Cannot_Be_Coerced_To_Insets() {
        MarkupException ex = assertThrows(MarkupException.class, () ->
            TestCompiler.newInstance(this, "StaticProperty_PropertyText_Cannot_Be_Coerced_To_Insets", """
                    <?import javafx.scene.layout.*?>
                    <VBox xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                        <Pane VBox.margin="10,10"/>
                    </VBox>
                """));

        assertEquals(ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, ex.getDiagnostic().getCode());
    }

    @Test
    public void StaticProperty_String_Cannot_Be_Assigned_To_Insets() {
        MarkupException ex = assertThrows(MarkupException.class, () ->
                TestCompiler.newInstance(this, "StaticProperty_String_Cannot_Be_Assigned_To_Insets", """
                    <?import java.lang.*?>
                    <?import javafx.scene.layout.*?>
                    <VBox xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                        <Pane>
                            <VBox.margin><String>10</String></VBox.margin>
                        </Pane>
                    </VBox>
                """));

        assertEquals(ErrorCode.INCOMPATIBLE_PROPERTY_TYPE, ex.getDiagnostic().getCode());
    }

    @Test
    public void NamedColor_Is_Coerced_To_Paint() {
        Button root = TestCompiler.newInstance(this, "NamedColor_Is_Coerced_To_Paint", """
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        textFill="red"/>
            """);
        assertSame(javafx.scene.paint.Color.RED, root.getTextFill());
    }

    @Test
    public void WebColor_Is_Coerced_To_Paint() {
        Button root = TestCompiler.newInstance(this, "WebColor_Is_Coerced_To_Paint", """
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        textFill="#12345678"/>
            """);
        assertEquals(javafx.scene.paint.Color.valueOf("12345678"), root.getTextFill());
    }

    @Test
    public void WebColor_Is_Coerced_To_Named_Color_Field() {
        Button root = TestCompiler.newInstance(this, "WebColor_Is_Coerced_To_Named_Color_Field", """
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        textFill="#ff0000"/>
            """);
        assertSame(javafx.scene.paint.Color.RED, root.getTextFill());
    }

}

// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.Supplier;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.List;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class PropertyAssignmentTest {

    @Nested
    public class BasicInvariantsTest extends CompilerTestBase {
        @Test
        public void Duplicate_AttributeProperty_And_ElementProperty_Is_Invalid() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <GridPane prefWidth="10">
                        <prefWidth>20</prefWidth>
                    </GridPane>
                </GridPane>
            """));

            assertEquals(ErrorCode.DUPLICATE_PROPERTY, ex.getDiagnostic().getCode());
            assertCodeHighlight("<prefWidth>20</prefWidth>", ex);
        }

        @Test
        public void Duplicate_AttributeProperty_Is_Invalid() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <GridPane prefWidth="10" prefWidth="20"/>
                </GridPane>
            """));

            assertEquals(ErrorCode.DUPLICATE_PROPERTY, ex.getDiagnostic().getCode());
            assertCodeHighlight("""
                prefWidth="20"
            """.trim(), ex);
        }

        @Test
        public void Duplicate_ElementProperty_Is_Invalid() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <GridPane>
                        <prefWidth>10</prefWidth>
                        <prefWidth>20</prefWidth>
                    </GridPane>
                </GridPane>
            """));

            assertEquals(ErrorCode.DUPLICATE_PROPERTY, ex.getDiagnostic().getCode());
            assertCodeHighlight("<prefWidth>20</prefWidth>", ex);
        }

        @Test
        public void Property_Without_Value_Is_Invalid() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <minHeight></minHeight>
                </Button>
            """));

            assertEquals(ErrorCode.PROPERTY_CANNOT_BE_EMPTY, ex.getDiagnostic().getCode());
            assertCodeHighlight("<minHeight></minHeight>", ex);
        }

        @Test
        public void Property_With_Multiple_Values_Is_Invalid() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <minHeight><Double fx:constant="NEGATIVE_INFINITY"/>5.0</minHeight>
                </Button>
            """));

            assertEquals(ErrorCode.PROPERTY_CANNOT_HAVE_MULTIPLE_VALUES, ex.getDiagnostic().getCode());
            assertCodeHighlight("""
                <minHeight><Double fx:constant="NEGATIVE_INFINITY"/>5.0</minHeight>
            """.trim(), ex);
        }
    }

    @Nested
    public class CoercionTest extends CompilerTestBase {
        @Test
        public void AttributeValue_Is_Coerced_To_String() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        text="Hello!"/>
            """);

            assertEquals("Hello!", root.getText());
        }

        @Test
        public void AttributeValue_Is_Coerced_To_String_And_Preserves_Whitespace() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        text="  Hello!  "/>
            """);

            assertEquals("  Hello!  ", root.getText());
        }

        @Test
        public void AttributeValue_Is_Coerced_To_Number() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        prefWidth="123.5"/>
            """);

            assertEquals(123.5, root.getPrefWidth(), 0.001);
        }

        @Test
        public void AttributeValue_Is_Coerced_To_InfiniteDouble() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        prefWidth="-Infinity"/>
            """);

            assertEquals(Double.NEGATIVE_INFINITY, root.getPrefWidth(), 0.001);
        }

        @Test
        public void AttributeValue_Is_Coerced_To_Boolean() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        managed="true" visible="false"/>
            """);

            assertTrue(root.isManaged());
            assertFalse(root.isVisible());
        }

        @Test
        public void AttributeValue_Is_Coerced_To_Enum() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          alignment="CENTER"/>
            """);

            assertEquals(Pos.CENTER, root.getAlignment());
        }

        @Test
        public void AttributeValue_Of_Chained_Property_Is_Coerced_To_Enum() {
            ListView<?> root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <ListView xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                    selectionModel.selectionMode="MULTIPLE"/>
            """);

            assertEquals(SelectionMode.MULTIPLE, root.getSelectionModel().getSelectionMode());
        }

        @Test
        public void AttributeValue_Is_Coerced_To_Static_Field_Of_TargetType() {
            GridPane root = compileAndRun("""
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
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <StaticFieldClass supplier="SUPPLIER"/>
                </GridPane>
            """);

            assertSame(StaticFieldClass.SUPPLIER, ((StaticFieldClass<?>)root.getChildren().get(0)).getSupplier());
        }

        @Test
        public void AttributeValue_Is_Coerced_To_Static_Field_Of_Typed_GenericClass() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <StaticFieldClass fx:typeArguments="String" supplier="SUPPLIER"/>
                </GridPane>
            """);

            assertSame(StaticFieldClass.SUPPLIER, ((StaticFieldClass<?>)root.getChildren().get(0)).getSupplier());
        }

        @Test
        public void AttributeValue_Cannot_Be_Coerced_To_Static_Field_Of_Incompatibly_Typed_GenericClass() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <StaticFieldClass fx:typeArguments="Boolean" supplier="SUPPLIER"/>
                </GridPane>
            """));

            assertEquals(ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, ex.getDiagnostic().getCode());
            assertCodeHighlight("SUPPLIER", ex);
        }

        @Test
        public void AttributeValue_Is_Coerced_To_Insets() {
            GridPane root = compileAndRun("""
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
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml" padding="1,2,3,4"/>
            """);

            assertEquals(1, root.getPadding().getTop(), 0.001);
            assertEquals(2, root.getPadding().getRight(), 0.001);
            assertEquals(3, root.getPadding().getBottom(), 0.001);
            assertEquals(4, root.getPadding().getLeft(), 0.001);
        }

        @Test
        public void AttributeValue_Unmatchable_Coercion_Throws_Exception() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml" padding="1,2"/>
            """));

            assertEquals(ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, ex.getDiagnostic().getCode());
            assertCodeHighlight("1,2", ex);
        }

        @Test
        public void ElementValue_Is_Coerced_To_String() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <text>Hello!</text>
                </Button>
            """);

            assertEquals("Hello!", root.getText());
        }

        @Test
        public void ElementValue_Is_Coerced_To_String_And_Removes_Insinificant_Whitespace() {
            Button root = compileAndRun("""
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
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <prefWidth>123.5</prefWidth>
                </Button>
            """);

            assertEquals(123.5, root.getPrefWidth(), 0.001);
        }

        @Test
        public void ElementValue_Is_Coerced_To_InfiniteDouble() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <prefWidth>-Infinity</prefWidth>
                </Button>
            """);

            assertEquals(Double.NEGATIVE_INFINITY, root.getPrefWidth(), 0.001);
        }

        @Test
        public void ElementValue_Is_Coerced_To_Boolean() {
            Button root = compileAndRun("""
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
            GridPane root = compileAndRun("""
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
            ListView<?> root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <ListView xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <selectionModel.selectionMode>MULTIPLE</selectionModel.selectionMode>
                </ListView>
            """);

            assertEquals(SelectionMode.MULTIPLE, root.getSelectionModel().getSelectionMode());
        }

        @Test
        public void Comma_Separated_String_Is_Coerced_To_Collection() {
            GridPane root = compileAndRun("""
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
            GridPane root = compileAndRun("""
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
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
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
        public void StaticProperty_PropertyText_Is_Coerced_To_Integer() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                     <Pane GridPane.columnIndex="1"/>
                </GridPane>
            """);

            assertEquals(1, (int)GridPane.getColumnIndex(root.getChildren().get(0)));
        }

        @Test
        public void StaticProperty_PropertyText_Is_Coerced_To_Insets() {
            VBox root = compileAndRun("""
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
            VBox root = compileAndRun("""
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
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <VBox xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <Pane VBox.margin="10,10"/>
                </VBox>
            """));

            assertEquals(ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, ex.getDiagnostic().getCode());
            assertCodeHighlight("10,10", ex);
        }

        @Test
        public void StaticProperty_String_Cannot_Be_Assigned_To_Insets() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <VBox xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <Pane>
                        <VBox.margin><String>10</String></VBox.margin>
                    </Pane>
                </VBox>
            """));

            assertEquals(ErrorCode.INCOMPATIBLE_PROPERTY_TYPE, ex.getDiagnostic().getCode());
            assertCodeHighlight("<String>10</String>", ex);
        }

        @Test
        public void NamedColor_Is_Coerced_To_Paint() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        textFill="red"/>
            """);

            assertSame(javafx.scene.paint.Color.RED, root.getTextFill());
        }

        @Test
        public void WebColor_Is_Coerced_To_Paint() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        textFill="#12345678"/>
            """);

            assertEquals(javafx.scene.paint.Color.valueOf("12345678"), root.getTextFill());
        }

        @Test
        public void WebColor_Is_Coerced_To_Named_Color_Field() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        textFill="#ff0000"/>
            """);

            assertSame(javafx.scene.paint.Color.RED, root.getTextFill());
        }
    }

    @Nested
    public class ConstantsTest extends CompilerTestBase {
        @Test
        public void Constant_Is_Referenced_With_FxConstant() {
            Button button = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <minHeight><Double fx:constant="POSITIVE_INFINITY"/></minHeight>
                </Button>
            """);

            assertTrue(Double.isInfinite(button.getMinHeight()));
        }

        @Test
        public void Constant_Is_Referenced_With_InContext_FxConstant() {
            Button button = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        minHeight="{fx:constant POSITIVE_INFINITY}"/>
            """);

            assertTrue(Double.isInfinite(button.getMinHeight()));
        }

        @Test
        public void InContext_FxConstant_With_Invalid_Value() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        minHeight="{fx:constant {fx:constant POSITIVE_INFINITY}}"/>
            """));

            assertEquals(ErrorCode.EXPECTED_IDENTIFIER, ex.getDiagnostic().getCode());
            assertCodeHighlight("{fx:constant POSITIVE_INFINITY}", ex);
        }

        @Test
        public void InContext_FxConstant_With_Empty_Value() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        minHeight="{fx:constant}"/>
            """));

            assertEquals(ErrorCode.INVALID_EXPRESSION, ex.getDiagnostic().getCode());
            assertCodeHighlight("{fx:constant}", ex);
        }

        @Test
        public void FxConstant_And_Child_Content_Cannot_Be_Used_At_Same_Time() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <minHeight><Double fx:constant="NEGATIVE_INFINITY">5.0</Double></minHeight>
                </Button>
            """));

            assertEquals(ErrorCode.OBJECT_CANNOT_HAVE_CONTENT, ex.getDiagnostic().getCode());
            assertCodeHighlight("""
                <Double fx:constant="NEGATIVE_INFINITY">5.0</Double>
            """.trim(), ex);
        }

        @Test
        public void FxConstant_And_FxValue_Content_Cannot_Be_Used_At_Same_Time() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <minHeight><Double fx:constant="NEGATIVE_INFINITY" fx:value="5.0"/></minHeight>
                </Button>
            """));

            assertEquals(ErrorCode.CONFLICTING_PROPERTIES, ex.getDiagnostic().getCode());
            assertCodeHighlight("""
                fx:value="5.0"
            """.trim(), ex);
        }

        @Test
        public void FxConstant_Resolves_Fully_Qualified_Name() {
            TableView<?> tableView = compileAndRun("""
                <?import javafx.scene.control.*?>
                <?import javafx.util.Callback?>
                <TableView xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <columnResizePolicy>
                        <Callback fx:constant="javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY"/>
                    </columnResizePolicy>
                </TableView>
            """);

            assertSame(tableView.getColumnResizePolicy(), TableView.CONSTRAINED_RESIZE_POLICY);
        }

        @Test
        public void Constant_With_Incompatible_Generic_Arguments_Cannot_Be_Assigned() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <?import javafx.util.Callback?>
                <TableView xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <columnResizePolicy>
                        <Callback fx:typeArguments="java.lang.String,java.lang.Double"
                                  fx:constant="javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY"/>
                    </columnResizePolicy>
                </TableView>
            """));

            assertEquals(ErrorCode.CANNOT_ASSIGN_CONSTANT, ex.getDiagnostic().getCode());
            assertCodeHighlight("""
                fx:constant="javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY"
            """.trim(), ex);
        }
    }

}

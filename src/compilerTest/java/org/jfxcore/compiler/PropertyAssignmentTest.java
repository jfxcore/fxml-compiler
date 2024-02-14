// Copyright (c) 2022, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
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
import java.util.ArrayList;
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
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
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
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
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
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
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
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
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
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <minHeight><Double fx:constant="NEGATIVE_INFINITY"/>5.0</minHeight>
                </Button>
            """));

            assertEquals(ErrorCode.PROPERTY_CANNOT_HAVE_MULTIPLE_VALUES, ex.getDiagnostic().getCode());
            assertCodeHighlight("""
                <minHeight><Double fx:constant="NEGATIVE_INFINITY"/>5.0</minHeight>
            """.trim(), ex);
        }

        @SuppressWarnings("unused")
        public static class StaticProperties {
            public static String getProp1(Object node) { return null; }
            public static void setProp1(GridPane node, String value) {}

            private static final StringProperty prop2 = new SimpleStringProperty();
            public static String getProp2(Object node) { return prop2.get(); }
            public static StringProperty prop2Property(GridPane node) { return prop2; }
        }

        @Test
        public void Contextual_ReadOnly_Static_Property_Cannot_Be_Assigned() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                        StaticProperties.prop1="foo"/>
            """));

            assertEquals(ErrorCode.CANNOT_MODIFY_READONLY_PROPERTY, ex.getDiagnostic().getCode());
            assertCodeHighlight("StaticProperties.prop1=\"foo\"", ex);
        }

        @Test
        public void Contextual_ReadOnly_Static_Property_Can_Be_Assigned() {
            var root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          StaticProperties.prop1="foo"/>
            """);

            assertMethodCall(root, methods -> methods.stream().anyMatch(m -> m.getName().equals("setProp1")));
        }

        @Test
        public void Contextual_ReadOnly_Observable_Static_Property_Cannot_Be_Assigned() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                        StaticProperties.prop2="foo"/>
            """));

            assertEquals(ErrorCode.CANNOT_MODIFY_READONLY_PROPERTY, ex.getDiagnostic().getCode());
            assertCodeHighlight("StaticProperties.prop2=\"foo\"", ex);
        }

        @Test
        public void Contextual_ReadOnly_Observable_Static_Property_Can_Be_Assigned() {
            var root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          StaticProperties.prop2="foo"/>
            """);

            assertMethodCall(root, methods ->
                methods.stream().noneMatch(m -> m.getName().equals("setProp2"))
                && methods.stream().anyMatch(m -> m.getName().equals("setValue")));
        }
    }

    @Nested
    public class PropertyNameTest extends CompilerTestBase {
        @Test
        public void Unresolvable_Property_Chain_Includes_All_Names_In_Diagnostic() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                        foo.bar.baz="Hello!"/>
            """));

            assertEquals(ErrorCode.PROPERTY_NOT_FOUND, ex.getDiagnostic().getCode());
            assertCodeHighlight("foo.bar.baz=\"Hello!\"", ex);
            assertTrue(ex.getDiagnostic().getMessage().startsWith("'foo.bar.baz' in"));
            assertTrue(ex.getDiagnostic().getMessage().endsWith("cannot be resolved"));
        }

        @Test
        public void Qualified_Property_With_Element_Notation_Is_Valid(){
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <Button.text>Hello!</Button.text>
                </Button>
            """);

            assertEquals("Hello!", root.getText());
        }

        @Test
        public void Qualified_Property_With_Attribute_Notation_Is_Invalid() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                        Button.text="Hello!"/>
            """));

            assertEquals(ErrorCode.PROPERTY_NOT_FOUND, ex.getDiagnostic().getCode());
            assertCodeHighlight("Button.text=\"Hello!\"", ex);
            assertTrue(ex.getDiagnostic().getMessage().startsWith("'Button.text' in"));
            assertTrue(ex.getDiagnostic().getMessage().endsWith("cannot be resolved"));
        }

        @Test
        public void Qualified_Property_Cannot_Be_Resolved_Without_Import() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <javafx.scene.control.Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <Button.text>Hello!</Button.text>
                </javafx.scene.control.Button>
            """));

            assertEquals(ErrorCode.CLASS_NOT_FOUND, ex.getDiagnostic().getCode());
            assertCodeHighlight("Button.text", ex);
            assertEquals("'Button.text' cannot be resolved", ex.getDiagnostic().getMessage());
        }

        @Test
        public void Nonexistent_Qualified_Property_Cannot_Be_Resolved() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <Button.doesNotExist>Hello!</Button.doesNotExist>
                </Button>
            """));

            assertEquals(ErrorCode.PROPERTY_NOT_FOUND, ex.getDiagnostic().getCode());
            assertCodeHighlight("Button.doesNotExist", ex);
            assertEquals("'doesNotExist' in javafx.scene.control.Button cannot be resolved", ex.getDiagnostic().getMessage());
        }

        @Test
        public void Qualified_Property_Is_Interpreted_As_Static_Property() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <Labeled xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <Button.text>Hello!</Button.text>
                </Labeled>
            """));

            assertEquals(ErrorCode.PROPERTY_NOT_FOUND, ex.getDiagnostic().getCode());
            assertCodeHighlight("<Button.text>Hello!</Button.text>", ex);
            assertTrue(ex.getDiagnostic().getMessage().startsWith("'text' in javafx.scene.control.Button cannot be resolved"));
            assertTrue(ex.getDiagnostic().getMessage().contains("'text' was interpreted as a static property"));
        }

        @Test
        public void Qualified_Property_Of_Base_Type_Is_Valid(){
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <Labeled.text>Hello!</Labeled.text>
                </Button>
            """);

            assertEquals("Hello!", root.getText());
        }

        @Test
        public void Fully_Qualified_Property_Of_Base_Type_Is_Valid(){
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <javafx.scene.control.Labeled.text>Hello!</javafx.scene.control.Labeled.text>
                </Button>
            """);

            assertEquals("Hello!", root.getText());
        }

        public static class lowercaseButton extends Button {}

        @Test
        public void Lowercase_Element_Is_Valid() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <graphic>
                        <lowercaseButton/>
                    </graphic>
                </Button>
            """);

            assertTrue(root.getGraphic() instanceof lowercaseButton);
        }

        @SuppressWarnings("unused")
        public static class StaticPropertyButton extends Button {
            public static void setText(Node node, String text) {
                node.getProperties().put(StaticPropertyButton.class, text);
            }
            public static String getText(Node node) {
                return (String)node.getProperties().get(StaticPropertyButton.class);
            }
        }

        @Test
        public void UnqualifiedPropertyName_Is_Not_Interpreted_As_StaticProperty_When_Name_Is_Ambiguous() {
            StaticPropertyButton root = compileAndRun("""
                <StaticPropertyButton xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <text>foo</text>
                </StaticPropertyButton>
            """);

            assertEquals("foo", root.getText());
            assertNull(StaticPropertyButton.getText(root));
        }

        @Test
        public void QualifiedPropertyName_Is_Interpreted_As_StaticProperty_When_Name_Is_Ambiguous() {
            StaticPropertyButton root = compileAndRun("""
                <StaticPropertyButton xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <StaticPropertyButton.text>foo</StaticPropertyButton.text>
                </StaticPropertyButton>
            """);

            assertEquals("foo", StaticPropertyButton.getText(root));
            assertEquals("", root.getText());
        }

        @Test
        public void StaticProperty_And_LocalProperty_With_Same_Name_Are_Valid() {
            StaticPropertyButton root = compileAndRun("""
                <StaticPropertyButton xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <StaticPropertyButton.text>foo</StaticPropertyButton.text>
                    <text>bar</text>
                </StaticPropertyButton>
            """);

            assertEquals("foo", StaticPropertyButton.getText(root));
            assertEquals("bar", root.getText());
        }

        public static class UppercasePropertyTest extends Button {
            private final StringProperty text = new SimpleStringProperty();
            public StringProperty TextProperty() { return text; }

            private final StringProperty otherText = new SimpleStringProperty();
            public StringProperty OtherTextProperty() { return otherText; }
        }

        @Test
        public void Uppercase_Property_Name_Is_Valid() {
            UppercasePropertyTest root = compileAndRun("""
                <UppercasePropertyTest xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <Text>foo</Text>
                    <OtherText>bar</OtherText>
                </UppercasePropertyTest>
            """);

            assertEquals("foo", root.TextProperty().get());
            assertEquals("bar", root.OtherTextProperty().get());
        }

        @Test
        public void Uppercase_Property_Name_In_Qualified_Notation_Is_Invalid() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <Button.Text>Hello!</Button.Text>
                </Button>
            """));

            assertEquals(ErrorCode.PROPERTY_NOT_FOUND, ex.getDiagnostic().getCode());
            assertCodeHighlight("Button.Text", ex);
        }

        @Test
        public void Uppercase_Static_Property_Name_In_Qualified_Notation_Is_Invalid() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <StaticPropertyButton.Text>Hello!</StaticPropertyButton.Text>
                </Button>
            """));

            assertEquals(ErrorCode.CLASS_NOT_FOUND, ex.getDiagnostic().getCode());
            assertCodeHighlight("StaticPropertyButton.Text", ex);
        }

        public static class VerbatimMatchTest extends Button {
            private final StringProperty text = new SimpleStringProperty();
            public StringProperty text() { return text; }

            private final StringProperty otherText = new SimpleStringProperty();
            public StringProperty textPropertyProperty() { return otherText; }
        }

        @Test
        public void Ambiguous_Property_Name_Matches_Verbatim_Interpretation() {
            VerbatimMatchTest root = compileAndRun("""
                <VerbatimMatchTest xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <text>foo</text>
                    <textProperty>bar</textProperty>
                    <textPropertyProperty>baz</textPropertyProperty>
                </VerbatimMatchTest>
            """);

            assertEquals("foo", root.text().get());
            assertEquals("bar", root.textProperty().get());
            assertEquals("baz", root.textPropertyProperty().get());
        }

        @SuppressWarnings("unused")
        public static class NamingSchemeTest extends Button {
            final List<String> trace = new ArrayList<>();
            private final StringProperty myText = new SimpleStringProperty();
            public StringProperty myText() { trace.add("myText"); return myText; }
            public StringProperty myTextProperty() { trace.add("myTextProperty"); return myText; }
            public void setMyText(String text) { trace.add("setMyText"); myText.set(text); }
            public String getMyText() { trace.add("getMyText"); return myText.get(); }
        }

        @Test
        public void Naming_Scheme_Is_Not_Detected_For_Verbatim_Property_Name() {
            NamingSchemeTest root = compileAndRun("""
                <NamingSchemeTest xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <myText>foo</myText>
                    <myTextProperty>bar</myTextProperty>
                </NamingSchemeTest>
            """);

            assertEquals(List.of("myText", "myTextProperty"), root.trace);
        }
    }

    @Nested
    public class CoercionTest extends CompilerTestBase {
        @Test
        public void AttributeValue_Is_Coerced_To_String() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                        text="Hello!"/>
            """);

            assertEquals("Hello!", root.getText());
        }

        @Test
        public void AttributeValue_Is_Coerced_To_String_And_Preserves_Whitespace() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                        text="  Hello!  "/>
            """);

            assertEquals("  Hello!  ", root.getText());
        }

        @Test
        public void AttributeValue_Is_Coerced_To_Number() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                        prefWidth="123.5"/>
            """);

            assertEquals(123.5, root.getPrefWidth(), 0.001);
        }

        @Test
        public void AttributeValue_Is_Coerced_To_InfiniteDouble() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                        prefWidth="-Infinity"/>
            """);

            assertEquals(Double.NEGATIVE_INFINITY, root.getPrefWidth(), 0.001);
        }

        @Test
        public void AttributeValue_Is_Coerced_To_Boolean() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                        managed="true" visible="false"/>
            """);

            assertTrue(root.isManaged());
            assertFalse(root.isVisible());
        }

        @Test
        public void AttributeValue_Is_Coerced_To_Enum() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          alignment="CENTER"/>
            """);

            assertEquals(Pos.CENTER, root.getAlignment());
        }

        @Test
        public void AttributeValue_Of_Chained_Property_Is_Coerced_To_Enum() {
            ListView<?> root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <ListView xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                    selectionModel.selectionMode="MULTIPLE"/>
            """);

            assertEquals(SelectionMode.MULTIPLE, root.getSelectionModel().getSelectionMode());
        }

        @Test
        public void AttributeValue_Is_Coerced_To_Static_Field_Of_TargetType() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
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
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <StaticFieldClass supplier="SUPPLIER"/>
                </GridPane>
            """);

            assertSame(StaticFieldClass.SUPPLIER, ((StaticFieldClass<?>)root.getChildren().get(0)).getSupplier());
        }

        @Test
        public void AttributeValue_Is_Coerced_To_Static_Field_Of_Typed_GenericClass() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <StaticFieldClass fx:typeArguments="String" supplier="SUPPLIER"/>
                </GridPane>
            """);

            assertSame(StaticFieldClass.SUPPLIER, ((StaticFieldClass<?>)root.getChildren().get(0)).getSupplier());
        }

        @Test
        public void AttributeValue_Cannot_Be_Coerced_To_Static_Field_Of_Incompatibly_Typed_GenericClass() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
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
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" padding="1"/>
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
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" padding="1,2,3,4"/>
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
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" padding="1,2"/>
            """));

            assertEquals(ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, ex.getDiagnostic().getCode());
            assertCodeHighlight("1,2", ex);
        }

        @Test
        public void ElementValue_Is_Coerced_To_String() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <text>Hello!</text>
                </Button>
            """);

            assertEquals("Hello!", root.getText());
        }

        @Test
        public void ElementValue_Is_Coerced_To_String_And_Removes_Insinificant_Whitespace() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
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
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <prefWidth>123.5</prefWidth>
                </Button>
            """);

            assertEquals(123.5, root.getPrefWidth(), 0.001);
        }

        @Test
        public void ElementValue_Is_Coerced_To_InfiniteDouble() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <prefWidth>-Infinity</prefWidth>
                </Button>
            """);

            assertEquals(Double.NEGATIVE_INFINITY, root.getPrefWidth(), 0.001);
        }

        @Test
        public void ElementValue_Is_Coerced_To_Boolean() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
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
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
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
                <ListView xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <selectionModel.selectionMode>MULTIPLE</selectionModel.selectionMode>
                </ListView>
            """);

            assertEquals(SelectionMode.MULTIPLE, root.getSelectionModel().getSelectionMode());
        }

        @SuppressWarnings("unused")
        public static class CsvArrayPane extends GridPane {
            private int[] intArray;
            public int[] getIntArray() { return intArray; }
            public void setIntArray(int[] values) { intArray = values; }

            private int[] varargsIntArray;
            public int[] getVarargsIntArray() { return varargsIntArray; }
            public void setVarargsIntArray(int... values) { varargsIntArray = values; }

            private double[] doubleArray;
            public double[] getDoubleArray() { return doubleArray; }
            public void setDoubleArray(double[] values) { doubleArray = values; }

            private double[] varargsDoubleArray;
            public double[] getVarargsDoubleArray() { return varargsDoubleArray; }
            public void setVarargsDoubleArray(double... values) { varargsDoubleArray = values; }
        }

        @Test
        public void Single_Value_Is_Coerced_To_Array() {
            CsvArrayPane root = compileAndRun("""
                <CsvArrayPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                              doubleArray="0.5"/>
            """);

            assertEquals(1, root.getDoubleArray().length);
            assertEquals(0.5, root.getDoubleArray()[0], 0.001);
        }

        @Test
        public void Single_Value_Is_Coerced_To_Varargs_Array() {
            CsvArrayPane root = compileAndRun("""
                <CsvArrayPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                              varargsDoubleArray="0.5"/>
            """);

            assertEquals(1, root.getVarargsDoubleArray().length);
            assertEquals(0.5, root.getVarargsDoubleArray()[0], 0.001);
        }

        @Test
        public void Comma_Separated_String_Is_Coerced_To_Array() {
            CsvArrayPane root = compileAndRun("""
                <CsvArrayPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                              doubleArray="1,2,3"/>
            """);

            assertEquals(3, root.getDoubleArray().length);
            assertEquals(1, root.getDoubleArray()[0], 0.001);
            assertEquals(2, root.getDoubleArray()[1], 0.001);
            assertEquals(3, root.getDoubleArray()[2], 0.001);
        }

        @Test
        public void Comma_Separated_String_Is_Coerced_To_Varargs_Array() {
            CsvArrayPane root = compileAndRun("""
                <CsvArrayPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                              varargsDoubleArray="1,2,3"/>
            """);

            assertEquals(3, root.getVarargsDoubleArray().length);
            assertEquals(1, root.getVarargsDoubleArray()[0], 0.001);
            assertEquals(2, root.getVarargsDoubleArray()[1], 0.001);
            assertEquals(3, root.getVarargsDoubleArray()[2], 0.001);
        }

        @Test
        public void Comma_Separated_String_With_Inconvertible_Components_Cannot_Be_Coerced_To_Array() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <CsvArrayPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                              intArray="1.5,2,3"/>
            """));

            assertEquals(ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, ex.getDiagnostic().getCode());
        }

        @Test
        public void Comma_Separated_String_With_Inconvertible_Components_Cannot_Be_Coerced_To_Varargs_Array() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <CsvArrayPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                              varargsIntArray="1.5,2,3"/>
            """));

            assertEquals(ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, ex.getDiagnostic().getCode());
        }

        @Test
        public void Comma_Separated_String_Is_Coerced_To_Collection() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
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
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
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
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
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
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                     <Pane GridPane.columnIndex="1"/>
                </GridPane>
            """);

            assertEquals(1, (int)GridPane.getColumnIndex(root.getChildren().get(0)));
        }

        @Test
        public void StaticProperty_PropertyText_Is_Coerced_To_Insets() {
            VBox root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <VBox xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
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
                <VBox xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
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
                <VBox xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
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
                <VBox xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
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
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                        textFill="red"/>
            """);

            assertSame(javafx.scene.paint.Color.RED, root.getTextFill());
        }

        @Test
        public void WebColor_Is_Coerced_To_Paint() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                        textFill="#12345678"/>
            """);

            assertEquals(javafx.scene.paint.Color.valueOf("12345678"), root.getTextFill());
        }

        @Test
        public void WebColor_Is_Coerced_To_Named_Color_Field() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                        textFill="#ff0000"/>
            """);

            assertSame(javafx.scene.paint.Color.RED, root.getTextFill());
        }
    }

}

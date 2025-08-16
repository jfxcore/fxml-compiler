// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.beans.NamedArg;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.event.EventType;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.Reflection;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class InstantiationTest extends CompilerTestBase {

    @Nested
    public class DefaultConstructorTest extends CompilerTestBase {
        @Test
        public void FxId_Sets_IDProperty_If_Not_Present() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <GridPane fx:id="pane0"/>
                </GridPane>
            """);

            assertEquals("pane0", root.getChildren().get(0).getId());
        }

        @Test
        public void FxId_Does_Not_Set_IDProperty_If_Already_Present() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <GridPane fx:id="pane0" id="foo"/>
                </GridPane>
            """);

            assertEquals("foo", root.getChildren().get(0).getId());
        }

        @Test
        public void Duplicate_Property_Assignment_Fails() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <GridPane id="foo" id="bar"/>
                </GridPane>
            """));

            assertEquals(ErrorCode.DUPLICATE_PROPERTY, ex.getDiagnostic().getCode());
        }
    }

    @Nested
    public class NamedArgsConstructorTest extends CompilerTestBase {
        @Test
        public void Insets_Is_Instantiated_With_First_NamedArgs_Constructor() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <?import javafx.geometry.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <fx:define>
                        <Insets fx:id="insets1" left="1" top="2" right="3" bottom="4"/>
                        <Insets fx:id="insets2">
                            <left>1</left>
                            <top>2</top>
                            <right>3</right>
                            <bottom>4</bottom>
                        </Insets>
                    </fx:define>
                </GridPane>
            """);

            Insets insets1 = (Insets)root.getProperties().get("insets1");
            Insets insets2 = (Insets)root.getProperties().get("insets2");
            assertEquals(1, insets1.getLeft(), 0.001);
            assertEquals(2, insets1.getTop(), 0.001);
            assertEquals(3, insets1.getRight(), 0.001);
            assertEquals(4, insets1.getBottom(), 0.001);
            assertEquals(1, insets2.getLeft(), 0.001);
            assertEquals(2, insets2.getTop(), 0.001);
            assertEquals(3, insets2.getRight(), 0.001);
            assertEquals(4, insets2.getBottom(), 0.001);
        }

        @Test
        public void Insets_Is_Instantiated_With_Second_NamedArgs_Constructor() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <?import javafx.geometry.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <padding><Insets fx:id="insets" topRightBottomLeft="1.5"/></padding>
                </GridPane>
            """);

            assertEquals(1.5, root.getPadding().getLeft(), 0.001);
            assertEquals(1.5, root.getPadding().getTop(), 0.001);
            assertEquals(1.5, root.getPadding().getRight(), 0.001);
            assertEquals(1.5, root.getPadding().getBottom(), 0.001);
        }

        @Test
        public void Insets_Cannot_Be_Instantiated_With_Missing_NamedArgs() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <?import javafx.geometry.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <padding><Insets fx:id="insets" left="1" top="2"/></padding>
                </GridPane>
            """));

            assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, ex.getDiagnostic().getCode());
            assertEquals(2, ex.getDiagnostic().getCauses().length);
            assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[0].getCode());
            assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[1].getCode());
            assertCodeHighlight("""
                <Insets fx:id="insets" left="1" top="2"/>
            """.trim(), ex);
        }

        @SuppressWarnings("unused")
        public static class MultiArgCtorObject extends GridPane {
            public MultiArgCtorObject(@NamedArg("arg1") GridPane arg1, @NamedArg("arg2") Button arg2) {}
        }

        @Test
        public void Object_Is_Instantiated_With_NamedArgs_Constructor_Using_Element_Notation() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <MultiArgCtorObject>
                        <arg1><GridPane/></arg1>
                        <arg2><Button/></arg2>
                    </MultiArgCtorObject>
                </GridPane>
            """);

            assertTrue(root.getChildren().get(0) instanceof MultiArgCtorObject);
        }

        @Test
        public void Missing_NamedArg_Param_Throws_Exception() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <MultiArgCtorObject>
                        <arg1><GridPane/></arg1>
                    </MultiArgCtorObject>
                </GridPane>
            """));

            assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, ex.getDiagnostic().getCode());
            assertEquals(1, ex.getDiagnostic().getCauses().length);
            assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[0].getCode());
            assertCodeHighlight("<MultiArgCtorObject>", ex);
        }

        @SuppressWarnings("unused")
        public static class ContextTestPane extends GridPane {
            public static class TestContext {
                public final StringProperty stringValue = new SimpleStringProperty("foo");
            }

            public final TestContext testContext = new TestContext();
        }

        @SuppressWarnings("unused")
        public static class TestPaneWithArg extends GridPane {
            public final String arg;

            public TestPaneWithArg(@NamedArg("arg") String arg) {
                this.arg = arg;
            }
        }

        @Test
        public void NamedArg_Instantiation_With_Context_Selector() {
            GridPane root = compileAndRun("""
                <ContextTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          fx:context="$testContext">
                    <TestPaneWithArg arg="$stringValue"/>
                </ContextTestPane>
            """);

            assertEquals("foo", ((TestPaneWithArg)root.getChildren().get(0)).arg);
        }

        @SuppressWarnings("unused")
        public static class MyButton extends Button {
            boolean defaultCtorCalled = false, namedArgCtorCalled = false;
            public MyButton() { defaultCtorCalled = true; }
            public MyButton(@NamedArg("data") String data) { this.data.set(data); namedArgCtorCalled = true; }
            private final StringProperty data = new SimpleStringProperty();
            public StringProperty dataProperty() { return data; }
            public String getData() { return data.get(); }
            public void setData(String data) { this.data.set(data); }
        }

        @Test
        public void DefaultConstructor_Is_Selected_When_NamedArgConstructor_Is_Invalid() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" id="foo">
                    <MyButton data="${id}"/>
                </GridPane>
            """);

            assertTrue(((MyButton)root.getChildren().get(0)).defaultCtorCalled);
            assertEquals(((MyButton)root.getChildren().get(0)).data.get(), "foo");
        }

        @SuppressWarnings("unused")
        public static class VarArgsConstructorClass extends Rectangle {
            public boolean varArgsConstructorCalled;
            public VarArgsConstructorClass() {}
            public VarArgsConstructorClass(@NamedArg("nodes") Node... nodes) { varArgsConstructorCalled = true; }
        }

        @Test
        public void Object_Is_Instantiated_With_Varargs_Constructor() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <VarArgsConstructorClass>
                        <nodes><GridPane/></nodes>
                    </VarArgsConstructorClass>
                </GridPane>
            """);

            assertTrue(((VarArgsConstructorClass)root.getChildren().get(0)).varArgsConstructorCalled);
        }

        @SuppressWarnings("unused")
        public static class ArrayConstructorClass extends Rectangle {
            public ArrayConstructorClass(@NamedArg("nodes") Node[] nodes) {}
        }

        @Test
        public void Object_Instantiation_Fails_With_NonVarargs_Constructor() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <ArrayConstructorClass>
                        <nodes><GridPane/></nodes>
                    </ArrayConstructorClass>
                </GridPane>
            """));

            assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
            assertEquals(0, ex.getDiagnostic().getCauses().length);
            assertCodeHighlight("<GridPane/>", ex);
        }

        public static class NamedArgWithDefaultValueClass extends Rectangle {
            final double x, y;
            public NamedArgWithDefaultValueClass(@NamedArg("x") double x, @NamedArg(value = "y", defaultValue = "5") double y) {
                this.x = x;
                this.y = y;
            }
        }

        @Test
        public void NamedArg_With_DefaultValue_Can_Be_Omitted() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <NamedArgWithDefaultValueClass x="1"/>
                </GridPane>
            """);

            var inst = (NamedArgWithDefaultValueClass)root.getChildren().get(0);
            assertEquals(1, inst.x, 0.001);
            assertEquals(5, inst.y, 0.001);
        }

        @Test
        public void Spinner_Can_Be_Instantiated_With_Named_Args() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <?import javafx.scene.control.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <Spinner fx:typeArguments="Integer" min="0" max="10" initialValue="5"/>
                    <Spinner fx:typeArguments="Double" min="0" max="10" initialValue="5"/>
                    <Spinner fx:typeArguments="Double" min="0.0" max="10" initialValue="5"/>
                </GridPane>
            """);

            Spinner<?> spinner1 = (Spinner<?>)root.getChildren().get(0);
            assertTrue(spinner1.getValue() instanceof Integer);

            Spinner<?> spinner2 = (Spinner<?>)root.getChildren().get(1);
            assertTrue(spinner2.getValue() instanceof Integer);

            Spinner<?> spinner3 = (Spinner<?>)root.getChildren().get(2);
            assertTrue(spinner3.getValue() instanceof Double);
        }
    }

    @Nested
    public class GenericsTest extends CompilerTestBase {
        @SuppressWarnings("unused")
        public static class MyData<T> {
            public MyData(@NamedArg("value") T value) { this.value = value; this.value2 = null; }
            public MyData(T value1, T value2) { this.value = value1; this.value2 = value2; }
            public final T value;
            public final T value2;
        }

        public static class MyGenericButton<T> extends Button {
            public MyGenericButton(@NamedArg("data") MyData<T> data) { this.data = data; }
            public final MyData<T> data;
        }

        @SuppressWarnings("unused")
        public static class MyDerivedData extends MyData<String> {
            public MyDerivedData(@NamedArg("value") String value) {
                super(value);
            }
        }

        @Test
        public void Object_Is_Instantiated_With_Generic_NamedArg() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <MyGenericButton fx:typeArguments="java.lang.String">
                        <data>
                            <MyData fx:typeArguments="java.lang.String" value="foo"/>
                        </data>
                    </MyGenericButton>
                </GridPane>
            """);

            assertEquals(((MyGenericButton<?>)root.getChildren().get(0)).data.value, "foo");
        }

        @Test
        public void RawType_Can_Be_Assigned_To_Typed_Element() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <MyGenericButton fx:typeArguments="java.lang.String">
                        <data><MyData value="foo"/></data>
                    </MyGenericButton>
                </GridPane>
            """);

            assertEquals(((MyGenericButton<?>)root.getChildren().get(0)).data.value, "foo");
        }

        @Test
        public void Incompatible_GenericType_Cannot_Be_Assigned_To_Typed_Element() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <MyGenericButton fx:typeArguments="java.lang.String">
                        <data><MyData fx:typeArguments="Double" value="foo"/></data>
                    </MyGenericButton>
                </GridPane>
            """));

            assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
            assertEquals(0, ex.getDiagnostic().getCauses().length);
            assertCodeHighlight("""
                <MyData fx:typeArguments="Double" value="foo"/>
            """.trim(), ex);
        }
    }

    @Nested
    public class CoercionTest extends CompilerTestBase {
        @Test
        public void Instantiation_By_Value_Coercion() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <?import javafx.geometry.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <fx:define>
                        <Insets fx:id="insets1">1,2,3,4</Insets>
                    </fx:define>
                    <padding>
                        <Insets fx:id="insets">1</Insets>
                    </padding>
                </GridPane>
            """);

            Insets insets1 = (Insets)root.getProperties().get("insets1");
            Insets insets2 = root.getPadding();
            assertEquals(1, insets1.getTop(), 0.001);
            assertEquals(2, insets1.getRight(), 0.001);
            assertEquals(3, insets1.getBottom(), 0.001);
            assertEquals(4, insets1.getLeft(), 0.001);
            assertEquals(1, insets2.getTop(), 0.001);
            assertEquals(1, insets2.getRight(), 0.001);
            assertEquals(1, insets2.getBottom(), 0.001);
            assertEquals(1, insets2.getLeft(), 0.001);
        }

        @Test
        public void Instantiation_By_Argument_Coercion_To_Static_Field_On_TargetType() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <fx:define>
                        <BackgroundFill fx:id="bf" fill="{fx:null}" radii="EMPTY" insets="0"/>
                    </fx:define>
                </GridPane>
            """);

            BackgroundFill fill = (BackgroundFill)root.getProperties().get("bf");
            assertSame(CornerRadii.EMPTY, fill.getRadii());
        }

        @Test
        public void Instantiation_By_Argument_Coercion_To_Insets() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <fx:define>
                        <BackgroundFill fx:id="bf" fill="{fx:null}" radii="{fx:null}" insets="1"/>
                    </fx:define>
                </GridPane>
            """);

            BackgroundFill fill = (BackgroundFill)root.getProperties().get("bf");
            assertEquals(1, fill.getInsets().getTop(), 0.001);
            assertEquals(1, fill.getInsets().getRight(), 0.001);
            assertEquals(1, fill.getInsets().getBottom(), 0.001);
            assertEquals(1, fill.getInsets().getLeft(), 0.001);
        }

        @Test
        public void Instantiation_By_Argument_Coercion_Of_Comma_Separated_List_To_Insets() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <fx:define>
                        <BackgroundFill fx:id="bf" fill="{fx:null}" radii="{fx:null}" insets="1,2,3,4"/>
                    </fx:define>
                </GridPane>
            """);

            BackgroundFill fill = (BackgroundFill)root.getProperties().get("bf");
            assertEquals(1, fill.getInsets().getTop(), 0.001);
            assertEquals(2, fill.getInsets().getRight(), 0.001);
            assertEquals(3, fill.getInsets().getBottom(), 0.001);
            assertEquals(4, fill.getInsets().getLeft(), 0.001);
        }

        @Test
        public void AssignmentExpression_Can_Be_Coerced_To_Constructor_Argument() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <?import javafx.scene.paint.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <fx:define>
                        <Color fx:id="col">red</Color>
                        <BackgroundFill fx:id="bf" fill="$col" radii="EMPTY" insets="0"/>
                    </fx:define>
                </GridPane>
            """);

            var backgroundFill = (BackgroundFill)root.getProperties().get("bf");
            assertEquals(Color.RED, backgroundFill.getFill());
        }

        @SuppressWarnings("unused")
        public static class NamedArgTestPane extends GridPane {
            // EventType's parameterized constructor is not annotated with NamedArg
            public NamedArgTestPane(@NamedArg("eventType") EventType<?> eventType) {}
        }

        @Test
        public void Constructor_Without_NamedArgAnnotations_Is_Not_Eligible() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <NamedArgTestPane eventType="MY_EVENT_TYPE"/>
                </GridPane>
            """));

            assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
            assertCodeHighlight("MY_EVENT_TYPE", ex);
        }
    }

    @Nested
    public class FxValueTest extends CompilerTestBase {
        @Test
        public void Object_Is_Instantiated_With_ValueOf_Method() {
            Button root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <?import javafx.scene.paint.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <textFill>
                        <Color fx:value="red"/>
                    </textFill>
                </Button>
            """);

            assertMethodCall(root, ms -> ms.stream().anyMatch(m -> m.getName().equals("valueOf")));
            assertEquals(javafx.scene.paint.Color.RED, root.getTextFill());
        }

        @Test
        public void ValueOf_And_Child_Content_Cannot_Be_Used_At_Same_Time() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <?import javafx.scene.paint.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <textFill>
                        <Color fx:value="red"><Button/></Color>
                    </textFill>
                </Button>
            """));

            assertEquals(ErrorCode.OBJECT_CANNOT_HAVE_CONTENT, ex.getDiagnostic().getCode());
            assertCodeHighlight("""
                fx:value="red"
            """.trim(), ex);
        }

        @Test
        public void FxValue_Cannot_Be_Used_As_Element() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                        minHeight="{fx:value}"/>
            """));

            assertEquals(ErrorCode.UNEXPECTED_INTRINSIC, ex.getDiagnostic().getCode());
            assertCodeHighlight("{fx:value}", ex);
        }

        @Test
        public void LiteralObject_Is_Instantiated_With_FxValue() {
            GridPane root = compileAndRun("""
                <?import java.lang.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <minHeight><Double fx:value="5.5"/></minHeight>
                </GridPane>
            """);

            assertMethodCall(root, ms -> ms.stream().anyMatch(m -> m.getName().equals("valueOf")));
            assertEquals(5.5, root.getMinHeight(), 0.001);
        }

        @Test
        public void Double_Is_Instantiated_From_String_And_Primitive() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <minHeight><Double><fx:value><Double fx:value="5.5D"/></fx:value></Double></minHeight>
                </GridPane>
            """);

            assertMethodCall(root, ms -> ms.stream().anyMatch(m -> m.getName().equals("valueOf")));
            assertEquals(5.5, root.getMinHeight(), 0.001);
        }

        @Test
        public void ValueOf_With_Object_Argument_Accepts_String() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <id><String fx:value="foo"/></id>
                </GridPane>
            """);

            assertMethodCall(root, ms -> ms.stream().anyMatch(m -> m.getName().equals("valueOf")));
            assertEquals("foo", root.getId());
        }

        @Test
        public void ValueOf_With_Object_Argument_Accepts_String_Expression() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <id><String fx:value="$String('foo')"/></id>
                </GridPane>
            """);

            assertMethodCall(root, ms -> ms.stream().anyMatch(m -> m.getName().equals("valueOf")));
            assertEquals("foo", root.getId());
        }

        @Test
        public void ValueOf_With_Object_Argument_Accepts_Number_Expression() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <id><String fx:value="$Double(123)"/></id>
                </GridPane>
            """);

            assertMethodCall(root, ms -> ms.stream().anyMatch(m -> m.getName().equals("valueOf")));
            assertEquals("123.0", root.getId());
        }

        @Test
        public void ValueOf_With_Object_Argument_Does_Not_Accept_Binding_Expression() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <id><String fx:value="${String('foo')}"/></id>
                </GridPane>
            """));

            assertEquals(ErrorCode.EXPRESSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("${String('foo')}".trim(), ex);
        }

        @Test
        public void ValueOf_With_Object_Argument_Does_Not_Accept_Bidirectional_Binding_Expression() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <id><String fx:value="#{String('foo')}"/></id>
                </GridPane>
            """));

            assertEquals(ErrorCode.EXPRESSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("#{String('foo')}".trim(), ex);
        }

        @Test
        public void ValueOf_With_Object_Argument_Does_Not_Accept_Content_Binding_Expression() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <id><String fx:value="$..String('foo')"/></id>
                </GridPane>
            """));

            assertEquals(ErrorCode.EXPRESSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("$..String('foo')".trim(), ex);
        }

        @SuppressWarnings("unused")
        public static class ValueOfBase {
            String value;

            public static ValueOfDerived valueOf(String value) {
                var result = new ValueOfDerived();
                result.value = value;
                return result;
            }
        }

        @SuppressWarnings("unused")
        public static class ValueOfDerived extends ValueOfBase {
            public String valueOf(Object value) { // not static, can't be selected
                throw new RuntimeException();
            }
        }

        @Test
        public void ValueOf_In_Superclass_Is_Acceptable() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <fx:define>
                        <ValueOfDerived fx:id="k" fx:value="foo"/>
                    </fx:define>
                </GridPane>
            """);

            assertInstanceOf(ValueOfDerived.class, root.getProperties().get("k"));
            assertEquals("foo", ((ValueOfDerived)root.getProperties().get("k")).value);
        }

        @SuppressWarnings("unused")
        public static class ValueOfInstance {
            public ValueOfDerived valueOf(String value) {
                throw new RuntimeException();
            }
        }

        @Test
        public void Instance_ValueOf_Method_Is_Not_Acceptable() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <fx:define>
                        <ValueOfInstance fx:id="k" fx:value="foo"/>
                    </fx:define>
                </GridPane>
            """));

            assertEquals(ErrorCode.VALUEOF_METHOD_NOT_FOUND, ex.getDiagnostic().getCode());
            assertEquals(1, ex.getDiagnostic().getCauses().length);
            assertEquals(ErrorCode.METHOD_NOT_STATIC, ex.getDiagnostic().getCauses()[0].getCode());
            assertCodeHighlight("fx:value=\"foo\"".trim(), ex);
        }
    }

    @Nested
    public class FactoryTest extends CompilerTestBase {
        @Test
        public void ObservableArrayList_Is_Instantiated_Via_Qualified_Factory_Method() {
            GridPane root = compileAndRun("""
                <?import javafx.collections.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <fx:define>
                        <FXCollections fx:id="list" fx:factory="   <String> observableArrayList ">
                            <String>foo</String>
                            <String>bar</String>
                            <String>baz</String>
                        </FXCollections>
                    </fx:define>
                </GridPane>
            """);

            ObservableList<?> list = (ObservableList<?>)root.getProperties().get("list");
            assertNotNull(list);
            assertEquals(3, list.size());
            assertEquals("foo", list.get(0));
            assertEquals("bar", list.get(1));
            assertEquals("baz", list.get(2));
        }

        @Test
        public void ObservableArrayList_With_Incompatible_TypeArguments() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.collections.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <fx:define>
                        <FXCollections fx:factory="<Double>observableArrayList">
                            <String>foo</String>
                        </FXCollections>
                    </fx:define>
                </GridPane>
            """));

            assertEquals(ErrorCode.CANNOT_ADD_ITEM_INCOMPATIBLE_TYPE, ex.getDiagnostic().getCode());
            assertCodeHighlight("<String>foo</String>", ex);
        }

        @SuppressWarnings("unused")
        public static class TestListFactory {
            public <T> ObservableList<T> newListInstance() { return null; }
            private <T> ObservableList<T> newListPrivate() { return null; }
        }

        @Test
        public void Inaccessible_Factory_Method_Not_Found() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <fx:define>
                        <TestListFactory fx:id="list" fx:factory="   newListPrivate  ">
                            <String>foo</String>
                        </TestListFactory>
                    </fx:define>
                </GridPane>
            """));

            assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
            assertCodeHighlight("newListPrivate", ex);
        }

        @Test
        public void Factory_Method_Must_Be_Static() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <fx:define>
                        <TestListFactory fx:id="list" fx:factory="   newListInstance  ">
                            <String>foo</String>
                        </TestListFactory>
                    </fx:define>
                </GridPane>
            """));

            assertEquals(ErrorCode.INSTANCE_MEMBER_REFERENCED_FROM_STATIC_CONTEXT, ex.getDiagnostic().getCode());
            assertCodeHighlight("newListInstance", ex);
        }
    }

    @Test
    public void Incompatible_Constructor_Param_Throws_Exception() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.geometry.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <padding><Insets>foo</Insets></padding>
            </GridPane>
        """));

        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, ex.getDiagnostic().getCode());
        assertEquals(2, ex.getDiagnostic().getCauses().length);
        assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[0].getCode());
        assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[1].getCode());
        assertCodeHighlight("<Insets>foo</Insets>", ex);
    }

    @Test
    public void Nested_Elements_Are_Instantiated_Correctly() {
        GridPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.geometry.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GridPane fx:id="pane0">
                    <GridPane/>
                    <GridPane/>
                    <GridPane fx:id="pane1">
                        <GridPane/>
                    </GridPane>
                </GridPane>
            </GridPane>
        """);

        assertEquals(1, root.getChildren().size());
        assertEquals(3, Reflection.<GridPane>getFieldValue(root, "pane0").getChildren().size());
        assertEquals(1, Reflection.<GridPane>getFieldValue(root, "pane1").getChildren().size());
    }

    @Test
    public void Incompatible_Element_In_Collection_Throws_Exception() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.geometry.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GridPane/>
                <GridPane/>
                <Insets>10</Insets>
            </GridPane>
        """));

        assertEquals(ErrorCode.CANNOT_ADD_ITEM_INCOMPATIBLE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("<Insets>10</Insets>", ex);
    }

    @Test
    public void Root_ChildContent_Without_DefaultProperty_Throws_Exception() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <ComboBox xmlns="http://javafx.com/javafx">
                <String>foo</String>
                <String>bar</String>
                <String>baz</String>
            </ComboBox>
        """));

        assertEquals(ErrorCode.OBJECT_CANNOT_HAVE_CONTENT, ex.getDiagnostic().getCode());
        assertCodeHighlight("""
            <ComboBox xmlns="http://javafx.com/javafx">
        """.trim(), ex);    }

    @Test
    public void ChildContent_Without_DefaultProperty_Throws_Exception() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <ComboBox>
                    <String>foo</String>
                    <String>bar</String>
                    <String>baz</String>
                </ComboBox>
            </GridPane>
        """));

        assertEquals(ErrorCode.OBJECT_CANNOT_HAVE_CONTENT, ex.getDiagnostic().getCode());
        assertCodeHighlight("<ComboBox>", ex);
    }

    @Test
    public void Instantiate_NonNode_Root() {
        Tab root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Tab xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                 text="foo">
                <content>
                    <GridPane/>
                </content>
            </Tab>
        """);

        assertEquals("foo", root.getText());
        assertInstanceOf(GridPane.class, root.getContent());
    }
}

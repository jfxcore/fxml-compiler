// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.beans.NamedArg;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.jfxcore.compiler.util.MethodReferencedSupport;
import org.jfxcore.compiler.util.Reflection;
import org.jfxcore.compiler.util.TestCompiler;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class InstantiationTest extends MethodReferencedSupport {

    public InstantiationTest() {
        super("org.jfxcore.compiler.classes.InstantiationTest");
    }

    @Test
    public void FxId_Sets_IDProperty_If_Not_Present() {
        GridPane root = TestCompiler.newInstance(
            this, "FxId_Sets_IDProperty_If_Not_Present", """
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <GridPane fx:id="pane0"/>
                </GridPane>
            """);

        assertEquals("pane0", root.getChildren().get(0).getId());
    }

    @Test
    public void FxId_Does_Not_Set_IDProperty_If_Already_Present() {
        GridPane root = TestCompiler.newInstance(
            this, "FxId_Does_Not_Set_IDProperty_If_Already_Present", """
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <GridPane fx:id="pane0" id="foo"/>
                </GridPane>
            """);

        assertEquals("foo", root.getChildren().get(0).getId());
    }

    @Test
    public void Duplicate_Property_Assignment_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Duplicate_Property_Assignment_Fails", """
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <GridPane id="foo" id="bar"/>
                </GridPane>
            """));

        assertEquals(ErrorCode.DUPLICATE_PROPERTY, ex.getDiagnostic().getCode());
    }

    @Test
    public void Object_Is_Instantiated_With_NamedArgs_Constructor() {
        GridPane root = TestCompiler.newInstance(
            this, "Object_Is_Instantiated_With_NamedArgs_Constructor", """
                <?import javafx.scene.layout.*?>
                <?import javafx.geometry.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
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
    public void Object_Is_Instantiated_With_Other_Parameterized_Constructor() {
        GridPane root = TestCompiler.newInstance(
            this, "Object_Is_Instantiated_With_Other_Parameterized_Constructor", """
                <?import javafx.scene.layout.*?>
                <?import javafx.geometry.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <padding><Insets fx:id="insets" topRightBottomLeft="1.5"/></padding>
                </GridPane>
            """);

        assertEquals(1.5, root.getPadding().getLeft(), 0.001);
        assertEquals(1.5, root.getPadding().getTop(), 0.001);
        assertEquals(1.5, root.getPadding().getRight(), 0.001);
        assertEquals(1.5, root.getPadding().getBottom(), 0.001);
    }

    @Test
    public void Object_Is_Instantiated_With_Parameterized_Constructor_Using_Default_Values() {
        GridPane root = TestCompiler.newInstance(
            this, "Object_Is_Instantiated_With_Parameterized_Constructor_Using_Default_Values", """
                <?import javafx.scene.layout.*?>
                <?import javafx.geometry.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <padding><Insets fx:id="insets" left="1" top="2"/></padding>
                </GridPane>
            """);

        assertEquals(1, root.getPadding().getLeft(), 0.001);
        assertEquals(2, root.getPadding().getTop(), 0.001);
        assertEquals(0, root.getPadding().getRight(), 0.001);
        assertEquals(0, root.getPadding().getBottom(), 0.001);
    }

    @SuppressWarnings("unused")
    public static class MultiArgCtorObject extends GridPane {
        public MultiArgCtorObject(@NamedArg("arg1") GridPane arg1, @NamedArg("arg2") Button arg2) {}
    }

    @Test
    public void Object_Is_Instantiated_With_Parameterized_Constructor_Using_Element_Notation() {
        GridPane root = TestCompiler.newInstance(
            this, "Object_Is_Instantiated_With_Parameterized_Constructor_Using_Element_Notation", """
                <?import javafx.scene.control.*?>
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.InstantiationTest.MultiArgCtorObject?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <MultiArgCtorObject>
                        <arg1><GridPane/></arg1>
                        <arg2><Button/></arg2>
                    </MultiArgCtorObject>
                </GridPane>
            """);

        assertTrue(root.getChildren().get(0) instanceof MultiArgCtorObject);
    }

    @SuppressWarnings("unused")
    public static class MyData<T> {
        public MyData(@NamedArg("value") T value) { this.value = value; this.value2 = null; }
        public MyData(T value1, T value2) { this.value = value1; this.value2 = value2; }
        public final T value;
        public final T value2;
    }
    public static class MyButton<T> extends Button {
        public MyButton(@NamedArg("data") MyData<T> data) { this.data = data; }
        public final MyData<T> data;
    }

    @Test
    public void Object_Is_Instantiated_With_Generic_NamedArg() {
        GridPane root = TestCompiler.newInstance(
            this, "Object_Is_Instantiated_With_Generic_NamedArg", """
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.InstantiationTest.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <MyButton fx:typeArguments="java.lang.String">
                        <data>
                            <MyData fx:typeArguments="java.lang.String" value="foo"/>
                        </data>
                    </MyButton>
                </GridPane>
            """);

        assertEquals(((MyButton<?>)root.getChildren().get(0)).data.value, "foo");
    }

    @Test
    public void RawType_Cannot_Be_Assigned_To_Typed_Element() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "RawType_Cannot_Be_Assigned_To_Typed_Element", """
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.InstantiationTest.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <MyButton fx:typeArguments="java.lang.String">
                        <data>
                            <MyData value="foo"/>
                        </data>
                    </MyButton>
                </GridPane>
            """));

        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, ex.getDiagnostic().getCode());
        assertEquals(2, ex.getDiagnostic().getCauses().length);
        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCauses()[0].getCode());
        assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[1].getCode());
    }

    @SuppressWarnings("unused")
    public static class MyButton2 extends Button {
        public MyButton2() {}
        public MyButton2(@NamedArg("data") String data) { this.data.set(data); }
        private final StringProperty data = new SimpleStringProperty();
        public StringProperty dataProperty() { return data; }
        public String getData() { return data.get(); }
        public void setData(String data) { this.data.set(data); }
    }

    @Test
    public void Parameterless_Constructor_Is_Selected_When_Parameterized_Constructor_Is_Invalid() {
        GridPane root = TestCompiler.newInstance(
            this, "Parameterless_Constructor_Is_Selected_When_Parameterized_Constructor_Is_Invalid", """
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.InstantiationTest.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml" id="foo">
                    <MyButton2 data="{fx:bind id}"/>
                </GridPane>
            """);

        assertEquals(((MyButton2)root.getChildren().get(0)).data.get(), "foo");
    }

    @Test
    public void Missing_NamedArg_Param_Throws_Exception() {
        MarkupException ex = assertThrows(
            MarkupException.class,
            () -> TestCompiler.newInstance(this, "Missing_NamedArg_Param_Throws_Exception", """
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.InstantiationTest.MultiArgCtorObject?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <MultiArgCtorObject>
                        <arg1><GridPane/></arg1>
                    </MultiArgCtorObject>
                </GridPane>
            """));

        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, ex.getDiagnostic().getCode());
        assertEquals(1, ex.getDiagnostic().getCauses().length);
        assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[0].getCode());
    }

    @Test
    public void Incompatible_Constructor_Param_Throws_Exception() {
        MarkupException ex = assertThrows(
            MarkupException.class,
            () -> TestCompiler.newInstance(this, "Incompatible_Constructor_Param_Throws_Exception", """
                <?import javafx.scene.layout.*?>
                <?import javafx.geometry.*?>
                <?import org.jfxcore.compiler.InstantiationTest.MultiArgCtorObject?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <padding><Insets>foo</Insets></padding>
                </GridPane>
            """));

        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, ex.getDiagnostic().getCode());
        assertEquals(2, ex.getDiagnostic().getCauses().length);
        assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[0].getCode());
        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCauses()[1].getCode());
    }

    @SuppressWarnings("unused")
    public static class VarArgsConstructorClass extends Rectangle {
        public boolean varArgsConstructorCalled;
        public VarArgsConstructorClass() {}
        public VarArgsConstructorClass(@NamedArg("nodes") Node... nodes) { varArgsConstructorCalled = true; }
    }

    @Test
    public void Object_Is_Instantiated_With_Varargs_Constructor() {
        GridPane root = TestCompiler.newInstance(
                this, "Object_Is_Instantiated_With_Varargs_Constructor","""
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.InstantiationTest.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <VarArgsConstructorClass>
                        <GridPane/>
                    </VarArgsConstructorClass>
                </GridPane>
            """);

        assertTrue(((VarArgsConstructorClass)root.getChildren().get(0)).varArgsConstructorCalled);
    }

    @Test
    public void Object_Is_Instantiated_With_NamedArg_Varargs_Constructor() {
        GridPane root = TestCompiler.newInstance(
                this, "Object_Is_Instantiated_With_NamedArg_Varargs_Constructor","""
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.InstantiationTest.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
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
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Object_Instantiation_Fails_With_NonVarargs_Constructor","""
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.InstantiationTest.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <ArrayConstructorClass>
                        <GridPane/>
                    </ArrayConstructorClass>
                </GridPane>
            """));

        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, ex.getDiagnostic().getCode());
        assertEquals(1, ex.getDiagnostic().getCauses().length);
        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCauses()[0].getCode());
    }

    @Test
    public void Object_Instantiation_Fails_With_NamedArg_NonVarargs_Constructor() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
                this, "Object_Instantiation_Fails_With_NamedArg_NonVarargs_Constructor","""
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.InstantiationTest.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <ArrayConstructorClass>
                        <nodes><GridPane/></nodes>
                    </ArrayConstructorClass>
                </GridPane>
            """));

        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, ex.getDiagnostic().getCode());
        assertEquals(2, ex.getDiagnostic().getCauses().length);
        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCauses()[0].getCode());
        assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[1].getCode());
    }

    @Test
    public void Object_Is_Instantiated_With_Single_Arg_Content_Constructor() {
        GridPane root = TestCompiler.newInstance(
            this, "Object_Is_Instantiated_With_Single_Arg_Content_Constructor","""
                <?import javafx.scene.layout.*?>
                <?import javafx.geometry.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <padding><Insets fx:id="insets">1</Insets></padding>
                </GridPane>
            """);

        assertEquals(1, root.getPadding().getLeft(), 0.001);
        assertEquals(1, root.getPadding().getTop(), 0.001);
        assertEquals(1, root.getPadding().getRight(), 0.001);
        assertEquals(1, root.getPadding().getBottom(), 0.001);
    }

    @Test
    public void Object_Is_Instantiated_With_Multi_Arg_Content_Constructor() {
        GridPane root = TestCompiler.newInstance(
            this, "Object_Is_Instantiated_With_Multi_Arg_Content_Constructor","""
                <?import java.lang.*?>
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.InstantiationTest.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <fx:define>
                        <MyData fx:id="data">
                            <String>foo</String>
                            <String>bar</String>
                        </MyData>
                    </fx:define>
                </GridPane>
            """);

        MyData<?> data = (MyData<?>)root.getProperties().get("data");
        assertEquals("foo", data.value);
        assertEquals("bar", data.value2);
    }

    @Test
    public void Instantiation_Fails_For_Mismatched_Number_Of_Content_Constructor_Arguments() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Instantiation_Fails_For_Mismatched_Number_Of_Content_Constructor_Arguments", """
                <?import java.lang.*?>
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.InstantiationTest.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <fx:define>
                        <MyData>
                            <String>foo</String>
                            <String>bar</String>
                            <String>baz</String>
                        </MyData>
                    </fx:define>
                </GridPane>
            """));

        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, ex.getDiagnostic().getCode());
        assertEquals(2, ex.getDiagnostic().getCauses().length);
        assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[0].getCode());
        assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[1].getCode());
    }

    @SuppressWarnings({"unused", "rawtypes", "unchecked"})
    public static class HierarchicalObject<T> {
        private T value;
        private HierarchicalObject[] children;
        public HierarchicalObject() {}
        public HierarchicalObject(T value) { this.value = value; }
        public HierarchicalObject(HierarchicalObject<T>... children) { this.children = children; }
        public T getValue() { return value; }
        public void setValue(T value) { this.value = value; }
    }

    @Test
    public void HierarchicalObject_Is_Instantiated_With_Single_Child() {
        GridPane root = TestCompiler.newInstance(
            this, "HierarchicalObject_Is_Instantiated_With_Single_Child","""
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.InstantiationTest.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <fx:define>
                        <HierarchicalObject fx:id="obj0" value="foo">
                            <HierarchicalObject value="bar">
                                <HierarchicalObject value="baz"/>
                            </HierarchicalObject>
                        </HierarchicalObject>
                    </fx:define>
                </GridPane>
            """);

        HierarchicalObject<?> obj = (HierarchicalObject<?>)root.getProperties().get("obj0");
        assertEquals(1, obj.children.length);
        assertEquals(1, obj.children[0].children.length);
        assertEquals("foo", obj.value);
        assertEquals("bar", obj.children[0].value);
        assertEquals("baz", obj.children[0].children[0].value);
    }

    @Test
    public void HierarchicalObject_Is_Instantiated_With_Multiple_Children() {
        GridPane root = TestCompiler.newInstance(
            this, "HierarchicalObject_Is_Instantiated_With_Multiple_Children","""
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.InstantiationTest.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <fx:define>
                        <HierarchicalObject fx:id="obj0" value="foo">
                            <HierarchicalObject value="bar">
                                <HierarchicalObject value="baz"/>
                                <HierarchicalObject value="qux"/>
                                <HierarchicalObject>quux</HierarchicalObject>
                            </HierarchicalObject>
                        </HierarchicalObject>
                    </fx:define>
                </GridPane>
            """);

        HierarchicalObject<?> obj = (HierarchicalObject<?>)root.getProperties().get("obj0");
        assertEquals(1, obj.children.length);
        assertEquals(3, obj.children[0].children.length);
        assertEquals("foo", obj.value);
        assertEquals("bar", obj.children[0].value);
        assertEquals("baz", obj.children[0].children[0].value);
        assertEquals("qux", obj.children[0].children[1].value);
        assertEquals("quux", obj.children[0].children[2].value);
    }

    @Test
    public void Object_Is_Instantiated_By_Coercion() {
        GridPane root = TestCompiler.newInstance(this, "Object_Is_Instantiated_By_Coercion", """
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml" padding="1"/>
            """);

        assertEquals(1, root.getPadding().getLeft(), 0.001);
        assertEquals(1, root.getPadding().getTop(), 0.001);
        assertEquals(1, root.getPadding().getRight(), 0.001);
        assertEquals(1, root.getPadding().getBottom(), 0.001);
    }

    @Test
    public void Object_Is_Instantiated_By_CommaSeparatedValues_Coercion() {
        GridPane root = TestCompiler.newInstance(this, "Object_Is_Instantiated_By_CommaSeparatedValues_Coercion", """
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml" padding="1,2,3,4"/>
            """);

        assertEquals(1, root.getPadding().getTop(), 0.001);
        assertEquals(2, root.getPadding().getRight(), 0.001);
        assertEquals(3, root.getPadding().getBottom(), 0.001);
        assertEquals(4, root.getPadding().getLeft(), 0.001);
    }

    @Test
    public void Object_Is_Instantiated_With_ValueOf_Method() {
        Button root = TestCompiler.newInstance(this, "Object_Is_Instantiated_With_ValueOf_Method", """
                <?import javafx.scene.control.*?>
                <?import javafx.scene.paint.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <textFill>
                        <Color fx:value="red"/>
                    </textFill>
                </Button>
            """);
        assertReferenced("Object_Is_Instantiated_With_ValueOf_Method", root, "valueOf");
        assertEquals(javafx.scene.paint.Color.RED, root.getTextFill());
    }

    @Test
    public void ValueOf_And_Child_Content_Cannot_Be_Used_At_Same_Time() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "ValueOf_And_Child_Content_Cannot_Be_Used_At_Same_Time", """
                <?import javafx.scene.control.*?>
                <?import javafx.scene.paint.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <textFill>
                        <Color fx:value="red"><Button/></Color>
                    </textFill>
                </Button>
            """));
        assertEquals(ErrorCode.VALUEOF_CANNOT_HAVE_CONTENT, ex.getDiagnostic().getCode());
    }

    @Test
    public void Object_Is_Instantiated_With_InContext_ValueOf_Method() {
        Button root = TestCompiler.newInstance(this, "Object_Is_Instantiated_With_InContext_ValueOf_Method", """
                <?import javafx.scene.control.*?>
                <?import javafx.scene.paint.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        textFill="{fx:value red}"/>
            """);
        assertReferenced("Object_Is_Instantiated_With_InContext_ValueOf_Method", root, "valueOf");
        assertEquals(javafx.scene.paint.Color.RED, root.getTextFill());
    }

    @Test
    public void InContext_ValueOf_As_Child_Content_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "InContext_ValueOf_As_Child_Content_Fails", """
                <?import javafx.scene.layout.*?>
                <?import javafx.scene.paint.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <children>
                        <fx:value>red</fx:value>
                    </children>
                </GridPane>
            """));
        assertEquals(ErrorCode.VALUEOF_METHOD_NOT_FOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void Unmatchable_Coercion_Throws_Exception() {
        assertThrows(
            MarkupException.class,
            () -> TestCompiler.newInstance(this, "Unmatchable_Coercion_Throws_Exception", """
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml" padding="1,2"/>
            """));
    }

    @Test
    public void Constant_Is_Referenced_With_FxConstant() {
        Button button = TestCompiler.newInstance(this, "Constant_Is_Referenced_With_FxConstant", """
                <?import java.lang.*?>
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <minHeight><Double fx:constant="POSITIVE_INFINITY"/></minHeight>
                </Button>
            """);

        assertTrue(Double.isInfinite(button.getMinHeight()));
    }

    @Test
    public void Constant_Is_Referenced_With_InContext_FxConstant() {
        Button button = TestCompiler.newInstance(this, "Constant_Is_Referenced_With_InContext_FxConstant", """
                <?import java.lang.*?>
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        minHeight="{fx:constant POSITIVE_INFINITY}"/>
            """);

        assertTrue(Double.isInfinite(button.getMinHeight()));
    }

    @Test
    public void LiteralObject_Is_Instantiated_With_FxValue() {
        GridPane root = TestCompiler.newInstance(this, "LiteralObject_Is_Instantiated_With_FxValue", """
                <?import java.lang.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <minHeight><Double fx:value="5.5"/></minHeight>
                </GridPane>
            """);
        assertReferenced("LiteralObject_Is_Instantiated_With_FxValue", root, "valueOf");
        assertEquals(5.5, root.getMinHeight(), 0.001);
    }

    @Test
    public void Object_Is_Instantiated_With_Invalid_FxValue_Throws_Exception() {
        MarkupException ex = assertThrows(
            MarkupException.class,
            () -> TestCompiler.newInstance(this, "Object_Is_Instantiated_With_Invalid_FxValue_Throws_Exception", """
                <?import java.lang.*?>
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <minHeight><Double><fx:value><Double fx:value="5.5D"/></fx:value></Double></minHeight>
                </Button>
            """));

        assertEquals(ErrorCode.PROPERTY_MUST_CONTAIN_TEXT, ex.getDiagnostic().getCode());
    }

    @Test
    public void FxConstant_And_Child_Content_Cannot_Be_Used_At_Same_Time() {
        MarkupException ex = assertThrows(
            MarkupException.class,
            () -> TestCompiler.newInstance(this, "FxConstant_And_Child_Content_Cannot_Be_Used_At_Same_Time", """
                <?import java.lang.*?>
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <minHeight><Double fx:constant="NEGATIVE_INFINITY">5.0</Double></minHeight>
                </Button>
            """));

        assertEquals(ErrorCode.CONSTANT_CANNOT_HAVE_CONTENT, ex.getDiagnostic().getCode());
    }

    @Test
    public void FxConstant_And_FxValue_Content_Cannot_Be_Used_At_Same_Time() {
        MarkupException ex = assertThrows(
            MarkupException.class,
            () -> TestCompiler.newInstance(this, "FxConstant_And_FxValue_Content_Cannot_Be_Used_At_Same_Time", """
                <?import java.lang.*?>
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <minHeight><Double fx:constant="NEGATIVE_INFINITY" fx:value="5.0"/></minHeight>
                </Button>
            """));

        assertEquals(ErrorCode.CONFLICTING_PROPERTIES, ex.getDiagnostic().getCode());
    }

    @Test
    public void Property_Without_Value_Throws() {
        MarkupException ex = assertThrows(
            MarkupException.class,
            () -> TestCompiler.newInstance(this, "Property_Without_Value_Throws", """
                <?import java.lang.*?>
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <minHeight></minHeight>
                </Button>
            """));

        assertEquals(ErrorCode.PROPERTY_CANNOT_BE_EMPTY, ex.getDiagnostic().getCode());
    }

    @Test
    public void Property_With_Multiple_Values_Throws() {
        MarkupException ex = assertThrows(
            MarkupException.class,
            () -> TestCompiler.newInstance(this, "Property_With_Multiple_Values_Throws", """
                <?import java.lang.*?>
                <?import javafx.scene.control.*?>
                <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <minHeight><Double fx:constant="NEGATIVE_INFINITY"/>5.0</minHeight>
                </Button>
            """));

        assertEquals(ErrorCode.PROPERTY_CANNOT_HAVE_MULTIPLE_VALUES, ex.getDiagnostic().getCode());
    }

    @Test
    public void Nested_Elements_Are_Instantiated_Correctly() {
        GridPane root = TestCompiler.newInstance(this, "Nested_Elements_Are_Instantiated_Correctly", """
                <?import javafx.scene.layout.*?>
                <?import javafx.geometry.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
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
    public void Attached_Property_Is_Assigned_Correctly() {
        GridPane root = TestCompiler.newInstance(this, "Attached_Property_Is_Assigned_Correctly", """
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                     <Pane GridPane.columnIndex="1"/>
                </GridPane>
            """);

        assertEquals(1, (int)GridPane.getColumnIndex(root.getChildren().get(0)));
    }

    @Test
    public void Incompatible_Element_In_Collection_Throws_Exception() {
        MarkupException ex = assertThrows(
            MarkupException.class,
            () -> TestCompiler.newInstance(this, "Incompatible_Element_In_Collection_Throws_Exception", """
                <?import javafx.scene.layout.*?>
                <?import javafx.geometry.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <GridPane/>
                    <GridPane/>
                    <Insets>10</Insets>
                </GridPane>
            """));

        assertEquals(ErrorCode.CANNOT_ADD_ITEM_INCOMPATIBLE_TYPE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Duplicate_FxId_Throws_Exception() {
        MarkupException ex = assertThrows(
            MarkupException.class,
            () -> TestCompiler.newInstance(this, "Duplicate_FxId_Throws_Exception", """
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <GridPane fx:id="pane0">
                        <GridPane fx:id="pane0"/>
                    </GridPane>
                </GridPane>
            """));

        assertEquals(ErrorCode.DUPLICATE_ID, ex.getDiagnostic().getCode());
    }

    @Test
    public void Invalid_FxId_Throws_Exception() {
        MarkupException ex = assertThrows(
            MarkupException.class,
            () -> TestCompiler.newInstance(this, "Invalid_FxId_Throws_Exception", """
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <GridPane fx:id="foo bar"/>
                </GridPane>
            """));

        assertEquals(ErrorCode.INVALID_ID, ex.getDiagnostic().getCode());
    }

    @Test
    public void Duplicate_Attribute_Throws_Exception() {
        MarkupException ex = assertThrows(
            MarkupException.class,
            () -> TestCompiler.newInstance(this, "Duplicate_Attribute_Throws_Exception", """
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <GridPane prefWidth="10">
                        <prefWidth>20</prefWidth>
                    </GridPane>
                </GridPane>
            """));

        assertEquals(ErrorCode.DUPLICATE_PROPERTY, ex.getDiagnostic().getCode());
    }

    @Test
    public void FxConstant_Resolves_Fully_Qualified_Name() {
        TableView<?> tableView = TestCompiler.newInstance(this, "FxConstant_Can_Use_Fully_Qualified_Name", """
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
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Constant_With_Incompatible_Generic_Arguments_Cannot_Be_Assigned", """
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
    }

    @Test
    public void Unknown_Intrinsic_Throws_Exception() {
        MarkupException ex = assertThrows(
            MarkupException.class,
            () -> TestCompiler.newInstance(this, "Unknown_Intrinsic_Throws_Exception", """
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <GridPane fx:foo="foo"/>
                </GridPane>
            """));

        assertEquals(ErrorCode.UNKNOWN_INTRINSIC, ex.getDiagnostic().getCode());
    }

    @Test
    public void Objects_Are_Added_To_List() {
        GridPane root = TestCompiler.newInstance(this, "Objects_Are_Added_To_List", """
                <?import java.lang.*?>
                <?import java.util.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <properties>
                        <ArrayList fx:typeArguments="Object" fx:id="list">
                            <String fx:id="str0">foo</String>
                            <Double fx:id="val0" fx:value="123.5"/>
                            <String>baz</String>
                        </ArrayList>
                    </properties>
                </GridPane>
            """);

        List<?> list = (List<?>)root.getProperties().get("list");
        assertEquals(3, list.size());
        assertEquals("foo", list.get(0));
        assertEquals(123.5, list.get(1));
        assertEquals("baz", list.get(2));
    }

    @Test
    public void Objects_Are_Added_To_Incompatible_List_ItemType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Objects_Are_Added_To_Incompatible_List_ItemType_Fails", """
                <?import java.lang.*?>
                <?import java.util.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <properties>
                        <ArrayList fx:typeArguments="Integer" fx:id="list">
                            <String>foo</String>
                        </ArrayList>
                    </properties>
                </GridPane>
            """));

        assertEquals(ErrorCode.CANNOT_ADD_ITEM_INCOMPATIBLE_TYPE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Objects_Are_Added_To_Map() {
        GridPane root = TestCompiler.newInstance(this, "Objects_Are_Added_To_Map", """
                <?import java.lang.*?>
                <?import java.util.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <properties>
                        <HashMap fx:id="map0">
                            <String>foo</String>
                            <Double fx:value="123.5"/>
                        </HashMap>
                    
                        <HashMap fx:typeArguments="String,Object" fx:id="map1">
                            <String fx:id="str0">foo</String>
                            <Double fx:id="val0" fx:value="123.5"/>
                            <String>baz</String>
                        </HashMap>
                    </properties>
                </GridPane>
            """);

        //noinspection unchecked
        Map<Object, Object> map0 = (Map<Object, Object>)root.getProperties().get("map0");
        assertEquals(2, map0.size());
        assertTrue(map0.containsValue("foo"));
        assertTrue(map0.containsValue(123.5));

        //noinspection unchecked
        Map<Object, Object> map1 = (Map<Object, Object>)root.getProperties().get("map1");
        assertEquals("foo", map1.get("str0"));
        assertEquals(123.5, (Double)map1.get("val0"), 0.001);

        //noinspection OptionalGetWithoutIsPresent
        Map.Entry<Object, Object> entry = map1.entrySet().stream()
            .filter(o -> o.getValue() instanceof String && o.getValue().equals("baz")).findFirst().get();
        assertTrue(entry.getKey() instanceof String);
    }

    @Test
    public void Object_Is_Added_To_Incompatible_Map_KeyType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Object_Is_Added_To_Incompatible_Map_KeyType_Fails", """
                <?import java.lang.*?>
                <?import java.util.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <properties>
                        <HashMap fx:typeArguments="Integer,String">
                            <String>foo</String>
                        </HashMap>
                    </properties>
                </GridPane>
            """));

        assertEquals(ErrorCode.UNSUPPORTED_MAP_KEY_TYPE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Object_Is_Added_To_Incompatible_Map_ValueType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Object_Is_Added_To_Incompatible_Map_ValueType_Fails", """
                <?import java.lang.*?>
                <?import java.util.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <properties>
                        <HashMap fx:typeArguments="String,Integer">
                            <String>foo</String>
                        </HashMap>
                    </properties>
                </GridPane>
            """));

        assertEquals(ErrorCode.CANNOT_ADD_ITEM_INCOMPATIBLE_TYPE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Literal_Is_Added_To_MapProperty_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Literal_Is_Added_To_MapProperty_Fails", """
                <?import java.lang.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <properties>
                        Hello123
                    </properties>
                </GridPane>
            """));

        assertEquals(ErrorCode.CANNOT_ADD_ITEM_INCOMPATIBLE_VALUE, ex.getDiagnostic().getCode());
    }

    @SuppressWarnings("unused")
    public static class UnsupportedKeyTestPane extends Pane {
        private final Map<Double, Object> unsupportedKeyMap = new HashMap<>();

        @SuppressWarnings("unused")
        public Map<Double, Object> getUnsupportedKeyMap() {
            return unsupportedKeyMap;
        }
    }

    @Test
    public void Object_Is_Added_To_Unsupported_MapProperty_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Object_Is_Added_To_Unsupported_MapProperty_Fails", """
                <?import java.lang.*?>
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.InstantiationTest.*?>
                <UnsupportedKeyTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <unsupportedKeyMap>
                        <String>foo</String>
                    </unsupportedKeyMap>
                </UnsupportedKeyTestPane>
            """));

        assertEquals(ErrorCode.UNSUPPORTED_MAP_KEY_TYPE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Objects_Are_Added_To_MapProperty() {
        GridPane root = TestCompiler.newInstance(this, "Objects_Are_Added_To_MapProperty", """
                <?import java.lang.*?>
                <?import javafx.scene.layout.*?>
                <?import javafx.scene.paint.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <properties>
                        <String fx:id="str0">foo</String>
                        <Double fx:id="val0" fx:value="123.5"/>
                        <String>baz</String>
                        <Color fx:id="col0" fx:constant="RED"/>
                    </properties>
                </GridPane>
            """);

        assertEquals("foo", root.getProperties().get("str0"));
        assertEquals(123.5, (Double)root.getProperties().get("val0"), 0.001);
        assertEquals(Color.RED, root.getProperties().get("col0"));

        //noinspection OptionalGetWithoutIsPresent
        Map.Entry<Object, Object> entry = root.getProperties().entrySet().stream()
            .filter(o -> o.getValue() instanceof String && o.getValue().equals("baz")).findFirst().get();
        assertFalse(entry.getKey() instanceof String);
    }

    public static class StringKeyMapTestPane extends Pane {
        private final Map<String, Object> stringKeyMap = new HashMap<>();

        @SuppressWarnings("unused")
        public Map<String, Object> getStringKeyMap() {
            return stringKeyMap;
        }
    }

    @Test
    public void Object_Are_Added_To_StringKey_MapProperty() {
        StringKeyMapTestPane root = TestCompiler.newInstance(
            this, "Object_Are_Added_To_StringKey_MapProperty", """
                <?import java.lang.*?>
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.InstantiationTest.*?>
                <StringKeyMapTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <stringKeyMap>
                        <String fx:id="str0">foo</String>
                        <Double fx:id="val0" fx:value="123.5"/>
                        <String>baz</String>
                    </stringKeyMap>
                </StringKeyMapTestPane>
            """);

        assertEquals("foo", root.stringKeyMap.get("str0"));
        assertEquals(123.5, (Double)root.stringKeyMap.get("val0"), 0.001);
        assertEquals(3, root.stringKeyMap.size());
    }

    @Test
    public void Root_Intrinsic_Cannot_Be_Used_On_Child_Element() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.compileClass(
            this, "Root_Intrinsic_Cannot_Be_Used_On_Child_Element", """
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <GridPane fx:class="java.lang.String"/>
                </GridPane>
            """));

        assertEquals(ErrorCode.UNEXPECTED_INTRINSIC, ex.getDiagnostic().getCode());
    }

    @Test
    public void Incompatible_Class_Parameters_Throws() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Incompatible_Class_Parameters_Throws", """
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          fx:classParameters="java.lang.String"/>
            """));

        assertEquals(ErrorCode.INTERNAL_ERROR, ex.getDiagnostic().getCode());
        assertEquals("compiler.err.cant.apply.symbol", ex.getDiagnostic().getMessage());
    }

    @SuppressWarnings("unused")
    public static class PaneWithParams extends Pane {
        public PaneWithParams(String param) {}
    }

    @Test
    public void Object_Is_Compiled_With_ClassParameters() {
        Class<?> clazz = TestCompiler.compileClass(
            this, "Object_Is_Compiled_With_ClassParameters", """
                <?import org.jfxcore.compiler.InstantiationTest.*?>
                <PaneWithParams xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                                fx:classParameters="java.lang.String"/>
            """);

        assertEquals(1, clazz.getConstructors().length);
        Constructor<?> ctor = clazz.getConstructors()[0];
        assertEquals(1, ctor.getParameterCount());
        assertEquals(String.class, ctor.getParameters()[0].getType());
    }

}

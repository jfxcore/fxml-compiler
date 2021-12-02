// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.fxml.InverseMethod;
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.MethodReferencedSupport;
import org.jfxcore.compiler.util.TestCompiler;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class FunctionBindingTest extends MethodReferencedSupport {

    public FunctionBindingTest() {
        super("org.jfxcore.compiler.classes.FunctionBindingTest");
    }

    @SuppressWarnings("unused")
    public interface TestDefaultMethod {
        default String defaultMethod(String format, Object... params) {
            return String.format(format, params);
        }
    }

    @SuppressWarnings("unused")
    public static class TestPane extends Pane implements TestDefaultMethod {
        public final ObjectProperty<BindingPathTest.TestContext> context = new SimpleObjectProperty<>(new BindingPathTest.TestContext());

        public DoubleProperty doubleProp = new SimpleDoubleProperty(1);
        public StringProperty stringProp = new SimpleStringProperty("bar");
        public double invariantDoubleVal = 1;
        public String invariantStringVal = "bar";

        public double add(double a, double b) {
            return a + b;
        }

        public static double staticAdd(double a, double b) {
            return a + b;
        }

        public Double boxedAdd(Double a, Double b) {
            return a + b;
        }

        public double sum(double... values) {
            double res = 0;
            for (double value : values) {
                res += value;
            }

            return res;
        }

        private final ObjectProperty<Stringifier> objProp = new SimpleObjectProperty<>();
        public ObjectProperty<Stringifier> objPropProperty() {
            return objProp;
        }
    }

    @SuppressWarnings("unused")
    public static class TestContext {
        private final DoubleProperty doubleVal = new SimpleDoubleProperty(123);
        public DoubleProperty doubleValProperty() {
            return doubleVal;
        }

        public double invariantDoubleVal = 234;
    }

    @SuppressWarnings("unused")
    public static class Stringifier {
        String value;

        public Stringifier(Object o) {
            value = o.toString();
        }

        public Stringifier(char... s) {
            value = new String(s);
        }

        public Stringifier(String s, char... t) {
            value = s + new String(t);
        }
    }

    @Test
    public void Bind_Once_To_Static_Method_With_Incompatible_ReturnType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Once_To_Static_Method_With_Incompatible_ReturnType_Fails", """
                <?import java.lang.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefWidth="{fx:once String.format('foo-%s', invariantDoubleVal)}"/>
            """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Once_To_Static_Method_With_Incompatible_ParamType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Once_To_Static_Method_With_Incompatible_ParamType_Fails", """
                <?import java.lang.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefWidth="{fx:once staticAdd(1, stringProp)}"/>
            """));

        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Once_To_Instance_Method_With_Incompatible_ParamType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Once_To_Instance_Method_With_Incompatible_ParamType_Fails", """
                <?import java.lang.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefWidth="{fx:once add(1, stringProp)}"/>
            """));

        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Once_To_Static_Method_With_Invariant_Param() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Once_To_Static_Method_With_Invariant_Param", """
                <?import java.lang.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:once String.format('foo-%s', invariantDoubleVal)}"/>
            """);

        assertFalse(root.idProperty().isBound());
        assertEquals("foo-1.0", root.getId());
    }

    @Test
    public void Bind_Once_To_Static_Method_With_Observable_Param() {
        String FILENAME = "Bind_Once_To_Static_Method_With_Observable_Param";

        TestPane root = TestCompiler.newInstance(
            this, FILENAME, """
                <?import java.lang.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:once String.format('foo-%s', doubleProp)}"/>
            """);

        assertFalse(root.idProperty().isBound());
        assertEquals("foo-1.0", root.getId());

        root.doubleProp.set(2);
        assertEquals("foo-1.0", root.getId());

        assertReferenced(FILENAME, root, "requireNonNull");
        assertReferenced(FILENAME, root, "doubleValue");
    }

    @Test
    public void Bind_Once_To_Static_Method_With_Multiple_Invariant_Params() {
        String FILENAME = "Bind_Once_To_Static_Method_With_Multiple_Invariant_Params";

        TestPane root = TestCompiler.newInstance(
            this, FILENAME, """
                <?import java.lang.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:once String.format('foo-%s-%s', invariantDoubleVal, invariantStringVal)}"/>
            """);

        assertFalse(root.idProperty().isBound());
        assertEquals("foo-1.0-bar", root.getId());

        assertNotReferenced(FILENAME, root, "requireNonNull");
        assertNotReferenced(FILENAME, root, "doubleValue");
    }

    @Test
    public void Bind_Once_To_Static_Method_With_Multiple_Observable_Params() {
        String FILENAME = "Bind_Once_To_Static_Method_With_Multiple_Observable_Params";

        TestPane root = TestCompiler.newInstance(
            this, FILENAME, """
                <?import java.lang.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:once String.format('foo-%s-%s', doubleProp, stringProp)}"/>
            """);

        assertFalse(root.idProperty().isBound());
        assertEquals("foo-1.0-bar", root.getId());

        root.doubleProp.set(2);
        root.stringProp.set("baz");
        assertEquals("foo-1.0-bar", root.getId());

        assertReferenced(FILENAME, root, "requireNonNull");
        assertReferenced(FILENAME, root, "doubleValue");
        assertReferenced(FILENAME, root, "getValue");
    }

    @Test
    public void Bind_Once_To_Instance_Method_With_Literal_Params() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Once_To_Instance_Method_With_Literal_Params", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefWidth="{fx:once add(10, 20)}"/>
            """);

        assertFalse(root.prefWidthProperty().isBound());
        assertEquals(30.0, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Once_To_Static_Method_With_Literal_Params() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Once_To_Static_Method_With_Literal_Params", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefWidth="{fx:once staticAdd(10, 20)}"/>
            """);

        assertFalse(root.prefWidthProperty().isBound());
        assertEquals(30.0, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Once_To_Nested_Instance_Methods() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Once_To_Nested_Instance_Methods", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefWidth="{fx:once add(add(1, 2), add(3, 4))}"
                          prefHeight="{fx:once boxedAdd(boxedAdd(1, 2), add(3, 4))}"/>
            """);

        assertFalse(root.prefWidthProperty().isBound());
        assertEquals(10.0, root.getPrefWidth(), 0.001);

        assertFalse(root.prefHeightProperty().isBound());
        assertEquals(10.0, root.getPrefHeight(), 0.001);
    }

    @Test
    public void Bind_Once_To_Instance_Method_With_Mixed_Params() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Once_To_Instance_Method_With_Mixed_Params", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefWidth="{fx:once add(invariantDoubleVal, doubleProp)}"
                          prefHeight="{fx:once boxedAdd(invariantDoubleVal, doubleProp)}"/>
            """);

        assertFalse(root.prefWidthProperty().isBound());
        assertEquals(2.0, root.getPrefWidth(), 0.001);

        assertFalse(root.prefHeightProperty().isBound());
        assertEquals(2.0, root.getPrefHeight(), 0.001);
    }

    @Test
    public void Bind_Once_To_Constructor() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Once_To_Constructor", """
                <?import java.lang.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:once String('foo')}"/>
            """);

        assertFalse(root.idProperty().isBound());
        assertEquals("foo", root.getId());
    }

    @Test
    public void Bind_Once_To_Fully_Qualified_Constructor() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Once_To_Fully_Qualified_Constructor", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:once java.lang.String('foo')}"/>
            """);

        assertFalse(root.idProperty().isBound());
        assertEquals("foo", root.getId());
    }

    @Test
    public void Bind_Once_To_Varargs_Constructor() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Once_To_Varargs_Constructor", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          objProp="{fx:once Stringifier(97, 98, 99)}"/>
            """);

        assertEquals("abc", root.objProp.get().value);
    }

    @Test
    public void Bind_Once_To_Constructor_With_More_Specific_Argument() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Once_To_Constructor_With_More_Specific_Argument", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          objProp="{fx:once Stringifier('foo')}"/>
            """);

        assertEquals("foo", root.objProp.get().value);
    }

    @Test
    public void Bind_Once_With_ParentScope_Arguments() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Once_With_ParentScope_Arguments", """
                <?import java.lang.*?>
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <Pane>
                        <Pane id="{fx:once String.format('foo-%s', parent[2]/invariantDoubleVal)}"/>
                    </Pane>
                </TestPane>
            """);

        Pane pane = (Pane)((Pane)root.getChildren().get(0)).getChildren().get(0);
        assertEquals("foo-1.0", pane.getId());
    }

    @Test
    public void Bind_Once_With_ParentScope_Function_And_Argument_Does_Not_Apply_Latest_Value() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Once_With_ParentScope_Function_And_Argument_Does_Not_Apply_Latest_Value", """
                <?import java.lang.*?>
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefHeight="1">
                    <Pane fx:id="pane" prefWidth="2">
                        <Pane prefWidth="{fx:once parent[2]/add(prefHeight, parent[2]/pane.prefWidth)}"/>
                    </Pane>
                </TestPane>
            """);

        Pane pane = (Pane)((Pane)root.getChildren().get(0)).getChildren().get(0);
        assertEquals(-2, pane.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Once_With_Invalid_ParentScope_Function_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Once_With_Invalid_ParentScope_Function_Fails", """
                <?import java.lang.*?>
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <Pane>
                        <Pane prefWidth="{fx:once parent[1]/add(1, 2)}"/>
                    </Pane>
                </TestPane>
            """));

        assertEquals(ErrorCode.METHOD_NOT_FOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Once_To_Static_Method_With_ParentScope_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Once_To_Static_Method_With_ParentScope_Fails", """
                <?import java.lang.*?>
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <Pane id="{fx:once parent/String.format('%s', 2)}"/>
                </TestPane>
            """));

        assertEquals(ErrorCode.INVALID_BINDING_CONTEXT, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Once_To_Interface_Default_Method() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Once_To_Interface_Default_Method", """
                <?import java.lang.*?>
                <?import javafx.fxml.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:once defaultMethod('foo-%s', invariantDoubleVal)}"/>
            """);

        assertEquals("foo-1.0", root.getId());
    }

    @Test
    public void Bind_Once_With_FxConstant_Param() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Once_With_FxConstant_Param", """
                <?import java.lang.*?>
                <?import javafx.fxml.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:once defaultMethod('foo-%s', {fx:constant Double.POSITIVE_INFINITY})}"/>
            """);

        assertEquals("foo-Infinity", root.getId());
    }

    @Test
    public void Bind_Once_With_FxConstant_Param_Fails_With_Unqualified_Constant() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Once_With_FxConstant_Param_Fails_With_Unqualified_Constant", """
                <?import java.lang.*?>
                <?import javafx.fxml.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:once defaultMethod('foo-%s', {fx:constant POSITIVE_INFINITY})}"/>
            """));

        assertEquals(ErrorCode.NOT_FOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Once_With_BindingExpression_Param_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Once_With_BindingExpression_Param_Fails", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:once defaultMethod('foo-%s', {fx:bind doubleProp})}"/>
            """));

        assertEquals(ErrorCode.EXPRESSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Once_With_AssignmentExpression_Param_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
                this, "Bind_Once_With_BindingExpression_Param_Fails", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:once defaultMethod('foo-%s', {fx:once doubleProp})}"/>
            """));

        assertEquals(ErrorCode.EXPRESSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Unidirectional_To_Static_Method_With_Incompatible_ReturnType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Unidirectional_To_Static_Method_With_Incompatible_ReturnType_Fails", """
                <?import java.lang.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefWidth="{fx:bind String.format('foo-%s', invariantDoubleVal)}"/>
            """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Unidirectional_To_Static_Method_With_Incompatible_ParamType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Unidirectional_To_Static_Method_With_Incompatible_ParamType_Fails", """
                <?import java.lang.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefWidth="{fx:bind add(1, stringProp)}"/>
            """));

        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Unidirectional_To_Static_Method_With_Invariant_Param() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_To_Static_Method_With_Invariant_Param", """
                <?import java.lang.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:bind String.format('foo-%s', invariantDoubleVal)}"/>
            """);

        assertTrue(root.idProperty().isBound());
        assertEquals("foo-1.0", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_Static_Method_With_Observable_Param() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_To_Static_Method_With_Observable_Param", """
                <?import java.lang.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:bind String.format('foo-%s', doubleProp)}"/>
            """);

        assertTrue(root.idProperty().isBound());
        assertEquals("foo-1.0", root.getId());

        root.doubleProp.set(2);
        assertEquals("foo-2.0", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_Static_Method_With_Multiple_Observable_Params() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_To_Static_Method_With_Multiple_Observable_Params", """
                <?import java.lang.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:bind String.format('foo-%s-%s', doubleProp, stringProp)}"/>
            """);

        assertTrue(root.idProperty().isBound());
        assertEquals("foo-1.0-bar", root.getId());

        root.doubleProp.set(2);
        assertEquals("foo-2.0-bar", root.getId());

        root.stringProp.set("baz");
        assertEquals("foo-2.0-baz", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_Instance_Method_With_Mixed_Params() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_To_Instance_Method_With_Mixed_Params", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefWidth="{fx:bind add(invariantDoubleVal, doubleProp)}"/>
            """);

        assertTrue(root.prefWidthProperty().isBound());
        assertEquals(2.0, root.getPrefWidth(), 0.001);

        root.doubleProp.set(2);
        assertEquals(3.0, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_Nested_Instance_Methods() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_To_Nested_Instance_Methods", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefWidth="{fx:bind add(add(invariantDoubleVal, doubleProp), add(invariantDoubleVal, doubleProp))}"/>
            """);

        assertTrue(root.prefWidthProperty().isBound());
        assertEquals(4.0, root.getPrefWidth(), 0.001);

        // If we get 4 here, the compiler didn't eliminate the duplicate class for the second nested method
        Class<?>[] classes = root.getClass().getDeclaredClasses();
        assertEquals(3, classes.length);
    }

    @Test
    public void Bind_Unidirectional_To_Nested_Instance_Method_With_Incompatible_ParamType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Unidirectional_To_Nested_Instance_Method_With_Incompatible_ParamType_Fails", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefWidth="{fx:bind add(1, add(1, stringProp))}"/>
            """));

        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Unidirectional_To_Varargs_Instance_Method_With_Mixed_Params() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_To_Varargs_Instance_Method_With_Mixed_Params", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefWidth="{fx:bind sum(invariantDoubleVal, doubleProp, doubleProp)}"/>
            """);

        assertTrue(root.prefWidthProperty().isBound());
        assertEquals(3.0, root.getPrefWidth(), 0.001);

        root.doubleProp.set(2);
        assertEquals(5.0, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_Static_Varargs_Method_With_Indirect_Path_Params() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_To_Static_Varargs_Method_With_Indirect_Path_Params", """
                <?import java.lang.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:bind String.format('%s-%s-%s', context.invariantDoubleVal, context.doubleVal, context.doubleVal)}"/>
            """);

        assertTrue(root.idProperty().isBound());
        assertEquals("234.0-123.0-123.0", root.getId());

        root.context.get().doubleValProperty().set(0);
        assertEquals("234.0-0.0-0.0", root.getId());

        BindingPathTest.TestContext newCtx = new BindingPathTest.TestContext();
        newCtx.invariantDoubleVal = 1;
        newCtx.doubleValProperty().set(2);
        root.context.set(newCtx);
        assertEquals("1.0-2.0-2.0", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_Constructor() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_To_Constructor", """
                <?import java.lang.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:bind String('foo')}"/>
            """);

        assertTrue(root.idProperty().isBound());
        assertEquals("foo", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_Varargs_Constructor() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_To_Varargs_Constructor", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          objProp="{fx:bind Stringifier('foo', 97, 98, 99)}"/>
            """);

        assertTrue(root.objPropProperty().isBound());
        assertEquals("fooabc", root.objProp.get().value);
    }

    @Test
    public void Bind_Unidirectional_To_Constructor_With_More_Specific_Argument() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_To_Constructor_With_More_Specific_Argument", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          objProp="{fx:bind Stringifier('foo')}"/>
            """);

        assertEquals("foo", root.objProp.get().value);
    }

    @Test
    public void Bind_Unidirectional_To_Varargs_Constructor_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Unidirectional_To_Varargs_Constructor_Fails", """
                <?import java.lang.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:bind String(0, 1, 'foo')}"/>
            """));

        assertEquals(ErrorCode.CANNOT_BIND_FUNCTION, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Unidirectional_With_ParentScope_Arguments() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_With_ParentScope_Arguments", """
                <?import java.lang.*?>
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <Pane prefWidth="123">
                        <Pane id="{fx:bind String.format('foo-%s-%s', parent/prefWidth, parent[2]/invariantDoubleVal)}"/>
                    </Pane>
                </TestPane>
            """);

        Pane pane = (Pane)((Pane)root.getChildren().get(0)).getChildren().get(0);
        assertEquals("foo-123.0-1.0", pane.getId());
    }

    @Test
    public void Bind_Unidirectional_With_This_Argument() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_With_This_Argument", """
                <?import java.lang.*?>
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <VBox>
                        <Pane id="{fx:bind String.format('foo-%s', this)}"/>
                    </VBox>
                </TestPane>
            """);

        Pane pane = (Pane)((Pane)root.getChildren().get(0)).getChildren().get(0);
        assertTrue(pane.getId().startsWith("foo-FunctionBindingTest_Bind_Unidirectional_With_This_Argument"));
    }

    @Test
    public void Bind_Unidirectional_With_ParentScope_This_Argument() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_With_ParentScope_This_Argument", """
                <?import java.lang.*?>
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <VBox>
                        <Pane id="{fx:bind String.format('foo-%s', parent/this)}"/>
                    </VBox>
                </TestPane>
            """);

        Pane pane = (Pane)((Pane)root.getChildren().get(0)).getChildren().get(0);
        assertTrue(pane.getId().startsWith("foo-VBox"));
    }

    @Test
    public void Bind_Unidirectional_With_ParentScope_Function() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_With_ParentScope_Function", """
                <?import java.lang.*?>
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <Pane>
                        <Pane prefWidth="{fx:bind parent[2]/add(1, 2)}"/>
                    </Pane>
                </TestPane>
            """);

        Pane pane = (Pane)((Pane)root.getChildren().get(0)).getChildren().get(0);
        assertEquals(3, pane.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_With_ParentScope_Function_And_Argument() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_With_ParentScope_Function_And_Argument", """
                <?import java.lang.*?>
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefHeight="1">
                    <Pane fx:id="pane" prefWidth="2">
                        <Pane prefWidth="{fx:bind parent[2]/add(prefHeight, parent[2]/pane.prefWidth)}"/>
                    </Pane>
                </TestPane>
            """);

        Pane pane = (Pane)((Pane)root.getChildren().get(0)).getChildren().get(0);
        assertEquals(3, pane.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_With_Invalid_ParentScope_Function_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Unidirectional_With_Invalid_ParentScope_Function_Fails", """
                <?import java.lang.*?>
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <Pane>
                        <Pane prefWidth="{fx:bind parent[1]/add(1, 2)}"/>
                    </Pane>
                </TestPane>
            """));

        assertEquals(ErrorCode.METHOD_NOT_FOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Unidirectional_To_Static_Method_With_ParentScope_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Unidirectional_To_Static_Method_With_ParentScope_Fails", """
                <?import java.lang.*?>
                <?import javafx.scene.layout.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <Pane id="{fx:bind parent/String.format('%s', 2)}"/>
                </TestPane>
            """));

        assertEquals(ErrorCode.INVALID_BINDING_CONTEXT, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Unidirectional_With_FxConstant_Param() {
        TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_With_FxConstant_Param", """
                <?import java.lang.*?>
                <?import javafx.fxml.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:bind defaultMethod('foo-%s', {fx:constant Double.POSITIVE_INFINITY})}"/>
            """);

        assertEquals("foo-Infinity", root.getId());
    }

    @Test
    public void Bind_Unidirectional_With_BindingExpression_Param_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Unidirectional_With_BindingExpression_Param_Fails", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:bind defaultMethod('foo-%s', {fx:bind doubleProp})}"/>
            """));

        assertEquals(ErrorCode.EXPRESSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Unidirectional_With_AssignmentExpression_Param_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Unidirectional_With_AssignmentExpression_Param_Fails", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:bind defaultMethod('foo-%s', {fx:once doubleProp})}"/>
            """));

        assertEquals(ErrorCode.EXPRESSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
    }

    @SuppressWarnings("unused")
    public static class BidirectionalIndirect {
        public DoubleProperty doubleProp = new SimpleDoubleProperty(1);
        public StringProperty stringProp = new SimpleStringProperty("1.0");
    }

    @SuppressWarnings("unused")
    public static class BidirectionalTestPane extends Pane {
        public BidirectionalIndirect indirect = new BidirectionalIndirect();
        public BidirectionalIndirect nullIndirect;

        public DoubleProperty doubleProp = new SimpleDoubleProperty(1);
        public StringProperty stringProp = new SimpleStringProperty("5");
        public BooleanProperty boolProp = new SimpleBooleanProperty(false);

        public ObservableBooleanValue readOnlyObservableBoolProperty() {
            return boolProp;
        }

        public int doubleToStringCalls;
        public int stringToDoubleCalls;

        @InverseMethod("instanceNot")
        public boolean instanceNot(boolean value) {
            return !value;
        }

        @InverseMethod("staticNot")
        public static boolean staticNot(boolean value) {
            return !value;
        }

        @InverseMethod("stringToDouble")
        public String doubleToString(double value) {
            ++doubleToStringCalls;
            return Double.toString(value);
        }

        @InverseMethod("doubleToString")
        public double stringToDouble(String value) {
            ++stringToDoubleCalls;
            return value != null ? Double.parseDouble(value) : 0;
        }

        @InverseMethod("staticNot2")
        public boolean instanceNot2(boolean value) {
            return !value;
        }

        @InverseMethod("instanceNot2")
        public static boolean staticNot2(boolean value) {
            return !value;
        }

        public String noInverseMethod(double value) {
            throw new UnsupportedOperationException();
        }

        public boolean customInverseMethod(boolean value) {
            return !value;
        }

        public String invalidInverseMethod(String value) {
            throw new UnsupportedOperationException();
        }

        public ObjectProperty<DoubleString> doubleStringProp = new SimpleObjectProperty<>(new DoubleString(5));

        public double doubleStringToDouble(DoubleString value) {
            return value.value;
        }
    }

    public static class DoubleString {
        final double value;
        public DoubleString(double value) {
            this.value = value;
        }
    }

    @Test
    public void Bind_Bidirectional_To_Static_Method_With_Two_Parameters_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Bidirectional_To_Static_Method_With_Two_Parameters_Fails", """
                <?import java.lang.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefWidth="{fx:sync String.format('%s', doubleProp)}"/>
            """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_METHOD_PARAM_COUNT, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Bidirectional_To_Instance_Method_With_Two_Parameters_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Bidirectional_To_Instance_Method_With_Two_Parameters_Fails", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefWidth="{fx:sync add(invariantDoubleVal, doubleProp)}"/>
            """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_METHOD_PARAM_COUNT, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Bidirectional_To_Indirect_DoubleProperty() {
        BidirectionalTestPane root = TestCompiler.newInstance(
            this, "Bind_Bidirectional_To_Indirect_DoubleProperty", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <BidirectionalTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:sync doubleToString(indirect.doubleProp)}"/>
            """);

        assertEquals("1.0", root.getId());
    }

    @Test
    public void Bind_Bidirectional_To_Indirect_StringProperty() {
        BidirectionalTestPane root = TestCompiler.newInstance(
            this, "Bind_Bidirectional_To_Indirect_StringProperty", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <BidirectionalTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefWidth="{fx:sync stringToDouble(indirect.stringProp)}"/>
            """);

        assertEquals(1.0, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Bidirectional_To_NullIndirect_DoubleProperty() {
        BidirectionalTestPane root = TestCompiler.newInstance(
            this, "Bind_Bidirectional_To_NullIndirect_DoubleProperty", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <BidirectionalTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:sync doubleToString(nullIndirect.doubleProp)}"/>
            """);

        assertEquals("0.0", root.getId());
    }

    @Test
    public void Bind_Bidirectional_To_NullIndirect_StringProperty() {
        BidirectionalTestPane root = TestCompiler.newInstance(
            this, "Bind_Bidirectional_To_NullIndirect_StringProperty", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <BidirectionalTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefWidth="{fx:sync stringToDouble(nullIndirect.stringProp)}"/>
            """);

        assertEquals(0.0, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Bidirectional_To_Method_With_InverseMethod() {
        BidirectionalTestPane root = TestCompiler.newInstance(
            this, "Bind_Bidirectional_To_Method_With_InverseMethod", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <BidirectionalTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:sync doubleToString(doubleProp)}"
                          prefWidth="{fx:sync stringToDouble(stringProp)}"
                          visible="{fx:sync instanceNot(boolProp)}"/>
            """);

        assertEquals(1, root.doubleToStringCalls);
        assertEquals(1, root.stringToDoubleCalls);
        assertEquals("1.0", root.getId());
        assertEquals(5, root.getPrefWidth(), 0.001);

        root.setId("2");
        assertEquals(1, root.doubleToStringCalls);
        assertEquals(2, root.stringToDoubleCalls);
        assertEquals(2, root.doubleProp.get(), 0.001);

        root.doubleProp.set(3);
        assertEquals(2, root.doubleToStringCalls);
        assertEquals(2, root.stringToDoubleCalls);
        assertEquals("3.0", root.getId());

        root.setPrefWidth(123);
        assertEquals(3, root.doubleToStringCalls);
        assertEquals(2, root.stringToDoubleCalls);
        assertEquals("123.0", root.stringProp.get());

        root.setPrefWidth(2.5);
        assertEquals(4, root.doubleToStringCalls);
        assertEquals(2, root.stringToDoubleCalls);
        assertEquals("2.5", root.stringProp.get());

        root.stringProp.set("0");
        assertEquals(4, root.doubleToStringCalls);
        assertEquals(3, root.stringToDoubleCalls);
        assertEquals(0, root.getPrefWidth(), 0.001);

        assertTrue(root.isVisible());
        root.setVisible(false);
        assertTrue(root.boolProp.get());
    }

    @Test
    public void Bind_Bidirectional_To_Static_Method_With_InverseMethod() {
        BidirectionalTestPane root = TestCompiler.newInstance(
            this, "Bind_Bidirectional_To_Static_Method_With_InverseMethod", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <BidirectionalTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          visible="{fx:sync BidirectionalTestPane.staticNot(boolProp)}"/>
            """);

        assertTrue(root.isVisible());
        assertFalse(root.boolProp.get());
        root.setVisible(false);
        assertTrue(root.boolProp.get());
    }

    @Test
    public void Bind_Bidirectional_To_Instance_Method_With_Static_InverseMethod() {
        BidirectionalTestPane root = TestCompiler.newInstance(
            this, "Bind_Bidirectional_To_Instance_Method_With_Static_InverseMethod", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <BidirectionalTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          visible="{fx:sync instanceNot2(boolProp)}"/>
            """);

        assertTrue(root.isVisible());
        assertFalse(root.boolProp.get());
        root.setVisible(false);
        assertTrue(root.boolProp.get());
    }

    @Test
    public void Bind_Bidirectional_To_Method_With_Custom_InverseMethod() {
        BidirectionalTestPane root = TestCompiler.newInstance(
            this, "Bind_Bidirectional_To_Method_With_Custom_InverseMethod", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <BidirectionalTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          visible="{fx:sync instanceNot(boolProp); inverseMethod=customInverseMethod}"/>
            """);

        assertTrue(root.isVisible());
        root.setVisible(false);
        assertTrue(root.boolProp.get());
    }

    @Test
    public void Bind_Bidirectional_To_Static_Method_With_Custom_InverseMethod() {
        BidirectionalTestPane root = TestCompiler.newInstance(
            this, "Bind_Bidirectional_To_Static_Method_With_Custom_InverseMethod", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <BidirectionalTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          visible="{fx:sync BidirectionalTestPane.staticNot(boolProp); inverseMethod=customInverseMethod}"/>
            """);

        assertTrue(root.isVisible());
        root.setVisible(false);
        assertTrue(root.boolProp.get());
    }

    @Test
    public void Bind_Bidirectional_To_Method_With_InverseConstructor() {
        BidirectionalTestPane root = TestCompiler.newInstance(
            this, "Bind_Bidirectional_To_Method_With_InverseConstructor", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <BidirectionalTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          prefWidth="{fx:sync doubleStringToDouble(doubleStringProp); inverseMethod=DoubleString}"/>
            """);

        assertEquals(5, root.getPrefWidth(), 0.001);
        root.setPrefWidth(4);
        assertEquals(4, root.doubleStringProp.get().value, 0.001);
    }

    @Test
    public void Bind_Bidirectional_To_Method_Without_InverseMethod_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Bidirectional_To_Method_Without_InverseMethod_Fails", """
                <?import javafx.fxml.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <BidirectionalTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:sync noInverseMethod(doubleProp)}"/>
            """));

        assertEquals(ErrorCode.METHOD_NOT_INVERTIBLE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Bidirectional_To_Method_With_Invalid_Custom_InverseMethod_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Bidirectional_To_Method_With_Invalid_Custom_InverseMethod_Fails", """
                <?import javafx.fxml.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <BidirectionalTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:sync noInverseMethod(doubleProp); inverseMethod=invalidInverseMethod}"/>
            """));

        assertEquals(ErrorCode.INVALID_INVERSE_METHOD, ex.getDiagnostic().getCode());

        ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Bidirectional_To_Method_With_Invalid_Custom_InverseMethod_Fails", """
                <?import javafx.fxml.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <BidirectionalTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:sync noInverseMethod(doubleProp); inverseMethod=java.lang.String.format}"/>
            """));

        assertEquals(ErrorCode.INVALID_INVERSE_METHOD, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Bidirectional_To_Method_With_Nonexistent_Custom_InverseMethod_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Bidirectional_To_Method_With_Nonexistent_Custom_InverseMethod_Fails", """
                <?import javafx.fxml.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <BidirectionalTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:sync noInverseMethod(doubleProp); inverseMethod=doesNotExist}"/>
            """));

        assertEquals(ErrorCode.METHOD_NOT_FOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Bidirectional_To_Method_With_ReadOnlyProperty_Argument_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Bidirectional_To_Method_With_ReadOnlyProperty_Argument_Fails", """
                <?import javafx.fxml.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <BidirectionalTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:sync instanceNot(readOnlyObservableBool)}"/>
            """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Bidirectional_To_Method_With_Unsuitable_Parameter_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Bidirectional_To_Method_With_Unsuitable_Parameter_Fails", """
                <?import javafx.fxml.*?>
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <BidirectionalTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:sync instanceNot(instanceNot(boolProp))}"/>
            """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_METHOD_PARAM_KIND, ex.getDiagnostic().getCode());
    }

    @SuppressWarnings("unused")
    public static class OverloadTestPane extends Pane {
        private final StringProperty stringProp = new SimpleStringProperty("bar");

        public StringProperty stringPropProperty() {
            return stringProp;
        }

        public String overloadedMethod(Object value) {
            return "Object";
        }

        public String overloadedMethod(String value) {
            return "String";
        }

        public MoreDerived d = new MoreDerived();

        public String overloadedMethod(Derived a, Derived b, Base c) {
            return null;
        }

        public String overloadedMethod(Derived a, Base b, Derived c) {
            return null;
        }

        public String overloadedMethod(Base a, Derived b, Derived c) {
            return null;
        }
    }

    public static class Base {}

    public static class Derived extends Base {}

    public static class MoreDerived extends Derived {}

    @Test
    public void Overloaded_Method_Is_Selected_Correctly() {
        OverloadTestPane root = TestCompiler.newInstance(
            this, "Overloaded_Method_Is_Selected_Correctly", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.OverloadTestPane?>
                <OverloadTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:bind overloadedMethod('ignored')}" stringProp="{fx:bind overloadedMethod(0)}"/>
            """);

        assertEquals("String", root.getId());
        assertEquals("Object", root.stringProp.get());
    }

    @Test
    public void Ambiguous_Method_Call_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Ambiguous_Method_Call_Fails", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.OverloadTestPane?>
                <OverloadTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          id="{fx:bind overloadedMethod(d, d, d)}"/>
            """));

        assertEquals(ErrorCode.AMBIGUOUS_METHOD_CALL, ex.getDiagnostic().getCode());
    }

    @SuppressWarnings("unused")
    public static class GenericTestPane<T> extends Pane {
        public GenericTestPane() { setPrefWidth(123); }

        public String stringify(T value) {
            return value.toString();
        }
    }

    @Test
    public void Bind_Once_To_Generic_Method_Of_Raw_Type_Works() {
        GenericTestPane<?> root = TestCompiler.newInstance(
            this, "Bind_Once_To_Generic_Method_Of_Raw_Type_Works", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <GenericTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        id="{fx:once stringify(prefWidth)}"/>
            """);

        assertEquals("123.0", root.getId());
    }

    @Test
    public void Bind_Once_To_Generic_Method_Argument_Out_Of_Bound_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Once_To_Generic_Method_Argument_Out_Of_Bound_Fails", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <GenericTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        fx:typeArguments="java.lang.String" id="{fx:once stringify(prefWidth)}"/>
            """));

        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Unidirectional_To_Generic_Method_Of_Raw_Type_Works() {
        GenericTestPane<?> root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_To_Generic_Method_Of_Raw_Type_Works", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <GenericTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        id="{fx:bind stringify(prefWidth)}"/>
            """);

        assertEquals("123.0", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_Generic_Method_Argument_Out_Of_Bound_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Unidirectional_To_Generic_Method_Argument_Out_Of_Bound_Fails", """
                <?import org.jfxcore.compiler.bindings.FunctionBindingTest.*?>
                <GenericTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                        fx:typeArguments="java.lang.String" id="{fx:bind stringify(prefWidth)}"/>
            """));

        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
    }

}

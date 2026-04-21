// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.TestExtension;
import org.jfxcore.markup.InverseMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.util.Locale;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class FunctionBindingTest extends CompilerTestBase {

    private void assertNewFunctionExpr(Object root, int num) {
        for (int i = 0; i < num; ++i) {
            assertNewExpr(root, NameHelper.getMangledClassName("Function$") + i);
        }

        assertNotNewExpr(root, NameHelper.getMangledClassName("Function$") + num);
    }

    @SuppressWarnings("unused")
    public interface TestDefaultMethod {
        default String defaultMethod(String format, Object... params) {
            return String.format(format, params);
        }
    }

    @SuppressWarnings("unused")
    public static class TestPane extends Pane implements TestDefaultMethod {
        public final ObjectProperty<TestContext> context = new SimpleObjectProperty<>(new TestContext());

        public DoubleProperty doubleProp = new SimpleDoubleProperty(1);
        public IntegerProperty selectedIndex = new SimpleIntegerProperty(0);
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

        public Object[] formatMethodArgs;

        public String formatMethod(String format, Object... params) {
            formatMethodArgs = params;
            return String.format(format, params);
        }

        public <T> T identity(T value) {
            return value;
        }

        public int listSize(ObservableList<?> value) {
            return value.size();
        }

        private final ObjectProperty<Stringifier> objProp = new SimpleObjectProperty<>();
        public ObjectProperty<Stringifier> objPropProperty() {
            return objProp;
        }

        public final ListProperty<String> listProp = new SimpleListProperty<>(
            FXCollections.observableArrayList("foo", "bar", "baz"));

        public final ObjectProperty<ObservableList<String>> itemsProp =
            new SimpleObjectProperty<>(FXCollections.observableArrayList("foo", "bar", "baz"));

        public final ObjectProperty<ObservableList<Integer>> indexesProp =
            new SimpleObjectProperty<>(FXCollections.observableArrayList(1, 2));

        public final Container1 c1 = new Container1(new Container2(new DecimalFormat("000")));
        public Container1 nullC1;
        public final ObjectProperty<Container1> observableC1 = new SimpleObjectProperty<>();
        public record Container1(Container2 c2) {}
        public record Container2(DecimalFormat fmt) {}

        public final DecimalFormat fmt = new DecimalFormat("000");

        public StringProperty methodReturningProperty(String value) {
            stringProp.set(value);
            return stringProp;
        }

        private final ObservableList<String> items = FXCollections.observableArrayList("foo", "bar", "baz");
        private final ObservableList<Integer> indexes = FXCollections.observableArrayList(1, 2);
        private final ObservableList<Long> longIndexes = FXCollections.observableArrayList(1L, 2L);

        public ObservableList<String> getItems() {
            return items;
        }

        public ObservableList<Integer> getIndexes() {
            return indexes;
        }

        public ObservableList<Long> getLongIndexes() {
            return longIndexes;
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
    public static class GcSourceHolder {
        public static DoubleProperty doubleProp = new SimpleDoubleProperty(1);
        public static ObjectProperty<BidirectionalIndirect> observableIndirect =
            new SimpleObjectProperty<>(new BidirectionalIndirect());

        @InverseMethod("stringToDouble")
        public static String doubleToString(double value) {
            return Double.toString(value);
        }

        public static double stringToDouble(String value) {
            return value != null ? Double.parseDouble(value) : 0;
        }

        public static void reset() {
            doubleProp = new SimpleDoubleProperty(1);
            observableIndirect = new SimpleObjectProperty<>(new BidirectionalIndirect());
        }
    }

    private WeakReference<Pane> createUnidirectionalGcTarget() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                  id="${String.format('foo-%s', GcSourceHolder.doubleProp)}"/>
        """);

        assertTrue(root.idProperty().isBound());
        assertEquals("foo-1.0", root.getId());
        return new WeakReference<>(root);
    }

    private WeakReference<Pane> createBidirectionalGcTarget(ObjectProperty<BidirectionalIndirect> source) {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                  id="#{GcSourceHolder.doubleToString(GcSourceHolder.observableIndirect.doubleProp)}"/>
        """);

        assertFalse(root.idProperty().isBound());
        assertEquals("1.0", root.getId());
        source.get().doubleProp.set(2);
        assertEquals("2.0", root.getId());
        return new WeakReference<>(root);
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
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="$String.format('foo-%s', invariantDoubleVal)"/>
        """));

        assertEquals(ErrorCode.CANNOT_BIND_FUNCTION, ex.getDiagnostic().getCode());
        assertEquals(2, ex.getDiagnostic().getCauses().length);
        assertEquals(ErrorCode.INCOMPATIBLE_RETURN_VALUE, ex.getDiagnostic().getCauses()[0].getCode());
        assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[1].getCode());
        assertCodeHighlight("String.format", ex);
    }

    @Test
    public void Bind_Once_To_Static_Method_With_Incompatible_ParamType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="$staticAdd(1, stringProp)"/>
        """));

        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
        assertCodeHighlight("stringProp", ex);
    }

    @Test
    public void Bind_Once_To_Instance_Method_With_Incompatible_ParamType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="$add(1, stringProp)"/>
        """));

        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
        assertCodeHighlight("stringProp", ex);
    }

    @Test
    public void Bind_Once_To_Static_Method_With_Invariant_Param() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$String.format('foo-%s', invariantDoubleVal)"/>
        """);

        assertNewFunctionExpr(root, 0);
        assertFalse(root.idProperty().isBound());
        assertEquals("foo-1.0", root.getId());
    }

    @Test
    public void Bind_Once_To_Static_Method_With_Observable_Param() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$String.format('foo-%s', doubleProp)"/>
        """);

        assertNewFunctionExpr(root, 0);
        assertFalse(root.idProperty().isBound());
        assertEquals("foo-1.0", root.getId());

        root.doubleProp.set(2);
        assertEquals("foo-1.0", root.getId());

        assertMethodCall(root, ms -> ms.stream().anyMatch(m -> m.name().equals("requireNonNull")));
        assertMethodCall(root, ms -> ms.stream().anyMatch(m -> m.name().equals("doubleValue")));
    }

    @Test
    public void Bind_Once_To_Static_Method_With_Multiple_Invariant_Params() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$String.format('foo-%s-%s', invariantDoubleVal, invariantStringVal)"/>
        """);

        assertNewFunctionExpr(root, 0);
        assertFalse(root.idProperty().isBound());
        assertEquals("foo-1.0-bar", root.getId());

        assertMethodCall(root, ms -> ms.stream().noneMatch(m -> m.name().equals("requireNonNull")));
        assertMethodCall(root, ms -> ms.stream().noneMatch(m -> m.name().equals("doubleValue")));
    }

    @Test
    public void Bind_Once_To_Static_Method_With_Multiple_Observable_Params() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$String.format('foo-%s-%s', doubleProp, stringProp)"/>
        """);

        assertNewFunctionExpr(root, 0);
        assertFalse(root.idProperty().isBound());
        assertEquals("foo-1.0-bar", root.getId());

        root.doubleProp.set(2);
        root.stringProp.set("baz");
        assertEquals("foo-1.0-bar", root.getId());

        assertMethodCall(root, ms -> ms.stream().anyMatch(m -> m.name().equals("requireNonNull")));
        assertMethodCall(root, ms -> ms.stream().anyMatch(m -> m.name().equals("doubleValue")));
        assertMethodCall(root, ms -> ms.stream().anyMatch(m -> m.name().equals("getValue")));
    }

    @Test
    public void Bind_Once_To_Static_Method_With_Same_Name_As_Instance_Method() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$Double.toString(doubleProp)"/>
        """);

        assertNewFunctionExpr(root, 0);
        assertFalse(root.idProperty().isBound());
        assertEquals("1.0", root.getId());
    }

    @Test
    public void Bind_Once_To_Instance_Method_With_Literal_Params() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="$add(10, 20)"/>
        """);

        assertNewFunctionExpr(root, 0);
        assertFalse(root.prefWidthProperty().isBound());
        assertEquals(30.0, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Once_To_Static_Method_With_Literal_Params() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="$staticAdd(10, 20)"/>
        """);

        assertNewFunctionExpr(root, 0);
        assertFalse(root.prefWidthProperty().isBound());
        assertEquals(30.0, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Once_To_Nested_Instance_Methods() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="$add(add(1, 2), add(3, 4))"
                      prefHeight="$boxedAdd(boxedAdd(1.0, 2.0), add(3, 4))"/>
        """);

        assertNewFunctionExpr(root, 0);
        assertFalse(root.prefWidthProperty().isBound());
        assertEquals(10.0, root.getPrefWidth(), 0.001);
        assertFalse(root.prefHeightProperty().isBound());
        assertEquals(10.0, root.getPrefHeight(), 0.001);
    }

    @Test
    public void Bind_Once_To_Instance_Method_With_Mixed_Params() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="$add(invariantDoubleVal, doubleProp)"
                      prefHeight="$boxedAdd(invariantDoubleVal, doubleProp)"/>
        """);

        assertNewFunctionExpr(root, 0);
        assertFalse(root.prefWidthProperty().isBound());
        assertEquals(2.0, root.getPrefWidth(), 0.001);
        assertFalse(root.prefHeightProperty().isBound());
        assertEquals(2.0, root.getPrefHeight(), 0.001);
    }

    @Test
    public void Bind_Once_To_Instance_Method_Of_LocalObject() {
        Pane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <fx:define>
                    <Double fx:id="val">7</Double>
                </fx:define>
                <Label text="$fmt.format(val)"/>
            </TestPane>
        """);

        assertNewFunctionExpr(root, 0);
        Label label = (Label)root.getChildren().get(0);
        assertEquals("007", label.getText());
    }

    @Test
    public void Bind_Once_To_Instance_Method_Of_Indirect_Object() {
        Pane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Label text="$c1.c2.fmt.format(7)"/>
            </TestPane>
        """);

        assertNewFunctionExpr(root, 0);
        Label label = (Label)root.getChildren().get(0);
        assertEquals("007", label.getText());
    }

    @Test
    public void Bind_Once_To_Instance_Method_Of_Unresolved_Indirect_Object_Returns_Default_Value() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$nullC1.c2.fmt.format(7)"/>
        """);

        assertNewFunctionExpr(root, 0);
        assertNull(root.getId());
    }

    @Test
    public void Bind_Once_To_Constructor() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$String('foo')"/>
        """);

        assertNewFunctionExpr(root, 0);
        assertFalse(root.idProperty().isBound());
        assertEquals("foo", root.getId());
    }

    @Test
    public void Bind_Once_To_Fully_Qualified_Constructor() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$java.lang.String('foo')"/>
        """);

        assertNewFunctionExpr(root, 0);
        assertFalse(root.idProperty().isBound());
        assertEquals("foo", root.getId());
    }

    @Test
    public void Bind_Once_To_Varargs_Constructor() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      objProp="$Stringifier(97, 98, 99)"/>
        """));

        assertEquals(ErrorCode.CANNOT_BIND_FUNCTION, ex.getDiagnostic().getCode());
        assertEquals(3, ex.getDiagnostic().getCauses().length);
        assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[0].getCode());
        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCauses()[1].getCode());
        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCauses()[2].getCode());
        assertCodeHighlight("Stringifier", ex);
    }

    @Test
    public void Bind_Once_To_Constructor_With_More_Specific_Argument() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      objProp="$Stringifier('foo')"/>
        """);

        assertNewFunctionExpr(root, 0);
        assertEquals("foo", root.objProp.get().value);
    }

    @Test
    public void Bind_Once_With_ParentScope_Arguments() {
        TestPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Pane>
                    <Pane id="$String.format('foo-%s', parent[1]/invariantDoubleVal)"/>
                </Pane>
            </TestPane>
        """);

        assertNewFunctionExpr(root, 0);
        Pane pane = (Pane)((Pane)root.getChildren().get(0)).getChildren().get(0);
        assertEquals("foo-1.0", pane.getId());
    }

    @Test
    public void Bind_Once_With_ParentScope_Function_And_Argument_Does_Not_Apply_Latest_Value() {
        TestPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefHeight="1">
                <Pane fx:id="pane" prefWidth="2">
                    <Pane prefWidth="$parent[1]/add(prefHeight, parent[1]/pane.prefWidth)"/>
                </Pane>
            </TestPane>
        """);

        assertNewFunctionExpr(root, 0);
        Pane pane = (Pane)((Pane)root.getChildren().get(0)).getChildren().get(0);
        assertEquals(-2, pane.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Once_With_Invalid_ParentScope_Function_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Pane>
                    <Pane prefWidth="$parent[0]/add(1, 2)"/>
                </Pane>
            </TestPane>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("parent[0]/add", ex);
    }

    @Test
    public void Bind_Once_To_Static_Method_With_ParentScope_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Pane id="$parent/String.format('%s', 2)"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.BINDING_CONTEXT_NOT_APPLICABLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("parent", ex);
    }

    @Test
    public void Bind_Once_To_Interface_Default_Method() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$defaultMethod('foo-%s', invariantDoubleVal)"/>
        """);

        assertNewFunctionExpr(root, 0);
        assertEquals("foo-1.0", root.getId());
    }

    @Test
    public void Bind_Once_With_Unexpected_FxValue_Param_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$defaultMethod('foo-%s', {fx:value})"/>
        """));

        assertEquals(ErrorCode.UNEXPECTED_INTRINSIC, ex.getDiagnostic().getCode());
        assertCodeHighlight("{fx:value}", ex);
    }

    @Test
    public void Bind_Once_With_FxConstant_Param() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$defaultMethod('foo-%s', {Double fx:constant=POSITIVE_INFINITY})"/>
        """);

        assertNewFunctionExpr(root, 0);
        assertEquals("foo-Infinity", root.getId());
    }

    @Test
    public void Bind_Once_With_BindingExpression_Param_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$defaultMethod('foo-%s', ${doubleProp})"/>
        """));

        assertEquals(ErrorCode.EXPRESSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("${doubleProp}", ex);
    }

    @Test
    public void Bind_Once_With_AssignmentExpression_Param_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$defaultMethod('foo-%s', $doubleProp)"/>
        """));

        assertEquals(ErrorCode.EXPRESSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("$doubleProp", ex);
    }

    @Test
    public void Null_Literal_Cannot_Have_Operator() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$defaultMethod('foo-%s', !null)"/>
        """));

        assertEquals(ErrorCode.UNEXPECTED_EXPRESSION, ex.getDiagnostic().getCode());
        assertCodeHighlight("null", ex);
    }

    @Test
    public void Boolean_Literal_Cannot_Have_Operator() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$defaultMethod('foo-%s', !true)"/>
        """));

        assertEquals(ErrorCode.UNEXPECTED_EXPRESSION, ex.getDiagnostic().getCode());
        assertCodeHighlight("true", ex);
    }

    @Test
    public void Bind_Once_With_Boolean_And_Null_Literals() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$formatMethod('foo-%s', 123.5, true, null)"/>
        """);

        assertArrayEquals(new Object[] { 123.5, true, null }, root.formatMethodArgs);
    }

    @Test
    public void Bind_Once_To_List_Get() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$items.get(0)"/>
        """);

        assertEquals("foo", root.getId());
    }

    @Test
    public void Bind_Once_To_List_Get_With_Nested_Generic_Method_TypeWitness() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$items.get(identity<Integer>(1))"/>
        """);

        assertEquals("bar", root.getId());
    }

    @Test
    public void Bind_Once_To_List_Get_With_Nested_Receiver_Generic_Return_Type() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$items.get(indexes.get(0))"/>
        """);

        assertEquals("bar", root.getId());
    }

    @Test
    public void Bind_Once_To_List_Get_With_Nested_Receiver_Generic_Return_Type_Mismatch_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$items.get(longIndexes.get(0))"/>
        """));

        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
        assertMessageContains("java.util.List.get(int): argument #1 cannot be assigned from java.lang.Long", ex);
        assertCodeHighlight("longIndexes.get(0)", ex);
    }

    @Test
    public void Bind_Unidirectional_With_Boolean_And_Null_Literals() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${formatMethod('foo-%s', prefWidth, true, null)}"/>
        """);

        root.getId(); // Force evaluation of the binding expression
        assertArrayEquals(new Object[] { -1.0, true, null }, root.formatMethodArgs);
        root.setPrefWidth(123.5);
        root.getId();
        assertArrayEquals(new Object[] { 123.5, true, null }, root.formatMethodArgs);
    }

    @Test
    public void Bind_Unidirectional_To_Static_Method_With_Incompatible_ReturnType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="${String.format('foo-%s', invariantDoubleVal)}"/>
        """));

        assertEquals(ErrorCode.CANNOT_BIND_FUNCTION, ex.getDiagnostic().getCode());
        assertEquals(2, ex.getDiagnostic().getCauses().length);
        assertEquals(ErrorCode.INCOMPATIBLE_RETURN_VALUE, ex.getDiagnostic().getCauses()[0].getCode());
        assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[1].getCode());
        assertCodeHighlight("String.format", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Static_Method_With_Incompatible_ParamType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="${add(1, stringProp)}"/>
        """));

        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
        assertCodeHighlight("stringProp", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Static_Method_With_Invariant_Param_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${String.format('foo-%s', invariantDoubleVal)}"/>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("String.format('foo-%s', invariantDoubleVal)", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Static_Method_With_Observable_Param() {
        TestPane root = compileAndRun( """
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${String.format('foo-%s', doubleProp)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.idProperty().isBound());
        assertEquals("foo-1.0", root.getId());

        root.doubleProp.set(2);
        assertEquals("foo-2.0", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_Static_Method_With_Observable_Param_Survives_GC() {
        TestPane root = compileAndRun( """
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${String.format('foo-%s', doubleProp)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.idProperty().isBound());
        assertEquals("foo-1.0", root.getId());

        for (int i = 0; i < 5; ++i) {
            gc();
        }

        root.doubleProp.set(2);
        assertEquals("foo-2.0", root.getId());

        for (int i = 0; i < 5; ++i) {
            gc();
        }

        root.doubleProp.set(3);
        assertEquals("foo-3.0", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_Static_Method_With_Observable_Param_Does_Not_Keep_Target_Alive() {
        GcSourceHolder.reset();
        DoubleProperty source = GcSourceHolder.doubleProp;
        WeakReference<Pane> ref = createUnidirectionalGcTarget();

        assertGarbageCollected(ref);

        source.set(2);
        assertNull(ref.get());
    }

    @Test
    public void Bind_Unidirectional_To_Static_Method_With_Multiple_Observable_Params() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${String.format('foo-%s-%s', doubleProp, stringProp)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.idProperty().isBound());
        assertEquals("foo-1.0-bar", root.getId());

        root.doubleProp.set(2);
        assertEquals("foo-2.0-bar", root.getId());

        root.stringProp.set("baz");
        assertEquals("foo-2.0-baz", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_Static_Method_With_Same_Name_As_Instance_Method() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${Double.toString(doubleProp)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.idProperty().isBound());
        assertEquals("1.0", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_Static_Method_With_Path_Expression() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${Integer.toString(listProp.size)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.idProperty().isBound());
        assertEquals("3", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_Instance_Method_With_Mixed_Params() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="${add(invariantDoubleVal, doubleProp)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.prefWidthProperty().isBound());
        assertEquals(2.0, root.getPrefWidth(), 0.001);

        root.doubleProp.set(2);
        assertEquals(3.0, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_Instance_Method_Of_LocalObject() {
        Pane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Label text="${fmt.format(self/prefWidth)}" prefWidth="7"/>
            </TestPane>
        """);

        assertNewFunctionExpr(root, 1);
        Label label = (Label)root.getChildren().get(0);
        assertEquals("007", label.getText());
        label.setPrefWidth(10);
        assertEquals("010", label.getText());
    }

    @Test
    public void Bind_Unidirectional_To_Instance_Method_Of_Indirect_Object() {
        Pane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Label text="${c1.c2.fmt.format(self/prefWidth)}" prefWidth="7"/>
            </TestPane>
        """);

        assertNewFunctionExpr(root, 1);
        Label label = (Label)root.getChildren().get(0);
        assertEquals("007", label.getText());
        label.setPrefWidth(10);
        assertEquals("010", label.getText());
    }

    @Test
    public void Bind_Unidirectional_To_Instance_Method_Of_Unresolved_Observable_Indirect_Object_Returns_Default_Value() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="7" id="${observableC1.c2.fmt.format(prefWidth)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.idProperty().isBound());
        assertNull(root.getId());

        root.observableC1.set(new TestPane.Container1(new TestPane.Container2(new DecimalFormat("000"))));
        assertEquals("007", root.getId());

        root.setPrefWidth(10);
        assertEquals("010", root.getId());

        root.observableC1.set(null);
        assertNull(root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_Nested_Instance_Methods() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="${add(add(invariantDoubleVal, doubleProp), add(invariantDoubleVal, doubleProp))}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.prefWidthProperty().isBound());
        assertEquals(4.0, root.getPrefWidth(), 0.001);

        // If we get 4 here, the compiler didn't eliminate the duplicate class for the second nested method.
        // This is most likely caused by an incorrect implementation of EmitObservableFunctionNode.equals/hashCode.
        Class<?>[] classes = root.getClass().getDeclaredClasses();
        assertEquals(3, classes.length);
    }

    @Test
    public void Bind_Unidirectional_To_Nested_Instance_Method_With_Incompatible_ParamType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="${add(1, add(1, stringProp))}"/>
        """));

        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
        assertCodeHighlight("stringProp", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Varargs_Instance_Method_With_Mixed_Params() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="${sum(invariantDoubleVal, doubleProp, doubleProp)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.prefWidthProperty().isBound());
        assertEquals(3.0, root.getPrefWidth(), 0.001);

        root.doubleProp.set(2);
        assertEquals(5.0, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_Static_Varargs_Method_With_Indirect_Path_Params() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${String.format('%s-%s-%s', context.invariantDoubleVal, context.doubleVal, context.doubleVal)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.idProperty().isBound());
        assertEquals("234.0-123.0-123.0", root.getId());

        root.context.get().doubleValProperty().set(0);
        assertEquals("234.0-0.0-0.0", root.getId());

        TestContext newCtx = new TestContext();
        newCtx.invariantDoubleVal = 1;
        newCtx.doubleValProperty().set(2);
        root.context.set(newCtx);
        assertEquals("1.0-2.0-2.0", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_Constructor_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${String('foo')}"/>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("String('foo')", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Varargs_Constructor() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      objProp="${Stringifier('foo', 97, 98, 99)}"/>
        """));

        assertEquals(ErrorCode.CANNOT_BIND_FUNCTION, ex.getDiagnostic().getCode());
        assertEquals(3, ex.getDiagnostic().getCauses().length);
        assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[0].getCode());
        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCauses()[1].getCode());
        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCauses()[2].getCode());
        assertCodeHighlight("Stringifier", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Constructor_With_More_Specific_Argument_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      objProp="${Stringifier('foo')}"/>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("Stringifier('foo')", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Varargs_Constructor_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${String(0, 1, 'foo')}"/>
        """));

        assertEquals(ErrorCode.CANNOT_BIND_FUNCTION, ex.getDiagnostic().getCode());
        assertCodeHighlight("String", ex);
    }

    @Test
    public void Bind_Unidirectional_With_ParentScope_Arguments() {
        TestPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Pane prefWidth="123">
                    <Pane id="${String.format('foo-%s-%s', parent/prefWidth, parent[1]/invariantDoubleVal)}"/>
                </Pane>
            </TestPane>
        """);

        assertNewFunctionExpr(root, 1);
        Pane pane = (Pane)((Pane)root.getChildren().get(0)).getChildren().get(0);
        assertEquals("foo-123.0-1.0", pane.getId());
    }

    @Test
    public void Bind_Unidirectional_With_This_Argument_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <VBox>
                    <Pane id="${String.format('foo-%s', this)}"/>
                </VBox>
            </TestPane>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("String.format('foo-%s', this)", ex);
    }

    @Test
    public void Bind_Unidirectional_With_ParentScope_This_Argument_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <VBox>
                    <Pane id="${String.format('foo-%s', parent/this)}"/>
                </VBox>
            </TestPane>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("String.format('foo-%s', parent/this)", ex);
    }

    @Test
    public void Bind_Unidirectional_With_ParentScope_Function_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Pane>
                    <Pane prefWidth="${parent[1]/add(1, 2)}"/>
                </Pane>
            </TestPane>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("parent[1]/add(1, 2)", ex);
    }

    @Test
    public void Bind_Unidirectional_With_ParentScope_Function_And_Argument() {
        TestPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefHeight="1">
                <Pane fx:id="pane" prefWidth="2">
                    <Pane prefWidth="${parent[1]/add(prefHeight, parent[1]/pane.prefWidth)}"/>
                </Pane>
            </TestPane>
        """);

        assertNewFunctionExpr(root, 1);
        Pane pane = (Pane)((Pane)root.getChildren().get(0)).getChildren().get(0);
        assertEquals(3, pane.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_With_Invalid_ParentScope_Function_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Pane>
                    <Pane prefWidth="${parent[0]/add(1, 2)}"/>
                </Pane>
            </TestPane>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("parent[0]/add", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Static_Method_With_ParentScope_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Pane id="${parent/String.format('%s', 2)}"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.BINDING_CONTEXT_NOT_APPLICABLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("parent", ex);
    }

    @Test
    public void Bind_Unidirectional_With_FxConstant_Param_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${defaultMethod('foo-%s', {Double fx:constant=POSITIVE_INFINITY})}"/>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("defaultMethod('foo-%s', {Double fx:constant=POSITIVE_INFINITY})", ex);
    }

    @Test
    public void Bind_Unidirectional_With_ConstantLiteral_Param_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${defaultMethod('foo-%s', Double.POSITIVE_INFINITY)}"/>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("defaultMethod('foo-%s', Double.POSITIVE_INFINITY)", ex);
    }

    @Test
    public void Bind_Unidirectional_With_BindingExpression_Param_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${defaultMethod('foo-%s', ${doubleProp})}"/>
        """));

        assertEquals(ErrorCode.EXPRESSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("${doubleProp}", ex);
    }

    @Test
    public void Bind_Unidirectional_With_AssignmentExpression_Param_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${defaultMethod('foo-%s', $doubleProp)}"/>
        """));

        assertEquals(ErrorCode.EXPRESSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("$doubleProp", ex);
    }

    @Test
    public void Bind_Unidirectional_To_List_Get_With_Nested_Method_Call_With_Wrong_ReturnType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${items.get(Math.round(prefWidth))}"/>
        """));

        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
        assertMessageContains("java.util.List.get(int): argument #1 cannot be assigned from long", ex);
        assertCodeHighlight("Math.round(prefWidth)", ex);
    }

    @Test
    public void Bind_Unidirectional_To_List_Get_With_Nested_Receiver_Generic_Return_Type() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${items.get(indexes.get(selectedIndex))}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.idProperty().isBound());
        assertEquals("bar", root.getId());

        root.selectedIndex.set(1);
        assertEquals("baz", root.getId());

        root.selectedIndex.set(0);
        root.getIndexes().set(0, 2);
        assertEquals("baz", root.getId());

        root.getItems().set(2, "qux");
        assertEquals("qux", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_Method_With_List_Argument_Reevaluates_On_Content_Change() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="${listSize(items)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.prefWidthProperty().isBound());
        assertEquals(3, root.getPrefWidth());

        root.getItems().add("qux");
        assertEquals(4, root.getPrefWidth());

        root.getItems().remove(0);
        assertEquals(3, root.getPrefWidth());
    }

    @Test
    public void Bind_Unidirectional_To_Method_With_Observable_List_Argument_Reevaluates_On_Content_Change_And_Replacement() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="${listSize(itemsProp)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.prefWidthProperty().isBound());
        assertEquals(3, root.getPrefWidth());

        root.itemsProp.get().add("qux");
        assertEquals(4, root.getPrefWidth());

        ObservableList<String> newItems = FXCollections.observableArrayList("one", "two");
        root.itemsProp.set(newItems);
        assertEquals(2, root.getPrefWidth());

        newItems.remove(0);
        assertEquals(1, root.getPrefWidth());
    }

    @Test
    public void Bind_Unidirectional_To_List_Get_With_Nested_Generic_Method_TypeWitness() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${items.get(identity<Integer>(selectedIndex))}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.idProperty().isBound());
        assertEquals("foo", root.getId());

        root.selectedIndex.set(2);
        assertEquals("baz", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_List_Get_With_Observable_List_Receiver_Reevaluates_On_Content_Change_And_Replacement() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${itemsProp.get(selectedIndex)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.idProperty().isBound());
        assertEquals("foo", root.getId());

        root.itemsProp.get().set(0, "qux");
        assertEquals("qux", root.getId());

        root.selectedIndex.set(1);
        assertEquals("bar", root.getId());

        ObservableList<String> newItems = FXCollections.observableArrayList("one", "two");
        root.itemsProp.set(newItems);
        assertEquals("two", root.getId());

        newItems.set(1, "deux");
        assertEquals("deux", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_List_Get_With_Observable_List_Receiver_Survives_GC() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${itemsProp.get(selectedIndex)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.idProperty().isBound());
        assertEquals("foo", root.getId());

        for (int i = 0; i < 5; ++i) {
            gc();
        }

        root.itemsProp.get().set(0, "qux");
        assertEquals("qux", root.getId());

        ObservableList<String> newItems = FXCollections.observableArrayList("one", "two");
        root.itemsProp.set(newItems);
        assertEquals("one", root.getId());

        for (int i = 0; i < 5; ++i) {
            gc();
        }

        root.selectedIndex.set(1);
        assertEquals("two", root.getId());

        newItems.set(1, "deux");
        assertEquals("deux", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_Nested_Generic_Method_With_Object_Return_Updates_Correctly() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${String.format('foo-%s', identity<String>(stringProp))}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.idProperty().isBound());
        assertEquals("foo-bar", root.getId());

        root.stringProp.set("baz");
        assertEquals("foo-baz", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_List_Get_With_Nested_Receiver_Generic_Return_Type_Mismatch_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${items.get(longIndexes.get(selectedIndex))}"/>
        """));

        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
        assertMessageContains("java.util.List.get(int): argument #1 cannot be assigned from java.lang.Long", ex);
        assertCodeHighlight("longIndexes.get(selectedIndex)", ex);
    }

    @Test
    public void Bind_Reverse_To_Method_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id=">{formatMethod('foo-%s', prefWidth, true, null)}"/>
        """));

        assertEquals(ErrorCode.INVALID_REVERSE_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertMessageContains("formatMethod is not a valid reverse binding source", ex);
        assertCodeHighlight("formatMethod('foo-%s', prefWidth, true, null)", ex);
    }

    @Test
    public void Bind_Reverse_To_Method_Returning_Property_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id=">{methodReturningProperty('asdf')}">
            </TestPane>
        """));

        assertEquals(ErrorCode.INVALID_REVERSE_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertMessageContains("methodReturningProperty is not a valid reverse binding source", ex);
        assertCodeHighlight("methodReturningProperty('asdf')", ex);
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
        public ObjectProperty<BidirectionalIndirect> observableIndirect =
            new SimpleObjectProperty<>(new BidirectionalIndirect());

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

        public ObjectProperty<DoubleContainer> doubleContainer = new SimpleObjectProperty<>(new DoubleContainer(5));

        public ObjectProperty<DoubleContainer> doubleContainerProperty() {
            return doubleContainer;
        }

        public double doubleContainerToDouble(DoubleContainer value) {
            return value.value;
        }

        public final C1 c1 = new C1(new C2());

        public static final C1 static_c1 = new C1(new C2());

        public static record C1(C2 c2) {}

        public static class C2 {
            public boolean instanceNot(boolean value) {
                return !value;
            }

            public boolean customInverseMethodIndirect(boolean value) {
                return !value;
            }
        }
    }

    public static class DoubleContainer {
        final double value;
        public DoubleContainer(double value) {
            this.value = value;
        }

        @SuppressWarnings("unused")
        public static double doubleContainerToDouble(DoubleContainer ds) {
            return ds.value;
        }
    }

    @Test
    public void Bind_Bidirectional_To_Unresolvable_InverseMethod_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="#{sum(doubleProp); inverseMethod=foo.doesNotExist}"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo.doesNotExist", ex);
    }

    @Test
    public void Bind_Bidirectional_To_Static_Method_With_Two_Parameters_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="#{String.format('%s', doubleProp)}"/>
        """));

        assertEquals(ErrorCode.CANNOT_BIND_FUNCTION, ex.getDiagnostic().getCode());
        assertEquals(2, ex.getDiagnostic().getCauses().length);
        assertEquals(ErrorCode.INCOMPATIBLE_RETURN_VALUE, ex.getDiagnostic().getCauses()[0].getCode());
        assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[1].getCode());
        assertCodeHighlight("String.format", ex);
    }

    @Test
    public void Bind_Bidirectional_To_Instance_Method_With_Two_Parameters_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="#{add(invariantDoubleVal, doubleProp)}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_METHOD_PARAM_COUNT, ex.getDiagnostic().getCode());
        assertCodeHighlight("add(invariantDoubleVal, doubleProp)", ex);
    }

    @Test
    public void Bind_Bidirectional_To_Indirect_DoubleProperty() {
        BidirectionalTestPane root = compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   id="#{doubleToString(indirect.doubleProp)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertEquals("1.0", root.getId());

        root.indirect.doubleProp.set(2);
        assertEquals("2.0", root.getId());

        root.setId("3.0");
        assertEquals(3.0, root.indirect.doubleProp.get(), 0.001);

        root.setId(null);
        assertEquals(0.0, root.indirect.doubleProp.get(), 0.001);
    }

    @Test
    public void Bind_Bidirectional_To_ObservableIndirect_DoubleProperty() {
        BidirectionalTestPane root = compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   id="#{doubleToString(observableIndirect.doubleProp)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertEquals("1.0", root.getId());

        root.observableIndirect.set(null);
        assertEquals("0.0", root.getId());

        var newIndirect = new BidirectionalIndirect();
        newIndirect.doubleProp.set(2);
        root.observableIndirect.set(newIndirect);
        assertEquals("2.0", root.getId());
    }

    @Test
    public void Bind_Bidirectional_To_ObservableIndirect_DoubleProperty_Survives_GC() {
        BidirectionalTestPane root = compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   id="#{doubleToString(observableIndirect.doubleProp)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertEquals("1.0", root.getId());

        for (int i = 0; i < 5; ++i) {
            gc();
        }

        root.observableIndirect.get().doubleProp.set(2);
        assertEquals("2.0", root.getId());

        var newIndirect = new BidirectionalIndirect();
        newIndirect.doubleProp.set(3);
        root.observableIndirect.set(newIndirect);
        assertEquals("3.0", root.getId());

        for (int i = 0; i < 5; ++i) {
            gc();
        }

        root.setId("4.0");
        assertEquals(4.0, newIndirect.doubleProp.get(), 0.001);
    }

    @Test
    public void Bind_Bidirectional_To_ObservableIndirect_DoubleProperty_Does_Not_Keep_Target_Alive() {
        GcSourceHolder.reset();
        ObjectProperty<BidirectionalIndirect> source = GcSourceHolder.observableIndirect;
        WeakReference<Pane> ref = createBidirectionalGcTarget(source);

        assertGarbageCollected(ref);

        source.get().doubleProp.set(3);
        assertNull(ref.get());

        BidirectionalIndirect newIndirect = new BidirectionalIndirect();
        newIndirect.doubleProp.set(4);
        source.set(newIndirect);
        assertNull(ref.get());
    }

    @Test
    public void Bind_Bidirectional_To_Indirect_StringProperty() {
        BidirectionalTestPane root = compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   prefWidth="#{stringToDouble(indirect.stringProp)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertEquals(1.0, root.getPrefWidth(), 0.001);

        root.indirect.stringProp.set(null);
        assertEquals(0.0, root.getPrefWidth(), 0.001);

        root.indirect.stringProp.set("2.0");
        assertEquals(2.0, root.getPrefWidth(), 0.001);

        root.setPrefWidth(3);
        assertEquals("3.0", root.indirect.stringProp.get());
    }

    @Test
    public void Bind_Bidirectional_To_ObservableIndirect_StringProperty() {
        BidirectionalTestPane root = compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   prefWidth="#{stringToDouble(observableIndirect.stringProp)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertEquals(1.0, root.getPrefWidth(), 0.001);

        root.observableIndirect.set(null);
        assertEquals(0.0, root.getPrefWidth(), 0.001);

        var newIndirect = new BidirectionalIndirect();
        newIndirect.stringProp.set("2.0");
        root.observableIndirect.set(newIndirect);
        assertEquals(2.0, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Bidirectional_To_NullIndirect_DoubleProperty() {
        BidirectionalTestPane root = compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   id="#{doubleToString(nullIndirect.doubleProp)}"/>
        """);

        assertFalse(root.idProperty().isBound());
        assertEquals("0.0", root.getId());
    }

    @Test
    public void Bind_Bidirectional_To_NullIndirect_StringProperty() {
        BidirectionalTestPane root = compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   prefWidth="#{stringToDouble(nullIndirect.stringProp)}"/>
        """);

        assertFalse(root.prefWidthProperty().isBound());
        assertEquals(0.0, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Bidirectional_To_Method_With_InverseMethod() {
        BidirectionalTestPane root = compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   id="#{doubleToString(doubleProp)}"
                                   prefWidth="#{stringToDouble(stringProp)}"
                                   visible="#{instanceNot(boolProp)}"/>
        """);

        assertNewFunctionExpr(root, 3);
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
        BidirectionalTestPane root = compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   visible="#{BidirectionalTestPane.staticNot(boolProp)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.isVisible());
        assertFalse(root.boolProp.get());
        root.setVisible(false);
        assertTrue(root.boolProp.get());
    }

    @Test
    public void Bind_Bidirectional_To_Instance_Method_With_Static_InverseMethod() {
        BidirectionalTestPane root = compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   visible="#{instanceNot2(boolProp)}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.isVisible());
        assertFalse(root.boolProp.get());
        root.setVisible(false);
        assertTrue(root.boolProp.get());
    }

    @Test
    public void Bind_Bidirectional_To_Method_With_Custom_InverseMethod() {
        BidirectionalTestPane root = compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   visible="#{instanceNot(boolProp); inverseMethod=customInverseMethod}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.isVisible());
        root.setVisible(false);
        assertTrue(root.boolProp.get());
    }

    @Test
    public void Bind_Bidirectional_To_Indirect_Method_With_Indirect_Custom_InverseMethod() {
        BidirectionalTestPane root = compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   visible="#{c1.c2.instanceNot(boolProp); inverseMethod=c1.c2.customInverseMethodIndirect}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.isVisible());
        root.setVisible(false);
        assertTrue(root.boolProp.get());
    }

    @Test
    public void Bind_Bidirectional_To_Statically_Resolvable_Indirect_Method_With_Indirect_Custom_InverseMethod() {
        BidirectionalTestPane root = compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   visible="#{static_c1.c2.instanceNot(boolProp); inverseMethod=static_c1.c2.customInverseMethodIndirect}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertTrue(root.isVisible());
        root.setVisible(false);
        assertTrue(root.boolProp.get());
    }

    @Test
    public void Bind_Bidirectional_To_Method_With_InverseConstructor() {
        BidirectionalTestPane root = compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   prefWidth="#{doubleContainerToDouble(doubleContainer); inverseMethod=DoubleContainer}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertEquals(5, root.getPrefWidth(), 0.001);
        root.setPrefWidth(4);
        assertEquals(4, root.doubleContainer.get().value, 0.001);
    }

    @Test
    public void Bind_Bidirectional_To_Method_With_Qualified_InverseConstructor() {
        BidirectionalTestPane root = compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   prefWidth="#{doubleContainerToDouble(doubleContainer); inverseMethod=BidirectionalTestPane.DoubleContainer}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertEquals(5, root.getPrefWidth(), 0.001);
        root.setPrefWidth(4);
        assertEquals(4, root.doubleContainer.get().value, 0.001);
    }

    @Test
    public void Bind_Bidirectional_To_Constructor_With_InverseMethod() {
        BidirectionalTestPane root = compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   doubleContainer="#{DoubleContainer(doubleProp); inverseMethod=doubleContainerToDouble}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertEquals(1, root.doubleProp.get(), 0.001);
        assertEquals(1, root.doubleContainer.get().value, 0.001);
        
        root.doubleProp.set(2);
        assertEquals(2, root.doubleContainer.get().value, 0.001);
        
        root.doubleContainer.set(new DoubleContainer(3));
        assertEquals(3, root.doubleProp.get(), 0.001);
    }

    @Test
    public void Bind_Bidirectional_To_Constructor_With_Qualified_InverseMethod() {
        BidirectionalTestPane root = compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   doubleContainer="#{DoubleContainer(doubleProp); inverseMethod=DoubleContainer.doubleContainerToDouble}"/>
        """);

        assertNewFunctionExpr(root, 1);
        assertEquals(1, root.doubleProp.get(), 0.001);
        assertEquals(1, root.doubleContainer.get().value, 0.001);

        root.doubleProp.set(2);
        assertEquals(2, root.doubleContainer.get().value, 0.001);

        root.doubleContainer.set(new DoubleContainer(3));
        assertEquals(3, root.doubleProp.get(), 0.001);
    }

    @Test
    public void Bind_Bidirectional_To_Method_Without_InverseMethod_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   id="#{noInverseMethod(doubleProp)}"/>
        """));

        assertEquals(ErrorCode.METHOD_NOT_INVERTIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("noInverseMethod", ex);
    }

    @Test
    public void Bind_Bidirectional_To_Method_With_Incompatible_ReturnType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   id="#{noInverseMethod(doubleProp); inverseMethod=invalidInverseMethod}"/>
        """));

        assertEquals(ErrorCode.INCOMPATIBLE_RETURN_VALUE, ex.getDiagnostic().getCode());
        assertCodeHighlight("invalidInverseMethod", ex);
    }

    @Test
    public void Bind_Bidirectional_To_Method_With_Invalid_Custom_InverseMethod2_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   id="#{noInverseMethod(doubleProp); inverseMethod=java.lang.String.format}"/>
        """));

        assertEquals(ErrorCode.CANNOT_BIND_FUNCTION, ex.getDiagnostic().getCode());
        assertEquals(2, ex.getDiagnostic().getCauses().length);
        assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[0].getCode());
        assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[1].getCode());
        assertCodeHighlight("java.lang.String.format", ex);
    }

    @Test
    public void Bind_Bidirectional_To_Method_With_Nonexistent_Custom_InverseMethod_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   id="#{noInverseMethod(doubleProp); inverseMethod=doesNotExist}"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("doesNotExist", ex);
    }

    @Test
    public void Bind_Bidirectional_To_Method_With_ReadOnlyProperty_Argument_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   managed="#{instanceNot(readOnlyObservableBool)}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("readOnlyObservableBool", ex);
    }

    @Test
    public void Bind_Bidirectional_To_Method_With_Unsuitable_Parameter_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <BidirectionalTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                   managed="#{instanceNot(instanceNot(boolProp))}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_METHOD_PARAM_KIND, ex.getDiagnostic().getCode());
        assertCodeHighlight("instanceNot(boolProp)", ex);
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
        OverloadTestPane root = compileAndRun("""
            <OverloadTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                              id="$overloadedMethod('ignored')" stringProp="$overloadedMethod(0)"/>
        """);

        assertEquals("String", root.getId());
        assertEquals("Object", root.stringProp.get());
    }

    @Test
    public void Ambiguous_Method_Call_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <OverloadTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                              id="$overloadedMethod(d, d, d)"/>
        """));

        assertEquals(ErrorCode.AMBIGUOUS_METHOD_CALL, ex.getDiagnostic().getCode());
        assertCodeHighlight("overloadedMethod", ex);
    }

    @SuppressWarnings("unused")
    public static class GenericTestPane<T> extends Pane {
        public GenericTestPane() { setPrefWidth(123); }

        public String m1(T value) {
            return value.toString();
        }

        public <S> S m2(S value) {
            return value;
        }

        @SuppressWarnings("TypeParameterHidesVisibleType")
        public <T extends Double> T m3_overloaded(T value) {
            return value;
        }

        public String m3_overloaded(T value) {
            return (String)value;
        }

        @SuppressWarnings("unchecked")
        public T m4(double v) {
            return (T)Double.toString(v);
        }

        public double invariantDoubleVal = 1;

        public final String genericFormat(T value, Object... args) {
            return String.format(value.toString(), args);
        }

        public final String genericFormat(Locale locale, T value, Object... args) {
            return String.format(locale, value.toString(), args);
        }
    }

    @Test
    public void Bind_Once_To_Generic_Method_Of_Raw_Type() {
        GenericTestPane<?> root = compileAndRun("""
            <GenericTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                             id="$m1(prefWidth)"/>
        """);

        assertEquals("123.0", root.getId());
    }

    @Test
    public void Bind_Once_To_Generic_Method_Argument_Out_Of_Bound_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <GenericTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                             fx:typeArguments="java.lang.String" id="$m1(prefWidth)"/>
        """));

        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
        assertCodeHighlight("prefWidth", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Generic_Method_Of_Raw_Type() {
        GenericTestPane<?> root = compileAndRun("""
                <GenericTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                 id="${m1(prefWidth)}"/>
            """);

        assertEquals("123.0", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_Generic_Method_Argument_Out_Of_Bound_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <GenericTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                             fx:typeArguments="java.lang.String" id="${m1(prefWidth)}"/>
        """));

        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
        assertCodeHighlight("prefWidth", ex);
    }

    @Test
    public void Generic_Method_Is_Not_Callable_Without_TypeWitness() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <GenericTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                 id="${m2(prefWidth)}"/>
            """));

        assertEquals(ErrorCode.NUM_TYPE_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCode());
        assertCodeHighlight("m2", ex);
        assertTrue(ex.getDiagnostic().getMessage().endsWith(
            "GenericTestPane.m2: required 1 type argument(s), but 0 were provided"));
    }

    @Test
    public void Generic_Method_Is_Not_Callable_With_Incompatible_TypeWitness() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <GenericTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                 id="${m2<String>(prefWidth)}"/>
            """));

        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
        assertCodeHighlight("prefWidth", ex);
        assertTrue(ex.getDiagnostic().getMessage().endsWith(
            "GenericTestPane.m2(java.lang.String): argument #1 cannot be assigned from double, expected java.lang.String"));
    }

    @Test
    public void Generic_Method_Is_Not_Assignable_With_Incompatible_TypeWitness() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <GenericTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                 id="${m2<Double>(prefWidth)}"/>
            """));

        assertEquals(ErrorCode.INCOMPATIBLE_RETURN_VALUE, ex.getDiagnostic().getCode());
        assertCodeHighlight("m2<Double>", ex);
        assertTrue(ex.getDiagnostic().getMessage().endsWith(
            "GenericTestPane.m2(java.lang.Double): return value cannot be converted to java.lang.String"));
    }

    @Test
    public void Generic_Method_Wrong_Number_Of_TypeWitnesses() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <GenericTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                 id="${m2<Object, String>(prefWidth)}"/>
            """));

        assertEquals(ErrorCode.NUM_TYPE_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCode());
        assertCodeHighlight("m2<Object, String>", ex);
        assertTrue(ex.getDiagnostic().getMessage().endsWith(
            "GenericTestPane.m2: required 1 type argument(s), but 2 were provided"));
    }

    @Test
    public void Bind_Once_To_Overloaded_Generic_Method() {
        GenericTestPane<?> root = compileAndRun("""
                <GenericTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                 prefWidth="$m3_overloaded<Double>(123d)"
                                 id="$m3_overloaded('foo')"/>
            """);

        assertEquals("foo", root.getId());
        assertEquals(123.0, root.getPrefWidth());
    }

    @Test
    public void Bind_Unidirectional_To_Overloaded_Generic_Method_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <GenericTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                 prefWidth="${m3_overloaded<Double>(123d)}"
                                 id="${m3_overloaded('foo')}"/>
            """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("m3_overloaded('foo')", ex);
    }

    @Test
    public void Generic_Return_Type_Of_Untyped_Class_Is_Not_Assignable() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <GenericTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                 id="$m4(123d)"/>
            """));

        assertEquals(ErrorCode.INCOMPATIBLE_RETURN_VALUE, ex.getDiagnostic().getCode());
        assertCodeHighlight("m4", ex);
    }

    @Test
    public void Generic_Return_Type_Of_Typed_Class_Is_Assignable() {
        GenericTestPane<?> root = compileAndRun("""
                <GenericTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                 fx:typeArguments="String" id="$m4(123d)"/>
            """);

        assertEquals("123.0", root.getId());
    }

    @Test
    public void Bind_Once_To_Generic_Method_With_Incompatible_ReturnType_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <GenericTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                             fx:typeArguments="String"
                             prefWidth="$genericFormat('foo-%s', invariantDoubleVal)"/>
        """));

        assertEquals(ErrorCode.CANNOT_BIND_FUNCTION, ex.getDiagnostic().getCode());
        assertEquals(2, ex.getDiagnostic().getCauses().length);
        assertEquals(ErrorCode.INCOMPATIBLE_RETURN_VALUE, ex.getDiagnostic().getCauses()[0].getCode());
        assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[1].getCode());
        assertCodeHighlight("genericFormat", ex);
        assertTrue(ex.getDiagnostic().getCauses()[0].getMessage().endsWith(
            "genericFormat(java.lang.String, java.lang.Object[]): return value cannot be converted to double"));
        assertTrue(ex.getDiagnostic().getCauses()[1].getMessage().endsWith(
            "genericFormat(java.util.Locale, java.lang.String, java.lang.Object[]): required 3 argument(s), but 2 were provided"));
    }
}

// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class LogicalOperatorTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class TestPane extends Pane {
        public static int failingFunctionCalls;

        public final BooleanProperty ready = new SimpleBooleanProperty(true);
        public final BooleanProperty other = new SimpleBooleanProperty(false);
        public Boolean boxedFalse = Boolean.FALSE;
        public Boolean boxedTrue = Boolean.TRUE;
        public Boolean boxedNull;
        public float floatNaN = Float.NaN;
        public double negativeZero = -0.0;
        public Number numberFraction = 0.5;
        public Object objectZero = Integer.valueOf(0);
        public Object nullObject;
        public int zero;
        public int functionCalls;
        public final List<String> acquisitionOrder = new ArrayList<>();

        private final BooleanProperty result0 = new SimpleBooleanProperty();
        private final BooleanProperty result1 = new SimpleBooleanProperty();
        private final BooleanProperty result2 = new SimpleBooleanProperty();
        private final BooleanProperty result3 = new SimpleBooleanProperty();
        private final BooleanProperty result4 = new SimpleBooleanProperty();
        private final BooleanProperty result5 = new SimpleBooleanProperty();
        private final BooleanProperty result6 = new SimpleBooleanProperty();
        private final BooleanProperty result7 = new SimpleBooleanProperty();

        public boolean isResult0() { return result0.get(); }
        public void setResult0(boolean value) { result0.set(value); }
        public BooleanProperty result0Property() { return result0; }

        public boolean isResult1() { return result1.get(); }
        public void setResult1(boolean value) { result1.set(value); }
        public BooleanProperty result1Property() { return result1; }

        public boolean isResult2() { return result2.get(); }
        public void setResult2(boolean value) { result2.set(value); }
        public BooleanProperty result2Property() { return result2; }

        public boolean isResult3() { return result3.get(); }
        public void setResult3(boolean value) { result3.set(value); }
        public BooleanProperty result3Property() { return result3; }

        public boolean isResult4() { return result4.get(); }
        public void setResult4(boolean value) { result4.set(value); }
        public BooleanProperty result4Property() { return result4; }

        public boolean isResult5() { return result5.get(); }
        public void setResult5(boolean value) { result5.set(value); }
        public BooleanProperty result5Property() { return result5; }

        public boolean isResult6() { return result6.get(); }
        public void setResult6(boolean value) { result6.set(value); }
        public BooleanProperty result6Property() { return result6; }

        public boolean isResult7() { return result7.get(); }
        public void setResult7(boolean value) { result7.set(value); }
        public BooleanProperty result7Property() { return result7; }

        public boolean fail() {
            ++failingFunctionCalls;
            throw new IllegalStateException("fail");
        }

        public boolean record(String name, boolean value) {
            ++functionCalls;
            acquisitionOrder.add(name);
            return value;
        }
    }

    @BeforeEach
    public void resetCounters() {
        TestPane.failingFunctionCalls = 0;
    }

    @Test
    public void Evaluate_Logical_Operators_Implement_The_Boolean_Truth_Table() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$false && false"
                      result1="$false && true"
                      result2="$true && false"
                      result3="$true && true"
                      result4="$false || false"
                      result5="$false || true"
                      result6="$true || false"
                      result7="$true || true"/>
        """);

        assertFalse(root.isResult0());
        assertFalse(root.isResult1());
        assertFalse(root.isResult2());
        assertTrue(root.isResult3());
        assertFalse(root.isResult4());
        assertTrue(root.isResult5());
        assertTrue(root.isResult6());
        assertTrue(root.isResult7());
    }

    @Test
    public void Xml_Entity_Decoded_Logical_Operator_Compiles_Semantically() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="${ready && other}"/>
        """);

        assertFalse(root.isResult0());
        root.other.set(true);
        assertTrue(root.isResult0());
    }

    @Test
    public void Evaluate_Logical_Operators_Treat_Null_Boxed_Boolean_As_False() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$boxedTrue && boxedNull"
                      result1="$boxedNull && boxedTrue"
                      result2="$boxedTrue || boxedNull"
                      result3="$boxedNull || boxedTrue"/>
        """);

        assertFalse(root.isResult0());
        assertFalse(root.isResult1());
        assertTrue(root.isResult2());
        assertTrue(root.isResult3());
    }

    @Test
    public void Evaluate_Unary_Truthiness_Uses_One_Static_Type_Directed_Table() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$!!boxedFalse"
                      result1="$!boxedNull"
                      result2="$!!floatNaN"
                      result3="$!!negativeZero"
                      result4="$!!numberFraction"
                      result5="$!!objectZero"
                      result6="$!!nullObject"
                      result7="$!nullObject"/>
        """);

        assertFalse(root.isResult0());
        assertTrue(root.isResult1());
        assertFalse(root.isResult2());
        assertFalse(root.isResult3());
        assertTrue(root.isResult4());
        assertTrue(root.isResult5());
        assertFalse(root.isResult6());
        assertTrue(root.isResult7());
    }

    @Test
    public void Evaluate_General_Unary_Operators_Accept_Compound_Operands() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$!(1 < 2)"
                      result1="$!!(1 < 2)"
                      result2="$!(true && false)"
                      result3="$!!(1 + 1)"/>
        """);

        assertFalse(root.isResult0());
        assertTrue(root.isResult1());
        assertTrue(root.isResult2());
        assertTrue(root.isResult3());
    }

    @Test
    public void Evaluate_Helper_Local_Truthiness_Uses_The_Same_Static_Type_Table() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$!!numberFraction && !!objectZero"
                      result1="$!floatNaN && !negativeZero"
                      result2="$!!boxedFalse || !!nullObject"/>
        """);

        assertTrue(root.isResult0());
        assertTrue(root.isResult1());
        assertFalse(root.isResult2());
    }

    @Test
    public void Logical_Operators_Reject_Implicit_Truthiness_And_Suggest_Explicit_Boolification() {
        MarkupException leftEx = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$1 && true"/>
        """, "LeftOperand", null));
        assertEquals(ErrorCode.INVALID_LOGICAL_OPERAND, leftEx.getDiagnostic().getCode());
        assertTrue(leftEx.getDiagnostic().getMessage().contains("!!"));
        assertCodeHighlight("1", leftEx);

        MarkupException rightEx = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$true || 'value'"/>
        """, "RightOperand", null));
        assertEquals(ErrorCode.INVALID_LOGICAL_OPERAND, rightEx.getDiagnostic().getCode());
        assertCodeHighlight("'value'", rightEx);

        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$!!1 && true"/>
        """, "ExplicitBoolification", null);
        assertTrue(root.isResult0());
    }

    @Test
    public void Evaluate_Acquires_Right_Function_Arguments_Before_Helper_Short_Circuiting() {
        assertThrows(IllegalStateException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$false && fail()"/>
        """, "AndFunction", null));
        assertEquals(1, TestPane.failingFunctionCalls);

        TestPane.failingFunctionCalls = 0;
        assertThrows(IllegalStateException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$true || fail()"/>
        """, "OrFunction", null));
        assertEquals(1, TestPane.failingFunctionCalls);
    }

    @Test
    public void Evaluate_Short_Circuits_Helper_Local_Operator_Subtrees() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$false && (1 / zero < 1)"
                      result1="$true || (1 / zero < 1)"/>
        """);

        assertFalse(root.isResult0());
        assertTrue(root.isResult1());
    }

    @Test
    public void Observe_Validates_All_Function_Arguments_But_Reuses_Valid_Nested_Wrappers() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="${record('left', ready) || record('right', other)}"/>
        """);

        assertTrue(root.result0Property().isBound());
        assertTrue(root.isResult0());
        assertEquals(2, root.functionCalls);
        assertEquals(List.of("left", "right"), root.acquisitionOrder);

        root.acquisitionOrder.clear();
        assertTrue(root.isResult0());
        assertEquals(2, root.functionCalls);
        assertTrue(root.acquisitionOrder.isEmpty());

        root.ready.set(false);
        assertEquals(2, root.functionCalls);
        assertFalse(root.isResult0());
        assertEquals(3, root.functionCalls);
        assertEquals(List.of("left"), root.acquisitionOrder);

        root.acquisitionOrder.clear();
        root.ready.set(true);
        root.other.set(true);
        assertEquals(3, root.functionCalls);
        assertTrue(root.isResult0());
        assertEquals(5, root.functionCalls);
        assertEquals(List.of("left", "right"), root.acquisitionOrder);

        root.acquisitionOrder.clear();
        assertTrue(root.isResult0());
        assertEquals(5, root.functionCalls);
        assertTrue(root.acquisitionOrder.isEmpty());
    }

    @Test
    public void Observe_Short_Circuits_Helper_Local_Arithmetic_Until_The_Guard_Changes() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="${ready || (1 / zero < 1)}"/>
        """);

        assertTrue(root.isResult0());
        root.ready.set(false);
        assertThrows(ArithmeticException.class, root::isResult0);
    }

    @Test
    public void Observe_Helper_Local_Truthiness_Uses_The_Same_Static_Type_Table() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="${ready && !!numberFraction}"
                      result1="${other || !!objectZero}"/>
        """);

        assertTrue(root.isResult0());
        assertTrue(root.isResult1());

        root.ready.set(false);
        assertFalse(root.isResult0());
        root.other.set(true);
        assertTrue(root.isResult1());
    }

    @Test
    public void Nested_Logical_Tree_Uses_One_Package_Access_Static_Helper() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$true || false && false"
                      result1="$(true == false) || !(2 < 1)"/>
        """);

        assertTrue(root.isResult0());
        assertTrue(root.isResult1());
        Method[] helpers = Arrays.stream(root.getClass().getDeclaredMethods())
            .filter(method -> method.getName().startsWith("__FX$eval$"))
            .toArray(Method[]::new);
        assertEquals(2, helpers.length);
        assertTrue(Arrays.stream(helpers).allMatch(method -> Modifier.isStatic(method.getModifiers())));
        assertTrue(Arrays.stream(helpers).noneMatch(method -> Modifier.isPrivate(method.getModifiers())));
    }

    @Test
    public void Invariant_Observe_Logical_Expression_Does_Not_Leak_The_Helper_Name() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="${true && false}"/>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertFalse(ex.getDiagnostic().getMessage().contains("__FX$Expression"));
    }

    @Test
    public void Logical_Binding_Mode_Restrictions_Are_Diagnosed() {
        String expression = "ready && other";
        MarkupException pushEx = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0=">{%s}"/>
        """.formatted(expression)));

        assertEquals(ErrorCode.UNEXPECTED_TOKEN, pushEx.getDiagnostic().getCode());

        MarkupException synchronizeEx = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="#{%s}"/>
        """.formatted(expression)));

        assertEquals(ErrorCode.EXPRESSION_NOT_INVERTIBLE, synchronizeEx.getDiagnostic().getCode());
        assertCodeHighlight(expression, synchronizeEx);
    }
}

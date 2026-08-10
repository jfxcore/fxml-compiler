// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.Pane;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.jfxcore.compiler.util.MoreAssertions.assertCodeHighlight;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class ArithmeticOperatorTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class TestPane extends Pane {
        public byte byteValue = 2;
        public short shortValue = 3;
        public char charValue = 4;
        public int intValue = 5;
        public long longValue = 6;
        public float floatValue = 2.5f;
        public double doubleValue = 0.25;
        public boolean booleanValue = true;
        public String stringValue = "not numeric";
        public Number numberValue = 10;
        public BigInteger bigIntegerValue = BigInteger.TEN;
        public BigDecimal decimalValue = BigDecimal.TEN;
        public Integer boxedInteger = null;

        public final IntegerProperty intProp = new SimpleIntegerProperty(5);
        public final DoubleProperty a = new SimpleDoubleProperty(10);
        public final DoubleProperty b = new SimpleDoubleProperty(4);
        public final DoubleProperty c = new SimpleDoubleProperty(3);
        public final ObjectProperty<Byte> boxedByteProp = new SimpleObjectProperty<>();
        public final ObjectProperty<Short> boxedShortProp = new SimpleObjectProperty<>();
        public final ObjectProperty<Character> boxedCharProp = new SimpleObjectProperty<>();
        public final ObjectProperty<Integer> boxedIntProp = new SimpleObjectProperty<>();
        public final ObjectProperty<Long> boxedLongProp = new SimpleObjectProperty<>();
        public final ObjectProperty<Float> boxedFloatProp = new SimpleObjectProperty<>();
        public final ObjectProperty<Double> boxedDoubleProp = new SimpleObjectProperty<>();

        public static int invocationCount;
        public int nextCount;
        public final List<Integer> evaluationOrder = new ArrayList<>();

        public int record(int value) {
            ++invocationCount;
            evaluationOrder.add(value);
            return value;
        }

        public Integer maybeBoxed(double value) {
            return value < 0 ? null : (int)value;
        }

        public int next() {
            return ++nextCount;
        }

        public int getTrue() {
            return 20;
        }

        public int getNull() {
            return 21;
        }

        private final IntegerProperty intResult = new SimpleIntegerProperty();
        private final LongProperty longResult = new SimpleLongProperty();
        private final FloatProperty floatResult = new SimpleFloatProperty();
        private final DoubleProperty doubleResult = new SimpleDoubleProperty();

        public int getIntResult() { return intResult.get(); }
        public void setIntResult(int value) { intResult.set(value); }
        public IntegerProperty intResultProperty() { return intResult; }

        public long getLongResult() { return longResult.get(); }
        public void setLongResult(long value) { longResult.set(value); }
        public LongProperty longResultProperty() { return longResult; }

        public float getFloatResult() { return floatResult.get(); }
        public void setFloatResult(float value) { floatResult.set(value); }
        public FloatProperty floatResultProperty() { return floatResult; }

        public double getDoubleResult() { return doubleResult.get(); }
        public void setDoubleResult(double value) { doubleResult.set(value); }
        public DoubleProperty doubleResultProperty() { return doubleResult; }
    }

    @Test
    public void Bind_Once_Uses_Node_By_Node_Numeric_Promotion() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      intResult="$byteValue + shortValue"
                      longResult="$intValue * longValue"
                      floatResult="$longValue / floatValue"
                      doubleResult="$floatValue + doubleValue"/>
        """);

        assertEquals(5, root.getIntResult());
        assertEquals(30L, root.getLongResult());
        assertEquals(2.4f, root.getFloatResult(), 0.0001f);
        assertEquals(2.75, root.getDoubleResult(), 0.0001);
    }

    @Test
    public void Bind_Once_Uses_Precedence_Grouping_Associativity_And_Unary_Promotion() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      intResult="$-(byteValue + shortValue) * charValue"
                      longResult="$(longValue - intValue) * 10"
                      floatResult="$20 / 4 / 2F"
                      doubleResult="$intValue + longValue * 0.5"/>
        """);

        assertEquals(-20, root.getIntResult());
        assertEquals(10L, root.getLongResult());
        assertEquals(2.5f, root.getFloatResult(), 0.0001f);
        assertEquals(8.0, root.getDoubleResult(), 0.0001);
    }

    @Test
    public void Observable_Arithmetic_Recomputes_When_Any_Operand_Changes() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      doubleResult="${a * b * 0.5 + c}"/>
        """);

        assertEquals(23, root.getDoubleResult(), 0.0001);
        root.a.set(20);
        assertEquals(43, root.getDoubleResult(), 0.0001);
        root.b.set(2);
        assertEquals(23, root.getDoubleResult(), 0.0001);
        root.c.set(-3);
        assertEquals(17, root.getDoubleResult(), 0.0001);
    }

    @Test
    public void Boxed_Null_Operands_Use_Primitive_Defaults() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      intResult="${boxedIntProp + intProp}"
                      doubleResult="$boxedInteger + 2"/>
        """);

        assertEquals(5, root.getIntResult());
        assertEquals(2, root.getDoubleResult(), 0.0001);

        root.boxedIntProp.set(7);
        assertEquals(12, root.getIntResult());
        root.boxedIntProp.set(null);
        assertEquals(5, root.getIntResult());
    }

    @Test
    public void Every_Numeric_Wrapper_Uses_Its_Primitive_Default_And_Remains_Observable() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="${boxedByteProp + 1}"
                      prefHeight="${boxedShortProp + 1}"
                      minWidth="${boxedCharProp + 1}"
                      minHeight="${boxedIntProp + 1}"
                      maxWidth="${boxedLongProp + 1}"
                      maxHeight="${boxedFloatProp + 1}"
                      translateX="${boxedDoubleProp + 1}"/>
        """);

        assertEquals(1, root.getPrefWidth(), 0.0001);
        assertEquals(1, root.getPrefHeight(), 0.0001);
        assertEquals(1, root.getMinWidth(), 0.0001);
        assertEquals(1, root.getMinHeight(), 0.0001);
        assertEquals(1, root.getMaxWidth(), 0.0001);
        assertEquals(1, root.getMaxHeight(), 0.0001);
        assertEquals(1, root.getTranslateX(), 0.0001);

        root.boxedByteProp.set((byte)2);
        root.boxedShortProp.set((short)3);
        root.boxedCharProp.set((char)4);
        root.boxedIntProp.set(5);
        root.boxedLongProp.set(6L);
        root.boxedFloatProp.set(2.5F);
        root.boxedDoubleProp.set(0.25);

        assertEquals(3, root.getPrefWidth(), 0.0001);
        assertEquals(4, root.getPrefHeight(), 0.0001);
        assertEquals(5, root.getMinWidth(), 0.0001);
        assertEquals(6, root.getMinHeight(), 0.0001);
        assertEquals(7, root.getMaxWidth(), 0.0001);
        assertEquals(3.5, root.getMaxHeight(), 0.0001);
        assertEquals(1.25, root.getTranslateX(), 0.0001);

        root.boxedByteProp.set(null);
        root.boxedShortProp.set(null);
        root.boxedCharProp.set(null);
        root.boxedIntProp.set(null);
        root.boxedLongProp.set(null);
        root.boxedFloatProp.set(null);
        root.boxedDoubleProp.set(null);

        assertEquals(1, root.getPrefWidth(), 0.0001);
        assertEquals(1, root.getPrefHeight(), 0.0001);
        assertEquals(1, root.getMinWidth(), 0.0001);
        assertEquals(1, root.getMinHeight(), 0.0001);
        assertEquals(1, root.getMaxWidth(), 0.0001);
        assertEquals(1, root.getMaxHeight(), 0.0001);
        assertEquals(1, root.getTranslateX(), 0.0001);
    }

    @Test
    public void Arithmetic_Can_Be_Used_As_Observable_Function_Argument() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      doubleResult="${Math.max(a * 0.7, 100)}"
                      intResult="${maybeBoxed(a) + 2}"
                      prefWidth="${Math.max(1 + 2, a)}"/>
        """);

        assertEquals(100, root.getDoubleResult(), 0.0001);
        assertEquals(12, root.getIntResult());
        assertEquals(10, root.getPrefWidth());

        root.a.set(200);
        assertEquals(140, root.getDoubleResult(), 0.0001);
        assertEquals(202, root.getIntResult());
        assertEquals(200, root.getPrefWidth());

        root.a.set(-1);
        assertEquals(100, root.getDoubleResult(), 0.0001);
        assertEquals(2, root.getIntResult());
        assertEquals(3, root.getPrefWidth());
    }

    @Test
    public void Operator_Argument_Forms_Separate_Compiled_Expression_Island() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      doubleResult="${Math.max(a + 1, 100) + a}"/>
        """);

        assertEquals(110, root.getDoubleResult(), 0.0001);
        assertEquals(2, Arrays.stream(root.getClass().getDeclaredMethods())
            .filter(method -> method.getName().startsWith("__FX$eval$"))
            .count());
        assertEquals(3, Arrays.stream(root.getClass().getDeclaredClasses())
            .filter(type -> type.getSimpleName().startsWith("__FX$Function$"))
            .count());
    }

    @Test
    public void Integral_And_Floating_Point_Runtime_Behavior_Matches_Java() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      intResult="$2147483647 + 1"
                      longResult="$-7 / 2"
                      doubleResult="$1.0 / 0.0"/>
        """);

        assertEquals(Integer.MIN_VALUE, root.getIntResult());
        assertEquals(-3, root.getLongResult());
        assertEquals(Double.POSITIVE_INFINITY, root.getDoubleResult());
    }

    @Test
    public void Integral_Division_By_Zero_Propagates_ArithmeticException() {
        assertThrows(ArithmeticException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      intResult="$1 / 0"/>
        """));
    }

    @Test
    public void Null_Boxed_Divisors_Use_Zero_Before_Java_Division_Semantics() {
        assertThrows(ArithmeticException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      intResult="$10 / boxedInteger"/>
        """));

        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      floatResult="${10F / boxedFloatProp}"/>
        """, "Floating", null);

        assertEquals(Float.POSITIVE_INFINITY, root.getFloatResult());
        root.boxedFloatProp.set(2F);
        assertEquals(5F, root.getFloatResult());
        root.boxedFloatProp.set(null);
        assertEquals(Float.POSITIVE_INFINITY, root.getFloatResult());
    }

    @Test
    public void Nonliteral_Leaves_Are_Evaluated_Left_To_Right_Without_Deduplication() {
        TestPane.invocationCount = 0;
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      intResult="$record(1) * 10 + record(2)"
                      longResult="$record(3) + record(3)"/>
        """);

        assertEquals(12, root.getIntResult());
        assertEquals(6, root.getLongResult());
        assertEquals(List.of(1, 2, 3, 3), root.evaluationOrder);
        assertEquals(4, TestPane.invocationCount);
    }

    @Test
    public void Repeated_Zero_Argument_Function_Operands_Are_Evaluated_Separately() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      intResult="$next() + next()"/>
        """);

        assertEquals(3, root.getIntResult());
        assertEquals(2, root.nextCount);
    }

    @Test
    public void Functional_Lowering_Evaluates_Later_Leaves_Before_Arithmetic_Throws() {
        TestPane.invocationCount = 0;

        assertThrows(ArithmeticException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      intResult="$10 / boxedInteger + record(9)"/>
        """));

        assertEquals(1, TestPane.invocationCount);
    }

    @Test
    public void Floating_Point_Trees_Are_Not_Reassociated() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      doubleResult="$10000000000000000.0 + -10000000000000000.0 + 1.0"
                      prefWidth="$10000000000000000.0 + (-10000000000000000.0 + 1.0)"/>
        """);

        assertEquals(1, root.getDoubleResult());
        assertEquals(0, root.getPrefWidth());
    }

    @Test
    public void Long_Form_Source_Syntax_Supports_Arithmetic() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <intResult><fx:Evaluate source="1 + 2 * 3"/></intResult>
                <doubleResult><fx:Observe source="a * 2"/></doubleResult>
            </TestPane>
        """);

        assertEquals(7, root.getIntResult());
        assertEquals(20, root.getDoubleResult(), 0.0001);
        root.a.set(4);
        assertEquals(8, root.getDoubleResult(), 0.0001);
    }

    @Test
    public void Multi_Operator_Observable_Expression_Uses_One_Helper_And_One_Wrapper() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      doubleResult="${a * b * 0.5 + c}"/>
        """);

        Method[] helpers = Arrays.stream(root.getClass().getDeclaredMethods())
            .filter(method -> method.getName().startsWith("__FX$eval$"))
            .toArray(Method[]::new);
        assertEquals(1, helpers.length);
        assertTrue(Modifier.isStatic(helpers[0].getModifiers()));
        assertFalse(Modifier.isPrivate(helpers[0].getModifiers()));
        assertFalse(Modifier.isPublic(helpers[0].getModifiers()));
        assertFalse(Modifier.isProtected(helpers[0].getModifiers()));
        assertEquals(double.class, helpers[0].getReturnType());
        assertArrayEquals(
            new Class<?>[] {double.class, double.class, double.class}, helpers[0].getParameterTypes());

        long wrappers = Arrays.stream(root.getClass().getDeclaredClasses())
            .filter(type -> type.getSimpleName().startsWith("__FX$Function$"))
            .count();
        assertEquals(1, wrappers);
    }

    @Test
    public void Arithmetic_Result_Can_Widen_But_Not_Narrow() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      doubleResult="$intValue + 1"/>
        """);
        assertEquals(6, root.getDoubleResult(), 0.0001);

        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      intResult="$doubleValue + 1"/>
        """));
        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("doubleValue + 1", ex);
    }

    @Test
    public void Nonnumeric_Operand_Is_Rejected_At_The_Operand() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      intResult="$stringValue * 2"/>
        """));

        assertEquals(ErrorCode.INVALID_ARITHMETIC_OPERAND, ex.getDiagnostic().getCode());
        assertCodeHighlight("stringValue", ex);
        assertTrue(ex.getDiagnostic().getMessage().contains("java.lang.String"));
    }

    @Test
    public void Number_And_BigDecimal_Are_Not_Arithmetic_Operands() {
        MarkupException numberEx = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      doubleResult="$numberValue + 1"/>
        """));

        assertEquals(ErrorCode.INVALID_ARITHMETIC_OPERAND, numberEx.getDiagnostic().getCode());
        assertCodeHighlight("numberValue", numberEx);

        MarkupException decimalEx = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      doubleResult="$decimalValue + 1"/>
        """));

        assertEquals(ErrorCode.INVALID_ARITHMETIC_OPERAND, decimalEx.getDiagnostic().getCode());
        assertCodeHighlight("decimalValue", decimalEx);

        MarkupException bigIntegerEx = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      doubleResult="$bigIntegerValue + 1"/>
        """, "BigInteger", null));

        assertEquals(ErrorCode.INVALID_ARITHMETIC_OPERAND, bigIntegerEx.getDiagnostic().getCode());
        assertCodeHighlight("bigIntegerValue", bigIntegerEx);
    }

    @Test
    public void Boolean_Operand_Is_Rejected_At_The_Operand() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      intResult="$booleanValue + 1"/>
        """));

        assertEquals(ErrorCode.INVALID_ARITHMETIC_OPERAND, ex.getDiagnostic().getCode());
        assertCodeHighlight("booleanValue", ex);
    }

    @Test
    public void Boolean_And_Null_Literals_Are_Rejected_At_The_Operand() {
        MarkupException booleanEx = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      intResult="$true + 1"/>
        """));

        assertEquals(ErrorCode.INVALID_ARITHMETIC_OPERAND, booleanEx.getDiagnostic().getCode());
        assertCodeHighlight("true", booleanEx);

        MarkupException nullEx = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      intResult="$null + 1"/>
        """));

        assertEquals(ErrorCode.INVALID_ARITHMETIC_OPERAND, nullEx.getDiagnostic().getCode());
        assertCodeHighlight("null", nullEx);
    }

    @Test
    public void Qualified_Keyword_Names_Remain_Path_Operands() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      intResult="$:element.true + :element.null"/>
        """);

        assertEquals(41, root.getIntResult());
    }

    @Test
    public void Arithmetic_Binding_Mode_Restrictions_Are_Diagnosed() {
        MarkupException pushEx = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      intResult=">{intProp + 1}"/>
        """));

        assertEquals(ErrorCode.UNEXPECTED_TOKEN, pushEx.getDiagnostic().getCode());
        assertCodeHighlight("+", pushEx);

        MarkupException synchronizeEx = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      intResult="#{intProp + 1}"/>
        """));

        assertEquals(ErrorCode.EXPRESSION_NOT_INVERTIBLE, synchronizeEx.getDiagnostic().getCode());
        assertCodeHighlight("intProp + 1", synchronizeEx);
    }

    @Test
    public void Observable_Arithmetic_Requires_An_Observable_Operand_Without_Leaking_Helper_Name() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      intResult="${1 + 2}"/>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertFalse(ex.getDiagnostic().getMessage().contains("__FX$Arithmetic"));
    }

    @Test
    public void Arithmetic_Works_Without_Code_Behind_Class() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.Pane?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                  prefWidth="$1 + 2 * 3"/>
        """);

        assertEquals(7, root.getPrefWidth());
        assertEquals(1, Arrays.stream(root.getClass().getDeclaredMethods())
            .filter(method -> method.getName().startsWith("__FX$eval$"))
            .count());
    }

    @Test
    public void Excessive_Helper_Parameter_Slots_Are_Diagnosed() {
        String expression = String.join(" + ", Collections.nCopies(256, "intValue"));
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      intResult="$%s"/>
        """.formatted(expression)));

        assertEquals(ErrorCode.ARITHMETIC_EXPRESSION_TOO_COMPLEX, ex.getDiagnostic().getCode());
    }
}

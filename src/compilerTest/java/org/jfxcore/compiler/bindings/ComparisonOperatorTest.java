// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class ComparisonOperatorTest extends CompilerTestBase {

    public enum Rank {
        LOW,
        HIGH
    }

    public static class WeirdComparable implements Comparable<WeirdComparable> {
        static int comparisonCount;
        public final int value;

        public WeirdComparable(int value) {
            this.value = value;
        }

        @Override
        public int compareTo(WeirdComparable other) {
            ++comparisonCount;
            return (value - other.value) * 10;
        }
    }

    @SuppressWarnings("rawtypes")
    public static class RawComparable implements Comparable {
        @Override
        public int compareTo(Object other) {
            return 0;
        }
    }

    public static class GenericComparable<T> implements Comparable<T> {
        public final String value;

        public GenericComparable(String value) {
            this.value = value;
        }

        @Override
        public int compareTo(T other) {
            return value.compareTo(other.toString());
        }
    }

    public static class StringComparable extends GenericComparable<String> {
        public StringComparable(String value) {
            super(value);
        }
    }

    public static class ComparablePair<T extends Comparable<? super T>> {
        public final T first;
        public final T second;

        public ComparablePair(T first, T second) {
            this.first = first;
            this.second = second;
        }
    }

    @SuppressWarnings({"unused", "StringOperationCanBeSimplified", "UnnecessaryBoxing"})
    public static class TestPane extends Pane {
        public int intValue = 4;
        public long longValue = 5;
        public float floatValue = Float.NaN;
        public double doubleValue = -0.0;
        public String firstString = "alpha";
        public String secondString = "beta";
        public Number firstNumber = Integer.valueOf(1);
        public Number secondNumber = Long.valueOf(1);
        public Object firstObject = firstNumber;
        public Object secondObject = secondNumber;
        public Boolean firstBoolean = true;
        public Boolean secondBoolean = true;
        public Boolean nullBoolean;
        public BigDecimal firstDecimal = new BigDecimal("1.0");
        public BigDecimal secondDecimal = new BigDecimal("1.00");
        public LocalDate firstDate = LocalDate.of(2026, 1, 1);
        public LocalDate secondDate = LocalDate.of(2026, 1, 2);
        public Rank firstRank = Rank.LOW;
        public Rank secondRank = Rank.HIGH;
        public WeirdComparable firstComparable = new WeirdComparable(1);
        public WeirdComparable secondComparable = new WeirdComparable(2);
        public WeirdComparable nullComparable;
        public RawComparable rawComparable = new RawComparable();
        public StringComparable stringComparable = new StringComparable("alpha");
        public Comparable<? super String> superStringComparable = firstString;
        public Comparable<? extends CharSequence> extendsCharSequenceComparable = firstString;
        public ComparablePair<String> stringPair = new ComparablePair<>(firstString, secondString);
        public String[] firstArray = {"value"};
        public String[] secondArray = {"value"};
        public String[] sameArray = firstArray;
        public final List<String> stringList = new ArrayList<>();
        public final List<Integer> integerList = new ArrayList<>();
        public final Collection<Integer> integerCollection = integerList;
        public String equalString = new String("value");
        public String otherEqualString = new String("value");
        public Object sameStringObject = equalString;
        public Object booleanObject = Boolean.TRUE;
        public int zero;
        public final List<Integer> acquisitionOrder = new ArrayList<>();
        public final ObjectProperty<Integer> firstInteger = new SimpleObjectProperty<>(1);
        public final ObjectProperty<Integer> nullInteger = new SimpleObjectProperty<>();
        public final ObjectProperty<Integer> secondInteger = new SimpleObjectProperty<>(2);
        public final ObjectProperty<Long> secondLong = new SimpleObjectProperty<>(1L);
        public final ObjectProperty<Long> nullLong = new SimpleObjectProperty<>();
        public final ObjectProperty<Number> firstObservableNumber = new SimpleObjectProperty<>(Integer.valueOf(1));
        public final ObjectProperty<Number> secondObservableNumber = new SimpleObjectProperty<>(Long.valueOf(1));
        public final ObjectProperty<Object> firstObservableObject = new SimpleObjectProperty<>(Integer.valueOf(1));
        public final ObjectProperty<Object> secondObservableObject = new SimpleObjectProperty<>(Long.valueOf(1));

        public WeirdComparable recordComparable(int value) {
            acquisitionOrder.add(value);
            return new WeirdComparable(value);
        }

        private final BooleanProperty result0 = new SimpleBooleanProperty();
        private final BooleanProperty result1 = new SimpleBooleanProperty();
        private final BooleanProperty result2 = new SimpleBooleanProperty();
        private final BooleanProperty result3 = new SimpleBooleanProperty();

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
    }

    @BeforeEach
    public void resetCounters() {
        WeirdComparable.comparisonCount = 0;
    }

    @Test
    public void Evaluate_Numeric_Relations_Use_Binary_Promotion_And_Java_Floating_Point_Semantics() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$intValue < longValue"
                      result1="$longValue >= intValue"
                      result2="$floatValue < 1"
                      result3="$doubleValue >= 0"/>
        """);

        assertTrue(root.isResult0());
        assertTrue(root.isResult1());
        assertFalse(root.isResult2());
        assertTrue(root.isResult3());
    }

    @Test
    public void Xml_Entity_Decoded_Relational_Operators_Compile_Semantically() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$intValue &lt; longValue"
                      result1="$intValue &lt;= intValue"/>
        """);

        assertTrue(root.isResult0());
        assertTrue(root.isResult1());
    }

    @Test
    public void Evaluate_Numeric_Relations_Emit_All_Four_Predicates() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$intValue <= 4"
                      result1="$longValue > intValue"
                      result2="$floatValue > 1"
                      result3="$doubleValue >= 0"/>
        """);

        assertTrue(root.isResult0());
        assertTrue(root.isResult1());
        assertFalse(root.isResult2());
        assertTrue(root.isResult3());
    }

    @Test
    public void Evaluate_Comparable_Relations_Invoke_The_Left_Contract() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$firstString < secondString"
                      result1="$secondString > firstString"
                      result2="$firstBoolean >= secondBoolean"/>
        """);

        assertTrue(root.isResult0());
        assertTrue(root.isResult1());
        assertTrue(root.isResult2());
    }

    @Test
    public void Evaluate_Value_Equality_Is_Selected_From_Static_Types() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$firstInteger == secondLong"
                      result1="$firstNumber == secondNumber"
                      result2="$firstObject == secondObject"
                      result3="$firstInteger != secondLong"/>
        """);

        assertTrue(root.isResult0());
        assertFalse(root.isResult1());
        assertFalse(root.isResult2());
        assertFalse(root.isResult3());
    }

    @Test
    public void Evaluate_Numeric_Wrapper_Relation_Returns_False_For_Runtime_Null() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$nullInteger < secondLong"/>
        """);

        assertFalse(root.isResult0());
    }

    @Test
    public void Observe_Numeric_Relation_Preserves_Wrapper_Nulls_And_Recomputes() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="${firstInteger < secondLong}"/>
        """);

        assertTrue(root.result0Property().isBound());
        assertFalse(root.isResult0());

        root.secondLong.set(2L);
        assertTrue(root.isResult0());

        root.firstInteger.set(null);
        assertFalse(root.isResult0());
    }

    @Test
    public void Observe_Value_Equality_Remains_Directed_By_Declared_Types() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="${firstInteger == secondLong}"
                      result1="${firstObservableNumber == secondObservableNumber}"
                      result2="${firstObservableObject == secondObservableObject}"
                      result3="${firstInteger != secondLong}"/>
        """);

        assertTrue(root.isResult0());
        assertFalse(root.isResult1());
        assertFalse(root.isResult2());
        assertFalse(root.isResult3());

        root.secondLong.set(2L);
        assertFalse(root.isResult0());
        assertTrue(root.isResult3());

        root.secondObservableNumber.set(Integer.valueOf(1));
        root.secondObservableObject.set(Integer.valueOf(1));
        assertTrue(root.isResult1());
        assertTrue(root.isResult2());
    }

    @Test
    public void Evaluate_Numeric_Equality_Preserves_Null_Nan_And_Signed_Zero_Rules() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$nullInteger == nullLong"
                      result1="$nullInteger != secondLong"
                      result2="$floatValue == floatValue"
                      result3="$doubleValue == 0.0"/>
        """);

        assertTrue(root.isResult0());
        assertTrue(root.isResult1());
        assertFalse(root.isResult2());
        assertTrue(root.isResult3());
    }

    @Test
    public void Evaluate_Boolean_Equality_Uses_Boxed_Null_Truth_Table() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$firstBoolean == secondBoolean"
                      result1="$nullBoolean == null"
                      result2="$nullBoolean != firstBoolean"
                      result3="$booleanObject == true"/>
        """);

        assertTrue(root.isResult0());
        assertTrue(root.isResult1());
        assertTrue(root.isResult2());
        assertTrue(root.isResult3());
    }

    @Test
    public void Evaluate_Fallback_Equality_Boxes_Primitives_And_Uses_Ordinary_Equals() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$firstObject == 1"
                      result1="$firstDecimal == secondDecimal"
                      result2="$firstArray == secondArray"
                      result3="$firstArray == sameArray"/>
        """);

        assertTrue(root.isResult0());
        assertFalse(root.isResult1());
        assertFalse(root.isResult2());
        assertTrue(root.isResult3());
    }

    @Test
    public void Evaluate_Identity_Equality_Uses_References_And_Null_Truth_Table() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$equalString == otherEqualString"
                      result1="$equalString === otherEqualString"
                      result2="$equalString === sameStringObject"
                      result3="$null !== null"/>
        """);

        assertTrue(root.isResult0());
        assertFalse(root.isResult1());
        assertTrue(root.isResult2());
        assertFalse(root.isResult3());
    }

    @Test
    public void Evaluate_Observable_Selection_Identity_Compares_Wrappers() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$::firstInteger === ::firstInteger"
                      result1="$::firstInteger !== ::secondInteger"/>
        """);

        assertTrue(root.isResult0());
        assertTrue(root.isResult1());
    }

    @Test
    public void Evaluate_Natural_Ordering_Supports_Standard_Comparable_Types() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$firstDecimal <= secondDecimal"
                      result1="$firstDate < secondDate"
                      result2="$firstRank < secondRank"
                      result3="$stringComparable <= firstString"/>
        """);

        assertTrue(root.isResult0());
        assertTrue(root.isResult1());
        assertTrue(root.isResult2());
        assertTrue(root.isResult3());
    }

    @Test
    public void Evaluate_Relation_Uses_Substituted_And_Lower_Bounded_Comparable_Contracts() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$stringPair.first < stringPair.second"
                      result1="$superStringComparable < secondString"/>
        """);

        assertTrue(root.isResult0());
        assertTrue(root.isResult1());
    }

    @Test
    public void Evaluate_Comparable_Operands_Are_Acquired_Left_To_Right_And_Compared_Once() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$recordComparable(1) < recordComparable(2)"/>
        """);

        assertTrue(root.isResult0());
        assertEquals(List.of(1, 2), root.acquisitionOrder);
        assertEquals(1, WeirdComparable.comparisonCount);
    }

    @Test
    public void Evaluate_Comparable_Null_Guard_Follows_Right_Operand_Acquisition() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$nullComparable < recordComparable(2)"/>
        """);

        assertFalse(root.isResult0());
        assertEquals(List.of(2), root.acquisitionOrder);
        assertEquals(0, WeirdComparable.comparisonCount);
    }

    @Test
    public void Relational_Null_Guard_Does_Not_Skip_A_Reached_Right_Operator_Subtree() {
        assertThrows(ArithmeticException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$nullInteger < 1 / zero"/>
        """));
    }

    @Test
    public void Mixed_Comparison_Uses_One_Package_Access_Static_Helper() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$intValue + 1 < longValue * 2"/>
        """);

        assertTrue(root.isResult0());
        Method[] helpers = Arrays.stream(root.getClass().getDeclaredMethods())
            .filter(method -> method.getName().startsWith("__FX$eval$"))
            .toArray(Method[]::new);
        assertEquals(1, helpers.length);
        assertTrue(Modifier.isStatic(helpers[0].getModifiers()));
        assertFalse(Modifier.isPrivate(helpers[0].getModifiers()));
    }

    @Test
    public void Chained_Relation_Is_Rejected_At_The_Second_Operator() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$intValue < longValue < doubleValue"/>
        """));

        assertEquals(ErrorCode.INVALID_CHAINED_RELATION, ex.getDiagnostic().getCode());
        assertCodeHighlight("<", ex);
    }

    @Test
    public void Raw_Comparable_Is_Rejected() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$rawComparable < rawComparable"/>
        """));

        assertEquals(ErrorCode.RAW_COMPARABLE_OPERAND, ex.getDiagnostic().getCode());
        assertCodeHighlight("rawComparable", ex);
    }

    @Test
    public void Incompatible_Comparable_Right_Operand_Is_Rejected() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$firstString < firstInteger"/>
        """));

        assertEquals(ErrorCode.INVALID_RELATIONAL_OPERANDS, ex.getDiagnostic().getCode());
        assertCodeHighlight("firstInteger", ex);
    }

    @Test
    public void Upper_Bounded_Comparable_Receiver_Is_Not_Safely_Invocable() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$extendsCharSequenceComparable < secondString"/>
        """));

        assertEquals(ErrorCode.INVALID_RELATIONAL_OPERANDS, ex.getDiagnostic().getCode());
        assertCodeHighlight("secondString", ex);
    }

    @Test
    public void Number_And_Object_Static_Types_Are_Not_Relationally_Redispatched() {
        MarkupException numberEx = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$firstNumber < secondNumber"/>
        """, "Number", null));
        assertEquals(ErrorCode.INVALID_RELATIONAL_OPERANDS, numberEx.getDiagnostic().getCode());

        MarkupException objectEx = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$firstObject < secondObject"/>
        """, "Object", null));
        assertEquals(ErrorCode.INVALID_RELATIONAL_OPERANDS, objectEx.getDiagnostic().getCode());
    }

    @Test
    public void Primitive_And_Incompatible_Reference_Identity_Are_Rejected() {
        MarkupException primitiveEx = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$1 === 1"/>
        """, "Primitive", null));
        assertEquals(ErrorCode.INVALID_IDENTITY_OPERANDS, primitiveEx.getDiagnostic().getCode());

        MarkupException referenceEx = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$firstString === firstInteger"/>
        """, "Reference", null));
        assertEquals(ErrorCode.INVALID_IDENTITY_OPERANDS, referenceEx.getDiagnostic().getCode());
    }

    @Test
    public void Provably_Distinct_Parameterized_References_Are_Not_Identity_Comparable() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$stringList === integerList"/>
        """));

        assertEquals(ErrorCode.INVALID_IDENTITY_OPERANDS, ex.getDiagnostic().getCode());
    }

    @Test
    public void Invariant_Observe_Comparison_Does_Not_Leak_The_Helper_Name() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="${1 < 2}"/>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertFalse(ex.getDiagnostic().getMessage().contains("__FX$Expression"));
    }

    @Test
    public void Comparison_Binding_Mode_Restrictions_Are_Diagnosed() {
        for (String expression : List.of("intValue < longValue", "intValue == longValue")) {
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

    @Test
    public void Relational_Null_Literal_Is_Rejected_At_The_Literal() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result0="$null < intValue"/>
        """));

        assertEquals(ErrorCode.INVALID_RELATIONAL_OPERANDS, ex.getDiagnostic().getCode());
        assertCodeHighlight("null", ex);
    }

    @Test
    public void Every_Relational_Operator_Rejects_Null_Literals_On_Either_Side() {
        String[] operators = {"<", "<=", ">", ">="};
        int suffix = 0;

        for (String operator : operators) {
            for (String expression : List.of(
                    "null " + operator + " intValue",
                    "intValue " + operator + " null",
                    "null " + operator + " null")) {
                String fxml = """
                    <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                              result0="$%s"/>
                """.formatted(expression);
                String classSuffix = "NullRelation" + suffix++;
                MarkupException ex = assertThrows(
                    MarkupException.class,
                    () -> compileAndRun(fxml, classSuffix, null));

                assertEquals(ErrorCode.INVALID_RELATIONAL_OPERANDS, ex.getDiagnostic().getCode());
                assertCodeHighlight("null", ex);
            }
        }
    }
}

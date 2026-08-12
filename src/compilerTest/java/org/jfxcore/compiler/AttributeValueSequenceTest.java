// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.beans.NamedArg;
import javafx.beans.property.ListProperty;
import javafx.beans.property.Property;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.geometry.Insets;
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.Reflection;
import org.jfxcore.compiler.util.TestExtension;
import org.jfxcore.markup.MarkupContext;
import org.jfxcore.markup.MarkupExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"HttpUrlsUsage", "unused"})
@ExtendWith(TestExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
public class AttributeValueSequenceTest extends CompilerTestBase {

    public record Invocation(
            double value,
            String targetName,
            Class<?> targetType,
            Object targetBean) {}

    private static final List<Invocation> supplierInvocations = new ArrayList<>();
    private static int consumerInvocations;

    @BeforeEach
    public void resetInvocations() {
        supplierInvocations.clear();
        consumerInvocations = 0;
    }

    public static class DoubleValue implements MarkupExtension.DoubleSupplier {
        private final double value;

        public DoubleValue(@NamedArg("value") double value) {
            this.value = value;
        }

        @Override
        public double get(MarkupContext context) {
            supplierInvocations.add(new Invocation(
                value, context.getTargetName(), context.getTargetType(), context.getTargetBean()));
            return value;
        }
    }

    public static class IntValue implements MarkupExtension.IntSupplier {
        private final int value;

        public IntValue(@NamedArg("value") int value) {
            this.value = value;
        }

        @Override
        public int get(MarkupContext context) {
            return value;
        }
    }

    public static class ByteValue implements MarkupExtension.Supplier<Byte> {
        private final byte value;

        public ByteValue(@NamedArg("value") byte value) {
            this.value = value;
        }

        @Override
        public Byte get(MarkupContext context) {
            return value;
        }
    }

    public static class DualRoleDoubleValue
            implements MarkupExtension.PropertyConsumer<Number>, MarkupExtension.DoubleSupplier {
        private final double value;

        public DualRoleDoubleValue(@NamedArg("value") double value) {
            this.value = value;
        }

        @Override
        public void accept(Property<Number> property, MarkupContext context) {
            ++consumerInvocations;
            property.setValue(-1);
        }

        @Override
        public double get(MarkupContext context) {
            supplierInvocations.add(new Invocation(
                value, context.getTargetName(), context.getTargetType(), context.getTargetBean()));
            return value;
        }
    }

    public static class ConsumerOnly implements MarkupExtension.PropertyConsumer<Number> {
        @Override
        public void accept(Property<Number> property, MarkupContext context) {
            ++consumerInvocations;
        }
    }

    public static class Box {
        private final double value;

        public Box(@NamedArg("value") double value) {
            this.value = value;
        }

        public double getValue() {
            return value;
        }
    }

    public static class Helper {
        public double makeDouble(String ignored) {
            return 13;
        }
    }

    public static class BoxValue implements MarkupExtension.Supplier<Box> {
        private final double value;

        public BoxValue(@NamedArg("value") double value) {
            this.value = value;
        }

        @Override
        public Box get(MarkupContext context) {
            return new Box(value);
        }
    }

    public static class AmbiguousValue {
        public AmbiguousValue(@NamedArg("value") String value) {}
        public AmbiguousValue(@NamedArg("value") URL value) {}
    }

    public static class AmbiguousLiteralValue {
        public AmbiguousLiteralValue(@NamedArg("value") String value) {}
        public AmbiguousLiteralValue(@NamedArg("value") Integer value) {}
    }

    public static class AmbiguousLiteralValueReversed {
        public AmbiguousLiteralValueReversed(@NamedArg("value") Integer value) {}
        public AmbiguousLiteralValueReversed(@NamedArg("value") String value) {}
    }

    public static class SelfSimilarValue {
        public SelfSimilarValue(@NamedArg("value") SelfSimilarValue value) {}
    }

    public static class LiteralFallbackChoice {
        private final String selected;

        public LiteralFallbackChoice(@NamedArg("value") Class<?> value) {
            selected = "Class";
        }

        public LiteralFallbackChoice(@NamedArg("value") String value) {
            selected = "String";
        }

        public String getSelected() { return selected; }
    }

    public static class DefaultToken {
        public static final DefaultToken DEFAULT = new DefaultToken("default");

        private final String value;

        private DefaultToken(String value) {
            this.value = value;
        }

        public String getValue() { return value; }
    }

    public static class StaticDefaultPane extends Pane {
        private final DefaultToken token;

        public StaticDefaultPane(
                @NamedArg(value = "token", defaultValue = "DEFAULT") DefaultToken token) {
            this.token = token;
        }

        public DefaultToken getToken() { return token; }
    }

    public static class LiteralConstructedObject {
        private final String selected;
        private String marker;

        public LiteralConstructedObject(@NamedArg("value") Class<?> value) {
            selected = "Class";
        }

        public LiteralConstructedObject(@NamedArg("value") String value) {
            selected = "String";
        }

        public String getSelected() { return selected; }
        public String getMarker() { return marker; }
        public void setMarker(String marker) { this.marker = marker; }
    }

    public static class FunctionChoice {
        private final String selected;
        private final double value;

        public FunctionChoice(@NamedArg("value") String value) {
            selected = "String";
            this.value = -1;
        }

        public FunctionChoice(@NamedArg("value") double value) {
            selected = "double";
            this.value = value;
        }

        public String getSelected() { return selected; }
        public double getValue() { return value; }
    }

    public static class PrimitiveChoice {
        private final String selected;
        private final long value;

        public PrimitiveChoice(@NamedArg("value") int value) {
            selected = "int";
            this.value = value;
        }

        public PrimitiveChoice(@NamedArg("value") long value) {
            selected = "long";
            this.value = value;
        }

        public String getSelected() { return selected; }
        public long getValue() { return value; }
    }

    public static class BoxingChoice {
        private final String selected;

        public BoxingChoice(@NamedArg("value") int value) {
            selected = "int";
        }

        public BoxingChoice(@NamedArg("value") Integer value) {
            selected = "Integer";
        }

        public String getSelected() { return selected; }
    }

    public static class UnboxingWideningChoice {
        private final String selected;

        public UnboxingWideningChoice(@NamedArg("value") int value) {
            selected = "int";
        }

        public UnboxingWideningChoice(@NamedArg("value") long value) {
            selected = "long";
        }

        public String getSelected() { return selected; }
    }

    public static class WideningDistanceChoice {
        private final String selected;

        public WideningDistanceChoice(@NamedArg("value") short value) {
            selected = "short";
        }

        public WideningDistanceChoice(@NamedArg("value") int value) {
            selected = "int";
        }

        public WideningDistanceChoice(@NamedArg("value") long value) {
            selected = "long";
        }

        public String getSelected() { return selected; }
    }

    public static class ReferenceChoice {
        private final String selected;

        public ReferenceChoice(@NamedArg("value") Number value) {
            selected = "Number";
        }

        public ReferenceChoice(@NamedArg("value") Integer value) {
            selected = "Integer";
        }

        public String getSelected() { return selected; }
    }

    public static class NoArityChoice {
        public NoArityChoice(
                @NamedArg("first") double first,
                @NamedArg("second") double second) {}
    }

    public static class PrefixFailureChoice {
        public PrefixFailureChoice(
                @NamedArg("first") double first,
                @NamedArg("second") int second) {}

        public PrefixFailureChoice(
                @NamedArg("first") String first,
                @NamedArg("second") double second) {}
    }

    public static class Wrapper {
        private final Box box;

        public Wrapper(@NamedArg("box") Box box) {
            this.box = box;
        }

        public Box getBox() { return box; }
    }

    public static class WrapperTarget extends Pane {
        private final Wrapper wrapper;

        public WrapperTarget(@NamedArg("wrapper") Wrapper wrapper) {
            this.wrapper = wrapper;
        }

        public Wrapper getWrapper() { return wrapper; }
    }

    public static class ConsumerTarget extends Pane {
        private final double value;

        public ConsumerTarget(@NamedArg("value") double value) {
            this.value = value;
        }

        public double getValue() { return value; }
    }

    public static class MatrixArgumentPane extends Pane {
        private final double[][] matrix;

        public MatrixArgumentPane(@NamedArg("matrix") double[][] matrix) {
            this.matrix = matrix;
        }

        public double[][] getMatrix() { return matrix; }
    }

    public static class ConstructedList extends ArrayList<Double> {
        private final boolean constructedFromArgument;

        public ConstructedList() {
            constructedFromArgument = false;
        }

        public ConstructedList(@NamedArg("value") double value) {
            constructedFromArgument = true;
            add(value);
        }

        public boolean isConstructedFromArgument() {
            return constructedFromArgument;
        }
    }

    public static class ConstructedMap extends HashMap<String, Object> {
        private final String resource;

        public ConstructedMap(@NamedArg("resource") String resource) {
            this.resource = resource;
            put("resource", resource);
        }

        public String getResource() {
            return resource;
        }
    }

    public static class ArrayArgumentPane extends Pane {
        private final int[] values;

        public ArrayArgumentPane(@NamedArg("values") int[] values) {
            this.values = values;
        }

        public int[] getValues() {
            return values;
        }
    }

    public static class ObjectArgumentPane extends Pane {
        private final Box box;

        public ObjectArgumentPane(@NamedArg("box") Box box) {
            this.box = box;
        }

        public Box getBox() {
            return box;
        }
    }

    public static class TestPane extends Pane {
        private double[] doubles;
        private int[] ints;
        private String[] strings;
        private double[][] matrix;
        private Insets paddingValue;
        private Box box;
        private AmbiguousValue ambiguousValue;
        private AmbiguousLiteralValue ambiguousLiteralValue;
        private AmbiguousLiteralValueReversed ambiguousLiteralValueReversed;
        private SelfSimilarValue selfSimilarValue;
        private LiteralFallbackChoice literalFallbackChoice;
        private LiteralConstructedObject literalConstructedObject;
        private FunctionChoice functionChoice;
        private PrimitiveChoice primitiveChoice;
        private BoxingChoice boxingChoice;
        private UnboxingWideningChoice unboxingWideningChoice;
        private WideningDistanceChoice wideningDistanceChoice;
        private ReferenceChoice referenceChoice;
        private NoArityChoice noArityChoice;
        private PrefixFailureChoice prefixFailureChoice;
        private ConstructedList constructedList = new ConstructedList();
        private ConstructedMap constructedMap;
        private int constructedListSetterCalls;
        private final DoubleProperty number = new SimpleDoubleProperty(this, "number");
        private final DoubleProperty source = new SimpleDoubleProperty(this, "source", 6);
        private final Integer boxedSource = 7;
        private final Helper helper = new Helper();

        private final ObservableList<Double> readOnlyValues = FXCollections.observableArrayList();
        private final ObservableList<String> textValues = FXCollections.observableArrayList();
        private final ListProperty<Double> listValues =
            new SimpleListProperty<>(FXCollections.observableArrayList());
        private final ObservableMap<String, Object> mapValues = FXCollections.observableHashMap();

        public double[] getDoubles() { return doubles; }
        public void setDoubles(double[] doubles) { this.doubles = doubles; }
        public int[] getInts() { return ints; }
        public void setInts(int[] ints) { this.ints = ints; }
        public String[] getStrings() { return strings; }
        public void setStrings(String[] strings) { this.strings = strings; }
        public double[][] getMatrix() { return matrix; }
        public void setMatrix(double[][] matrix) { this.matrix = matrix; }
        public Insets getPaddingValue() { return paddingValue; }
        public void setPaddingValue(Insets value) { paddingValue = value; }
        public Box getBox() { return box; }
        public void setBox(Box box) { this.box = box; }
        public AmbiguousValue getAmbiguousValue() { return ambiguousValue; }
        public void setAmbiguousValue(AmbiguousValue value) { ambiguousValue = value; }
        public AmbiguousLiteralValue getAmbiguousLiteralValue() { return ambiguousLiteralValue; }
        public void setAmbiguousLiteralValue(AmbiguousLiteralValue value) { ambiguousLiteralValue = value; }
        public AmbiguousLiteralValueReversed getAmbiguousLiteralValueReversed() {
            return ambiguousLiteralValueReversed;
        }
        public void setAmbiguousLiteralValueReversed(AmbiguousLiteralValueReversed value) {
            ambiguousLiteralValueReversed = value;
        }
        public SelfSimilarValue getSelfSimilarValue() { return selfSimilarValue; }
        public void setSelfSimilarValue(SelfSimilarValue value) { selfSimilarValue = value; }
        public LiteralFallbackChoice getLiteralFallbackChoice() { return literalFallbackChoice; }
        public void setLiteralFallbackChoice(LiteralFallbackChoice value) {
            literalFallbackChoice = value;
        }
        public LiteralConstructedObject getLiteralConstructedObject() {
            return literalConstructedObject;
        }
        public void setLiteralConstructedObject(LiteralConstructedObject value) {
            literalConstructedObject = value;
        }
        public FunctionChoice getFunctionChoice() { return functionChoice; }
        public void setFunctionChoice(FunctionChoice value) { functionChoice = value; }
        public PrimitiveChoice getPrimitiveChoice() { return primitiveChoice; }
        public void setPrimitiveChoice(PrimitiveChoice value) { primitiveChoice = value; }
        public BoxingChoice getBoxingChoice() { return boxingChoice; }
        public void setBoxingChoice(BoxingChoice value) { boxingChoice = value; }
        public UnboxingWideningChoice getUnboxingWideningChoice() { return unboxingWideningChoice; }
        public void setUnboxingWideningChoice(UnboxingWideningChoice value) { unboxingWideningChoice = value; }
        public WideningDistanceChoice getWideningDistanceChoice() { return wideningDistanceChoice; }
        public void setWideningDistanceChoice(WideningDistanceChoice value) { wideningDistanceChoice = value; }
        public ReferenceChoice getReferenceChoice() { return referenceChoice; }
        public void setReferenceChoice(ReferenceChoice value) { referenceChoice = value; }
        public NoArityChoice getNoArityChoice() { return noArityChoice; }
        public void setNoArityChoice(NoArityChoice value) { noArityChoice = value; }
        public PrefixFailureChoice getPrefixFailureChoice() { return prefixFailureChoice; }
        public void setPrefixFailureChoice(PrefixFailureChoice value) { prefixFailureChoice = value; }
        public ObservableList<Double> getReadOnlyValues() { return readOnlyValues; }
        public ObservableList<String> getTextValues() { return textValues; }
        public ListProperty<Double> listValuesProperty() { return listValues; }
        public ObservableMap<String, Object> getMapValues() { return mapValues; }
        public ConstructedList getConstructedList() { return constructedList; }
        public void setConstructedList(ConstructedList value) {
            constructedList = value;
            ++constructedListSetterCalls;
        }
        public int getConstructedListSetterCalls() { return constructedListSetterCalls; }
        public ConstructedMap getConstructedMap() { return constructedMap; }
        public void setConstructedMap(ConstructedMap value) { constructedMap = value; }
        public double getNumber() { return number.get(); }
        public void setNumber(double value) { number.set(value); }
        public DoubleProperty numberProperty() { return number; }
        public double getSource() { return source.get(); }
        public void setSource(double value) { source.set(value); }
        public DoubleProperty sourceProperty() { return source; }
        public Integer getBoxedSource() { return boxedSource; }
        public Helper getHelper() { return helper; }
        public double makeDouble(String ignored) { return 12; }
    }

    @Test
    public void Primitive_Array_Mixes_Literals_And_Suppliers_In_Source_Order() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      doubles="1, {DoubleValue value=2}, {DoubleValue value=3}, 4"/>
        """);

        assertArrayEquals(new double[] {1, 2, 3, 4}, root.getDoubles(), 0.001);
        assertEquals(List.of(2.0, 3.0), supplierInvocations.stream().map(Invocation::value).toList());
        assertTrue(supplierInvocations.stream().allMatch(invocation ->
            invocation.targetName().equals("doubles") && invocation.targetType() == double.class));
        assertTrue(supplierInvocations.stream().allMatch(invocation -> invocation.targetBean() == root));
    }

    @Test
    public void Reference_Array_Mixes_Literal_And_ClassPath_Resource() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      strings="plain.css, @image.jpg"/>
        """);

        assertEquals("plain.css", root.getStrings()[0]);
        assertTrue(root.getStrings()[1].endsWith("org/jfxcore/compiler/image.jpg"));
    }

    @Test
    public void ReadOnly_Collection_Adds_Each_Item_In_Source_Order() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      readOnlyValues="1, {DoubleValue value=2}, 3"/>
        """);

        assertEquals(List.of(1.0, 2.0, 3.0), root.getReadOnlyValues());
        assertEquals("readOnlyValues", supplierInvocations.get(0).targetName());
        assertEquals(Double.class, supplierInvocations.get(0).targetType());
        assertSame(root, supplierInvocations.get(0).targetBean());
    }

    @Test
    public void DualRole_Extensions_Use_Their_Supplier_Role_As_ListProperty_Items() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      listValues="{DualRoleDoubleValue value=1}, {DualRoleDoubleValue value=2}"/>
        """);

        assertEquals(List.of(1.0, 2.0), root.listValuesProperty());
        assertEquals(0, consumerInvocations);
        assertEquals(List.of(1.0, 2.0), supplierInvocations.stream().map(Invocation::value).toList());
    }

    @Test
    public void DualRole_Extension_Uses_Its_Consumer_Role_For_A_Direct_Property() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      number="{DualRoleDoubleValue value=8}"/>
        """);

        assertEquals(-1, root.getNumber(), 0.001);
        assertEquals(1, consumerInvocations);
        assertTrue(supplierInvocations.isEmpty());
    }

    @Test
    public void Once_Binding_Can_Be_An_Array_Item() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      doubles="$source, 2"/>
        """);

        assertArrayEquals(new double[] {6, 2}, root.getDoubles(), 0.001);
    }

    @Test
    public void Single_Once_Binding_Becomes_A_One_Element_Array() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      doubles="$source"/>
        """);

        assertArrayEquals(new double[] {6}, root.getDoubles(), 0.001);
    }

    @Test
    public void Single_Once_Binding_Becomes_A_ReadOnly_Collection_Item() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      readOnlyValues="$source"/>
        """);

        assertEquals(List.of(6.0), root.getReadOnlyValues());
    }

    @Test
    public void Single_Once_Binding_Becomes_A_Writable_Collection_Item() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      listValues="$source"/>
        """);

        assertEquals(List.of(6.0), root.listValuesProperty());
    }

    @Test
    public void Boxed_Binding_Is_Unboxed_For_A_Primitive_Array_Item() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      ints="$boxedSource, 2"/>
        """);

        assertArrayEquals(new int[] {7, 2}, root.getInts());
    }

    @Test
    public void Sequence_Constructs_A_Named_Array_Argument() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <ArrayArgumentPane values="{IntValue value=1}, 2"/>
            </TestPane>
        """);

        assertArrayEquals(
            new int[] {1, 2}, ((ArrayArgumentPane)root.getChildren().get(0)).getValues());
    }

    @Test
    public void Sequence_Implicitly_Constructs_A_Named_Object_Argument() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <ObjectArgumentPane box="{DoubleValue value=4}"/>
            </TestPane>
        """);

        ObjectArgumentPane child = (ObjectArgumentPane)root.getChildren().get(0);
        assertEquals(4, child.getBox().getValue(), 0.001);
        assertEquals("value", supplierInvocations.get(0).targetName());
        assertEquals(double.class, supplierInvocations.get(0).targetType());
        assertNull(supplierInvocations.get(0).targetBean());
    }

    @Test
    public void Incompatible_Direct_Binding_Can_Fall_Back_To_Implicit_Construction() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      box="$source"/>
        """);

        assertEquals(6, root.getBox().getValue(), 0.001);
    }

    @Test
    public void TargetTyped_Function_Can_Fall_Back_To_Implicit_Construction() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      box="$makeDouble('x')"/>
        """);

        assertEquals(12, root.getBox().getValue(), 0.001);
    }

    @Test
    public void TargetTyped_Invocation_Can_Fall_Back_To_Implicit_Construction() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      box="$helper.makeDouble('x')"/>
        """);

        assertEquals(13, root.getBox().getValue(), 0.001);
    }

    @Test
    public void TargetTyped_Function_Does_Not_Stop_Constructor_Enumeration() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      functionChoice="$makeDouble('x')"/>
        """);

        assertEquals("double", root.getFunctionChoice().getSelected());
        assertEquals(12, root.getFunctionChoice().getValue(), 0.001);
    }

    @Test
    public void Intrinsically_Invalid_Function_Does_Not_Become_A_Constructor_Mismatch() {
        MarkupException exception = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      box="$doesNotExist('x')"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, exception.getDiagnostic().getCode());
    }

    @Test
    public void Implicit_Constructor_Prefers_Identity_Over_Primitive_Widening() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      primitiveChoice="{IntValue value=3}"/>
        """);

        assertEquals("int", root.getPrimitiveChoice().getSelected());
        assertEquals(3, root.getPrimitiveChoice().getValue());
    }

    @Test
    public void Implicit_Constructor_Prefers_Identity_Over_Boxing() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      boxingChoice="{IntValue value=3}"/>
        """);

        assertEquals("int", root.getBoxingChoice().getSelected());
    }

    @Test
    public void Implicit_Constructor_Ranks_Unboxing_And_Widening_Distance() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      unboxingWideningChoice="$boxedSource"
                      wideningDistanceChoice="{ByteValue value=4}"/>
        """);

        assertEquals("int", root.getUnboxingWideningChoice().getSelected());
        assertEquals("short", root.getWideningDistanceChoice().getSelected());
    }

    @Test
    public void Implicit_Constructor_Uses_Reference_Specificity_After_Boxing() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      referenceChoice="{IntValue value=3}"/>
        """);

        assertEquals("Integer", root.getReferenceChoice().getSelected());
    }

    @Test
    public void Writable_Concrete_Collection_Prefers_Implicit_Construction() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      constructedList="{DoubleValue value=7}"/>
        """);

        assertEquals(List.of(7.0), root.getConstructedList());
        assertTrue(root.getConstructedList().isConstructedFromArgument());
        assertEquals(1, root.getConstructedListSetterCalls());
    }

    @Test
    public void Four_Item_Sequence_Invokes_Insets_Constructor_Left_To_Right() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      paddingValue="{DoubleValue value=1}, {DoubleValue value=2}, 3, 4"/>
        """);

        assertEquals(new Insets(1, 2, 3, 4), root.getPaddingValue());
        assertEquals(List.of(1.0, 2.0), supplierInvocations.stream().map(Invocation::value).toList());
        assertEquals(List.of("top", "right"), supplierInvocations.stream()
            .map(Invocation::targetName).toList());
        assertTrue(supplierInvocations.stream().allMatch(invocation ->
            invocation.targetType() == double.class && invocation.targetBean() == null));
    }

    @Test
    public void Single_Supplier_Falls_Back_To_One_Argument_Implicit_Constructor() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      paddingValue="{DoubleValue value=5}"/>
        """);

        assertEquals(new Insets(5), root.getPaddingValue());
        assertEquals("topRightBottomLeft", supplierInvocations.get(0).targetName());
    }

    @Test
    public void Direct_Single_Value_Wins_Over_Implicit_Construction() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      box="{BoxValue value=9}"/>
        """);

        assertEquals(9, root.getBox().getValue(), 0.001);
    }

    @Test
    public void Leading_Doubled_And_Trailing_Empty_Members_Are_Preserved() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      textValues=", @image.jpg,,"/>
        """);

        assertEquals(4, root.getTextValues().size());
        assertEquals("", root.getTextValues().get(0));
        assertTrue(root.getTextValues().get(1).endsWith("org/jfxcore/compiler/image.jpg"));
        assertEquals("", root.getTextValues().get(2));
        assertEquals("", root.getTextValues().get(3));
    }

    @Test
    public void Escaped_Later_Prefix_Keeps_The_Whole_Attribute_Literal() {
        javafx.scene.control.Label root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   text="hello, \\@image.jpg"/>
        """);

        assertEquals("hello, @image.jpg", root.getText());
    }

    @Test
    public void Escaped_Custom_And_Overridden_Prefixes_Remain_Literal_Items() {
        TestPane root = compileAndRun("""
            <?prefix ^ = org.jfxcore.markup.resource.ClassPathResource?>
            <?prefix % = org.jfxcore.markup.resource.ClassPathResource?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      strings="\\^literal, ^image.jpg, \\%other, %image.jpg"/>
        """);

        assertEquals("^literal", root.getStrings()[0]);
        assertTrue(root.getStrings()[1].endsWith("org/jfxcore/compiler/image.jpg"));
        assertEquals("%other", root.getStrings()[2]);
        assertTrue(root.getStrings()[3].endsWith("org/jfxcore/compiler/image.jpg"));
    }

    @Test
    public void Literal_Separator_Remains_Whole_Text_For_A_String_Target() {
        javafx.scene.control.Label root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   text="hello, world"/>
        """);

        assertEquals("hello, world", root.getText());
    }

    @Test
    public void Unescaped_Later_Prefix_Is_Structural_For_A_Scalar_String() {
        MarkupException exception = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   text="hello, @image.jpg"/>
        """));

        assertEquals(ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, exception.getDiagnostic().getCode());
        assertTrue(exception.getDiagnostic().getMessage().matches(
            "'hello, @image\\.jpg' is not a valid value for .+\\.text"));
    }

    @Test
    public void Map_Population_Without_Keys_Has_A_Targeted_Diagnostic() {
        MarkupException exception = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      mapValues="plain, @image.jpg"/>
        """));

        assertEquals(ErrorCode.CANNOT_POPULATE_MAP_WITHOUT_KEYS, exception.getDiagnostic().getCode());
        assertTrue(exception.getDiagnostic().getMessage().matches(
            "Cannot add items to .+\\.mapValues because no map keys are defined"));
    }

    @Test
    public void Writable_Custom_Map_Can_Win_Through_Implicit_Construction() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      constructedMap="@image.jpg"/>
        """);

        assertTrue(root.getConstructedMap().getResource().endsWith("org/jfxcore/compiler/image.jpg"));
        assertEquals(root.getConstructedMap().getResource(), root.getConstructedMap().get("resource"));
    }

    @Test
    public void Multidimensional_Array_Falls_Back_To_Property_Coercion_Diagnostic() {
        MarkupException exception = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      matrix="1, {DoubleValue value=2}"/>
        """));

        assertEquals(ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, exception.getDiagnostic().getCode());
        assertTrue(exception.getDiagnostic().getMessage().startsWith(
            "'1, {DoubleValue value=2}' is not a valid value for "));
        assertTrue(exception.getDiagnostic().getMessage().endsWith(".matrix"));
    }

    @Test
    public void ConsumerOnly_Extension_Is_Rejected_In_A_Nested_Value_Position() {
        MarkupException exception = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      doubles="1, {ConsumerOnly}"/>
        """));

        assertEquals(ErrorCode.INVALID_MARKUP_EXTENSION_USAGE, exception.getDiagnostic().getCode());
        assertEquals(0, consumerInvocations);
    }

    @Test
    public void ConsumerOnly_Extension_Is_Rejected_In_A_Named_Constructor_Argument() {
        MarkupException exception = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <ConsumerTarget value="{ConsumerOnly}"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.INVALID_MARKUP_EXTENSION_USAGE, exception.getDiagnostic().getCode());
        assertEquals(0, consumerInvocations);
    }

    @Test
    public void Unconstructible_Supplier_Produces_A_Markup_Diagnostic() {
        MarkupException exception = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <WrapperTarget wrapper="{BoxValue}"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, exception.getDiagnostic().getCode());
        assertTrue(exception.getMessage().contains("BoxValue"));
    }

    @Test
    public void Named_Multidimensional_Array_Argument_Falls_Back_To_Constructor_Diagnostic() {
        MarkupException exception = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <MatrixArgumentPane matrix="1, {DoubleValue value=2}"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, exception.getDiagnostic().getCode());
        assertEquals(1, exception.getDiagnostic().getCauses().length);
        assertEquals(
            ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT,
            exception.getDiagnostic().getCauses()[0].getCode());
        assertTrue(exception.getMessage().contains("named argument 'matrix'"));
    }

    @Test
    public void No_Arity_Match_Reports_Constructor_Candidates() {
        MarkupException exception = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      noArityChoice="{DoubleValue value=1}"/>
        """));

        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, exception.getDiagnostic().getCode());
        assertTrue(exception.getMessage().contains("NoArityChoice"));
        assertTrue(exception.getMessage().contains("required 2 argument"));
    }

    @Test
    public void Best_Constructor_Failure_Highlights_Its_First_Incompatible_Item() {
        MarkupException exception = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefixFailureChoice="{DoubleValue value=1}, not-an-int"/>
        """));

        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, exception.getDiagnostic().getCode());
        assertEquals("not-an-int", exception.getOriginalSourceInfo().getText());
        assertTrue(exception.getMessage().contains("second: int"));
        assertFalse(exception.getMessage().contains("first: java.lang.String"));
    }

    @Test
    public void Unrelated_Implicit_Constructor_Candidates_Are_Ambiguous() {
        MarkupException exception = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      ambiguousValue="@image.jpg"/>
        """));

        assertEquals(
            ErrorCode.AMBIGUOUS_METHOD_OR_CONSTRUCTOR_CALL,
            exception.getDiagnostic().getCode());
    }

    @Test
    public void Literal_Implicit_Constructor_Uses_The_Same_Ambiguity_Rules() {
        MarkupException exception = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      ambiguousLiteralValue="1"/>
        """));

        assertEquals(
            ErrorCode.AMBIGUOUS_METHOD_OR_CONSTRUCTOR_CALL,
            exception.getDiagnostic().getCode());
    }

    @Test
    public void Literal_Ambiguity_Is_Independent_Of_Constructor_Declaration_Order() {
        MarkupException exception = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      ambiguousLiteralValueReversed="1"/>
        """));

        assertEquals(
            ErrorCode.AMBIGUOUS_METHOD_OR_CONSTRUCTOR_CALL,
            exception.getDiagnostic().getCode());
    }

    @Test
    public void Self_Similar_Implicit_Constructor_Terminates() {
        MarkupException exception = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      selfSimilarValue="not-convertible"/>
        """));

        assertEquals(ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, exception.getDiagnostic().getCode());
        assertEquals("not-convertible", exception.getOriginalSourceInfo().getText());
    }

    @Test
    public void Target_Local_Literal_Failure_Does_Not_Reject_Another_Constructor() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      literalFallbackChoice="DefinitelyNotAType"/>
        """);

        assertEquals("String", root.getLiteralFallbackChoice().getSelected());
    }

    @Test
    public void Named_Argument_Default_Resolves_Static_Field_On_Parameter_Type() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <StaticDefaultPane/>
            </TestPane>
        """);

        StaticDefaultPane child = (StaticDefaultPane)root.getChildren().get(0);
        assertSame(DefaultToken.DEFAULT, child.getToken());
    }

    @Test
    public void Literal_Construction_Attaches_Identity_And_Properties_Only_On_Commit() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <TestPane.literalConstructedObject>
                    <LiteralConstructedObject fx:id="literalObject" marker="attached">DefinitelyNotAType</LiteralConstructedObject>
                </TestPane.literalConstructedObject>
            </TestPane>
        """);

        LiteralConstructedObject value = root.getLiteralConstructedObject();
        assertEquals("String", value.getSelected());
        assertEquals("attached", value.getMarker());
        assertSame(value, Reflection.getFieldValue(root, "literalObject"));
    }
}

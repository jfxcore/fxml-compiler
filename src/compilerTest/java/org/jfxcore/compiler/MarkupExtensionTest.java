// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.jfxcore.markup.MarkupContext;
import org.jfxcore.markup.MarkupExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class MarkupExtensionTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class StaticPropertyHolder {
        public static DoubleProperty propProperty(Pane pane) {
            return (DoubleProperty)pane.getProperties().computeIfAbsent("prop", key -> new SimpleDoubleProperty());
        }

        public static void setProp(Pane pane, double value) {
            propProperty(pane).set(value);
        }

        public static double getProp(Pane pane) {
            return propProperty(pane).get();
        }
    }

    @SuppressWarnings("unused")
    public static class TestPane extends Pane {
        private final BooleanProperty boolProp = new SimpleBooleanProperty(true);
        public final BooleanProperty boolPropProperty() { return boolProp; }
        public final boolean getBoolProp() { return boolProp.get(); }
        public final void setBoolProp(boolean value) { boolProp.set(value); }

        private final IntegerProperty intProp = new SimpleIntegerProperty(1);
        public final IntegerProperty intPropProperty() { return intProp; }
        public final int getIntProp() { return intProp.get(); }
        public final void setIntProp(int value) { intProp.set(value); }

        private final LongProperty longProp = new SimpleLongProperty(1);
        public final LongProperty longPropProperty() { return longProp; }
        public final long getLongProp() { return longProp.get(); }
        public final void setLongProp(long value) { longProp.set(value); }

        private final FloatProperty floatProp = new SimpleFloatProperty(1);
        public final FloatProperty floatPropProperty() { return floatProp; }
        public final float getFloatProp() { return floatProp.get(); }
        public final void setFloatProp(float value) { floatProp.set(value); }

        private final DoubleProperty doubleProp1 = new SimpleDoubleProperty(1);
        public final DoubleProperty doubleProp1Property() { return doubleProp1; }
        public final double getDoubleProp1() { return doubleProp1.get(); }
        public final void setDoubleProp1(double value) { doubleProp1.set(value); }

        private final DoubleProperty doublePropWithoutSetter = new SimpleDoubleProperty(1);
        public final DoubleProperty doublePropWithoutSetterProperty() { return doublePropWithoutSetter; }

        private double doubleProp2;
        public void setDoubleProp2(double value) { doubleProp2 = value;}
        public double getDoubleProp2() { return doubleProp2; }

        private final ListProperty<Double> doubleListProp = new SimpleListProperty<>(FXCollections.observableArrayList());
        public final ListProperty<Double> doubleListPropProperty() { return doubleListProp; }

        private final ReadOnlyDoubleProperty readOnlyDoubleProp = new SimpleDoubleProperty();
        public final ReadOnlyDoubleProperty readOnlyDoublePropProperty() { return readOnlyDoubleProp; }
        public final double getReadOnlyDoubleProp() { return readOnlyDoubleProp.get(); }

        private final ReadOnlyBooleanProperty readOnlyBoolProp = new SimpleBooleanProperty();
        public final ReadOnlyBooleanProperty readOnlyBoolPropProperty() { return readOnlyBoolProp; }
        public final boolean getReadOnlyBoolProp() { return readOnlyBoolProp.get(); }

        public static double add(double a, double b) {
            return a + b;
        }

        public static double sum(double... values) {
            return Arrays.stream(values).sum();
        }
    }

    private record ExtensionInvocation(
            Object root,
            List<Object> ancestors,
            int ancestorCount,
            Object targetBean,
            String targetName,
            Class<?> targetType,
            ReadOnlyProperty<?> targetProperty) {

        static ExtensionInvocation of(MarkupContext context) {
            return new ExtensionInvocation(
                context.getRoot(),
                IntStream.range(0, context.getAncestorCount()).mapToObj(context::getAncestor).toList(),
                context.getAncestorCount(),
                context.getTargetBean(),
                context.getTargetName(),
                context.getTargetType(),
                null);
        }

        static ExtensionInvocation of(ReadOnlyProperty<?> property, MarkupContext context) {
            return new ExtensionInvocation(
                context.getRoot(),
                IntStream.range(0, context.getAncestorCount()).mapToObj(context::getAncestor).toList(),
                context.getAncestorCount(),
                context.getTargetBean(),
                context.getTargetName(),
                context.getTargetType(),
                property);
        }
    }

    private static void assertExtensionInvocation(
            ExtensionInvocation invocation,
            Object root, int ancestorCount, Object targetBean, String targetName,
            Class<?> targetType, ReadOnlyProperty<?> property) {
        assertSame(root, invocation.root);
        assertEquals(ancestorCount, invocation.ancestorCount);
        assertSame(targetBean, invocation.targetBean);
        assertEquals(targetName, invocation.targetName);
        assertSame(targetType, invocation.targetType);
        assertSame(property, invocation.targetProperty);
    }

    @Test
    public void Type_Used_Like_Markup_Extension_Is_Invalid() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                    tooltip="{Tooltip text=foo}"/>
        """));

        assertEquals(ErrorCode.UNEXPECTED_MARKUP_EXTENSION, ex.getDiagnostic().getCode());
        assertCodeHighlight("{Tooltip text=foo}", ex);
    }

    @Test
    public void Missing_CloseCurly_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   text="{fx:foo bar"/>
        """));

        assertEquals(ErrorCode.EXPECTED_TOKEN, ex.getDiagnostic().getCode());
        assertTrue(ex.getDiagnostic().getMessage().contains("}"));
        assertCodeHighlight("\"", ex);
    }

    @Nested
    public class PropertyConsumerExtensionTest extends CompilerTestBase {

        static List<ExtensionInvocation> invocations = new ArrayList<>();

        @BeforeEach
        void setup() {
            invocations.clear();
        }

        @SuppressWarnings("unused")
        public static class PropertyConsumerExtension implements MarkupExtension.PropertyConsumer<Number> {
            private final Double param;

            public PropertyConsumerExtension(@NamedArg("param") Double param) {
                this.param = param;
            }

            @Override
            public void accept(Property<Number> property, MarkupContext context) {
                invocations.add(ExtensionInvocation.of(property, context));
                property.setValue(param);
            }
        }

        @Test
        public void PropertyConsumerExtension_Is_Applied_To_CompatibleProperty() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          doubleProp1="{PropertyConsumerExtension param=123.0}"
                          doublePropWithoutSetter="{PropertyConsumerExtension param=456.0}"
                          floatProp="{PropertyConsumerExtension param=1.0}"
                          intProp="{PropertyConsumerExtension param=2.0}"
                          longProp="{PropertyConsumerExtension param=3.0}"/>
            """);

            assertEquals(123.0, root.getDoubleProp1(), 0.001);
            assertEquals(456.0, root.doublePropWithoutSetterProperty().get(), 0.001);
            assertEquals(1.0, root.getFloatProp(), 0.001);
            assertEquals(2, root.getIntProp());
            assertEquals(3, root.getLongProp());
            assertEquals(5, invocations.size());
            assertExtensionInvocation(invocations.get(0), root, 1, root, "doubleProp1",
                                      double.class, root.doubleProp1Property());
            assertExtensionInvocation(invocations.get(1), root, 1, root, "doublePropWithoutSetter",
                                      double.class, root.doublePropWithoutSetterProperty());
            assertExtensionInvocation(invocations.get(2), root, 1, root, "floatProp",
                                      float.class, root.floatPropProperty());
            assertExtensionInvocation(invocations.get(3), root, 1, root, "intProp",
                                      int.class, root.intPropProperty());
            assertExtensionInvocation(invocations.get(4), root, 1, root, "longProp",
                                      long.class, root.longPropProperty());
        }

        @Test
        public void PropertyConsumerExtension_Cannot_Be_Applied_To_IncompatiblePropertyType() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          boolProp="{PropertyConsumerExtension param=123.0}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{PropertyConsumerExtension param=123.0}", ex);
        }

        @Test
        public void PropertyConsumerExtension_Cannot_Be_Applied_To_ReadOnlyPropertyType() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          readOnlyDoubleProp="{PropertyConsumerExtension param=123.0}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{PropertyConsumerExtension param=123.0}", ex);
        }

        @Test
        public void PropertyConsumerExtension_Cannot_Be_Applied_To_GetterSetterPair() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          doubleProp2="{PropertyConsumerExtension param=123.0}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{PropertyConsumerExtension param=123.0}", ex);
        }

        @Test
        public void PropertyConsumerExtension_Cannot_Be_Applied_To_FunctionArgument() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          doubleProp1="${add(0, {PropertyConsumerExtension param=123.0})}"/>
            """));

            assertEquals(ErrorCode.INVALID_MARKUP_EXTENSION_USAGE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{PropertyConsumerExtension param=123.0}", ex);
        }

        @Test
        public void PropertyConsumerExtension_Is_Applied_To_Static_Property() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          StaticPropertyHolder.prop="{PropertyConsumerExtension param=123.0}"/>
            """);

            assertEquals(1, invocations.size());
            assertEquals(123.0, StaticPropertyHolder.getProp(root), 0.001);
            assertExtensionInvocation(invocations.get(0), root, 1, root, "prop", double.class,
                                      StaticPropertyHolder.propProperty(root));
        }
    }

    @Nested
    @Execution(ExecutionMode.SAME_THREAD)
    public class ReadOnlyPropertyConsumerExtensionTest extends CompilerTestBase {

        static List<ExtensionInvocation> invocations = new ArrayList<>();

        @BeforeEach
        void setup() {
            invocations.clear();
        }

        @SuppressWarnings("unused")
        public static class ReadOnlyPropertyConsumerExtension implements MarkupExtension.ReadOnlyPropertyConsumer<Number> {
            @Override
            public void accept(ReadOnlyProperty<Number> property, MarkupContext context) {
                invocations.add(ExtensionInvocation.of(property, context));
            }
        }

        @Test
        public void ReadOnlyPropertyConsumerExtension_Is_Applied_To_CompatibleProperty() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          readOnlyDoubleProp="{ReadOnlyPropertyConsumerExtension}"/>
            """);

            assertEquals(1, invocations.size());
            assertExtensionInvocation(invocations.get(0), root, 1, root, "readOnlyDoubleProp",
                                      double.class, root.readOnlyDoublePropProperty());
        }

        @Test
        public void ReadOnlyPropertyConsumerExtension_Cannot_Be_Applied_To_IncompatiblePropertyType() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          readOnlyBoolProp="{ReadOnlyPropertyConsumerExtension}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{ReadOnlyPropertyConsumerExtension}", ex);
        }
    }

    @Nested
    @Execution(ExecutionMode.SAME_THREAD)
    public class SupplierExtensionTest extends CompilerTestBase {

        static List<ExtensionInvocation> invocations = new ArrayList<>();

        @BeforeEach
        void setup() {
            invocations.clear();
        }

        @SuppressWarnings("unused")
        public static class ObjectSupplierWithReturnTypeAnnotation implements MarkupExtension.Supplier<Object> {
            private final String stringParam;
            private Double doubleParam;

            public ObjectSupplierWithReturnTypeAnnotation(@NamedArg("param") Double param) {
                doubleParam = param;
                stringParam = null;
            }

            public ObjectSupplierWithReturnTypeAnnotation(@NamedArg("param") String param) {
                doubleParam = null;
                stringParam = param;
            }

            public double getDoubleParam() {
                return doubleParam;
            }

            public void setDoubleParam(double value) {
                doubleParam = value;
            }

            @Override
            @ReturnType({Double.class, Float.class})
            public Object get(MarkupContext context) {
                invocations.add(ExtensionInvocation.of(context));
                return context.getTargetType() == String.class ? stringParam : doubleParam;
            }
        }

        @Test
        public void Object_Supplier_With_ReturnType_Annotation_Is_Applied_To_Primitive_Type() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          doubleProp1="{ObjectSupplierWithReturnTypeAnnotation param=123.0}"
                          doubleProp2="{ObjectSupplierWithReturnTypeAnnotation param=456.0}"
                          doublePropWithoutSetter="{ObjectSupplierWithReturnTypeAnnotation param=789.0}"/>
            """);

            assertEquals(3, invocations.size());

            assertEquals(123.0, root.getDoubleProp1(), 0.001);
            assertExtensionInvocation(invocations.get(0), root, 1, root, "doubleProp1", double.class, null);

            assertEquals(456.0, root.getDoubleProp2(), 0.001);
            assertExtensionInvocation(invocations.get(1), root, 1, root, "doubleProp2", double.class, null);

            assertEquals(789.0, root.doublePropWithoutSetterProperty().get(), 0.001);
            assertExtensionInvocation(invocations.get(2), root, 1, root, "doublePropWithoutSetter", double.class, null);
        }

        @Test
        public void Object_Supplier_With_ReturnType_Annotation_Cannot_Be_Applied_To_IncompatiblePropertyType() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          boolProp="{ObjectSupplierWithReturnTypeAnnotation param=123.0}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{ObjectSupplierWithReturnTypeAnnotation param=123.0}", ex);
        }

        @Test
        public void Object_Supplier_Is_Applied_To_Parameter_Of_MarkupExtension() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          doubleProp1="{ObjectSupplierWithReturnTypeAnnotation param={ObjectSupplierWithReturnTypeAnnotation param=123.0}}">
                    <doublePropWithoutSetter>
                        <ObjectSupplierWithReturnTypeAnnotation>
                            <param>
                                <ObjectSupplierWithReturnTypeAnnotation param="456.0"/>
                            </param>
                        </ObjectSupplierWithReturnTypeAnnotation>
                    </doublePropWithoutSetter>
                </TestPane>
            """);

            assertEquals(4, invocations.size());
            assertEquals(123.0, root.getDoubleProp1(), 0.001);
            assertEquals(456.0, root.doublePropWithoutSetterProperty().get(), 0.001);
            assertExtensionInvocation(invocations.get(0), root, 1, null, "param", Double.class, null);
            assertExtensionInvocation(invocations.get(1), root, 1, root, "doubleProp1", double.class, null);
            assertExtensionInvocation(invocations.get(2), root, 1, null, "param", Double.class, null);
            assertExtensionInvocation(invocations.get(3), root, 1, root, "doublePropWithoutSetter", double.class, null);
        }

        @SuppressWarnings("unused")
        public static class ObjectSupplierWithoutReturnTypeAnnotation implements MarkupExtension.Supplier<Object> {
            @Override
            public Object get(MarkupContext context) {
                throw new UnsupportedOperationException();
            }
        }

        @Test
        public void Object_Supplier_Without_ReturnType_Annotation_Cannot_Be_Applied_To_Primitive_Type() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          doubleProp1="{ObjectSupplierWithoutReturnTypeAnnotation}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{ObjectSupplierWithoutReturnTypeAnnotation}", ex);
        }

        @Test
        public void Object_Supplier_Without_ReturnType_Annotation_Cannot_Be_Applied_To_ConstructorArgument() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          doubleProp1="{ObjectSupplierWithReturnTypeAnnotation param={ObjectSupplierWithoutReturnTypeAnnotation}}"/>
            """));

            assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
            assertCodeHighlight("{ObjectSupplierWithoutReturnTypeAnnotation}", ex);
        }

        @Test
        public void Object_Supplier_Without_ReturnType_Annotation_Cannot_Be_Applied_To_ConstructorArgument_Nested() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <doubleProp1>
                        <ObjectSupplierWithReturnTypeAnnotation>
                            <param>
                                <ObjectSupplierWithReturnTypeAnnotation>
                                    <param>
                                        <ObjectSupplierWithoutReturnTypeAnnotation/>
                                    </param>
                                </ObjectSupplierWithReturnTypeAnnotation>
                            </param>
                        </ObjectSupplierWithReturnTypeAnnotation>
                    </doubleProp1>
                </TestPane>
            """));

            assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
            assertCodeHighlight("<ObjectSupplierWithoutReturnTypeAnnotation/>", ex);
        }

        @Test
        public void Supplier_Applied_To_Property_Returns_Correct_TargetBean() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <doubleProp1>
                        <ObjectSupplierWithReturnTypeAnnotation param="123.0">
                            <doubleParam>
                                <ObjectSupplierWithReturnTypeAnnotation>
                                    <param>
                                        <ObjectSupplierWithReturnTypeAnnotation param="456.0"/>
                                    </param>
                                </ObjectSupplierWithReturnTypeAnnotation>
                            </doubleParam>
                        </ObjectSupplierWithReturnTypeAnnotation>
                    </doubleProp1>
                </TestPane>
            """);

            assertEquals(456.0, root.doubleProp1Property().get(), 0.001);
            assertEquals(3, invocations.size());
            var invocation1 = invocations.get(0);
            var invocation2 = invocations.get(1);
            var invocation3 = invocations.get(2);

            assertSame(root, invocation2.root());
            assertInstanceOf(ObjectSupplierWithReturnTypeAnnotation.class, invocation2.targetBean());
            assertEquals("doubleParam", invocation2.targetName());
            assertSame(double.class, invocation2.targetType());
            assertNull(invocation2.targetProperty());

            assertExtensionInvocation(invocation1, root, 1, null, "param", Double.class, null);
            assertExtensionInvocation(invocation3, root, 1, root, "doubleProp1", double.class, null);
        }

        @Test
        public void Supplier_Applied_To_Static_Property_Returns_Correct_TargetBean() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <StaticPropertyHolder.prop>
                        <ObjectSupplierWithReturnTypeAnnotation param="123.0">
                            <doubleParam>
                                <ObjectSupplierWithReturnTypeAnnotation>
                                    <param>
                                        <ObjectSupplierWithReturnTypeAnnotation param="456.0"/>
                                    </param>
                                </ObjectSupplierWithReturnTypeAnnotation>
                            </doubleParam>
                        </ObjectSupplierWithReturnTypeAnnotation>
                    </StaticPropertyHolder.prop>
                </TestPane>
            """);

            assertEquals(456.0, StaticPropertyHolder.getProp(root), 0.001);
            assertEquals(3, invocations.size());
            var invocation1 = invocations.get(0);
            var invocation2 = invocations.get(1);
            var invocation3 = invocations.get(2);

            assertSame(root, invocation2.root());
            assertInstanceOf(ObjectSupplierWithReturnTypeAnnotation.class, invocation2.targetBean());
            assertEquals("doubleParam", invocation2.targetName());
            assertSame(double.class, invocation2.targetType());
            assertNull(invocation2.targetProperty());

            assertExtensionInvocation(invocation1, root, 1, null, "param", Double.class, null);
            assertExtensionInvocation(invocation3, root, 1, root, "prop", double.class, null);
        }

        @SuppressWarnings("unused")
        public static class DoublePropertyConsumer implements MarkupExtension.PropertyConsumer<Number> {
            @Override
            public void accept(Property<Number> property, MarkupContext markupContext) {
                throw new UnsupportedOperationException();
            }
        }

        @Test
        public void PropertyConsumerExtension_Cannot_Be_Applied_To_ConstructorArgument() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          doubleProp1="{ObjectSupplierWithReturnTypeAnnotation param={DoublePropertyConsumer}}"/>
            """));

            assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
            assertCodeHighlight("{DoublePropertyConsumer}", ex);
        }

        @Test
        public void ObjectSupplier_With_Multiple_ReturnTypes_Is_Applied_To_Function_Argument() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          doubleProp1="${add(doublePropWithoutSetter, {ObjectSupplierWithReturnTypeAnnotation param=2.5})}"/>
            """);

            assertEquals(3.5, root.getDoubleProp1(), 0.001);
            root.doublePropWithoutSetterProperty().set(10);
            assertEquals(12.5, root.getDoubleProp1(), 0.001);
        }

        @Test
        public void ObjectSupplier_With_Multiple_ReturnTypes_Is_Applied_To_VarArg_Function_Argument() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          doubleProp1="${sum(doublePropWithoutSetter, {ObjectSupplierWithReturnTypeAnnotation param=2.5})}"/>
            """);

            assertEquals(3.5, root.getDoubleProp1(), 0.001);
            root.doublePropWithoutSetterProperty().set(10);
            assertEquals(12.5, root.getDoubleProp1(), 0.001);
            assertEquals(1, invocations.size());
            assertExtensionInvocation(invocations.get(0), root, 1, null, null, double.class, null);
        }

        @Test
        public void Ancestors_Are_Correctly_Returned() {
            Pane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <Pane>
                        <TestPane doubleProp1="{ObjectSupplierWithReturnTypeAnnotation param=2.5}"/>
                    </Pane>
                </Pane>
            """);

            Pane pane2 = (Pane)root.getChildren().get(0);
            TestPane pane3 = (TestPane)pane2.getChildren().get(0);
            assertEquals(1, invocations.size());
            assertExtensionInvocation(invocations.get(0), root, 3, pane3, "doubleProp1", double.class, null);
            assertSame(pane3, invocations.get(0).ancestors().get(0));
            assertSame(pane2, invocations.get(0).ancestors().get(1));
            assertSame(root, invocations.get(0).ancestors().get(2));
        }
    }

    @Nested
    @Execution(ExecutionMode.SAME_THREAD)
    public class BooleanSupplierExtensionTest extends CompilerTestBase {

        static List<ExtensionInvocation> invocations = new ArrayList<>();

        @BeforeEach
        void setup() {
            invocations.clear();
        }

        @SuppressWarnings("unused")
        public static class PrimitiveBooleanSupplier implements MarkupExtension.BooleanSupplier {
            private final boolean param;

            public PrimitiveBooleanSupplier(@NamedArg("param") boolean param) {
                this.param = param;
            }

            @Override
            public boolean get(MarkupContext context) {
                invocations.add(ExtensionInvocation.of(context));
                return param;
            }
        }

        @Test
        public void PrimitiveBooleanSupplier_Is_Applied_To_PrimitiveBoolean() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          boolProp="{PrimitiveBooleanSupplier param=true}"/>
            """);

            assertEquals(1, invocations.size());
            assertTrue(root.getBoolProp());
            assertExtensionInvocation(invocations.get(0), root, 1, root, "boolProp", boolean.class, null);
        }

        @Test
        public void PrimitiveBooleanSupplier_Cannot_Be_Applied_To_PrimitiveDouble() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          doubleProp1="{PrimitiveBooleanSupplier param=true}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{PrimitiveBooleanSupplier param=true}", ex);
        }

        @Test
        public void PrimitiveBooleanSupplier_Cannot_Be_Applied_To_PrimitiveFloat() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          floatProp="{PrimitiveBooleanSupplier param=true}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{PrimitiveBooleanSupplier param=true}", ex);
        }

        @Test
        public void PrimitiveBooleanSupplier_Cannot_Be_Applied_To_PrimitiveLong() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          longProp="{PrimitiveBooleanSupplier param=true}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{PrimitiveBooleanSupplier param=true}", ex);
        }

        @Test
        public void PrimitiveBooleanSupplier_Cannot_Be_Applied_To_PrimitiveInteger() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          intProp="{PrimitiveBooleanSupplier param=true}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{PrimitiveBooleanSupplier param=true}", ex);
        }
    }

    @Nested
    @Execution(ExecutionMode.SAME_THREAD)
    public class DoubleSupplierExtensionTest extends CompilerTestBase {

        static List<ExtensionInvocation> invocations = new ArrayList<>();

        @BeforeEach
        void setup() {
            invocations.clear();
        }

        @SuppressWarnings("unused")
        public static class PrimitiveDoubleSupplier implements MarkupExtension.DoubleSupplier {
            private final double param;

            public PrimitiveDoubleSupplier(@NamedArg("param") double param) {
                this.param = param;
            }

            @Override
            public double get(MarkupContext context) {
                invocations.add(ExtensionInvocation.of(context));
                return param;
            }
        }

        @Test
        public void PrimitiveDoubleSupplier_Is_Applied_To_ListProperty() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          doubleListProp="{PrimitiveDoubleSupplier param=123.0}"/>
            """);

            assertEquals(1, root.doubleListProp.size());
            assertEquals(123, root.doubleListProp.get(0), 0.001);
        }

        @Test
        public void Multiple_PrimitiveDoubleSuppliers_Are_Applied_To_ListProperty() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <doubleListProp>
                        <PrimitiveDoubleSupplier param="123"/>
                        <PrimitiveDoubleSupplier param="456"/>
                    </doubleListProp>
                </TestPane>
            """);

            assertEquals(2, root.doubleListProp.size());
            assertEquals(123, root.doubleListProp.get(0), 0.001);
            assertEquals(456, root.doubleListProp.get(1), 0.001);
        }

        @Test
        public void PrimitiveDoubleSupplier_Is_Applied_To_PrimitiveDouble() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          doubleProp1="{PrimitiveDoubleSupplier param=123.0}"
                          doubleProp2="{PrimitiveDoubleSupplier param=456.0}"
                          doublePropWithoutSetter="{PrimitiveDoubleSupplier param=789.0}"/>
            """);

            assertEquals(3, invocations.size());

            assertEquals(123.0, root.getDoubleProp1(), 0.001);
            assertExtensionInvocation(invocations.get(0), root, 1, root, "doubleProp1", double.class, null);

            assertEquals(456.0, root.getDoubleProp2(), 0.001);
            assertExtensionInvocation(invocations.get(1), root, 1, root, "doubleProp2", double.class, null);

            assertEquals(789, root.doublePropWithoutSetterProperty().get(), 0.001);
            assertExtensionInvocation(invocations.get(2), root, 1, root, "doublePropWithoutSetter", double.class, null);
        }

        @Test
        public void PrimitiveDoubleSupplier_Cannot_Be_Applied_To_PrimitiveBoolean() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          boolProp="{PrimitiveDoubleSupplier param=123.0}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{PrimitiveDoubleSupplier param=123.0}", ex);
        }

        @Test
        public void PrimitiveDoubleSupplier_Cannot_Be_Applied_To_PrimitiveInteger() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          intProp="{PrimitiveDoubleSupplier param=123.0}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{PrimitiveDoubleSupplier param=123.0}", ex);
        }

        @Test
        public void PrimitiveDoubleSupplier_Cannot_Be_Applied_To_PrimitiveLong() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          longProp="{PrimitiveDoubleSupplier param=123.0}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{PrimitiveDoubleSupplier param=123.0}", ex);
        }

        @Test
        public void PrimitiveDoubleSupplier_Cannot_Be_Applied_To_PrimitiveFloat() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          floatProp="{PrimitiveDoubleSupplier param=123.0}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{PrimitiveDoubleSupplier param=123.0}", ex);
        }

        @Test
        public void PrimitiveDoubleSupplier_Is_Applied_To_Static_Property() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          StaticPropertyHolder.prop="{PrimitiveDoubleSupplier param=123.0}"/>
            """);

            assertEquals(123.0, StaticPropertyHolder.getProp(root), 0.001);
        }

        @Test
        public void PrimitiveDoubleSupplier_Is_Applied_To_Function_Arguments() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          doubleProp1="${add({PrimitiveDoubleSupplier param=1.5}, {PrimitiveDoubleSupplier param=2.5})}"/>
            """);

            assertEquals(4.0, root.getDoubleProp1(), 0.001);
        }
    }

    @Nested
    @Execution(ExecutionMode.SAME_THREAD)
    public class FloatSupplierExtensionTest extends CompilerTestBase {

        static List<ExtensionInvocation> invocations = new ArrayList<>();

        @BeforeEach
        void setup() {
            invocations.clear();
        }

        @SuppressWarnings("unused")
        public static class PrimitiveFloatSupplier implements MarkupExtension.FloatSupplier {
            private final float param;

            public PrimitiveFloatSupplier(@NamedArg("param") float param) {
                this.param = param;
            }

            @Override
            public float get(MarkupContext context) {
                invocations.add(ExtensionInvocation.of(context));
                return param;
            }
        }

        @Test
        public void PrimitiveFloatSupplier_Is_Applied_To_PrimitiveDouble() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          doubleProp1="{PrimitiveFloatSupplier param=123.0}"
                          doubleProp2="{PrimitiveFloatSupplier param=456.0}"
                          doublePropWithoutSetter="{PrimitiveFloatSupplier param=789.0}"/>
            """);

            assertEquals(3, invocations.size());

            assertEquals(123.0, root.getDoubleProp1(), 0.001);
            assertExtensionInvocation(invocations.get(0), root, 1, root, "doubleProp1", double.class, null);

            assertEquals(456.0, root.getDoubleProp2(), 0.001);
            assertExtensionInvocation(invocations.get(1), root, 1, root, "doubleProp2", double.class, null);

            assertEquals(789, root.doublePropWithoutSetterProperty().get(), 0.001);
            assertExtensionInvocation(invocations.get(2), root, 1, root, "doublePropWithoutSetter", double.class, null);
        }

        @Test
        public void PrimitiveFloatSupplier_Is_Applied_To_PrimitiveFloat() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          floatProp="{PrimitiveFloatSupplier param=123.0}"/>
            """);

            assertEquals(1, invocations.size());
            assertEquals(123.0, root.getFloatProp(), 0.001);
            assertExtensionInvocation(invocations.get(0), root, 1, root, "floatProp", float.class, null);
        }

        @Test
        public void PrimitiveFloatSupplier_Cannot_Be_Applied_To_PrimitiveBoolean() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          boolProp="{PrimitiveFloatSupplier param=123.0}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{PrimitiveFloatSupplier param=123.0}", ex);
        }

        @Test
        public void PrimitiveFloatSupplier_Cannot_Be_Applied_To_PrimitiveInteger() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          intProp="{PrimitiveFloatSupplier param=123.0}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{PrimitiveFloatSupplier param=123.0}", ex);
        }

        @Test
        public void PrimitiveFloatSupplier_Cannot_Be_Applied_To_PrimitiveLong() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          longProp="{PrimitiveFloatSupplier param=123.0}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{PrimitiveFloatSupplier param=123.0}", ex);
        }
    }

    @Nested
    @Execution(ExecutionMode.SAME_THREAD)
    public class IntSupplierExtensionTest extends CompilerTestBase {

        static List<ExtensionInvocation> invocations = new ArrayList<>();

        @BeforeEach
        void setup() {
            invocations.clear();
        }

        @SuppressWarnings("unused")
        public static class PrimitiveIntSupplier implements MarkupExtension.IntSupplier {
            private final int param;

            public PrimitiveIntSupplier(@NamedArg("param") int param) {
                this.param = param;
            }

            @Override
            public int get(MarkupContext context) {
                invocations.add(ExtensionInvocation.of(context));
                return param;
            }
        }

        @Test
        public void PrimitiveIntSupplier_Is_Applied_To_PrimitiveDouble() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          doubleProp1="{PrimitiveIntSupplier param=123}"
                          doubleProp2="{PrimitiveIntSupplier param=456}"
                          doublePropWithoutSetter="{PrimitiveIntSupplier param=789}"/>
            """);

            assertEquals(3, invocations.size());

            assertEquals(123.0, root.getDoubleProp1(), 0.001);
            assertExtensionInvocation(invocations.get(0), root, 1, root, "doubleProp1", double.class, null);

            assertEquals(456.0, root.getDoubleProp2(), 0.001);
            assertExtensionInvocation(invocations.get(1), root, 1, root, "doubleProp2", double.class, null);

            assertEquals(789, root.doublePropWithoutSetterProperty().get(), 0.001);
            assertExtensionInvocation(invocations.get(2), root, 1, root, "doublePropWithoutSetter", double.class, null);
        }

        @Test
        public void PrimitiveIntSupplier_Is_Applied_To_PrimitiveFloat() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          floatProp="{PrimitiveIntSupplier param=123}"/>
            """);

            assertEquals(1, invocations.size());
            assertEquals(123.0, root.getFloatProp(), 0.001);
            assertExtensionInvocation(invocations.get(0), root, 1, root, "floatProp", float.class, null);
        }

        @Test
        public void PrimitiveIntSupplier_Is_Applied_To_PrimitiveInteger() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          intProp="{PrimitiveIntSupplier param=123}"/>
            """);

            assertEquals(1, invocations.size());
            assertEquals(123, root.getIntProp());
            assertExtensionInvocation(invocations.get(0), root, 1, root, "intProp", int.class, null);
        }

        @Test
        public void PrimitiveIntSupplier_Is_Applied_To_PrimitiveLong() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          longProp="{PrimitiveIntSupplier param=123}"/>
            """);

            assertEquals(1, invocations.size());
            assertEquals(123, root.getLongProp(), 0.001);
            assertExtensionInvocation(invocations.get(0), root, 1, root, "longProp", long.class, null);
        }

        @Test
        public void PrimitiveIntSupplier_Cannot_Be_Applied_To_PrimitiveBoolean() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          boolProp="{PrimitiveIntSupplier param=123}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{PrimitiveIntSupplier param=123}", ex);
        }
    }

    @Nested
    @Execution(ExecutionMode.SAME_THREAD)
    public class LongSupplierExtensionTest extends CompilerTestBase {

        static List<ExtensionInvocation> invocations = new ArrayList<>();

        @BeforeEach
        void setup() {
            invocations.clear();
        }

        @SuppressWarnings("unused")
        public static class PrimitiveLongSupplier implements MarkupExtension.LongSupplier {
            private final long param;

            public PrimitiveLongSupplier(@NamedArg("param") long param) {
                this.param = param;
            }

            @Override
            public long get(MarkupContext context) {
                invocations.add(ExtensionInvocation.of(context));
                return param;
            }
        }

        @Test
        public void PrimitiveLongSupplier_Is_Applied_To_PrimitiveDouble() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          doubleProp1="{PrimitiveLongSupplier param=123}"
                          doubleProp2="{PrimitiveLongSupplier param=456}"
                          doublePropWithoutSetter="{PrimitiveLongSupplier param=789}"/>
            """);

            assertEquals(3, invocations.size());

            assertEquals(123.0, root.getDoubleProp1(), 0.001);
            assertExtensionInvocation(invocations.get(0), root, 1, root, "doubleProp1", double.class, null);

            assertEquals(456.0, root.getDoubleProp2(), 0.001);
            assertExtensionInvocation(invocations.get(1), root, 1, root, "doubleProp2", double.class, null);

            assertEquals(789, root.doublePropWithoutSetterProperty().get(), 0.001);
            assertExtensionInvocation(invocations.get(2), root, 1, root, "doublePropWithoutSetter", double.class, null);
        }

        @Test
        public void PrimitiveLongSupplier_Is_Applied_To_PrimitiveFloat() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          floatProp="{PrimitiveLongSupplier param=123}"/>
            """);

            assertEquals(1, invocations.size());
            assertEquals(123.0, root.getFloatProp(), 0.001);
            assertExtensionInvocation(invocations.get(0), root, 1, root, "floatProp", float.class, null);
        }

        @Test
        public void PrimitiveLongSupplier_Is_Applied_To_PrimitiveLong() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          longProp="{PrimitiveLongSupplier param=123}"/>
            """);

            assertEquals(1, invocations.size());
            assertEquals(123, root.getLongProp(), 0.001);
            assertExtensionInvocation(invocations.get(0), root, 1, root, "longProp", long.class, null);
        }

        @Test
        public void PrimitiveLongSupplier_Cannot_Be_Applied_To_PrimitiveInteger() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          intProp="{PrimitiveLongSupplier param=123}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{PrimitiveLongSupplier param=123}", ex);
        }

        @Test
        public void PrimitiveLongSupplier_Cannot_Be_Applied_To_PrimitiveBoolean() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          boolProp="{PrimitiveLongSupplier param=123}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{PrimitiveLongSupplier param=123}", ex);
        }
    }

    @Nested
    public class ResourceExtensionTest extends CompilerTestBase {

        @SuppressWarnings("unused")
        public static class TestLabel extends Label {
            private final ObjectProperty<URL> url = new SimpleObjectProperty<>();
            public final ObjectProperty<URL> urlProperty() { return url; }
            public final URL getUrl() { return url.get(); }

            private final ObjectProperty<URI> uri = new SimpleObjectProperty<>();
            public final ObjectProperty<URI> uriProperty() { return uri; }
            public final URI getUri() { return uri.get(); }
        }

        @Test
        public void Resource_With_Relative_Location_Is_Evaluated_Correctly() throws Exception {
            TestLabel root = compileAndRun("""
                <?import org.jfxcore.markup.*?>
                <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                           text="{Resource image.jpg}"
                           url="{Resource image.jpg}"
                           uri="{Resource image.jpg}"/>
            """);

            URL url = Objects.requireNonNull(root.getClass().getResource("image.jpg"));
            assertTrue(root.getText().endsWith("org/jfxcore/compiler/classes/image.jpg"));
            assertEquals(url, root.getUrl());
            assertEquals(url.toURI(), root.getUri());
        }

        @Test
        public void Resource_With_Root_Location_Is_Evaluated_Correctly() throws Exception {
            TestLabel root = compileAndRun("""
                <?import org.jfxcore.markup.*?>
                <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                           text="{Resource /org/jfxcore/compiler/classes/image.jpg}"
                           url="{Resource /org/jfxcore/compiler/classes/image.jpg}"
                           uri="{Resource /org/jfxcore/compiler/classes/image.jpg}"/>
            """);

            URL url = Objects.requireNonNull(root.getClass().getResource("image.jpg"));
            assertTrue(root.getText().endsWith("org/jfxcore/compiler/classes/image.jpg"));
            assertEquals(url, root.getUrl());
            assertEquals(url.toURI(), root.getUri());
        }

        @Test
        public void Resource_With_Quoted_Path_Is_Evaluated_Correctly() throws Exception {
            TestLabel root = compileAndRun("""
                <?import org.jfxcore.markup.*?>
                <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                           text="{Resource '/org/jfxcore/compiler/classes/image with   spaces.jpg'}"
                           url="{Resource '/org/jfxcore/compiler/classes/image with   spaces.jpg'}"
                           uri="{Resource '/org/jfxcore/compiler/classes/image with   spaces.jpg'}"/>
            """);

            URL url = Objects.requireNonNull(root.getClass().getResource("image with   spaces.jpg"));
            assertTrue(root.getText().endsWith("org/jfxcore/compiler/classes/image%20with%20%20%20spaces.jpg"));
            assertEquals(url, root.getUrl());
            assertEquals(url.toURI(), root.getUri());
        }

        @Test
        public void Resource_Extension_Works_In_ValueOf_Expression() {
            Label root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <?import org.jfxcore.markup.*?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <text>
                        <String fx:value="{Resource image.jpg}"/>
                    </text>
                </Label>
            """);

            assertTrue(root.getText().endsWith("org/jfxcore/compiler/classes/image.jpg"));
        }

        @Test
        public void Resource_Can_Be_Added_To_String_Collection() {
            Label root = compileAndRun("""
                <?import javafx.scene.control.*?>
                <?import org.jfxcore.markup.*?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <stylesheets>
                        <Resource>image.jpg</Resource>
                    </stylesheets>
                </Label>
            """);

            assertTrue(root.getStylesheets().stream().anyMatch(s -> s.endsWith("org/jfxcore/compiler/classes/image.jpg")));
        }

        @Test
        public void Resource_Cannot_Be_Assigned_To_Incompatible_Property() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <?import org.jfxcore.markup.*?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       prefWidth="{Resource image.jpg}"/>
            """));

            assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("{Resource image.jpg}", ex);
        }

        @Test
        public void Unsuitable_Parameter_Fails() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <?import org.jfxcore.markup.*?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="{Resource ${foo}}"/>
            """));

            assertEquals(ErrorCode.EXPRESSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("${foo}", ex);
        }

        @Test
        public void Nonexistent_Resource_Throws_RuntimeException() {
            RuntimeException ex = assertThrows(RuntimeException.class, () -> compileAndRun("""
                <?import javafx.scene.control.*?>
                <?import org.jfxcore.markup.*?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="{Resource foobarbaz.jpg}"/>
            """));

            assertTrue(ex.getMessage().startsWith("Resource not found"));
        }
    }
}

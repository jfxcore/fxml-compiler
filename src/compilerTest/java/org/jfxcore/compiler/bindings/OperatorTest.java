// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.TestExtension;
import org.jfxcore.markup.InverseMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.beans.value.ObservableFloatValue;
import javafx.beans.value.ObservableIntegerValue;
import javafx.beans.value.ObservableLongValue;
import javafx.beans.value.ObservableValue;
import javafx.scene.layout.Pane;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class OperatorTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static String stringifyWithNull(String format, Object... args) {
        if (args.length == 1 && args[0] instanceof Number && ((Number)args[0]).intValue() == 0) {
            return null;
        }

        return String.format(format, args);
    }

    @InverseMethod("stringToDouble")
    @SuppressWarnings("unused")
    public static String doubleToString(double value) {
        return Double.toString(value);
    }

    @SuppressWarnings("unused")
    public static double stringToDouble(String value) {
        return Double.parseDouble(value);
    }

    @SuppressWarnings("unused")
    public static class TestPane extends Pane {
        public final DoubleProperty doubleProp = new SimpleDoubleProperty(123);
        public final BooleanProperty booleanProp = new SimpleBooleanProperty(true);
        public final BooleanProperty failingBooleanProp = new SimpleBooleanProperty(true) {
            @Override
            public void set(boolean newValue) {
                throw new RuntimeException();
            }
        };

        public final ObjectProperty<IndirectContext> indirectContext = new SimpleObjectProperty<>(new IndirectContext());

        public final ObservableValue<Boolean> observableBool = new SimpleObjectProperty<>(true);
    }

    public static class IndirectContext {
        public final BooleanProperty booleanProp = new SimpleBooleanProperty(false);
    }

    public enum ImplType {
        UNIDIRECTIONAL_LOCAL((testBase, root, type, invert) ->
            testBase.assertNewExpr(root, ctors -> ctors.stream().anyMatch(
                ctor -> ctor.name().endsWith(getMangledClassName(type, invert))))),

        UNIDIRECTIONAL_LIBRARY((testBase, root, type, invert) -> {
            Class<?> argType = getArgClass(type);
            String methodName;
            if (Number.class.isAssignableFrom(type)) {
                methodName = invert ? "isZero" : "isNotZero";
            } else if (Boolean.class.isAssignableFrom(type)) {
                if (!invert) throw new AssertionError();
                methodName = "isNot";
            } else {
                methodName = invert ? "isNull" : "isNotNull";
            }

            testBase.assertMethodCall(root, methods -> methods.stream().anyMatch(method ->
                methodName.equals(method.name())
                && "BooleanBindings".equals(method.declaringType().simpleName())
                && argType.getSimpleName().equals(method.parameters().get(0).type().simpleName())));
        }),

        BIDIRECTIONAL_INVERSE_LOCAL((testBase, root, type, invert) ->
            testBase.assertMethodCall(root, methods -> methods.stream().anyMatch(method ->
                "bindBidirectional".equals(method.name())
                && method.declaringType().simpleName().endsWith("InvertBooleanBinding")
            ))),

        BIDIRECTIONAL_INVERSE_LIBRARY((testBase, root, type, invert) ->
            testBase.assertMethodCall(root, methods -> methods.stream().anyMatch(method ->
                "bindBidirectionalComplement".equals(method.name())
                && "BooleanBindings".equals(method.declaringType().simpleName())
            )));

        ImplType(ImplTest implTest) {
            this.implTest = implTest;
        }

        final ImplTest implTest;

        public void configure(CompilationContext context) {
            context.put(
                CompilationContext.USE_SHARED_IMPLEMENTATION,
                this == UNIDIRECTIONAL_LOCAL || this == BIDIRECTIONAL_INVERSE_LOCAL ? Boolean.FALSE : Boolean.TRUE);
        }

        public void assertImpl(CompilerTestBase testBase, Object root, Class<?> type, boolean invert) {
            implTest.assertImpl(testBase, root, type, invert);
        }

        public String suffix() {
            return this == UNIDIRECTIONAL_LOCAL || this == BIDIRECTIONAL_INVERSE_LOCAL ? "_LocalImpl" : "_LibraryImpl";
        }

        private static String getMangledClassName(Class<?> type, boolean invert) {
            String className;
            if (type == Double.class) className = invert ? "DoubleToInvBoolean" : "DoubleToBoolean";
            else if (type == Float.class) className = invert ? "FloatToInvBoolean" : "FloatToBoolean";
            else if (type == Integer.class) className = invert ? "IntToInvBoolean" : "IntToBoolean";
            else if (type == Long.class) className = invert ? "LongToInvBoolean" : "LongToBoolean";
            else if (type == Boolean.class) className = invert ? "BooleanToInvBoolean" : null;
            else className = invert ? "ObjectToInvBoolean" : "ObjectToBoolean";

            return NameHelper.getMangledClassName(className);
        }

        private static Class<?> getArgClass(Class<?> type) {
            if (type == Double.class) return ObservableDoubleValue.class;
            else if (type == Float.class) return ObservableFloatValue.class;
            else if (type == Integer.class) return ObservableIntegerValue.class;
            else if (type == Long.class) return ObservableLongValue.class;
            return ObservableValue.class;
        }

        private interface ImplTest {
            void assertImpl(CompilerTestBase testBase, Object root, Class<?> type, boolean invert);
        }
    }

    @Test
    public void Bind_Once_With_NotOperator_Succeeds_For_BooleanProperty() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="$!booleanProp"/>
        """);

        assertFalse(root.isVisible());
    }

    @Test
    public void Bind_Once_With_NotOperator_Succeeds_For_DoubleProperty() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="$!doubleProp"/>
        """);

        assertFalse(root.isVisible());
    }

    @Test
    public void Bind_Once_With_BoolifyOperator_Succeeds_For_DoubleProperty() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="$!!doubleProp"/>
        """);

        assertTrue(root.isVisible());
    }

    @Test
    public void Bind_Once_With_NotOperator_Succeeds_For_FunctionExpression() {
        TestPane root = compileAndRun("""
            <?import org.jfxcore.compiler.bindings.OperatorTest?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="$!OperatorTest.stringifyWithNull('%s', doubleProp)"/>
        """);

        assertFalse(root.isVisible());
        root.doubleProp.set(0);
        assertFalse(root.isVisible());
    }

    @Test
    public void Bind_Once_With_BoolifyOperator_Succeeds_For_FunctionExpression() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="$!!java.lang.String.format('%s', doubleProp)"/>
        """);

        assertTrue(root.isVisible());
        root.doubleProp.set(0);
        assertTrue(root.isVisible());
    }

    @ParameterizedTest
    @EnumSource(value = ImplType.class, names = {"UNIDIRECTIONAL_LOCAL", "UNIDIRECTIONAL_LIBRARY"})
    public void Bind_Unidirectional_With_NotOperator_Succeeds_For_ObservableValue(ImplType implType) {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="${!observableBool}"/>
        """, implType.suffix(), implType::configure);

        assertFalse(root.isVisible());
        ((ObjectProperty<Boolean>)root.observableBool).set(false);
        assertTrue(root.isVisible());
        ((ObjectProperty<Boolean>)root.observableBool).set(true);
        assertFalse(root.isVisible());
        ((ObjectProperty<Boolean>)root.observableBool).setValue(null);
        assertTrue(root.isVisible());

        implType.assertImpl(this, root, Boolean.class, true);
    }

    @ParameterizedTest
    @EnumSource(value = ImplType.class, names = {"UNIDIRECTIONAL_LOCAL", "UNIDIRECTIONAL_LIBRARY"})
    public void Bind_Unidirectional_With_NotOperator_Succeeds_For_BooleanProperty(ImplType implType) {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="${!booleanProp}"/>
        """, implType.suffix(), implType::configure);

        assertFalse(root.isVisible());
        root.booleanProp.set(false);
        assertTrue(root.isVisible());
        root.booleanProp.set(true);
        assertFalse(root.isVisible());

        implType.assertImpl(this, root, Boolean.class, true);
    }

    @Test
    public void Bind_Unidirectional_With_BoolifyOperator_Is_Elided() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="${!!booleanProp}"/>
        """);

        assertTrue(root.isVisible());
        root.booleanProp.set(false);
        assertFalse(root.isVisible());
        root.booleanProp.set(true);
        assertTrue(root.isVisible());

        assertNewExpr(root, ctors -> ctors.stream().noneMatch(ctor -> ctor.name().endsWith("ToBoolean")));
    }

    @ParameterizedTest
    @EnumSource(value = ImplType.class, names = {"UNIDIRECTIONAL_LOCAL", "UNIDIRECTIONAL_LIBRARY"})
    public void Bind_Unidirectional_With_NotOperator_Succeeds_For_DoubleProperty(ImplType implType) {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="${!doubleProp}"/>
        """, implType.suffix(), implType::configure);

        assertFalse(root.isVisible());
        root.doubleProp.set(0);
        assertTrue(root.isVisible());
        root.doubleProp.set(1);
        assertFalse(root.isVisible());

        implType.assertImpl(this, root, Double.class, true);
    }

    @ParameterizedTest
    @EnumSource(value = ImplType.class, names = {"UNIDIRECTIONAL_LOCAL", "UNIDIRECTIONAL_LIBRARY"})
    public void Bind_Unidirectional_With_BoolifyOperator_Succeeds_For_DoubleProperty(ImplType implType) {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="${!!doubleProp}"/>
        """, implType.suffix(), implType::configure);

        assertTrue(root.isVisible());
        root.doubleProp.set(0);
        assertFalse(root.isVisible());
        root.doubleProp.set(1);
        assertTrue(root.isVisible());

        implType.assertImpl(this, root, Double.class, false);
    }

    @ParameterizedTest
    @EnumSource(value = ImplType.class, names = {"UNIDIRECTIONAL_LOCAL", "UNIDIRECTIONAL_LIBRARY"})
    public void Bind_Unidirectional_With_NotOperator_Succeeds_For_FunctionExpression(ImplType implType) {
        TestPane root = compileAndRun("""
            <?import org.jfxcore.compiler.bindings.OperatorTest?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="${!OperatorTest.stringifyWithNull('%s', doubleProp)}"/>
        """, implType.suffix(), implType::configure);

        assertFalse(root.isVisible());
        root.doubleProp.set(0);
        assertTrue(root.isVisible());
        root.doubleProp.set(1);
        assertFalse(root.isVisible());

        implType.assertImpl(this, root, String.class, true);
    }

    @ParameterizedTest
    @EnumSource(value = ImplType.class, names = {"UNIDIRECTIONAL_LOCAL", "UNIDIRECTIONAL_LIBRARY"})
    public void Bind_Unidirectional_With_BoolifyOperator_Succeeds_For_FunctionExpression(ImplType implType) {
        TestPane root = compileAndRun("""
            <?import org.jfxcore.compiler.bindings.OperatorTest?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="${!!OperatorTest.stringifyWithNull('%s', doubleProp)}"/>
        """, implType.suffix(), implType::configure);

        assertTrue(root.isVisible());
        root.doubleProp.set(0);
        assertFalse(root.isVisible());
        root.doubleProp.set(1);
        assertTrue(root.isVisible());

        implType.assertImpl(this, root, String.class, false);
    }

    @Test
    public void Bind_Reverse_With_NotOperator_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible=">{!booleanProp}"/>
        """));

        assertEquals(ErrorCode.UNEXPECTED_TOKEN, ex.getDiagnostic().getCode());
        assertCodeHighlight("!", ex);
    }

    @Test
    public void Bind_Reverse_With_BoolifyOperator_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible=">{!!booleanProp}"/>
        """));

        assertEquals(ErrorCode.UNEXPECTED_TOKEN, ex.getDiagnostic().getCode());
        assertCodeHighlight("!!", ex);
    }

    @Test
    public void Bind_Bidirectional_With_NotOperator_Fails_For_DoubleProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="#{!doubleProp}"/>
        """));

        assertEquals(ErrorCode.EXPRESSION_NOT_INVERTIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("doubleProp", ex);
    }

    @Test
    public void Bind_Bidirectional_With_BoolifyOperator_Fails_For_DoubleProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="#{!!doubleProp}"/>
        """));

        assertEquals(ErrorCode.EXPRESSION_NOT_INVERTIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("doubleProp", ex);
    }

    @Test
    public void Bind_Bidirectional_With_NotOperator_Fails_For_FunctionExpression() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import org.jfxcore.compiler.bindings.OperatorTest?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="#{!OperatorTest.doubleToString(doubleProp)}"/>
        """));

        assertEquals(ErrorCode.EXPRESSION_NOT_INVERTIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("OperatorTest.doubleToString(doubleProp)", ex);
    }

    @Test
    public void Bind_Bidirectional_With_BoolifyOperator_Fails_For_FunctionExpression() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import org.jfxcore.compiler.bindings.OperatorTest?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="#{!!OperatorTest.doubleToString(doubleProp)}"/>
        """));

        assertEquals(ErrorCode.EXPRESSION_NOT_INVERTIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("OperatorTest.doubleToString(doubleProp)", ex);
    }

    @ParameterizedTest
    @EnumSource(value = ImplType.class, names = {"BIDIRECTIONAL_INVERSE_LOCAL", "BIDIRECTIONAL_INVERSE_LIBRARY"})
    public void Bind_Bidirectional_With_NotOperator_Succeeds_For_BooleanProperty(ImplType implType) {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="#{!booleanProp}"/>
        """, implType.suffix(), implType::configure);

        root.booleanProp.set(false);
        assertTrue(root.isVisible());

        root.booleanProp.set(true);
        assertFalse(root.isVisible());

        root.setVisible(true);
        assertFalse(root.booleanProp.get());

        root.setVisible(false);
        assertTrue(root.booleanProp.get());

        implType.assertImpl(this, root, Boolean.class, true);
    }

    @Test
    public void Bind_Bidirectional_With_BoolifyOperator_Succeeds_For_BooleanProperty() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="#{!!booleanProp}"/>
        """);

        root.booleanProp.set(true);
        assertTrue(root.isVisible());

        root.booleanProp.set(false);
        assertFalse(root.isVisible());

        root.setVisible(true);
        assertTrue(root.booleanProp.get());

        root.setVisible(false);
        assertFalse(root.booleanProp.get());
    }

    @ParameterizedTest
    @EnumSource(value = ImplType.class, names = {"BIDIRECTIONAL_INVERSE_LOCAL", "BIDIRECTIONAL_INVERSE_LIBRARY"})
    public void Bind_Bidirectional_With_NotOperator_Succeeds_For_Indirect_BooleanProperty(ImplType implType) {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="#{!indirectContext.booleanProp}"/>
        """, implType.suffix(), implType::configure);

        var context = root.indirectContext.get();
        context.booleanProp.set(false);
        assertTrue(root.isVisible());

        context.booleanProp.set(true);
        assertFalse(root.isVisible());

        root.setVisible(true);
        assertFalse(context.booleanProp.get());

        root.setVisible(false);
        assertTrue(context.booleanProp.get());

        root.indirectContext.set(context = new IndirectContext());
        context.booleanProp.set(true);
        assertFalse(root.isVisible());

        context.booleanProp.set(false);
        assertTrue(root.isVisible());

        root.setVisible(false);
        assertTrue(context.booleanProp.get());

        root.setVisible(true);
        assertFalse(context.booleanProp.get());

        implType.assertImpl(this, root, Boolean.class, true);
    }

    @Test
    public void Bind_Bidirectional_With_NotOperator_Failing_Property() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="#{!failingBooleanProp}"/>
        """);

        var uncaughtExceptionHandler = Thread.currentThread().getUncaughtExceptionHandler();
        var exception = new Throwable[1];
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> exception[0] = e);
        boolean oldValue = root.failingBooleanProp.get();
        root.setVisible(true);
        Thread.currentThread().setUncaughtExceptionHandler(uncaughtExceptionHandler);

        assertTrue(oldValue);
        assertInstanceOf(RuntimeException.class, exception[0]);
        assertTrue(exception[0].getMessage().contains("Bidirectional binding failed"));
    }
}

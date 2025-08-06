// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.InverseMethod;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
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

        public final ObservableValue<Boolean> observableBool = new ObservableValue<>() {
            @Override public void addListener(ChangeListener<? super Boolean> changeListener) {}
            @Override public void removeListener(ChangeListener<? super Boolean> changeListener) {}
            @Override public void addListener(InvalidationListener invalidationListener) {}
            @Override public void removeListener(InvalidationListener invalidationListener) {}
            @Override public Boolean getValue() { return true; }
        };
    }

    public static class IndirectContext {
        public final BooleanProperty booleanProp = new SimpleBooleanProperty(false);
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

    @Test
    public void Bind_Unidirectional_With_NotOperator_Succeeds_For_ObservableValue() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="${!observableBool}"/>
        """);

        assertFalse(root.isVisible());

        assertNewExpr(root, ctors -> ctors.stream().anyMatch(
            ctor -> ctor.getName().endsWith(NameHelper.getMangledClassName("BooleanToInvBoolean"))));
    }

    @Test
    public void Bind_Unidirectional_With_NotOperator_Succeeds_For_BooleanProperty() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="${!booleanProp}"/>
        """);

        assertFalse(root.isVisible());
        root.booleanProp.set(false);
        assertTrue(root.isVisible());
        root.booleanProp.set(true);
        assertFalse(root.isVisible());

        assertNewExpr(root, ctors -> ctors.stream().anyMatch(
            ctor -> ctor.getName().endsWith(NameHelper.getMangledClassName("BooleanToInvBoolean"))));
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

        assertNewExpr(root, ctors -> ctors.stream().noneMatch(ctor -> ctor.getName().endsWith("ToBoolean")));
    }

    @Test
    public void Bind_Unidirectional_With_NotOperator_Succeeds_For_DoubleProperty() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="${!doubleProp}"/>
        """);

        assertFalse(root.isVisible());
        root.doubleProp.set(0);
        assertTrue(root.isVisible());
        root.doubleProp.set(1);
        assertFalse(root.isVisible());

        assertNewExpr(root, ctors -> ctors.stream().anyMatch(
            ctor -> ctor.getName().endsWith(NameHelper.getMangledClassName("DoubleToInvBoolean"))));
    }

    @Test
    public void Bind_Unidirectional_With_BoolifyOperator_Succeeds_For_DoubleProperty() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="${!!doubleProp}"/>
        """);

        assertTrue(root.isVisible());
        root.doubleProp.set(0);
        assertFalse(root.isVisible());
        root.doubleProp.set(1);
        assertTrue(root.isVisible());

        assertNewExpr(root, ctors -> ctors.stream().anyMatch(
            ctor -> ctor.getName().endsWith(NameHelper.getMangledClassName("DoubleToBoolean"))));
    }

    @Test
    public void Bind_Unidirectional_With_NotOperator_Succeeds_For_FunctionExpression() {
        TestPane root = compileAndRun("""
            <?import org.jfxcore.compiler.bindings.OperatorTest?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="${!OperatorTest.stringifyWithNull('%s', doubleProp)}"/>
        """);

        assertFalse(root.isVisible());
        root.doubleProp.set(0);
        assertTrue(root.isVisible());
        root.doubleProp.set(1);
        assertFalse(root.isVisible());

        assertNewExpr(root, ctors -> ctors.stream().anyMatch(
            ctor -> ctor.getName().endsWith(NameHelper.getMangledClassName("ObjectToInvBoolean"))));
    }

    @Test
    public void Bind_Unidirectional_With_BoolifyOperator_Succeeds_For_FunctionExpression() {
        TestPane root = compileAndRun("""
            <?import org.jfxcore.compiler.bindings.OperatorTest?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="${!!OperatorTest.stringifyWithNull('%s', doubleProp)}"/>
        """);

        assertTrue(root.isVisible());
        root.doubleProp.set(0);
        assertFalse(root.isVisible());
        root.doubleProp.set(1);
        assertTrue(root.isVisible());

        assertNewExpr(root, ctors -> ctors.stream().anyMatch(
            ctor -> ctor.getName().endsWith(NameHelper.getMangledClassName("ObjectToBoolean"))));
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

    @Test
    public void Bind_Bidirectional_With_NotOperator_Succeeds_For_BooleanProperty() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="#{!booleanProp}"/>
        """);

        root.booleanProp.set(false);
        assertTrue(root.isVisible());

        root.booleanProp.set(true);
        assertFalse(root.isVisible());

        root.setVisible(true);
        assertFalse(root.booleanProp.get());

        root.setVisible(false);
        assertTrue(root.booleanProp.get());
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

    @Test
    public void Bind_Bidirectional_With_NotOperator_Succeeds_For_Indirect_BooleanProperty() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="#{!indirectContext.booleanProp}"/>
        """);

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

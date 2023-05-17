// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.InverseMethod;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
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

        public final ObservableValue<Boolean> observableBool = new ObservableValue<>() {
            @Override public void addListener(ChangeListener<? super Boolean> changeListener) {}
            @Override public void removeListener(ChangeListener<? super Boolean> changeListener) {}
            @Override public void addListener(InvalidationListener invalidationListener) {}
            @Override public void removeListener(InvalidationListener invalidationListener) {}
            @Override public Boolean getValue() { return true; }
        };
    }

    @Test
    public void Bind_Once_With_NotOperator_Succeeds_For_BooleanProperty() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="{fx:once !booleanProp}"/>
        """);

        assertFalse(root.isVisible());
    }

    @Test
    public void Bind_Once_With_NotOperator_Succeeds_For_DoubleProperty() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="{fx:once !doubleProp}"/>
        """);

        assertFalse(root.isVisible());
    }

    @Test
    public void Bind_Once_With_BoolifyOperator_Succeeds_For_DoubleProperty() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="{fx:once !!doubleProp}"/>
        """);

        assertTrue(root.isVisible());
    }

    @Test
    public void Bind_Once_With_NotOperator_Succeeds_For_FunctionExpression() {
        TestPane root = compileAndRun("""
            <?import org.jfxcore.compiler.bindings.OperatorTest?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="{fx:once !OperatorTest.stringifyWithNull('%s', doubleProp)}"/>
        """);

        assertFalse(root.isVisible());
        root.doubleProp.set(0);
        assertFalse(root.isVisible());
    }

    @Test
    public void Bind_Once_With_BoolifyOperator_Succeeds_For_FunctionExpression() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="{fx:once !!java.lang.String.format('%s', doubleProp)}"/>
        """);

        assertTrue(root.isVisible());
        root.doubleProp.set(0);
        assertTrue(root.isVisible());
    }

    @Test
    public void Bind_Unidirectional_With_NotOperator_Succeeds_For_ObservableValue() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="{fx:bind !observableBool}"/>
        """);

        assertFalse(root.isVisible());

        assertNewExpr(root, ctors -> ctors.stream().anyMatch(
            ctor -> ctor.getName().endsWith(NameHelper.getMangledClassName("BooleanToInvBoolean"))));
    }

    @Test
    public void Bind_Unidirectional_With_NotOperator_Succeeds_For_BooleanProperty() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="{fx:bind !booleanProp}"/>
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
                      visible="{fx:bind !!booleanProp}"/>
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
                      visible="{fx:bind !doubleProp}"/>
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
                      visible="{fx:bind !!doubleProp}"/>
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
                      visible="{fx:bind !OperatorTest.stringifyWithNull('%s', doubleProp)}"/>
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
                      visible="{fx:bind !!OperatorTest.stringifyWithNull('%s', doubleProp)}"/>
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
                      visible="{fx:bindBidirectional !doubleProp}"/>
        """));

        assertEquals(ErrorCode.EXPRESSION_NOT_INVERTIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("doubleProp", ex);
    }

    @Test
    public void Bind_Bidirectional_With_BoolifyOperator_Fails_For_DoubleProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="{fx:bindBidirectional !!doubleProp}"/>
        """));

        assertEquals(ErrorCode.EXPRESSION_NOT_INVERTIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("doubleProp", ex);
    }

    @Test
    public void Bind_Bidirectional_With_NotOperator_Fails_For_FunctionExpression() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import org.jfxcore.compiler.bindings.OperatorTest?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="{fx:bindBidirectional !OperatorTest.doubleToString(doubleProp)}"/>
        """));

        assertEquals(ErrorCode.EXPRESSION_NOT_INVERTIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("OperatorTest.doubleToString(doubleProp)", ex);
    }

    @Test
    public void Bind_Bidirectional_With_BoolifyOperator_Fails_For_FunctionExpression() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import org.jfxcore.compiler.bindings.OperatorTest?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="{fx:bindBidirectional !!OperatorTest.doubleToString(doubleProp)}"/>
        """));

        assertEquals(ErrorCode.EXPRESSION_NOT_INVERTIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("OperatorTest.doubleToString(doubleProp)", ex);
    }

    @Test
    @Disabled("Disabled until support for invertible bidirectional boolean bindings is available")
    public void Bind_Bidirectional_With_NotOperator_Succeeds_For_BooleanProperty() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="{fx:bindBidirectional !booleanProp}"/>
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



}

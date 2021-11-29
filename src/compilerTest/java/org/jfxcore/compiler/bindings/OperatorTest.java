// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.TestCompiler;
import org.jfxcore.compiler.util.TestExtension;
import javafx.fxml.InverseMethod;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class OperatorTest {

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

    @Test
    public void Bind_Once_With_NotOperator_Succeeds_For_DoubleProperty() {
        BindingPathTest.TestPane root = TestCompiler.newInstance(
            this, "Bind_Once_With_NotOperator_Succeeds_For_DoubleProperty", """
                <?import javafx.fxml.*?>
                <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          visible="{fx:once !invariantContext.doubleVal}"/>
            """);

        assertFalse(root.isVisible());
    }

    @Test
    public void Bind_Once_With_BoolifyOperator_Succeeds_For_DoubleProperty() {
        BindingPathTest.TestPane root = TestCompiler.newInstance(
            this, "Bind_Once_With_BoolifyOperator_Succeeds_For_DoubleProperty", """
                <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          visible="{fx:once !!invariantContext.doubleVal}"/>
            """);

        assertTrue(root.isVisible());
    }

    @Test
    public void Bind_Once_With_NotOperator_Succeeds_For_FunctionExpression() {
        BindingPathTest.TestPane root = TestCompiler.newInstance(
            this, "Bind_Once_With_NotOperator_Succeeds_For_FunctionExpression", """
                <?import org.jfxcore.compiler.bindings.*?>
                <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          visible="{fx:once !OperatorTest.stringifyWithNull('%s', invariantContext.doubleVal)}"/>
            """);

        assertFalse(root.isVisible());
        root.invariantContext.doubleValProperty().set(0);
        assertFalse(root.isVisible());
    }

    @Test
    public void Bind_Once_With_BoolifyOperator_Succeeds_For_FunctionExpression() {
        BindingPathTest.TestPane root = TestCompiler.newInstance(
            this, "Bind_Once_With_BoolifyOperator_Succeeds_For_FunctionExpression", """
                <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          visible="{fx:once !!java.lang.String.format('%s', invariantContext.doubleVal)}"/>
            """);

        assertTrue(root.isVisible());
        root.invariantContext.doubleValProperty().set(0);
        assertTrue(root.isVisible());
    }

    @Test
    public void Bind_Unidirectional_With_NotOperator_Succeeds_For_DoubleProperty() {
        BindingPathTest.TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_With_NotOperator_Succeeds_For_DoubleProperty", """
                <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          visible="{fx:bind !invariantContext.doubleVal}"/>
            """);

        assertFalse(root.isVisible());
        root.invariantContext.doubleValProperty().set(0);
        assertTrue(root.isVisible());
    }

    @Test
    public void Bind_Unidirectional_With_BoolifyOperator_Succeeds_For_DoubleProperty() {
        BindingPathTest.TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_With_BoolifyOperator_Succeeds_For_DoubleProperty", """
                <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          visible="{fx:bind !!invariantContext.doubleVal}"/>
            """);

        assertTrue(root.isVisible());
        root.invariantContext.doubleValProperty().set(0);
        assertFalse(root.isVisible());
    }

    @Test
    public void Bind_Unidirectional_With_NotOperator_Succeeds_For_FunctionExpression() {
        BindingPathTest.TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_With_NotOperator_Succeeds_For_FunctionExpression", """
                <?import org.jfxcore.compiler.bindings.*?>
                <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          visible="{fx:bind !OperatorTest.stringifyWithNull('%s', invariantContext.doubleVal)}"/>
            """);

        assertFalse(root.isVisible());
        root.invariantContext.doubleValProperty().set(0);
        assertTrue(root.isVisible());
    }

    @Test
    public void Bind_Unidirectional_With_BoolifyOperator_Succeeds_For_FunctionExpression() {
        BindingPathTest.TestPane root = TestCompiler.newInstance(
            this, "Bind_Unidirectional_With_BoolifyOperator_Succeeds_For_FunctionExpression", """
                <?import org.jfxcore.compiler.bindings.*?>
                <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          visible="{fx:bind !!OperatorTest.stringifyWithNull('%s', invariantContext.doubleVal)}"/>
            """);

        assertTrue(root.isVisible());
        root.invariantContext.doubleValProperty().set(0);
        assertFalse(root.isVisible());
    }

    @Test
    public void Bind_Bidirectional_With_NotOperator_Fails_For_DoubleProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Bidirectional_With_NotOperator_Fails_For_DoubleProperty", """
                <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          visible="{fx:sync !invariantContext.doubleVal}"/>
            """));

        assertEquals(ErrorCode.EXPRESSION_NOT_INVERTIBLE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Bidirectional_With_BoolifyOperator_Fails_For_DoubleProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Bidirectional_With_BoolifyOperator_Fails_For_DoubleProperty", """
                <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          visible="{fx:sync !!invariantContext.doubleVal}"/>
            """));

        assertEquals(ErrorCode.EXPRESSION_NOT_INVERTIBLE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Bidirectional_With_NotOperator_Fails_For_FunctionExpression() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Bidirectional_With_NotOperator_Fails_For_FunctionExpression", """
                <?import org.jfxcore.compiler.bindings.*?>
                <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          visible="{fx:sync !OperatorTest.doubleToString(invariantContext.doubleVal)}"/>
            """));

        assertEquals(ErrorCode.EXPRESSION_NOT_INVERTIBLE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Bind_Bidirectional_With_BoolifyOperator_Fails_For_FunctionExpression() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bind_Bidirectional_With_BoolifyOperator_Fails_For_FunctionExpression", """
                <?import org.jfxcore.compiler.bindings.*?>
                <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          visible="{fx:sync !!OperatorTest.doubleToString(invariantContext.doubleVal)}"/>
            """));

        assertEquals(ErrorCode.EXPRESSION_NOT_INVERTIBLE, ex.getDiagnostic().getCode());
    }

    @Test
    @Disabled("Disabled until support for invertible bidirectional boolean bindings is available")
    public void Bind_Bidirectional_With_NotOperator_Succeeds_For_BooleanProperty() {
        BindingPathTest.TestPane root = TestCompiler.newInstance(
            this, "Bind_Bidirectional_With_NotOperator_Succeeds_For_BooleanProperty", """
                <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
                <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                          visible="{fx:sync !invariantContext.boolVal}"/>
            """);

        root.invariantContext.boolValProperty().set(false);
        assertTrue(root.isVisible());

        root.invariantContext.boolValProperty().set(true);
        assertFalse(root.isVisible());

        root.setVisible(true);
        assertFalse(root.invariantContext.boolValProperty().get());

        root.setVisible(false);
        assertTrue(root.invariantContext.boolValProperty().get());
    }



}

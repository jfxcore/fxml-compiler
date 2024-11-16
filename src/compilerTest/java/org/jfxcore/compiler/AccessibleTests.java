// Copyright (c) 2022, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.scene.layout.Pane;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.InverseMethod;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class AccessibleTests extends CompilerTestBase {

    @SuppressWarnings("unused")
    static class InaccessiblePane extends Pane {}

    @Test
    public void NonPublic_Pane_Is_Not_Accessible() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <InaccessiblePane/>
            </GridPane>
        """));

        assertEquals(ErrorCode.CLASS_NOT_ACCESSIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("InaccessiblePane", ex);
    }

    @SuppressWarnings("unused")
    public static class InaccessibleConstant {
        private static final double VALUE = 1.0;
    }

    @Test
    public void NonPublic_Constant_Is_Not_Accessible() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <prefWidth>
                    <InaccessibleConstant fx:constant="VALUE"/>
                </prefWidth>
            </GridPane>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_ACCESSIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("VALUE", ex);
    }

    @SuppressWarnings("unused")
    public static class ValueOfTest {
        static ValueOfTest valueOf(String value) { return null; }
    }

    @Test
    public void NonPublic_ValueOf_Method_Is_Not_Accessible() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <fx:define>
                    <ValueOfTest fx:value="foo"/>
                </fx:define>
            </GridPane>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_ACCESSIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("fx:value=\"foo\"", ex);
    }

    @SuppressWarnings("unused")
    public static class InaccessibleMethodHolder {
        protected static double protectedMethod(double d) {
            return d;
        }
    }

    @SuppressWarnings("unused")
    public static class TestPane extends Pane {
        @InverseMethod("protectedMethod")
        protected double protectedMethod(double d) { return d; }
        double packagePrivateMethod(double d) { return d; }
        private double privateMethod(double d) { return d; }

        protected static class Nested {
            public double publicMethod(double d) { return d; }
            protected double protectedMethod(double d) { return d; }
            protected static double staticProtectedMethod(double d) { return d; }
        }
    }

    @Test
    public void Bind_Once_To_Private_Method_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="$privateMethod(123)"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("privateMethod", ex);
    }

    @Test
    public void Bind_Once_To_PackagePrivate_Method_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="$packagePrivateMethod(123)"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_ACCESSIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("packagePrivateMethod", ex);
    }

    @Test
    public void Bind_Once_To_Protected_Method_With_Constant_Argument_Succeeds() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="$protectedMethod(123)"/>
        """);

        assertEquals(123, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Once_To_Protected_With_Observable_Argument_Method_Succeeds() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="$protectedMethod(prefHeight)"/>
        """);

        assertEquals(-1, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Once_To_Nested_Protected_Method_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="$Nested.protectedMethod(123)"/>
        """));

        assertEquals(ErrorCode.INSTANCE_MEMBER_REFERENCED_FROM_STATIC_CONTEXT, ex.getDiagnostic().getCode());
        assertCodeHighlight("Nested.protectedMethod", ex);
    }

    @Test
    public void Bind_Once_To_Nested_Static_Protected_Method_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="$Nested.staticProtectedMethod(123)"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_ACCESSIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("Nested.staticProtectedMethod", ex);
    }

    @Test
    public void Bind_Unidirectional_To_PackagePrivate_Method_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="${packagePrivateMethod(123)}"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_ACCESSIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("packagePrivateMethod", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Protected_Method_With_Constant_Argument_Succeeds() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="${protectedMethod(123)}"/>
        """);

        assertEquals(123, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_Protected_Method_With_Observable_Argument_Succeeds() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="${protectedMethod(prefHeight)}"/>
        """);

        root.setPrefHeight(5);
        assertEquals(5, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Bidirectional_To_Protected_Method_With_Observable_Argument_Succeeds() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="#{protectedMethod(prefHeight)}"/>
        """);

        root.setPrefHeight(5);
        assertEquals(5, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Bidirectional_To_Protected_Method_With_Inaccessible_InverseMethod_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="#{protectedMethod(prefHeight); inverseMethod=InaccessibleMethodHolder.protectedMethod}"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_ACCESSIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("InaccessibleMethodHolder.protectedMethod", ex);
    }

}

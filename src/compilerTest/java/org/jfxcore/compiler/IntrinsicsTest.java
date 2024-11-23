// Copyright (c) 2021, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.beans.NamedArg;
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.reflect.Constructor;

import static org.jfxcore.compiler.util.MoreAssertions.assertCodeHighlight;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class IntrinsicsTest extends CompilerTestBase {

    @Test
    public void Duplicate_FxId_Is_Invalid() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GridPane fx:id="pane0">
                    <GridPane fx:id="pane0"/>
                </GridPane>
            </GridPane>
        """));

        assertEquals(ErrorCode.DUPLICATE_ID, ex.getDiagnostic().getCode());
        assertCodeHighlight("pane0", ex);
    }

    @Test
    public void FxId_Non_JavaIdentifier_Is_Invalid() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GridPane fx:id="foo bar"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.INVALID_ID, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo bar", ex);
    }

    @Test
    public void FxId_On_Constructor_Argument_Must_Be_Initialized_Before_Parent_In_Preamble() {
        compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.chart.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <LineChart fx:id="chart1">
                    <xAxis>
                        <NumberAxis fx:id="xAxis1"/>
                    </xAxis>
                    <yAxis>
                        <NumberAxis/>
                    </yAxis>
                </LineChart>
            </GridPane>
        """);
    }

    @Test
    public void Unknown_Intrinsic() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GridPane fx:foo="foo"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.UNKNOWN_INTRINSIC, ex.getDiagnostic().getCode());
        assertCodeHighlight("""
            fx:foo="foo"
        """.trim(), ex);
    }

    @Test
    public void Root_Intrinsic_Cannot_Be_Used_On_Child_Element() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compile("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GridPane fx:class="java.lang.String"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.UNEXPECTED_INTRINSIC, ex.getDiagnostic().getCode());
        assertCodeHighlight("""
            fx:class="java.lang.String"
        """.trim(), ex);
    }

    @Test
    public void Incompatible_Class_Parameters_Are_Invalid() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      fx:classParameters="java.lang.String"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("fx:classParameters=\"java.lang.String\"", ex);
    }

    @SuppressWarnings("unused")
    public static class PaneWithParams extends Pane {
        public PaneWithParams(String param) {}
        public PaneWithParams(@NamedArg("myArg1") String myArg1, @NamedArg("myArg2") double myArg2) {}
    }

    @Test
    public void Object_Is_Compiled_With_ClassParameters() {
        Class<?> clazz = compile("""
            <?import org.jfxcore.compiler.IntrinsicsTest.*?>
            <PaneWithParams xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            fx:classParameters="java.lang.String"/>
        """);

        assertEquals(1, clazz.getConstructors().length);
        Constructor<?> ctor = clazz.getConstructors()[0];
        assertEquals(1, ctor.getParameterCount());
        assertEquals(String.class, ctor.getParameters()[0].getType());
    }

    @Test
    public void Object_Is_Compiled_With_NamedArgs_ClassParameters() {
        Class<?> clazz = compile("""
            <?import org.jfxcore.compiler.IntrinsicsTest.*?>
            <PaneWithParams xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            fx:classParameters="java.lang.String, double"/>
        """);

        assertEquals(1, clazz.getConstructors().length);
        Constructor<?> ctor = clazz.getConstructors()[0];
        assertEquals(2, ctor.getParameterCount());
        assertEquals(String.class, ctor.getParameters()[0].getType());
        assertEquals(double.class, ctor.getParameters()[1].getType());

        var annotations = ctor.getParameterAnnotations();
        assertEquals(2, annotations.length);
        assertEquals(1, annotations[0].length);
        assertEquals("javafx.beans.NamedArg", annotations[0][0].annotationType().getName());
        assertEquals(1, annotations[1].length);
        assertEquals("javafx.beans.NamedArg", annotations[1][0].annotationType().getName());
    }

    @Test
    public void TypeArguments_And_Constant_Cannot_Be_Used_At_Same_Time() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compile("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GridPane fx:constant="FOO" fx:typeArguments="bar"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.CONFLICTING_PROPERTIES, ex.getDiagnostic().getCode());
        assertCodeHighlight("fx:typeArguments=\"bar\"", ex);
    }


}

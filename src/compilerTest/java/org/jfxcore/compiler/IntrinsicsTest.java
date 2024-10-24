// Copyright (c) 2021, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

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
        RuntimeException ex = assertThrows(RuntimeException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      fx:classParameters="java.lang.String"/>
        """));

        assertEquals("compiler.err.cant.apply.symbols", ex.getMessage());
    }

    @SuppressWarnings("unused")
    public static class PaneWithParams extends Pane {
        public PaneWithParams(String param) {}
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

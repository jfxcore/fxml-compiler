// Copyright (c) 2021, JFXcore. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class IntrinsicsTest extends CompilerTestBase {

    @Test
    public void Duplicate_FxId_Is_Invalid() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <GridPane fx:id="pane0">
                    <GridPane fx:id="pane0"/>
                </GridPane>
            </GridPane>
        """));

        assertEquals(ErrorCode.DUPLICATE_ID, ex.getDiagnostic().getCode());
    }

    @Test
    public void FxId_Non_JavaIdentifier_Is_Invalid() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <GridPane fx:id="foo bar"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.INVALID_ID, ex.getDiagnostic().getCode());
    }

    @Test
    public void Unknown_Intrinsic() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <GridPane fx:foo="foo"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.UNKNOWN_INTRINSIC, ex.getDiagnostic().getCode());
    }

    @Test
    public void Root_Intrinsic_Cannot_Be_Used_On_Child_Element() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compile("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <GridPane fx:class="java.lang.String"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.UNEXPECTED_INTRINSIC, ex.getDiagnostic().getCode());
    }

    @Test
    public void Incompatible_Class_Parameters_Are_Invalid() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                      fx:classParameters="java.lang.String"/>
        """));

        assertEquals(ErrorCode.INTERNAL_ERROR, ex.getDiagnostic().getCode());
        assertEquals("compiler.err.cant.apply.symbol", ex.getDiagnostic().getMessage());
    }

    @SuppressWarnings("unused")
    public static class PaneWithParams extends Pane {
        public PaneWithParams(String param) {}
    }

    @Test
    public void Object_Is_Compiled_With_ClassParameters() {
        Class<?> clazz = compile("""
            <?import org.jfxcore.compiler.IntrinsicsTest.*?>
            <PaneWithParams xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                            fx:classParameters="java.lang.String"/>
        """);

        assertEquals(1, clazz.getConstructors().length);
        Constructor<?> ctor = clazz.getConstructors()[0];
        assertEquals(1, ctor.getParameterCount());
        assertEquals(String.class, ctor.getParameters()[0].getType());
    }

}

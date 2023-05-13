// Copyright (c) 2022, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class ConstantsTest extends CompilerTestBase {

    @Test
    public void Constant_Is_Referenced_With_FxConstant() {
        Button button = compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <minHeight><Double fx:constant="POSITIVE_INFINITY"/></minHeight>
            </Button>
        """);

        assertTrue(Double.isInfinite(button.getMinHeight()));
    }

    @Test
    public void Constant_Is_Referenced_With_InContext_FxConstant() {
        Button button = compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                    minHeight="{fx:constant POSITIVE_INFINITY}"/>
        """);

        assertTrue(Double.isInfinite(button.getMinHeight()));
    }

    @Test
    public void InContext_FxConstant_With_Invalid_Value() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                    minHeight="{fx:constant {fx:constant POSITIVE_INFINITY}}"/>
        """));

        assertEquals(ErrorCode.EXPECTED_IDENTIFIER, ex.getDiagnostic().getCode());
        assertCodeHighlight("{fx:constant POSITIVE_INFINITY}", ex);
    }

    @Test
    public void InContext_FxConstant_With_Empty_Value() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                    minHeight="{fx:constant}"/>
        """));

        assertEquals(ErrorCode.INVALID_EXPRESSION, ex.getDiagnostic().getCode());
        assertCodeHighlight("{fx:constant}", ex);
    }

    @Test
    public void FxConstant_And_Child_Content_Cannot_Be_Used_At_Same_Time() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <minHeight><Double fx:constant="NEGATIVE_INFINITY">5.0</Double></minHeight>
            </Button>
        """));

        assertEquals(ErrorCode.OBJECT_CANNOT_HAVE_CONTENT, ex.getDiagnostic().getCode());
        assertCodeHighlight("""
            <Double fx:constant="NEGATIVE_INFINITY">5.0</Double>
        """.trim(), ex);
    }

    @Test
    public void FxConstant_And_FxValue_Content_Cannot_Be_Used_At_Same_Time() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <minHeight><Double fx:constant="NEGATIVE_INFINITY" fx:value="5.0"/></minHeight>
            </Button>
        """));

        assertEquals(ErrorCode.CONFLICTING_PROPERTIES, ex.getDiagnostic().getCode());
        assertCodeHighlight("""
            fx:value="5.0"
        """.trim(), ex);
    }

    @Test
    public void FxConstant_Resolves_Fully_Qualified_Name() {
        TableView<?> tableView = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.util.Callback?>
            <TableView xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <columnResizePolicy>
                    <Callback fx:constant="javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY"/>
                </columnResizePolicy>
            </TableView>
        """);

        assertSame(tableView.getColumnResizePolicy(), TableView.CONSTRAINED_RESIZE_POLICY);
    }

    @Test
    public void Constant_With_Incompatible_Generic_Arguments_Cannot_Be_Assigned() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.util.Callback?>
            <TableView xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <columnResizePolicy>
                    <Callback fx:typeArguments="java.lang.String,java.lang.Double"
                              fx:constant="javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY"/>
                </columnResizePolicy>
            </TableView>
        """));

        assertEquals(ErrorCode.CANNOT_ASSIGN_CONSTANT, ex.getDiagnostic().getCode());
        assertCodeHighlight("""
            fx:constant="javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY"
        """.trim(), ex);
    }

}

// Copyright (c) 2022, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.scene.Node;
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
        Node root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <minHeight><Double fx:constant="POSITIVE_INFINITY"/></minHeight>
            </Button>
        """);

        assertFieldAccess(root, "java.lang.Double", "POSITIVE_INFINITY", "D");
    }

    @Test
    public void FxConstant_In_Element_Notation_Is_Invalid() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                    minHeight="{fx:constant}"/>
        """));

        assertEquals(ErrorCode.UNEXPECTED_INTRINSIC, ex.getDiagnostic().getCode());
        assertCodeHighlight("{fx:constant}", ex);
    }

    @Test
    public void FxConstant_With_Invalid_Value_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                    minHeight="{Double fx:constant={Double fx:constant=POSITIVE_INFINITY}}"/>
        """));

        assertEquals(ErrorCode.PROPERTY_MUST_CONTAIN_TEXT, ex.getDiagnostic().getCode());
        assertCodeHighlight("{Double fx:constant=POSITIVE_INFINITY}", ex);
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
    public void FxConstant_And_TypeArguments_Cannot_Be_Used_At_Same_Time() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.util.Callback?>
            <TableView xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <columnResizePolicy>
                    <TableView fx:typeArguments="java.lang.String,java.lang.Double"
                               fx:constant="CONSTRAINED_RESIZE_POLICY"/>
                </columnResizePolicy>
            </TableView>
        """));

        assertEquals(ErrorCode.CONFLICTING_PROPERTIES, ex.getDiagnostic().getCode());
        assertCodeHighlight("""
            fx:typeArguments="java.lang.String,java.lang.Double"
        """.trim(), ex);
    }

    @Test
    public void FxConstant_Resolves_Constant_In_ElementType() {
        TableView<?> tableView = compileAndRun("""
            <?import javafx.scene.control.*?>
            <TableView xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <columnResizePolicy>
                    <TableView fx:constant="CONSTRAINED_RESIZE_POLICY"/>
                </columnResizePolicy>
            </TableView>
        """);

        assertFieldAccess(
            tableView,
            "javafx.scene.control.TableView",
            "CONSTRAINED_RESIZE_POLICY",
            "Ljavafx/util/Callback;");
        assertSame(tableView.getColumnResizePolicy(), TableView.CONSTRAINED_RESIZE_POLICY);
    }

}

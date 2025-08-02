// Copyright (c) 2023, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

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
public class CompactSyntaxTest extends CompilerTestBase {

    @Test
    public void FxOnce_Compact_Syntax_Has_Correct_CodeHighlight() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                    prefHeight="$foo.bar"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo", ex);
    }

    @Test
    public void FxBind_Compact_Syntax_Has_Correct_CodeHighlight() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                    prefHeight="${foo.bar}"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo", ex);
    }

    @Test
    public void FxBindBidirectional_Compact_Syntax_Has_Correct_CodeHighlight() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                    prefHeight="#{foo.bar}"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo", ex);
    }

    @Test
    public void FxContent_Compact_Syntax_Has_Correct_CodeHighlight() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                    styleClass="$..foo.bar"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo", ex);
    }

    @Test
    public void FxContent_Compact_Syntax_WithPathProperty_Has_Correct_CodeHighlight() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                    styleClass="$path=..foo.bar"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo", ex);
    }

    @Test
    public void FxBindContent_Compact_Syntax_Has_Correct_CodeHighlight() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                    styleClass="${..foo.bar}"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo", ex);
    }

    @Test
    public void FxBindContentBidirectional_Compact_Syntax_Has_Correct_CodeHighlight() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                    styleClass="#{..foo.bar}"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo", ex);
    }

    @Test
    public void FxContent_Element_Syntax_With_Expansion_Operator_Has_Correct_CodeHighlight() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                    styleClass="{fx:content path=..foo.bar}"/>
        """));

        assertEquals(ErrorCode.UNEXPECTED_TOKEN, ex.getDiagnostic().getCode());
        assertCodeHighlight(".", ex);
    }

    @Test
    public void FxBindContent_Element_Syntax_With_Expansion_Operator_Has_Correct_CodeHighlight() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                    styleClass="{fx:bindContent path=..foo.bar}"/>
        """));

        assertEquals(ErrorCode.UNEXPECTED_TOKEN, ex.getDiagnostic().getCode());
        assertCodeHighlight(".", ex);
    }

    @Test
    public void FxBindContentBidirectional_Element_Syntax_With_Expansion_Operator_Has_Correct_CodeHighlight() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                    styleClass="{fx:bindContent path=..foo.bar}"/>
        """));

        assertEquals(ErrorCode.UNEXPECTED_TOKEN, ex.getDiagnostic().getCode());
        assertCodeHighlight(".", ex);
    }
}

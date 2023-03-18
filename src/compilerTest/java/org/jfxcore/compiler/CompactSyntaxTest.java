// Copyright (c) 2023, JFXcore. All rights reserved.
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
            <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                    prefHeight="$foo.bar"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo", ex);
    }

    @Test
    public void FxBind_Compact_Syntax_Has_Correct_CodeHighlight() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                    prefHeight="${foo.bar}"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo", ex);
    }

    @Test
    public void FxBindBidirectional_Compact_Syntax_Has_Correct_CodeHighlight() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                    prefHeight="#{foo.bar}"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo", ex);
    }

    @Test
    public void FxContent_Compact_Syntax_Has_Correct_CodeHighlight() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                    styleClass="[$]foo.bar"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo", ex);
    }

    @Test
    public void FxBindContent_Compact_Syntax_Has_Correct_CodeHighlight() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                    styleClass="[$]{foo.bar}"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo", ex);
    }

    @Test
    public void FxBindContentBidirectional_Compact_Syntax_Has_Correct_CodeHighlight() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Button xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                    styleClass="[#]{foo.bar}"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo", ex);
    }

}

// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.CompilationScope;
import org.jfxcore.compiler.util.CompilationSource;
import org.jfxcore.compiler.util.XmlEntityDecoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SourceMappedTextTest {

    @Test
    public void Identity_Input_Maps_Utf16_Ranges_From_Origin() {
        SourceMappedText input = SourceMappedText.identity("ab\uD83D\uDE00cd", new Location(3, 7));

        assertEquals(new SourceInfo(3, 7), input.getSourceInfo(0, 0));
        assertEquals(new SourceInfo(3, 9, 3, 10), input.getSourceInfo(2, 3));
        assertEquals(new SourceInfo(3, 10, 3, 11), input.getSourceInfo(3, 4));
        assertEquals(new SourceInfo(3, 13), input.getSourceInfo(6, 6));
        assertEquals(new SourceInfo(3, 13), input.getEndOfInput());
    }

    @Test
    public void Mapped_Input_Uses_Decoded_Locations_And_Projects_Entity_Ranges_Outward() {
        String raw = "ab&amp;cd&#x1F600;ef";
        SourceMappedText input = SourceMappedText.decodedXml(raw, new Location(4, 10), XmlEntityDecoder.decode(raw));

        assertEquals("ab&cd\uD83D\uDE00ef", input.getText());
        assertEquals(new SourceInfo(4, 12), input.getSourceInfo(2, 2));
        assertEquals(new SourceInfo(4, 12, 4, 13), input.getSourceInfo(2, 3));
        assertEquals(new SourceInfo(4, 13), input.getSourceInfo(3, 3));
        assertEquals(new SourceInfo(4, 15, 4, 16), input.getSourceInfo(5, 6));
        assertEquals(new SourceInfo(4, 16, 4, 17), input.getSourceInfo(6, 7));
        assertEquals(new SourceInfo(4, 16), input.getSourceInfo(6, 6));
        assertEquals(new SourceInfo(4, 17), input.getSourceInfo(7, 7));
        assertEquals(new SourceInfo(4, 11, 4, 18), input.getSourceInfo(1, 8));
        assertEquals(new SourceInfo(4, 19), input.getEndOfInput());

        assertEquals(new SourceInfo(4, 12), input.getSourceInfo(2, 2).toOriginal());
        assertEquals(new SourceInfo(4, 12, 4, 17), input.getSourceInfo(2, 3).toOriginal());
        assertEquals(new SourceInfo(4, 17), input.getSourceInfo(3, 3).toOriginal());
        assertEquals(new SourceInfo(4, 19, 4, 28), input.getSourceInfo(5, 6).toOriginal());
        assertEquals(new SourceInfo(4, 19, 4, 28), input.getSourceInfo(6, 7).toOriginal());
        assertEquals(new SourceInfo(4, 19), input.getSourceInfo(6, 6).toOriginal());
        assertEquals(new SourceInfo(4, 28), input.getSourceInfo(7, 7).toOriginal());
        assertEquals(new SourceInfo(4, 11, 4, 29), input.getSourceInfo(1, 8).toOriginal());
        assertEquals(new SourceInfo(4, 30), input.getEndOfInput().toOriginal());
    }

    @Test
    public void Decoded_Newline_Changes_Logical_Line_But_Projects_To_Raw_Line() {
        String raw = "a&#10;b";
        SourceMappedText input = SourceMappedText.decodedXml(raw, new Location(2, 3), XmlEntityDecoder.decode(raw));

        assertEquals("a\nb", input.getText());
        assertEquals(new SourceInfo(2, 4, 3, 0), input.getSourceInfo(1, 2));
        assertEquals(new SourceInfo(3, 0, 3, 1), input.getSourceInfo(2, 3));
        assertEquals(new SourceInfo(2, 4, 2, 9), input.getSourceInfo(1, 2).toOriginal());
        assertEquals(new SourceInfo(2, 9, 2, 10), input.getSourceInfo(2, 3).toOriginal());
    }

    @Test
    public void Identity_Input_Maps_Crlf_Boundaries_Once() {
        SourceMappedText input = SourceMappedText.identity("a\r\nb", new Location(5, 4));

        assertEquals(new SourceInfo(5, 4), input.getSourceInfo(0, 0));
        assertEquals(new SourceInfo(5, 5), input.getSourceInfo(1, 1));
        assertEquals(new SourceInfo(6, 0), input.getSourceInfo(2, 2));
        assertEquals(new SourceInfo(6, 0), input.getSourceInfo(3, 3));
        assertEquals(new SourceInfo(6, 1), input.getSourceInfo(4, 4));
    }

    @Test
    public void Identity_Input_Recognizes_All_Supported_Line_Separators() {
        for (String separator : new String[] {"\n", "\r", "\u000B", "\u000C", "\u0085", "\u2028", "\u2029"}) {
            SourceMappedText input = SourceMappedText.identity("a" + separator + "b", new Location(8, 2));
            int b = 1 + separator.length();
            assertEquals(new SourceInfo(9, 0, 9, 1), input.getSourceInfo(b, b + 1), separator);
        }
    }

    @Test
    public void Empty_And_Newline_Terminated_Input_Have_Deterministic_Eof() {
        assertEquals(new SourceInfo(7, 9), SourceMappedText.identity("", new Location(7, 9)).getEndOfInput());
        assertEquals(new SourceInfo(8, 0), SourceMappedText.identity("x\n", new Location(7, 9)).getEndOfInput());
    }

    @Test
    public void Invalid_Ranges_Fail_Fast() {
        SourceMappedText input = SourceMappedText.identity("abc", new Location(0, 0));

        assertThrows(IndexOutOfBoundsException.class, () -> input.getSourceInfo(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> input.getSourceInfo(0, 4));
        assertThrows(IllegalArgumentException.class, () -> input.getSourceInfo(2, 1));
    }

    @Test
    public void Mapped_SourceInfo_Text_Is_Decoded_And_Original_Text_Contains_Entity() {
        String source = "prefix a&amp;b suffix";
        var context = new CompilationContext(new CompilationSource.InMemory(source));

        try (var ignored = new CompilationScope(context)) {
            String raw = "a&amp;b";
            SourceMappedText input = SourceMappedText.decodedXml(raw, new Location(0, 7), XmlEntityDecoder.decode(raw));

            SourceInfo sourceInfo = input.getSourceInfo(1, 2);
            assertEquals("&", sourceInfo.getText());
            assertEquals("&amp;", sourceInfo.toOriginal().getText());
        }
    }

    @Test
    public void Span_Of_Different_Transformed_Views_Uses_Original_Source() {
        String source = "left=a&amp;b; right=c&#100;d";
        var context = new CompilationContext(new CompilationSource.InMemory(source));

        try (var ignored = new CompilationScope(context)) {
            String firstRaw = "a&amp;b";
            int firstOrigin = source.indexOf(firstRaw);
            SourceMappedText first = SourceMappedText.decodedXml(
                firstRaw, new Location(0, firstOrigin), XmlEntityDecoder.decode(firstRaw));

            String secondRaw = "c&#100;d";
            int secondOrigin = source.indexOf(secondRaw);
            SourceMappedText second = SourceMappedText.decodedXml(
                secondRaw, new Location(0, secondOrigin), XmlEntityDecoder.decode(secondRaw));

            SourceInfo span = SourceInfo.span(first.getSourceInfo(1, 2), second.getSourceInfo(1, 2));

            assertEquals(new SourceInfo(0, firstOrigin + 1, 0, secondOrigin + 7), span);
            assertEquals("&amp;b; right=c&#100;", span.getText());
            assertSame(context.getSourceInfoSource(), span.getSource());
            assertEquals(span, span.toOriginal());
        }
    }

    @Test
    public void Removed_Escape_Uses_Logical_Text_And_Projects_Onto_Retained_Source() {
        String source = "prefix \\${foo} suffix";
        var context = new CompilationContext(new CompilationSource.InMemory(source));

        try (var ignored = new CompilationScope(context)) {
            SourceMappedText input = SourceMappedText.identity("\\${foo}", new Location(0, 7)).without(0);
            SourceInfo sourceInfo = input.getSourceInfo(0, input.getText().length());

            assertEquals("${foo}", sourceInfo.getText());
            assertEquals(new SourceInfo(0, 7, 0, 13), sourceInfo);
            assertEquals(new SourceInfo(0, 8, 0, 14), sourceInfo.toOriginal());
            assertEquals("${foo}", sourceInfo.toOriginal().getText());
        }
    }

    @Test
    public void Trimming_Uses_Decoded_Whitespace() {
        String source = "prefix &#32;Foo&#32; suffix";
        var context = new CompilationContext(new CompilationSource.InMemory(source));

        try (var ignored = new CompilationScope(context)) {
            String raw = "&#32;Foo&#32;";
            SourceMappedText input = SourceMappedText.decodedXml(raw, new Location(0, 7), XmlEntityDecoder.decode(raw));
            SourceInfo trimmed = input.getSourceInfo(0, input.getText().length()).getTrimmed();

            assertEquals(new SourceInfo(0, 8, 0, 11), trimmed);
            assertEquals("Foo", trimmed.getText());
            assertEquals(new SourceInfo(0, 12, 0, 15), trimmed.toOriginal());
        }
    }

    @Test
    public void Formatted_Error_Projects_To_Raw_Entity_After_Compilation_Scope_Closes() {
        String source = "prefix a&amp;b suffix";
        var context = new CompilationContext(new CompilationSource.InMemory(source));
        MarkupException exception;

        try (var ignored = new CompilationScope(context)) {
            String raw = "a&amp;b";
            SourceMappedText input = SourceMappedText.decodedXml(raw, new Location(0, 7), XmlEntityDecoder.decode(raw));
            exception = new MarkupException(
                input.getSourceInfo(1, 2),
                Diagnostic.newDiagnostic(ErrorCode.INVALID_EXPRESSION));
        }

        assertEquals("&", exception.getSourceInfo().getText());
        assertEquals("&amp;", exception.getOriginalSourceInfo().getText());
        assertTrue(exception.getMessageWithSourceInfo().endsWith(
            source + System.lineSeparator() + " ".repeat(8) + "^^^^^"));
    }

    @Test
    public void Derived_Type_Parser_Retains_Decoded_Source_View() {
        String raw = "java.lang.String&#35;Foo";
        String source = "prefix " + raw + " suffix";
        var context = new CompilationContext(new CompilationSource.InMemory(source));

        try (var ignored = new CompilationScope(context)) {
            SourceMappedText input = SourceMappedText.decodedXml(
                raw, new Location(0, 7), XmlEntityDecoder.decode(raw));
            MarkupException exception = assertThrows(
                MarkupException.class,
                () -> new TypeParser(
                    input.getText(), input.getSourceInfo(0, input.getText().length())).parse());

            assertEquals(new SourceInfo(0, 23, 0, 27), exception.getSourceInfo());
            assertEquals(new SourceInfo(0, 23, 0, 31), exception.getOriginalSourceInfo());
            assertEquals("#Foo", exception.getSourceInfo().getText());
            assertEquals("&#35;Foo", exception.getOriginalSourceInfo().getText());
        }
    }
}

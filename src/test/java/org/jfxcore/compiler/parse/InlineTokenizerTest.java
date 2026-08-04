// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.XmlEntityDecoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
public class InlineTokenizerTest {

    @Test
    public void Mark_And_Reset() {
        var tokenizer = new InlineTokenizer("{foo bar=baz}", new Location(-1, -1));
        tokenizer.mark();
        tokenizer.remove(CurlyTokenType.OPEN_CURLY);
        tokenizer.remove(CurlyTokenType.IDENTIFIER);
        tokenizer.remove(CurlyTokenType.IDENTIFIER);
        tokenizer.resetToMark();

        assertEquals(6, tokenizer.size());
        assertEquals(CurlyTokenType.OPEN_CURLY, tokenizer.remove().getType());
        assertEquals(CurlyTokenType.IDENTIFIER, tokenizer.remove().getType());
        assertEquals(CurlyTokenType.IDENTIFIER, tokenizer.remove().getType());
        assertEquals(CurlyTokenType.EQUALS, tokenizer.remove().getType());
        assertEquals(CurlyTokenType.IDENTIFIER, tokenizer.remove().getType());
        assertEquals(CurlyTokenType.CLOSE_CURLY, tokenizer.remove().getType());
    }

    @Test
    public void Mark_And_Reset_Recursive() {
        var tokenizer = new InlineTokenizer("{foo bar=baz}", new Location(-1, -1));
        tokenizer.mark();
        tokenizer.remove(CurlyTokenType.OPEN_CURLY);
        tokenizer.remove(CurlyTokenType.IDENTIFIER);
        tokenizer.remove(CurlyTokenType.IDENTIFIER);

        tokenizer.mark();
        tokenizer.remove(CurlyTokenType.EQUALS);
        tokenizer.remove(CurlyTokenType.IDENTIFIER);

        assertEquals(1, tokenizer.size());
        assertEquals(CurlyTokenType.CLOSE_CURLY, tokenizer.peekNotNull().getType());

        tokenizer.resetToMark();
        assertEquals(3, tokenizer.size());

        tokenizer.resetToMark();
        assertEquals(6, tokenizer.size());
        assertEquals(CurlyTokenType.OPEN_CURLY, tokenizer.remove().getType());
        assertEquals(CurlyTokenType.IDENTIFIER, tokenizer.remove().getType());
        assertEquals(CurlyTokenType.IDENTIFIER, tokenizer.remove().getType());
        assertEquals(CurlyTokenType.EQUALS, tokenizer.remove().getType());
        assertEquals(CurlyTokenType.IDENTIFIER, tokenizer.remove().getType());
        assertEquals(CurlyTokenType.CLOSE_CURLY, tokenizer.remove().getType());
    }

    @Test
    public void Mark_And_Forget_Recursive() {
        var tokenizer = new InlineTokenizer("{foo bar=baz}", new Location(-1, -1));
        tokenizer.mark();
        tokenizer.remove(CurlyTokenType.OPEN_CURLY);
        tokenizer.remove(CurlyTokenType.IDENTIFIER);
        tokenizer.remove(CurlyTokenType.IDENTIFIER);

        tokenizer.mark();
        tokenizer.remove(CurlyTokenType.EQUALS);
        tokenizer.remove(CurlyTokenType.IDENTIFIER);

        assertEquals(1, tokenizer.size());
        assertEquals(CurlyTokenType.CLOSE_CURLY, tokenizer.peekNotNull().getType());

        tokenizer.forgetMark();
        assertEquals(1, tokenizer.size());

        tokenizer.resetToMark();
        assertEquals(6, tokenizer.size());
        assertEquals(CurlyTokenType.OPEN_CURLY, tokenizer.remove().getType());
        assertEquals(CurlyTokenType.IDENTIFIER, tokenizer.remove().getType());
        assertEquals(CurlyTokenType.IDENTIFIER, tokenizer.remove().getType());
        assertEquals(CurlyTokenType.EQUALS, tokenizer.remove().getType());
        assertEquals(CurlyTokenType.IDENTIFIER, tokenizer.remove().getType());
        assertEquals(CurlyTokenType.CLOSE_CURLY, tokenizer.remove().getType());
    }

    @Test
    public void Tokenize_Additional_Symbols_As_Unknown() {
        var tokenizer = new InlineTokenizer("{@%&^°§?~}", new Location(-1, -1));

        assertEquals(10, tokenizer.size());
        assertToken(tokenizer.remove(), CurlyTokenType.OPEN_CURLY, "{");
        assertToken(tokenizer.remove(), CurlyTokenType.UNKNOWN, "@");
        assertToken(tokenizer.remove(), CurlyTokenType.UNKNOWN, "%");
        assertToken(tokenizer.remove(), CurlyTokenType.UNKNOWN, "&");
        assertToken(tokenizer.remove(), CurlyTokenType.UNKNOWN, "^");
        assertToken(tokenizer.remove(), CurlyTokenType.UNKNOWN, "°");
        assertToken(tokenizer.remove(), CurlyTokenType.UNKNOWN, "§");
        assertToken(tokenizer.remove(), CurlyTokenType.UNKNOWN, "?");
        assertToken(tokenizer.remove(), CurlyTokenType.UNKNOWN, "~");
        assertToken(tokenizer.remove(), CurlyTokenType.CLOSE_CURLY, "}");
        assertTrue(tokenizer.isEmpty());
    }

    @Test
    public void Mapped_Input_Uses_Decoded_Locations_And_Raw_Projection() {
        String raw = "fo&#111;&#111;";
        var input = LexerInput.decodedXml(raw, new Location(2, 5), XmlEntityDecoder.decode(raw));
        var tokenizer = new InlineTokenizer(input);

        InlineToken token = tokenizer.remove(CurlyTokenType.IDENTIFIER);
        assertEquals("fooo", token.getValue());
        assertEquals(new SourceInfo(2, 5, 2, 9), token.getSourceInfo());
        assertEquals(new SourceInfo(2, 5, 2, 19), token.getSourceInfo().toOriginal());
    }

    @Test
    public void Namespace_Concatenation_Retains_Logical_Span_And_Mapped_Envelope() {
        String raw = "fx&#58;foo";
        var input = LexerInput.decodedXml(raw, new Location(1, 3), XmlEntityDecoder.decode(raw));
        var tokenizer = new InlineTokenizer(input);

        InlineToken token = tokenizer.remove(CurlyTokenType.IDENTIFIER);
        assertEquals("fx:foo", token.getValue());
        assertEquals(new SourceInfo(1, 3, 1, 9), token.getSourceInfo());
        assertEquals(new SourceInfo(1, 3, 1, 13), token.getSourceInfo().toOriginal());
    }

    @Test
    public void Decoded_Newline_Keeps_Decoded_Line_Text_And_Projects_Raw_Source_Location() {
        String raw = "foo&#10;bar";
        var input = LexerInput.decodedXml(raw, new Location(4, 7), XmlEntityDecoder.decode(raw));
        var tokenizer = new InlineTokenizer(input);

        InlineToken foo = tokenizer.remove();
        InlineToken newline = tokenizer.remove();
        assertEquals(new SourceInfo(4, 7, 4, 10), foo.getSourceInfo());
        assertEquals(new SourceInfo(4, 10, 5, 0), newline.getSourceInfo());
        assertEquals(new SourceInfo(4, 10, 4, 15), newline.getSourceInfo().toOriginal());
        InlineToken bar = tokenizer.remove();
        assertEquals("bar", bar.getValue());
        assertEquals("bar", bar.getLine());
        assertEquals(new SourceInfo(5, 0, 5, 3), bar.getSourceInfo());
        assertEquals(new SourceInfo(4, 15, 4, 18), bar.getSourceInfo().toOriginal());
    }

    @Test
    public void Source_Text_Uses_Logical_Lines_At_Nonzero_Origin() {
        var tokenizer = new InlineTokenizer("foo\nbar", new Location(4, 7));
        InlineToken foo = tokenizer.remove();
        tokenizer.remove(CurlyTokenType.NEWLINE);
        InlineToken bar = tokenizer.remove();

        assertEquals(" ".repeat(7) + "foo\n", tokenizer.getSourceText(foo.getSourceInfo()));
        assertEquals("bar\n", tokenizer.getSourceText(bar.getSourceInfo()));
    }

    @Test
    public void Exhaustion_Uses_Complete_Input_End() {
        for (String suffix : new String[] {"   ", "/* comment */", "// comment", ";"}) {
            var tokenizer = new InlineTokenizer("foo" + suffix, new Location(3, 2));
            tokenizer.remove(CurlyTokenType.IDENTIFIER);

            MarkupException exception = assertThrows(MarkupException.class, tokenizer::peekNotNull);
            assertEquals(new SourceInfo(3, 5 + suffix.length()), exception.getSourceInfo());
        }

        var newlineTokenizer = new InlineTokenizer("foo\n", new Location(3, 2));
        newlineTokenizer.remove(CurlyTokenType.IDENTIFIER);
        MarkupException exception = assertThrows(MarkupException.class, newlineTokenizer::remove);
        assertEquals(new SourceInfo(4, 0), exception.getSourceInfo());
    }

    @Test
    public void Expected_Token_Exhaustion_Uses_Complete_Input_End() {
        var tokenizer = new InlineTokenizer("foo /* comment */", new Location(2, 6));
        tokenizer.remove(CurlyTokenType.IDENTIFIER);

        MarkupException exception = assertThrows(
            MarkupException.class, () -> tokenizer.remove(CurlyTokenType.CLOSE_CURLY));
        assertEquals(new SourceInfo(2, 23), exception.getSourceInfo());
    }

    @Test
    public void Tokenizer_Empty_Input_Reports_Complete_Input_End() {
        MarkupException empty = assertThrows(MarkupException.class,
            () -> new InlineTokenizer("", new Location(6, 4)));
        assertEquals(new SourceInfo(6, 4), empty.getSourceInfo());

        MarkupException whitespace = assertThrows(MarkupException.class,
            () -> new InlineTokenizer("  \n", new Location(6, 4)));
        assertEquals(new SourceInfo(7, 0), whitespace.getSourceInfo());

        MarkupException comment = assertThrows(MarkupException.class,
            () -> new InlineTokenizer("/* comment */", new Location(6, 4)));

        assertEquals(new SourceInfo(6, 17), comment.getSourceInfo());
    }

    private void assertToken(InlineToken token, CurlyTokenType type, String value) {
        assertEquals(type, token.getType());
        assertEquals(value, token.getValue());
    }
}

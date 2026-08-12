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
        var tokenizer = new InlineTokenizer("{@%&|^°§?~}", new Location(-1, -1));

        assertEquals(11, tokenizer.size());
        assertToken(tokenizer.remove(), CurlyTokenType.OPEN_CURLY, "{");
        assertToken(tokenizer.remove(), CurlyTokenType.UNKNOWN, "@");
        assertToken(tokenizer.remove(), CurlyTokenType.UNKNOWN, "%");
        assertToken(tokenizer.remove(), CurlyTokenType.UNKNOWN, "&");
        assertToken(tokenizer.remove(), CurlyTokenType.UNKNOWN, "|");
        assertToken(tokenizer.remove(), CurlyTokenType.UNKNOWN, "^");
        assertToken(tokenizer.remove(), CurlyTokenType.UNKNOWN, "°");
        assertToken(tokenizer.remove(), CurlyTokenType.UNKNOWN, "§");
        assertToken(tokenizer.remove(), CurlyTokenType.UNKNOWN, "?");
        assertToken(tokenizer.remove(), CurlyTokenType.UNKNOWN, "~");
        assertToken(tokenizer.remove(), CurlyTokenType.CLOSE_CURLY, "}");
        assertTrue(tokenizer.isEmpty());
    }

    @Test
    public void Complete_Operators_Use_Maximal_Munch() {
        assertTokens(
            "a<=b>=c==d!=e===f!==g&&h||i",
            token(CurlyTokenType.IDENTIFIER, "a"),
            token(CurlyTokenType.LESS_THAN_OR_EQUAL, "<="),
            token(CurlyTokenType.IDENTIFIER, "b"),
            token(CurlyTokenType.GREATER_THAN_OR_EQUAL, ">="),
            token(CurlyTokenType.IDENTIFIER, "c"),
            token(CurlyTokenType.VALUE_EQUALITY, "=="),
            token(CurlyTokenType.IDENTIFIER, "d"),
            token(CurlyTokenType.VALUE_INEQUALITY, "!="),
            token(CurlyTokenType.IDENTIFIER, "e"),
            token(CurlyTokenType.IDENTITY_EQUALITY, "==="),
            token(CurlyTokenType.IDENTIFIER, "f"),
            token(CurlyTokenType.IDENTITY_INEQUALITY, "!=="),
            token(CurlyTokenType.IDENTIFIER, "g"),
            token(CurlyTokenType.LOGICAL_AND, "&&"),
            token(CurlyTokenType.IDENTIFIER, "h"),
            token(CurlyTokenType.LOGICAL_OR, "||"),
            token(CurlyTokenType.IDENTIFIER, "i"));

        assertTokens(
            "!!a!=b !c",
            token(CurlyTokenType.BOOLIFY, "!!"),
            token(CurlyTokenType.IDENTIFIER, "a"),
            token(CurlyTokenType.VALUE_INEQUALITY, "!="),
            token(CurlyTokenType.IDENTIFIER, "b"),
            token(CurlyTokenType.NOT, "!"),
            token(CurlyTokenType.IDENTIFIER, "c"));

        assertTokens(
            "a====b&&&c| |d",
            token(CurlyTokenType.IDENTIFIER, "a"),
            token(CurlyTokenType.IDENTITY_EQUALITY, "==="),
            token(CurlyTokenType.EQUALS, "="),
            token(CurlyTokenType.IDENTIFIER, "b"),
            token(CurlyTokenType.LOGICAL_AND, "&&"),
            token(CurlyTokenType.UNKNOWN, "&"),
            token(CurlyTokenType.IDENTIFIER, "c"),
            token(CurlyTokenType.UNKNOWN, "|"),
            token(CurlyTokenType.UNKNOWN, "|"),
            token(CurlyTokenType.IDENTIFIER, "d"));
    }

    @Test
    public void Compact_Generic_And_Construction_Adjacency_Is_Unambiguous() {
        assertTokens(
            "model.<T>m()==x",
            token(CurlyTokenType.IDENTIFIER, "model"),
            token(CurlyTokenType.DOT, "."),
            token(CurlyTokenType.OPEN_ANGLE, "<"),
            token(CurlyTokenType.IDENTIFIER, "T"),
            token(CurlyTokenType.CLOSE_ANGLE, ">"),
            token(CurlyTokenType.IDENTIFIER, "m"),
            token(CurlyTokenType.OPEN_PAREN, "("),
            token(CurlyTokenType.CLOSE_PAREN, ")"),
            token(CurlyTokenType.VALUE_EQUALITY, "=="),
            token(CurlyTokenType.IDENTIFIER, "x"));

        assertTokens(
            "new<W>Type<T>(x)==y",
            token(CurlyTokenType.KEYWORD, "new"),
            token(CurlyTokenType.OPEN_ANGLE, "<"),
            token(CurlyTokenType.IDENTIFIER, "W"),
            token(CurlyTokenType.CLOSE_ANGLE, ">"),
            token(CurlyTokenType.IDENTIFIER, "Type"),
            token(CurlyTokenType.OPEN_ANGLE, "<"),
            token(CurlyTokenType.IDENTIFIER, "T"),
            token(CurlyTokenType.CLOSE_ANGLE, ">"),
            token(CurlyTokenType.OPEN_PAREN, "("),
            token(CurlyTokenType.IDENTIFIER, "x"),
            token(CurlyTokenType.CLOSE_PAREN, ")"),
            token(CurlyTokenType.VALUE_EQUALITY, "=="),
            token(CurlyTokenType.IDENTIFIER, "y"));

        assertTokens(
            "outer.new<W>Inner<T>(x)==y",
            token(CurlyTokenType.IDENTIFIER, "outer"),
            token(CurlyTokenType.DOT, "."),
            token(CurlyTokenType.KEYWORD, "new"),
            token(CurlyTokenType.OPEN_ANGLE, "<"),
            token(CurlyTokenType.IDENTIFIER, "W"),
            token(CurlyTokenType.CLOSE_ANGLE, ">"),
            token(CurlyTokenType.IDENTIFIER, "Inner"),
            token(CurlyTokenType.OPEN_ANGLE, "<"),
            token(CurlyTokenType.IDENTIFIER, "T"),
            token(CurlyTokenType.CLOSE_ANGLE, ">"),
            token(CurlyTokenType.OPEN_PAREN, "("),
            token(CurlyTokenType.IDENTIFIER, "x"),
            token(CurlyTokenType.CLOSE_PAREN, ")"),
            token(CurlyTokenType.VALUE_EQUALITY, "=="),
            token(CurlyTokenType.IDENTIFIER, "y"));
    }

    @Test
    public void Infix_Operators_Continue_Across_Newlines_On_Both_Sides() {
        ExpectedToken[] operators = {
            token(CurlyTokenType.PLUS, "+"),
            token(CurlyTokenType.MINUS, "-"),
            token(CurlyTokenType.STAR, "*"),
            token(CurlyTokenType.SLASH, "/"),
            token(CurlyTokenType.OPEN_ANGLE, "<"),
            token(CurlyTokenType.LESS_THAN_OR_EQUAL, "<="),
            token(CurlyTokenType.CLOSE_ANGLE, ">"),
            token(CurlyTokenType.GREATER_THAN_OR_EQUAL, ">="),
            token(CurlyTokenType.VALUE_EQUALITY, "=="),
            token(CurlyTokenType.VALUE_INEQUALITY, "!="),
            token(CurlyTokenType.IDENTITY_EQUALITY, "==="),
            token(CurlyTokenType.IDENTITY_INEQUALITY, "!=="),
            token(CurlyTokenType.LOGICAL_AND, "&&"),
            token(CurlyTokenType.LOGICAL_OR, "||")
        };

        for (ExpectedToken operator : operators) {
            ExpectedToken left = token(CurlyTokenType.IDENTIFIER, "left");
            ExpectedToken right = token(CurlyTokenType.IDENTIFIER, "right");
            assertTokens("left " + operator.value() + " right", left, operator, right);
            assertTokens("left\n" + operator.value() + "\nright", left, operator, right);
        }
    }

    @Test
    public void Unary_Operators_Continue_Across_A_Following_Newline() {
        assertTokens(
            "!\nvalue !!\nother",
            token(CurlyTokenType.NOT, "!"),
            token(CurlyTokenType.IDENTIFIER, "value"),
            token(CurlyTokenType.BOOLIFY, "!!"),
            token(CurlyTokenType.IDENTIFIER, "other"));
    }

    @Test
    public void Namespace_Context_And_Colon_Boundaries_Remain_Positional() {
        assertTokens(
            "fx:name :parent<Pane>(1) this::foo :foo ::foo $:parent",
            token(CurlyTokenType.IDENTIFIER, "fx"),
            token(CurlyTokenType.COLON, ":"),
            token(CurlyTokenType.IDENTIFIER, "name"),
            token(CurlyTokenType.COLON, ":"),
            token(CurlyTokenType.IDENTIFIER, "parent"),
            token(CurlyTokenType.OPEN_ANGLE, "<"),
            token(CurlyTokenType.IDENTIFIER, "Pane"),
            token(CurlyTokenType.CLOSE_ANGLE, ">"),
            token(CurlyTokenType.OPEN_PAREN, "("),
            token(CurlyTokenType.NUMBER, "1"),
            token(CurlyTokenType.CLOSE_PAREN, ")"),
            token(CurlyTokenType.IDENTIFIER, "this"),
            token(CurlyTokenType.COLON, ":"),
            token(CurlyTokenType.COLON, ":"),
            token(CurlyTokenType.IDENTIFIER, "foo"),
            token(CurlyTokenType.COLON, ":"),
            token(CurlyTokenType.IDENTIFIER, "foo"),
            token(CurlyTokenType.COLON, ":"),
            token(CurlyTokenType.COLON, ":"),
            token(CurlyTokenType.IDENTIFIER, "foo"),
            token(CurlyTokenType.IDENTIFIER, "$"),
            token(CurlyTokenType.COLON, ":"),
            token(CurlyTokenType.IDENTIFIER, "parent"));
    }

    @Test
    public void Operators_In_Strings_And_Comments_Are_Not_Tokenized() {
        assertTokens(
            "\"a!==b\" 'c&&d' left /* !== && */ == right // ||\n!= end",
            token(CurlyTokenType.STRING, "a!==b"),
            token(CurlyTokenType.STRING, "c&&d"),
            token(CurlyTokenType.IDENTIFIER, "left"),
            token(CurlyTokenType.VALUE_EQUALITY, "=="),
            token(CurlyTokenType.IDENTIFIER, "right"),
            token(CurlyTokenType.VALUE_INEQUALITY, "!="),
            token(CurlyTokenType.IDENTIFIER, "end"));
    }

    @Test
    public void Mapped_Input_Uses_Decoded_Locations_And_Raw_Projection() {
        String raw = "fo&#111;&#111;";
        var input = SourceMappedText.decodedXml(raw, new Location(2, 5), XmlEntityDecoder.decode(raw));
        var tokenizer = new InlineTokenizer(input);

        InlineToken token = tokenizer.remove(CurlyTokenType.IDENTIFIER);
        assertEquals("fooo", token.getValue());
        assertEquals(new SourceInfo(2, 5, 2, 9), token.getSourceInfo());
        assertEquals(new SourceInfo(2, 5, 2, 19), token.getSourceInfo().toOriginal());
    }

    @Test
    public void Mapped_Sign_Normalization_Retains_Logical_And_Original_Spans() {
        String raw = "a&#45;1";
        var input = SourceMappedText.decodedXml(raw, new Location(2, 5), XmlEntityDecoder.decode(raw));
        var tokenizer = new InlineTokenizer(input);

        assertToken(tokenizer.remove(), CurlyTokenType.IDENTIFIER, "a");

        InlineToken sign = tokenizer.remove();
        assertToken(sign, CurlyTokenType.MINUS, "-");
        assertEquals(new SourceInfo(2, 6, 2, 7), sign.getSourceInfo());
        assertEquals(new SourceInfo(2, 6, 2, 11), sign.getSourceInfo().toOriginal());

        InlineToken number = tokenizer.remove();
        assertToken(number, CurlyTokenType.NUMBER, "1");
        assertEquals(new SourceInfo(2, 7, 2, 8), number.getSourceInfo());
        assertEquals(new SourceInfo(2, 11, 2, 12), number.getSourceInfo().toOriginal());
    }

    @Test
    public void Both_Entry_Points_Use_The_Same_Normalization_Sequence() {
        String source = "a-1 fx:name b+-2";
        Location origin = new Location(3, 4);
        var stringTokenizer = new InlineTokenizer(source, origin);
        var mappedTokenizer = new InlineTokenizer(SourceMappedText.identity(source, origin));

        assertEquals(stringTokenizer.size(), mappedTokenizer.size());
        while (!stringTokenizer.isEmpty()) {
            InlineToken stringToken = stringTokenizer.remove();
            InlineToken mappedToken = mappedTokenizer.remove();
            assertEquals(stringToken.getType(), mappedToken.getType());
            assertEquals(stringToken.getValue(), mappedToken.getValue());
            assertEquals(stringToken.getLexeme(), mappedToken.getLexeme());
            assertEquals(stringToken.getSourceInfo(), mappedToken.getSourceInfo());
            assertEquals(stringToken.getSourceInfo().toOriginal(), mappedToken.getSourceInfo().toOriginal());
        }
    }

    @Test
    public void Xml_Entity_Operators_Retain_Logical_And_Original_Spans() {
        String relationalRaw = "a&lt;b";
        var relationalInput = SourceMappedText.decodedXml(
            relationalRaw, new Location(3, 7), XmlEntityDecoder.decode(relationalRaw));
        var relationalTokenizer = new InlineTokenizer(relationalInput);

        relationalTokenizer.remove(CurlyTokenType.IDENTIFIER);
        InlineToken lessThan = relationalTokenizer.remove(CurlyTokenType.OPEN_ANGLE);
        assertEquals(new SourceInfo(3, 8, 3, 9), lessThan.getSourceInfo());
        assertEquals(new SourceInfo(3, 8, 3, 12), lessThan.getSourceInfo().toOriginal());

        String raw = "a&lt;=b&amp;&amp;c";
        var input = SourceMappedText.decodedXml(raw, new Location(2, 5), XmlEntityDecoder.decode(raw));
        var tokenizer = new InlineTokenizer(input);

        tokenizer.remove(CurlyTokenType.IDENTIFIER);
        InlineToken lessThanOrEqual = tokenizer.remove(CurlyTokenType.LESS_THAN_OR_EQUAL);
        assertEquals(new SourceInfo(2, 6, 2, 8), lessThanOrEqual.getSourceInfo());
        assertEquals(new SourceInfo(2, 6, 2, 11), lessThanOrEqual.getSourceInfo().toOriginal());

        tokenizer.remove(CurlyTokenType.IDENTIFIER);
        InlineToken logicalAnd = tokenizer.remove(CurlyTokenType.LOGICAL_AND);
        assertEquals(new SourceInfo(2, 9, 2, 11), logicalAnd.getSourceInfo());
        assertEquals(new SourceInfo(2, 12, 2, 22), logicalAnd.getSourceInfo().toOriginal());

        tokenizer.remove(CurlyTokenType.IDENTIFIER);
        assertTrue(tokenizer.isEmpty());
    }

    @Test
    public void Xml_Entity_Generic_Angles_Retain_Logical_And_Original_Spans() {
        String raw = "model.&lt;T&gt;m()==x";
        var input = SourceMappedText.decodedXml(raw, new Location(1, 3), XmlEntityDecoder.decode(raw));
        var tokenizer = new InlineTokenizer(input);

        tokenizer.remove(CurlyTokenType.IDENTIFIER);
        tokenizer.remove(CurlyTokenType.DOT);
        InlineToken openAngle = tokenizer.remove(CurlyTokenType.OPEN_ANGLE);
        assertEquals(new SourceInfo(1, 9, 1, 10), openAngle.getSourceInfo());
        assertEquals(new SourceInfo(1, 9, 1, 13), openAngle.getSourceInfo().toOriginal());

        tokenizer.remove(CurlyTokenType.IDENTIFIER);
        InlineToken closeAngle = tokenizer.remove(CurlyTokenType.CLOSE_ANGLE);
        assertEquals(new SourceInfo(1, 11, 1, 12), closeAngle.getSourceInfo());
        assertEquals(new SourceInfo(1, 14, 1, 18), closeAngle.getSourceInfo().toOriginal());

        tokenizer.remove(CurlyTokenType.IDENTIFIER);
        tokenizer.remove(CurlyTokenType.OPEN_PAREN);
        tokenizer.remove(CurlyTokenType.CLOSE_PAREN);
        InlineToken equality = tokenizer.remove(CurlyTokenType.VALUE_EQUALITY);
        assertEquals(new SourceInfo(1, 15, 1, 17), equality.getSourceInfo());
        assertEquals(new SourceInfo(1, 21, 1, 23), equality.getSourceInfo().toOriginal());
    }

    @Test
    public void Namespace_Tokens_Retain_Logical_Spans_And_Mapped_Envelopes() {
        String raw = "fx&#58;foo";
        var input = SourceMappedText.decodedXml(raw, new Location(1, 3), XmlEntityDecoder.decode(raw));
        var tokenizer = new InlineTokenizer(input);

        InlineToken namespace = tokenizer.remove(CurlyTokenType.IDENTIFIER);
        InlineToken colon = tokenizer.remove(CurlyTokenType.COLON);
        InlineToken localName = tokenizer.remove(CurlyTokenType.IDENTIFIER);

        assertEquals("fx", namespace.getValue());
        assertEquals(new SourceInfo(1, 3, 1, 5), namespace.getSourceInfo());
        assertEquals(new SourceInfo(1, 3, 1, 5), namespace.getSourceInfo().toOriginal());
        assertEquals(new SourceInfo(1, 5, 1, 6), colon.getSourceInfo());
        assertEquals(new SourceInfo(1, 5, 1, 10), colon.getSourceInfo().toOriginal());
        assertEquals("foo", localName.getValue());
        assertEquals(new SourceInfo(1, 6, 1, 9), localName.getSourceInfo());
        assertEquals(new SourceInfo(1, 10, 1, 13), localName.getSourceInfo().toOriginal());
    }

    @Test
    public void Decoded_Newline_Keeps_Decoded_Line_Text_And_Projects_Raw_Source_Location() {
        String raw = "foo&#10;bar";
        var input = SourceMappedText.decodedXml(raw, new Location(4, 7), XmlEntityDecoder.decode(raw));
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
        var emptyTokenizer = new InlineTokenizer("", new Location(6, 4));
        MarkupException empty = assertThrows(MarkupException.class, emptyTokenizer::peekNotNull);
        assertEquals(new SourceInfo(6, 4), empty.getSourceInfo());

        var whitespaceTokenizer = new InlineTokenizer("  \n", new Location(6, 4));
        MarkupException whitespace = assertThrows(MarkupException.class, whitespaceTokenizer::peekNotNull);
        assertEquals(new SourceInfo(7, 0), whitespace.getSourceInfo());

        var commentTokenizer = new InlineTokenizer("/* comment */", new Location(6, 4));
        MarkupException comment = assertThrows(MarkupException.class, commentTokenizer::peekNotNull);

        assertEquals(new SourceInfo(6, 17), comment.getSourceInfo());
    }

    @Test
    public void Tokenize_Adjacent_Number_And_Identifier_Separately() {
        var tokenizer = new InlineTokenizer("123value", new Location(-1, -1));

        assertToken(tokenizer.remove(), CurlyTokenType.NUMBER, "123");
        assertToken(tokenizer.remove(), CurlyTokenType.IDENTIFIER, "value");
        assertTrue(tokenizer.isEmpty());
    }

    @Test
    public void Normalize_Arithmetic_Signs() {
        var tokenizer = new InlineTokenizer("a-1 a+-1 -width a--b", new Location(0, 0));

        assertToken(tokenizer.remove(), CurlyTokenType.IDENTIFIER, "a");
        InlineToken minus = tokenizer.remove();
        assertToken(minus, CurlyTokenType.MINUS, "-");
        assertEquals("0:1..0:2", minus.getSourceInfo().toString());
        InlineToken one = tokenizer.remove();
        assertToken(one, CurlyTokenType.NUMBER, "1");
        assertEquals("0:2..0:3", one.getSourceInfo().toString());

        assertToken(tokenizer.remove(), CurlyTokenType.IDENTIFIER, "a");
        assertToken(tokenizer.remove(), CurlyTokenType.PLUS, "+");
        assertToken(tokenizer.remove(), CurlyTokenType.MINUS, "-");
        assertToken(tokenizer.remove(), CurlyTokenType.NUMBER, "1");
        assertToken(tokenizer.remove(), CurlyTokenType.MINUS, "-");
        assertToken(tokenizer.remove(), CurlyTokenType.IDENTIFIER, "width");
        assertToken(tokenizer.remove(), CurlyTokenType.IDENTIFIER, "a");
        assertToken(tokenizer.remove(), CurlyTokenType.MINUS, "-");
        assertToken(tokenizer.remove(), CurlyTokenType.MINUS, "-");
        assertToken(tokenizer.remove(), CurlyTokenType.IDENTIFIER, "b");
        assertTrue(tokenizer.isEmpty());
    }

    @Test
    public void Arrow_Remains_A_Single_Token() {
        var tokenizer = new InlineTokenizer("->", new Location(0, 0));

        assertEquals(1, tokenizer.size());
        assertToken(tokenizer.remove(), CurlyTokenType.UNKNOWN, "->");
    }

    private void assertTokens(String source, ExpectedToken... expectedTokens) {
        var tokenizer = new InlineTokenizer(source, new Location(0, 0));
        assertEquals(expectedTokens.length, tokenizer.size(), source);

        for (ExpectedToken expected : expectedTokens) {
            InlineToken actual = tokenizer.remove();
            assertToken(actual, expected.type(), expected.value());
        }

        assertTrue(tokenizer.isEmpty());
    }

    private ExpectedToken token(CurlyTokenType type, String value) {
        return new ExpectedToken(type, value);
    }

    private void assertToken(InlineToken token, CurlyTokenType type, String value) {
        assertEquals(type, token.getType());
        assertEquals(value, token.getValue());
    }

    private record ExpectedToken(CurlyTokenType type, String value) {}
}

// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

    private void assertToken(InlineToken token, CurlyTokenType type, String value) {
        assertEquals(type, token.getType());
        assertEquals(value, token.getValue());
    }
}

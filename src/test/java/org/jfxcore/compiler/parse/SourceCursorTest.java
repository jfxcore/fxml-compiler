// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.XmlEntityDecoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SourceCursorTest {

    @Test
    public void Checkpoint_Reset_And_Slices_Use_Decoded_Utf16_Offsets() {
        SourceMappedText source = SourceMappedText.identity("a\uD83D\uDE00bc", new Location(2, 4));
        SourceCursor cursor = new SourceCursor(source);

        cursor.advance();
        int checkpoint = cursor.checkpoint();
        assertEquals('\uD83D', cursor.peek());
        assertEquals('\uDE00', cursor.peek(1));

        cursor.advance(3);
        assertEquals('c', cursor.peek());
        cursor.reset(checkpoint);

        assertEquals(1, cursor.getOffset());
        assertEquals("\uD83D\uDE00b", cursor.slice(1, 4).getText());
        assertEquals(new SourceInfo(2, 5, 2, 8), cursor.sourceInfo(1, 4));
    }

    @Test
    public void Remaining_View_Preserves_Xml_Entity_Projection() {
        String raw = "a&amp;b";
        SourceCursor cursor = new SourceCursor(SourceMappedText.decodedXml(
            raw, new Location(3, 7), XmlEntityDecoder.decode(raw)));

        cursor.advance();
        SourceMappedText remaining = cursor.remaining();

        assertEquals("&b", remaining.getText());
        assertEquals(new SourceInfo(3, 8, 3, 13),
            remaining.getSourceInfo(0, 1).toOriginal());
    }

    @Test
    public void Cursor_Rejects_Invalid_Offsets_And_End_Peeks() {
        SourceCursor cursor = new SourceCursor(SourceMappedText.identity("x", new Location(0, 0)));

        assertThrows(IndexOutOfBoundsException.class, () -> cursor.setOffset(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> cursor.setOffset(2));
        cursor.advance();
        assertTrue(cursor.isAtEnd());
        assertThrows(IndexOutOfBoundsException.class, cursor::peek);
    }
}

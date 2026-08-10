// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class XmlEntityDecoderTest {

    @Test
    public void Decodes_Predefined_Entities_In_One_Pass() {
        var result = XmlEntityDecoder.decode("&gt;&lt;&amp;&quot;&apos;&amp;lt;");

        assertEquals("><&\"'&lt;", result.text());
        assertEquals(6, result.replacements().size());
    }

    @Test
    public void Retains_Unknown_Named_References_And_Bare_Ampersands() {
        assertEquals("&unknown; & &AMP;", XmlEntityDecoder.decode("&unknown; & &AMP;").text());
    }

    @Test
    public void Decodes_Bmp_And_Supplementary_Numeric_References() {
        var result = XmlEntityDecoder.decode("&#65;&#x0042;&#x1F600;");

        assertEquals("AB\uD83D\uDE00", result.text());
        assertEquals(3, result.replacements().size());
        assertEquals(new XmlEntityDecoder.Replacement(13, 22, 2, 4), result.replacements().get(2));
    }

    @Test
    public void Records_Every_Replacement() {
        var result = XmlEntityDecoder.decode("a&amp;b&#99;c");

        assertEquals("a&bcc", result.text());
        assertEquals(
            new XmlEntityDecoder.Replacement(1, 6, 1, 2),
            result.replacements().get(0));
        assertEquals(
            new XmlEntityDecoder.Replacement(7, 12, 3, 4),
            result.replacements().get(1));
    }

    @Test
    public void Reports_Deterministic_Invalid_Attempt_Ranges() {
        assertInvalidRange("x&#12 y;&amp;", 1, 8);
        assertInvalidRange("x&#12 y&amp;", 1, 7);
        assertInvalidRange("x&#;&amp;", 1, 4);
        assertInvalidRange("x&#x;&amp;", 1, 5);
    }

    @Test
    public void Rejects_Invalid_Numeric_References() {
        String[] invalid = {
            "&#;", "&#x;", "&#12", "&#xG;", "&#12z;", "&#999999999999999999999;",
            "&#x110000;", "&#xD800;", "&#0;", "&#1;", "&#xB;", "&#xFFFF;"
        };

        for (String text : invalid) {
            assertThrows(XmlEntityDecoder.DecodeException.class, () -> XmlEntityDecoder.decode(text), text);
        }
    }

    @Test
    public void Accepts_All_Xml10_Numeric_Character_Ranges() {
        assertEquals(
            "\t\n\r \uD7FF\uE000\uFFFD\uD800\uDC00\uDBFF\uDFFF",
            XmlEntityDecoder.decode(
                "&#9;&#10;&#13;&#32;&#xD7FF;&#xE000;&#xFFFD;&#x10000;&#x10FFFF;").text());
    }

    private static void assertInvalidRange(String text, int start, int end) {
        var exception = assertThrows(XmlEntityDecoder.DecodeException.class, () -> XmlEntityDecoder.decode(text));
        assertEquals(start, exception.rawStart());
        assertEquals(end, exception.rawEnd());
    }
}

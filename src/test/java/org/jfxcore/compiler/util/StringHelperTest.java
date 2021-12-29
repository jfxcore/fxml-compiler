// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StringHelperTest {

    @Test
    public void Split_Lines_Of_Multiline_Document() {
        String document = """
            line0
            
            line2
            line3
            
            """;

        String[] lines = StringHelper.splitLines(document, false);
        assertEquals(5, lines.length);
        assertEquals("line0", lines[0]);
        assertEquals("", lines[1]);
        assertEquals("line2", lines[2]);
        assertEquals("line3", lines[3]);
        assertEquals("", lines[4]);
    }

    @Test
    public void Test_Quote_String() {
        assertEquals("\"✖\"", StringHelper.quote("✖"));
        assertEquals("\"'✖'\"", StringHelper.quote("'✖'"));
        assertEquals("'\"✖\"'", StringHelper.quote("\"✖\""));
    }

}

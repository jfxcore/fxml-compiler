// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NumberUtilTest {

    @Test
    public void Parse_Implicit_Int() {
        Number number = NumberUtil.parse("123");
        assertTrue(number instanceof Integer);
        assertEquals(123, (int)number);
    }

    @Test
    public void Parse_Implicit_Double() {
        Number number = NumberUtil.parse("123.0");
        assertTrue(number instanceof Double);
        assertEquals(123.0D, (double)number, 0.001);
    }

    @Test
    public void Parse_Implicit_Long() {
        Number number = NumberUtil.parse("1234567890123");
        assertTrue(number instanceof Long);
        assertEquals(1234567890123L, (long)number);
    }

    @Test
    public void Parse_Explicit_Long() {
        Number number = NumberUtil.parse("123L");
        assertTrue(number instanceof Long);
        assertEquals(123L, (long)number);

        number = NumberUtil.parse("123l");
        assertTrue(number instanceof Long);
        assertEquals(123L, (long)number);
    }

    @Test
    public void Parse_Explicit_Float() {
        Number number = NumberUtil.parse("123.0F");
        assertTrue(number instanceof Float);
        assertEquals(123.0f, (float)number, 0.001);

        number = NumberUtil.parse("123.0f");
        assertTrue(number instanceof Float);
        assertEquals(123.0f, (float)number, 0.001);
    }

    @Test
    public void Parse_Explicit_Double() {
        Number number = NumberUtil.parse("123.0D");
        assertTrue(number instanceof Double);
        assertEquals(123.0D, (double)number, 0.001);

        number = NumberUtil.parse("123.0d");
        assertTrue(number instanceof Double);
        assertEquals(123.0D, (double)number, 0.001);
    }

}

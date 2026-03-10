// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.util.StringHelper.Part;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;

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

    @Test
    public void Unescape_Xml_String() {
        String result = StringHelper.unescapeXml("  &lt;foo &#123;&#36;&#125;bar  &gt;");
        assertEquals("  <foo {$}bar  >", result);
    }

    @Nested
    public class SplitListTest {
        @Test
        public void Single_Item() {
            List<Part> parts = StringHelper.splitList("abc");
            assertEquals(1, parts.size());
            assertPart(parts.get(0), "abc", false, 0, 0);
        }

        @Test
        public void Comma_Separated_Items() {
            List<Part> parts = StringHelper.splitList("a,b,c");
            assertEquals(3, parts.size());
            assertPart(parts.get(0), "a", false, 0, 0);
            assertPart(parts.get(1), "b", false, 0, 2);
            assertPart(parts.get(2), "c", false, 0, 4);
        }

        @Test
        public void Spaces_After_Comma_Are_Trimmed() {
            List<Part> parts = StringHelper.splitList("a,   b,    c");
            assertEquals(3, parts.size());
            assertPart(parts.get(0), "a", false, 0, 0);
            assertPart(parts.get(1), "b", false, 0, 5);
            assertPart(parts.get(2), "c", false, 0, 11);
        }

        @Test
        public void Newline_Separated_Items() {
            List<Part> parts = StringHelper.splitList("a\nb\nc");
            assertEquals(3, parts.size());
            assertPart(parts.get(0), "a", true, 0, 0);
            assertPart(parts.get(1), "b", true, 1, 0);
            assertPart(parts.get(2), "c", false, 2, 0);
        }

        @Test
        public void Indentation_After_Newline_Is_Trimmed_But_Column_Is_Preserved() {
            String input = "123.5,\n" +
                "                     foo\n" +
                "                     baz";
            List<Part> parts = StringHelper.splitList(input);
            assertEquals(3, parts.size());
            assertPart(parts.get(0), "123.5", true, 0, 0);
            assertPart(parts.get(1), "foo", true, 1, 21);
            assertPart(parts.get(2), "baz", false, 2, 21);
        }

        @Test
        public void Comma_Then_Newline_Acts_As_One_Separator() {
            List<Part> parts = StringHelper.splitList("foo,\nbar");
            assertEquals(2, parts.size());
            assertPart(parts.get(0), "foo", true, 0, 0);
            assertPart(parts.get(1), "bar", false, 1, 0);
        }

        @Test
        public void Comma_Whitespace_Newline_Acts_As_One_Separator() {
            List<Part> parts = StringHelper.splitList("foo,      \nbar");
            assertEquals(2, parts.size());
            assertPart(parts.get(0), "foo", true, 0, 0);
            assertPart(parts.get(1), "bar", false, 1, 0);
        }

        @Test
        public void Newline_Then_Comma_Acts_As_One_Separator() {
            List<Part> parts = StringHelper.splitList("foo\n,bar");
            assertEquals(2, parts.size());
            assertPart(parts.get(0), "foo", true, 0, 0);
            assertPart(parts.get(1), "bar", false, 1, 1);
        }

        @Test
        public void Newline_Spaces_Comma_Acts_As_One_Separator() {
            List<Part> parts = StringHelper.splitList("foo\n   ,bar");
            assertEquals(2, parts.size());
            assertPart(parts.get(0), "foo", true, 0, 0);
            assertPart(parts.get(1), "bar", false, 1, 4);
        }

        @Test
        public void Multiple_Blank_Lines_Are_Collapsed() {
            List<Part> parts = StringHelper.splitList("a\n\n\nb");
            assertEquals(2, parts.size());
            assertPart(parts.get(0), "a", true, 0, 0);
            assertPart(parts.get(1), "b", false, 3, 0);
        }

        @Test
        public void Blank_Lines_With_Whitespace_Are_Collapsed() {
            List<Part> parts = StringHelper.splitList("a\n   \n\t\nb");
            assertEquals(2, parts.size());
            assertPart(parts.get(0), "a", true, 0, 0);
            assertPart(parts.get(1), "b", false, 3, 0);
        }

        @Test
        public void Multiple_Commas_Produce_Empty_Items() {
            List<Part> parts = StringHelper.splitList("a,,b");
            assertEquals(3, parts.size());
            assertPart(parts.get(0), "a", false, 0, 0);
            assertPart(parts.get(1), "", false, 0, 2);
            assertPart(parts.get(2), "b", false, 0, 3);
        }

        @Test
        public void Comma_Newline_Comma_Newline_Produces_Empty_Item_Between_Commas() {
            List<Part> parts = StringHelper.splitList("a,\n,\n b");
            assertEquals(3, parts.size());
            assertPart(parts.get(0), "a", true, 0, 0);
            assertPart(parts.get(1), "", true, 1, 0);
            assertPart(parts.get(2), "b", false, 2, 1);
        }

        @Test
        public void Mixed_Comma_And_Newline() {
            String input = "123.5,\n" +
                "                     foo\n" +
                "                     ,bar,\n" +
                "                     baz";
            List<Part> parts = StringHelper.splitList(input);
            assertEquals(4, parts.size());
            assertPart(parts.get(0), "123.5", true, 0, 0);
            assertPart(parts.get(1), "foo", true, 1, 21);
            assertPart(parts.get(2), "bar", true, 2, 22);
            assertPart(parts.get(3), "baz", false, 3, 21);
        }

        @Test
        public void Leading_Whitespace_Of_First_Item_Is_Trimmed_And_Column_Is_Reported() {
            List<Part> parts = StringHelper.splitList("   first, second");
            assertEquals(2, parts.size());
            assertPart(parts.get(0), "first", false, 0, 3);
            assertPart(parts.get(1), "second", false, 0, 10);
        }

        private static void assertPart(Part part, String text, boolean lineBreak, int line, int column) {
            assertEquals(text, part.text());
            assertEquals(lineBreak, part.lineBreak());
            assertEquals(line, part.line());
            assertEquals(column, part.column());
        }
    }
}

// Copyright (c) 2025, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.CompilationScope;
import org.jfxcore.compiler.util.CompilationSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SourceInfoTest {

    @Test
    public void Trimmed_Source_Info_Blank_Single_Line_Text() {
        String sourceText = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="    ">
                </Label>
           """;

        var context = new CompilationContext(new CompilationSource.InMemory(sourceText));
        try (var ignored = new CompilationScope(context)) {
            DocumentNode document = new FxmlParser(sourceText).parseDocument();
            var textNode = (TextNode)document.getRoot().as(ObjectNode.class).getProperty("text").getValues().get(0);
            var sourceInfo = textNode.getSourceInfo().getTrimmed();
            assertEquals(new SourceInfo(2, 18), sourceInfo);
        }
    }

    @Test
    public void Trimmed_Source_Info_Blank_Multi_Line_Text() {
        String sourceText = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="    \s
                          \s
                       ">
                </Label>
           """;

        var context = new CompilationContext(new CompilationSource.InMemory(sourceText));
        try (var ignored = new CompilationScope(context)) {
            DocumentNode document = new FxmlParser(sourceText).parseDocument();
            var textNode = (TextNode)document.getRoot().as(ObjectNode.class).getProperty("text").getValues().get(0);
            var sourceInfo = textNode.getSourceInfo().getTrimmed();
            assertEquals(new SourceInfo(2, 18), sourceInfo);
        }
    }

    @Test
    public void Trimmed_Source_Info_Without_Whitespace() {
        String sourceText = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="foo">
                </Label>
           """;

        var context = new CompilationContext(new CompilationSource.InMemory(sourceText));
        try (var ignored = new CompilationScope(context)) {
            DocumentNode document = new FxmlParser(sourceText).parseDocument();
            var textNode = (TextNode)document.getRoot().as(ObjectNode.class).getProperty("text").getValues().get(0);
            var sourceInfo = textNode.getSourceInfo().getTrimmed();
            assertEquals(new SourceInfo(2, 18, 2, 21), sourceInfo);
        }
    }

    @Test
    public void Trimmed_Source_Info_Single_Line_With_Whitespace() {
        String sourceText = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="   foo  ">
                </Label>
           """;

        var context = new CompilationContext(new CompilationSource.InMemory(sourceText));
        try (var ignored = new CompilationScope(context)) {
            DocumentNode document = new FxmlParser(sourceText).parseDocument();
            var textNode = (TextNode)document.getRoot().as(ObjectNode.class).getProperty("text").getValues().get(0);
            var sourceInfo = textNode.getSourceInfo().getTrimmed();
            assertEquals(new SourceInfo(2, 21, 2, 24), sourceInfo);
        }
    }

    @Test
    public void Trimmed_Source_Info_Multi_Line_With_Whitespace() {
        String sourceText = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="    \s

                            foo
                       bar   \s
                   baz   \s

                       ">
                </Label>
           """;

        var context = new CompilationContext(new CompilationSource.InMemory(sourceText));
        try (var ignored = new CompilationScope(context)) {
            DocumentNode document = new FxmlParser(sourceText).parseDocument();
            var textNode = (TextNode)document.getRoot().as(ObjectNode.class).getProperty("text").getValues().get(0);
            var sourceInfo = textNode.getSourceInfo().getTrimmed();
            assertEquals(new SourceInfo(4, 17, 6, 11), sourceInfo);
        }
    }

    @Test
    public void Content_Of_SingleLine_Text_Is_Trimmed() {
        var sourceInfo = SourceInfo.content("  foo  ", 3, 10);
        assertSourceInfo(sourceInfo, 3, 12, 3, 15);
    }

    @Test
    public void Content_Of_MultiLine_Text_Spans_First_And_Last_Non_Whitespace() {
        var sourceInfo = SourceInfo.content("\n                foo\n      bar         \nbaz     ", 10, 5);
        assertSourceInfo(sourceInfo, 11, 16, 13, 3);
    }

    @Test
    public void Content_Of_MultiLine_Text_Applies_Origin_Column_Only_To_First_Line() {
        var sourceInfo = SourceInfo.content("   foo\n  bar   ", 6, 8);
        assertSourceInfo(sourceInfo, 6, 11, 7, 5);
    }

    @Test
    public void Content_Of_Text_Without_Outer_Whitespace_Preserves_Full_Span() {
        var sourceInfo = SourceInfo.content("foo\nbar", 2, 7);
        assertSourceInfo(sourceInfo, 2, 7, 3, 3);
    }

    @Test
    public void Content_Of_Whitespace_Only_Text_Is_None() {
        var sourceInfo = SourceInfo.content("  \n   \n  ", 5, 9);
        assertSourceInfo(sourceInfo, -1, -1, -1, -1);
    }

    private static void assertSourceInfo(
            SourceInfo sourceInfo,
            int startLine, int startColumn,
            int endLine, int endColumn) {

        assertEquals(startLine, sourceInfo.getStart().getLine());
        assertEquals(startColumn, sourceInfo.getStart().getColumn());
        assertEquals(endLine, sourceInfo.getEnd().getLine());
        assertEquals(endColumn, sourceInfo.getEnd().getColumn());
    }
}

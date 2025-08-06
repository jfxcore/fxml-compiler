// Copyright (c) 2023, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FileUtilTest {

    @Test
    public void JavaStubFile_For_Single_Document() {
        var document = new DocumentNode(
            Path.of("foo", "bar", "MyCustomNode"), List.of(),
            new ObjectNode(
                new TypeNode("TestNode", SourceInfo.none()),
                List.of(), List.of(), false, SourceInfo.none()));

        assertEquals(Path.of("foo", "bar", "MyCustomNode.java"), FileUtil.getMarkupJavaFile(document));
    }

    @Test
    public void JavaStubFile_For_Document_With_CodeBehind() {
        var document = new DocumentNode(
            Path.of("foo", "bar", "MyCustomNode"), List.of(),
            new ObjectNode(
                new TypeNode("TestNode", SourceInfo.none()), List.of(new PropertyNode(
                    new String[] {"class"}, "fx:class",
                    TextNode.createRawUnresolved("foo.bar.MyCustomNode", SourceInfo.none()),
                    true, false, SourceInfo.none())),
                    List.of(), false, SourceInfo.none()));

        assertEquals(Path.of("foo", "bar", "MyCustomNodeBase.java"), FileUtil.getMarkupJavaFile(document));
    }

    @Test
    public void JavaStubFile_For_Document_With_CodeBehind_Must_Match_MarkupFileName() {
        var document = new DocumentNode(
            Path.of("foo", "bar", "MyCustomNode"), List.of(),
            new ObjectNode(
                new TypeNode("TestNode", SourceInfo.none()),
                List.of(new PropertyNode(
                    new String[] {"class"}, "fx:class",
                    TextNode.createRawUnresolved("foo.bar.AnotherName", SourceInfo.none()),
                    true, false, SourceInfo.none())),
                List.of(), false, SourceInfo.none()));

        MarkupException ex = assertThrows(MarkupException.class, () -> FileUtil.getMarkupJavaFile(document));
        assertEquals(ErrorCode.CODEBEHIND_CLASS_NAME_MISMATCH, ex.getDiagnostic().getCode());
    }

    @Test
    public void JavaStubFile_For_Document_With_CodeBehind_Must_Contain_Fully_Qualified_ClassName() {
        var document = new DocumentNode(
            Path.of("foo", "bar", "MyCustomNode"), List.of(),
            new ObjectNode(
                new TypeNode("TestNode", SourceInfo.none()),
                List.of(new PropertyNode(
                    new String[] {"class"}, "fx:class",
                    TextNode.createRawUnresolved("MyCustomNode", SourceInfo.none()),
                    true, false, SourceInfo.none())),
                List.of(), false, SourceInfo.none()));

        MarkupException ex = assertThrows(MarkupException.class, () -> FileUtil.getMarkupJavaFile(document));
        assertEquals(ErrorCode.UNNAMED_PACKAGE_NOT_SUPPORTED, ex.getDiagnostic().getCode());
    }

    @Test
    public void JavaStubFile_For_Document_With_CustomStubFile() {
        var document = new DocumentNode(
            Path.of("foo", "bar", "MyCustomNode"), List.of(),
            new ObjectNode(
                new TypeNode("TestNode", SourceInfo.none()),
                List.of(new PropertyNode(
                    new String[] {"markupClassName"}, "fx:markupClassName",
                    TextNode.createRawUnresolved("MyCustomFileName", SourceInfo.none()),
                    true, false, SourceInfo.none())),
                List.of(), false, SourceInfo.none()));

        assertEquals(Path.of("foo", "bar", "MyCustomFileName.java"), FileUtil.getMarkupJavaFile(document));
    }

    @Test
    public void JavaStubFile_For_Document_With_CustomStubFile_Must_Not_Contain_Fully_Qualified_ClassName() {
        var document = new DocumentNode(
            Path.of("foo", "bar", "MyCustomNode"), List.of(),
            new ObjectNode(
                new TypeNode("TestNode", SourceInfo.none()),
                List.of(new PropertyNode(
                    new String[] {"markupClassName"}, "fx:markupClassName",
                    TextNode.createRawUnresolved("foo.bar.MyCustomFileName", SourceInfo.none()),
                    true, false, SourceInfo.none())),
                List.of(), false, SourceInfo.none()));

        MarkupException ex = assertThrows(MarkupException.class, () -> FileUtil.getMarkupJavaFile(document));
        assertEquals(ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, ex.getDiagnostic().getCode());
    }
}

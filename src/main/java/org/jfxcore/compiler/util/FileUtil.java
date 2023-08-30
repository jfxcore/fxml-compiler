// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

public class FileUtil {

    public static Path getMarkupJavaFile(DocumentNode document) {
        var root = document.getRoot().as(ObjectNode.class);
        if (root == null) {
            throw new IllegalArgumentException("document");
        }

        var markupClassNameProperty = root.findIntrinsicProperty(Intrinsics.MARKUP_CLASS_NAME);
        if (markupClassNameProperty != null) {
            String className = getTextNotEmpty(root, markupClassNameProperty);
            if (!NameHelper.isJavaIdentifier(className)) {
                throw PropertyAssignmentErrors.cannotCoercePropertyValue(
                    markupClassNameProperty.getSourceInfo(),
                    markupClassNameProperty.getMarkupName(),
                    className);
            }

            return getJavaFilePath(className);
        }

        var classNameProperty = root.findIntrinsicProperty(Intrinsics.CLASS);
        if (classNameProperty != null) {
            String[] parts = getTextNotEmpty(root, classNameProperty).split("\\.");
            String packageName = Arrays.stream(parts).limit(parts.length - 1).collect(Collectors.joining("."));
            String className = parts[parts.length - 1];

            if (packageName.isEmpty()) {
                throw SymbolResolutionErrors.unnamedPackageNotSupported(
                    classNameProperty.getSourceInfo(), classNameProperty.getMarkupName());
            }

            String fileName = FileUtil.getFileNameWithoutExtension(document.getSourceFile().getFileName().toString());
            if (!className.equals(fileName)) {
                throw GeneralErrors.codeBehindClassNameMismatch(classNameProperty.getSourceInfo());
            }

            Path file = document.getSourceFile();
            fileName = getFileNameWithoutExtension(file.getName(file.getNameCount() - 1).toString());
            String markupClassName = NameHelper.getDefaultMarkupClassName(fileName);
            return file.getParent().resolve(markupClassName + ".java");
        }

        Path file = document.getSourceFile();
        String fileName = getFileNameWithoutExtension(file.getName(file.getNameCount() - 1).toString());
        return file.getParent().resolve(fileName + ".java");
    }

    private static Path getJavaFilePath(String className) {
        String[] parts = className.split("\\.");
        if (parts.length > 1) {
            String first = parts[0];
            parts = Arrays.copyOfRange(parts, 1, parts.length - 1);
            parts[parts.length - 1] = parts[parts.length - 1] + ".java";
            return Path.of(first, parts);
        }

        return Path.of(parts[0] + ".java");
    }

    private static String getTextNotEmpty(ObjectNode parent, PropertyNode node) {
        if (node.getValues().size() > 1 || !(node.getValues().get(0) instanceof TextNode)) {
            throw PropertyAssignmentErrors.propertyMustContainText(
                node.getSourceInfo(), parent.getType().getMarkupName(), node.getMarkupName());
        }

        String text = ((TextNode)node.getValues().get(0)).getText();
        if (text.isBlank()) {
            throw PropertyAssignmentErrors.propertyCannotBeEmpty(
                node.getSourceInfo(), parent.getType().getMarkupName(), node.getMarkupName());
        }

        return text;
    }

    public static String getFileNameWithoutExtension(String file) {
        int lastIdx = file.lastIndexOf('.');
        return file.substring(0, lastIdx < 0 ? file.length() : lastIdx);
    }

    public static Path removeLastN(Path path, int n) {
        for (int i = 0; i < n; ++i) {
            path = path.getParent();
        }

        return path;
    }

}

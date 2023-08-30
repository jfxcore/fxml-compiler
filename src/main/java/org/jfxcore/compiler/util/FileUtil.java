// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import java.nio.file.Path;
import java.util.Arrays;

public class FileUtil {

    public static Path getMarkupJavaFile(DocumentNode document) {
        var root = document.getRoot().as(ObjectNode.class);
        if (root == null) {
            throw new IllegalArgumentException("document");
        }

        var markupClassNameProperty = root.findIntrinsicProperty(Intrinsics.MARKUP_CLASS_NAME);
        if (markupClassNameProperty == null) {
            Path file = document.getSourceFile();
            String fileName = getFileNameWithoutExtension(file.getName(file.getNameCount() - 1).toString());
            String markupClassName = NameHelper.getDefaultMarkupClassName(fileName);
            return file.getParent().resolve(markupClassName + ".java");
        }

        if (markupClassNameProperty.getValues().size() > 1 ||
                !(markupClassNameProperty.getValues().get(0) instanceof TextNode)) {
            throw PropertyAssignmentErrors.propertyMustContainText(
                markupClassNameProperty.getSourceInfo(),
                root.getType().getMarkupName(),
                markupClassNameProperty.getMarkupName());
        }

        String className = ((TextNode)markupClassNameProperty.getValues().get(0)).getText();
        if (className.isBlank()) {
            throw PropertyAssignmentErrors.propertyCannotBeEmpty(
                markupClassNameProperty.getSourceInfo(),
                root.getType().getMarkupName(),
                markupClassNameProperty.getMarkupName());
        }

        if (!NameHelper.isJavaIdentifier(className)) {
            throw PropertyAssignmentErrors.cannotCoercePropertyValue(
                markupClassNameProperty.getSourceInfo(),
                markupClassNameProperty.getMarkupName(),
                className);
        }

        String[] parts = className.split("\\.");
        if (parts.length > 1) {
            String first = parts[0];
            parts = Arrays.copyOfRange(parts, 1, parts.length - 1);
            parts[parts.length - 1] = parts[parts.length - 1] + ".java";
            return Path.of(first, parts);
        }

        return Path.of(parts[0] + ".java");
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

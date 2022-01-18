// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.codebehind;

import javassist.Modifier;
import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.NodeDataKey;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.codebehind.AddCodeFieldNode;
import org.jfxcore.compiler.ast.codebehind.ClassNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.FileUtil;
import org.jfxcore.compiler.util.NameHelper;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Replaces the root element of the AST with a {@link ClassNode} and deletes all other
 * nodes except {@link AddCodeFieldNode}.
 */
public class FlattenClassTransform implements Transform {

    private static final String DEFAULT_MARKUP_CLASS_NAME = "%sMarkup";

    @Override
    public Set<Class<? extends Transform>> getDependsOn() {
        return Set.of(AddCodeFieldsTransform.class);
    }

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof ObjectNode root) || !(context.getParent() instanceof DocumentNode)) {
            return node;
        }

        PropertyNode codeBehindClass = root.findIntrinsicProperty(Intrinsics.CLASS);
        String packageName, className, markupClassName;
        int classModifiers = Modifier.PUBLIC;

        PropertyNode classModifierNode = root.findIntrinsicProperty(Intrinsics.CLASS_MODIFIER);
        if (classModifierNode != null) {
            String value = classModifierNode.getTextValueNotEmpty(context);

            classModifiers = switch (value) {
                case "public" -> Modifier.PUBLIC;
                case "protected" -> Modifier.PROTECTED;
                case "package" -> 0;
                default -> throw PropertyAssignmentErrors.cannotCoercePropertyValue(
                        classModifierNode.getSourceInfo(), classModifierNode.getMarkupName(), value);
            };
        }

        PropertyNode paramsNode = root.findIntrinsicProperty(Intrinsics.CLASS_PARAMETERS);
        String[] params = paramsNode != null ? paramsNode.getTextValueNotEmpty(context).split(",") : new String[0];

        if (codeBehindClass != null) {
            String[] parts = codeBehindClass.getTextValueNotEmpty(context).split("\\.");
            packageName = Arrays.stream(parts).limit(parts.length - 1).collect(Collectors.joining("."));
            className = parts[parts.length - 1];
            classModifiers |= Modifier.ABSTRACT;

            if (packageName.isEmpty()) {
                throw SymbolResolutionErrors.unnamedPackageNotSupported(
                    codeBehindClass.getSourceInfo(), codeBehindClass.getMarkupName());
            }

            String fileName = FileUtil.getFileNameWithoutExtension(
                context.getDocument().getSourceFile().getFileName().toString());

            if (!className.equals(fileName)) {
                throw GeneralErrors.codeBehindClassNameMismatch(codeBehindClass.getSourceInfo());
            }
        } else {
            Path sourceFile = context.getParent(DocumentNode.class).getSourceFile();
            StringBuilder stringBuilder = new StringBuilder();

            for (int i = 0; i < sourceFile.getNameCount() - 1; ++i) {
                stringBuilder.append(sourceFile.getName(i));

                if (i < sourceFile.getNameCount() - 2) {
                    stringBuilder.append('.');
                }
            }

            packageName = stringBuilder.toString();
            className = sourceFile.getFileName().toString();
            className = className.substring(0, className.lastIndexOf('.'));
        }

        PropertyNode markupClassNameNode = root.findIntrinsicProperty(Intrinsics.MARKUP_CLASS_NAME);
        if (markupClassNameNode != null) {
            if (codeBehindClass == null) {
                throw GeneralErrors.markupClassNameWithoutCodeBehind(
                    markupClassNameNode.getSourceInfo(), markupClassNameNode.getMarkupName());
            }

            if (!NameHelper.isJavaIdentifier(markupClassNameNode.getTextValueNotEmpty(context))) {
                throw PropertyAssignmentErrors.cannotCoercePropertyValue(
                    markupClassNameNode.getSourceInfo(),
                    markupClassNameNode.getMarkupName(),
                    markupClassNameNode.getTextValueNotEmpty(context));
            }

            markupClassName = markupClassNameNode.getTextValueNotEmpty(context);
        } else {
            markupClassName = String.format(DEFAULT_MARKUP_CLASS_NAME, className);
        }

        return new ClassNode(
            packageName,
            className,
            markupClassName,
            classModifiers,
            params,
            (String)root.getNodeData(NodeDataKey.FORMATTED_TYPE_ARGUMENTS),
            codeBehindClass != null,
            root.getType(),
            root.getProperties().stream().filter(p -> p instanceof AddCodeFieldNode).collect(Collectors.toList()),
            root.getSourceInfo());
    }

}

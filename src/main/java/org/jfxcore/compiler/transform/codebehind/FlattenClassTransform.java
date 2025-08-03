// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.codebehind;

import javassist.CtConstructor;
import javassist.Modifier;
import javassist.bytecode.annotation.AnnotationImpl;
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
import org.jfxcore.compiler.parse.TypeParser;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.FileUtil;
import org.jfxcore.compiler.util.MethodFinder;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Replaces the root element of the AST with a {@link ClassNode} and deletes all other
 * nodes except {@link AddCodeFieldNode}.
 */
public class FlattenClassTransform implements Transform {

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
            String value = classModifierNode.getTrimmedTextValue(context);

            classModifiers = switch (value) {
                case "public" -> Modifier.PUBLIC;
                case "protected" -> Modifier.PROTECTED;
                case "package" -> 0;
                default -> throw PropertyAssignmentErrors.cannotCoercePropertyValue(
                    classModifierNode.getTrimmedTextSourceInfo(context), classModifierNode.getMarkupName(), value);
            };
        }

        PropertyNode paramsNode = root.findIntrinsicProperty(Intrinsics.CLASS_PARAMETERS);
        List<TypeInstance> params = paramsNode != null
            ? new TypeParser(paramsNode.getTrimmedTextValue(context)).parse()
            : List.of();

        if (codeBehindClass != null) {
            String[] parts = codeBehindClass.getTrimmedTextValue(context).split("\\.");
            packageName = Arrays.stream(parts).limit(parts.length - 1).collect(Collectors.joining("."));
            className = parts[parts.length - 1];
            classModifiers |= Modifier.ABSTRACT;

            if (packageName.isEmpty()) {
                throw SymbolResolutionErrors.unnamedPackageNotSupported(
                    codeBehindClass.getTrimmedTextSourceInfo(context), codeBehindClass.getMarkupName());
            }

            String fileName = FileUtil.getFileNameWithoutExtension(
                context.getDocument().getSourceFile().getFileName().toString());

            if (!className.equals(fileName)) {
                throw GeneralErrors.codeBehindClassNameMismatch(codeBehindClass.getTrimmedTextSourceInfo(context));
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

            if (!NameHelper.isJavaIdentifier(markupClassNameNode.getTrimmedTextValue(context))) {
                throw PropertyAssignmentErrors.cannotCoercePropertyValue(
                    markupClassNameNode.getTrimmedTextSourceInfo(context),
                    markupClassNameNode.getMarkupName(),
                    markupClassNameNode.getTrimmedTextValue(context));
            }

            markupClassName = markupClassNameNode.getTrimmedTextValue(context);
        } else {
            markupClassName = NameHelper.getDefaultMarkupClassName(className);
        }

        List<List<String>> paramAnnotations = new ArrayList<>();

        if (paramsNode != null) {
            var rootType = TypeHelper.getTypeInstance(root);
            var methodFinder = new MethodFinder(rootType, rootType.jvmType());

            CtConstructor constructor = methodFinder.findConstructor(
                List.of(),
                params,
                params.stream().map(x -> paramsNode.getSourceInfo()).toList(),
                null,
                paramsNode.getSourceInfo());

            if (constructor == null) {
                throw SymbolResolutionErrors.memberNotFound(
                    paramsNode.getSourceInfo(), rootType.jvmType(), String.format("<ctor>(%s)",
                        String.join(", ", params.stream().map(TypeInstance::getJavaName).toList())));
            }

            for (Object[] ctorParamAnnotations : constructor.getAvailableParameterAnnotations()) {
                List<String> annotation = new ArrayList<>();
                paramAnnotations.add(annotation);

                for (Object ctorParamAnnotation : ctorParamAnnotations) {
                    if (Proxy.getInvocationHandler(ctorParamAnnotation) instanceof AnnotationImpl annotationImpl
                            && Classes.NamedArgAnnotationName.equals(annotationImpl.getTypeName())) {
                        annotation.add(annotationImpl.getAnnotation().toString());
                    }
                }
            }
        }

        return new ClassNode(
            packageName,
            className,
            markupClassName,
            classModifiers,
            params.stream().map(TypeInstance::getJavaName).toArray(String[]::new),
            paramAnnotations.stream().map(list -> list.toArray(String[]::new)).toArray(String[][]::new),
            (String)root.getNodeData(NodeDataKey.FORMATTED_TYPE_ARGUMENTS),
            codeBehindClass != null,
            root.getType(),
            root.getProperties().stream().filter(p -> p instanceof AddCodeFieldNode).collect(Collectors.toList()),
            root.getSourceInfo());
    }

}

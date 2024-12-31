// Copyright (c) 2022, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform;

import javassist.ClassPool;
import javassist.CtClass;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.transform.codebehind.AddCodeFieldsTransform;
import org.jfxcore.compiler.transform.codebehind.FlattenClassTransform;
import org.jfxcore.compiler.transform.common.ContentExpressionTransform;
import org.jfxcore.compiler.transform.common.DefaultPropertyTransform;
import org.jfxcore.compiler.transform.common.IntrinsicsTransform;
import org.jfxcore.compiler.transform.common.ResolveTypeTransform;
import org.jfxcore.compiler.transform.markup.AddTemplateIdFields;
import org.jfxcore.compiler.transform.markup.BindingContextTransform;
import org.jfxcore.compiler.transform.markup.BindingTransform;
import org.jfxcore.compiler.transform.markup.ConstantTransform;
import org.jfxcore.compiler.transform.markup.DefineBlockTransform;
import org.jfxcore.compiler.transform.markup.DocumentTransform;
import org.jfxcore.compiler.transform.markup.IdPropertyTransform;
import org.jfxcore.compiler.transform.markup.NullIntrinsicTransform;
import org.jfxcore.compiler.transform.markup.ObjectToPropertyTransform;
import org.jfxcore.compiler.transform.markup.ObjectTransform;
import org.jfxcore.compiler.transform.markup.PropertyAssignmentTransform;
import org.jfxcore.compiler.transform.markup.RemoveIntrinsicsTransform;
import org.jfxcore.compiler.transform.markup.StylesheetTransform;
import org.jfxcore.compiler.transform.markup.TemplateContentTransform;
import org.jfxcore.compiler.transform.markup.TopologyTransform;
import org.jfxcore.compiler.transform.markup.TypeIntrinsicTransform;
import org.jfxcore.compiler.transform.markup.ResourceIntrinsicTransform;
import org.jfxcore.compiler.transform.markup.ValidateTypeTransform;
import java.util.Arrays;
import java.util.List;

public class Transformer {

    private static final boolean DEBUG = false;

    private final ClassPool classPool;
    private final List<Transform> transforms;

    public Transformer(@Nullable ClassPool classPool, Transform... transforms) {
        this.classPool = classPool;
        this.transforms = Arrays.asList(transforms);

        if (DEBUG) {
            System.out.println("AST transforms:");

            for (int i = 0; i < this.transforms.size(); ++i) {
                System.out.printf("  %s) %s%n", i + 1, this.transforms.get(i).getClass().getSimpleName());
            }

            System.out.println();
        }
    }

    public static Transformer getCodeTransformer(ClassPool classPool) {
        return new Transformer(
            classPool,
            new DefaultPropertyTransform(true),
            new ContentExpressionTransform(),
            new IntrinsicsTransform(),
            new ResolveTypeTransform(true),
            new AddCodeFieldsTransform(),
            new FlattenClassTransform());
    }

    public static Transformer getBytecodeTransformer(ClassPool classPool) {
        return new Transformer(
            classPool,
            new DefaultPropertyTransform(true),
            new ContentExpressionTransform(),
            new IntrinsicsTransform(),
            new ResolveTypeTransform(false),
            new ObjectToPropertyTransform(),
            new ConstantTransform(),
            new ValidateTypeTransform(),
            new DefaultPropertyTransform(false),
            new TemplateContentTransform(),
            new AddTemplateIdFields(),
            new RemoveIntrinsicsTransform(),
            new StylesheetTransform(),
            new DocumentTransform(),
            new IdPropertyTransform(),
            new BindingContextTransform(),
            new BindingTransform(true),
            new DefineBlockTransform(),
            new ResourceIntrinsicTransform(),
            new NullIntrinsicTransform(),
            new TypeIntrinsicTransform(),
            new ObjectTransform(),
            new PropertyAssignmentTransform(),
            new TopologyTransform());
    }

    public ClassPool getClassPool() {
        return classPool;
    }

    public Node transform(Node node, @Nullable CtClass codeBehindClass, @Nullable CtClass markupClass) {
        TransformContext context = new TransformContext(
            ((DocumentNode)node).getImports(), classPool, codeBehindClass, markupClass);

        node = node.deepClone(); // make a copy so we don't destroy the original AST

        for (int i = 0; i < transforms.size(); ++i) {
            node = Visitor.visit(node, new TransformVisitor(transforms.get(i), context));

            if (DEBUG) {
                System.out.printf("%s) %s%n", i + 1, transforms.get(i).getClass().getSimpleName());

                Visitor.visit(node, new Visitor() {
                    int indent;

                    @Override
                    public Node onVisited(Node node) {
                        System.out.print("  ".repeat(indent) + node.getClass().getSimpleName());

                        if (node instanceof ValueNode) {
                            System.out.println("[" + ((ValueNode)node).getType().getMarkupName() + "]");
                        } else if (node instanceof PropertyNode) {
                            System.out.println("[" + ((PropertyNode)node).getMarkupName() + "]");
                        } else if (node instanceof TypeNode) {
                            System.out.println("[" + ((TypeNode)node).getMarkupName() + "]");
                        } else {
                            System.out.println();
                        }

                        return node;
                    }

                    @Override
                    public void push(Node node) {
                        indent++;
                    }

                    @Override
                    public void pop() {
                        indent--;
                    }
                });

                System.out.println();
            }
        }

        return node;
    }

}

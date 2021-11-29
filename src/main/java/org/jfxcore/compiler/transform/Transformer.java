// Copyright (c) 2021, JFXcore. All rights reserved.
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
import org.jfxcore.compiler.transform.markup.AddTemplateIdFields;
import org.jfxcore.compiler.transform.markup.ConstantIntrinsicTransform;
import org.jfxcore.compiler.transform.markup.DefineBlockTransform;
import org.jfxcore.compiler.transform.markup.NullIntrinsicTransform;
import org.jfxcore.compiler.transform.markup.RemoveIntrinsicsTransform;
import org.jfxcore.compiler.transform.markup.TemplateContentTransform;
import org.jfxcore.compiler.transform.markup.TopologyTransform;
import org.jfxcore.compiler.transform.markup.StylesheetTransform;
import org.jfxcore.compiler.transform.common.ValidateIntrinsicsTransform;
import org.jfxcore.compiler.transform.markup.BindingTransform;
import org.jfxcore.compiler.transform.markup.DefaultPropertyTransform;
import org.jfxcore.compiler.transform.markup.DocumentTransform;
import org.jfxcore.compiler.transform.markup.IdPropertyTransform;
import org.jfxcore.compiler.transform.markup.ObjectTransform;
import org.jfxcore.compiler.transform.markup.PropertyAssignmentTransform;
import org.jfxcore.compiler.transform.common.ResolveTypeTransform;
import org.jfxcore.compiler.transform.markup.TypeIntrinsicTransform;
import org.jfxcore.compiler.transform.markup.UrlIntrinsicTransform;
import org.jfxcore.compiler.transform.markup.ValueIntrinsicTransform;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class Transformer {

    private static final boolean DEBUG = false;

    private final ClassPool classPool;
    private final List<Transform> transforms;

    public Transformer(@Nullable ClassPool classPool, Transform... transforms) {
        this.classPool = classPool;
        this.transforms = new ArrayList<>(Arrays.asList(transforms));

        Map<Class<? extends Transform>, Set<Class<? extends Transform>>> dependencies = new HashMap<>();

        for (Transform transform : transforms) {
            Set<Class<? extends Transform>> deps = new HashSet<>();
            getDependencies(transform.getClass(), deps);
            dependencies.put(transform.getClass(), deps);
        }

        sort(this.transforms, dependencies::get);

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
            new ValidateIntrinsicsTransform(),
            new ResolveTypeTransform(true),
            new AddCodeFieldsTransform(),
            new FlattenClassTransform());
    }

    public static Transformer getBytecodeTransformer(ClassPool classPool) {
        return new Transformer(
            classPool,
            new AddTemplateIdFields(),
            new TemplateContentTransform(),
            new ValidateIntrinsicsTransform(),
            new RemoveIntrinsicsTransform(),
            new StylesheetTransform(),
            new DocumentTransform(),
            new ObjectTransform(),
            new DefineBlockTransform(),
            new BindingTransform(),
            new PropertyAssignmentTransform(),
            new TopologyTransform(),
            new DefaultPropertyTransform(),
            new IdPropertyTransform(),
            new ResolveTypeTransform(false),
            new NullIntrinsicTransform(),
            new TypeIntrinsicTransform(),
            new ConstantIntrinsicTransform(),
            new ValueIntrinsicTransform(),
            new UrlIntrinsicTransform());
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

    private void getDependencies(Class<? extends Transform> transformClass, Set<Class<? extends Transform>> set) {
        getDependencies(transformClass, transformClass, set);
    }

    private void getDependencies(
            Class<? extends Transform> transformClass,
            Class<? extends Transform> dependencyClass,
            Set<Class<? extends Transform>> set) {
        Transform currentTransform = null;
        for (Transform transform : transforms) {
            if (dependencyClass.isInstance(transform)) {
                currentTransform = transform;
                break;
            }
        }

        if (currentTransform == null) {
            throw new RuntimeException("Transform dependency not found: " + dependencyClass.getSimpleName());
        }

        if (set.contains(transformClass)) {
            throw new RuntimeException("Transform dependency cycle detected: " + transformClass.getSimpleName());
        }

        Set<Class<? extends Transform>> dependencies = new HashSet<>(currentTransform.getDependsOn());
        dependencies.removeAll(set);
        set.addAll(dependencies);

        for (Class<? extends Transform> dep : dependencies) {
            getDependencies(transformClass, dep, set);
        }
    }

    private void sort(
            List<Transform> source,
            Function<Class<? extends Transform>, Collection<Class<? extends Transform>>> dependencies) {
        List<Transform> result = new ArrayList<>();
        List<Class<? extends Transform>> sorted = new ArrayList<>();
        Set<Class<? extends Transform>> visited = new HashSet<>();

        for (var item : source) {
            visit(item.getClass(), visited, sorted, dependencies);
        }

        for (Class<? extends Transform> item : sorted) {
            for (Transform transform : source) {
                if (transform.getClass() == item) {
                    result.add(transform);
                    break;
                }
            }
        }

        source.clear();
        source.addAll(result);
    }

    private void visit(
            Class<? extends Transform> item,
            Set<Class<? extends Transform>> visited,
            List<Class<? extends Transform>> sorted,
            Function<Class<? extends Transform>, Collection<Class<? extends Transform>>> dependencies) {
        if (!visited.contains(item)) {
            visited.add(item);

            for (var dep : dependencies.apply(item)) {
                visit(dep, visited, sorted, dependencies);
            }

            sorted.add(item);
        } else if (!sorted.contains(item)) {
            throw new RuntimeException("Transform dependency cycle detected: " + item.getSimpleName());
        }
    }

}

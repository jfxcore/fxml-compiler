// Copyright (c) 2021, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform;

import javassist.ClassPool;
import javassist.CtClass;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.TemplateContentNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.util.ArrayStack;
import org.jfxcore.compiler.util.CompilationContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Predicate;

public class TransformContext {

    private final ArrayStack<Node> parents = new ArrayStack<>();
    private final List<String> ids = new ArrayList<>();
    private final CtClass markupClass;
    private final CtClass codeBehindClass;

    public TransformContext(
            List<String> imports,
            ClassPool classPool,
            @Nullable CtClass codeBehindClass,
            @Nullable CtClass markupClass) {
        this.markupClass = markupClass;
        this.codeBehindClass = codeBehindClass;
        CompilationContext context = CompilationContext.getCurrent();
        context.setClassPool(classPool);
        context.setImports(imports);
    }

    public List<String> getIds() {
        return ids;
    }

    public CtClass getMarkupClass() {
        return markupClass;
    }

    public @Nullable CtClass getCodeBehindOrMarkupClass() {
        return codeBehindClass != null ? codeBehindClass : markupClass;
    }

    public boolean isTemplate() {
        ListIterator<Node> it = parents.listIterator(parents.size());

        while (it.hasPrevious()) {
            Node node = it.previous();

            if (node instanceof TemplateContentNode) {
                return true;
            }
        }

        return false;
    }

    public void push(Node node) {
        parents.push(node);
    }

    public void pop() {
        parents.pop();
    }

    public List<Node> getParents() {
        return parents;
    }

    public Node getParent() {
        return parents.peek();
    }

    public Node getParent(int index) {
        if (index >= parents.size()) {
            return null;
        }

        return parents.get(parents.size() - index - 1);
    }

    public Node getParent(Node node) {
        if (parents.isEmpty()) {
            throw new RuntimeException("Specified node has no parent.");
        }

        Node root = parents.get(0);
        Node[] parent = new Node[1];

        Visitor.visit(root, new Visitor() {
            final Deque<Node> stack = new ArrayDeque<>();

            @Override
            public Node onVisited(Node n) {
                if (node == n) {
                    parent[0] = stack.peek();
                    return Visitor.STOP;
                }

                return n;
            }

            @Override
            public void push(Node node) {
                stack.push(node);
            }

            @Override
            public void pop() {
                stack.pop();
            }
        });

        return parent[0];
    }

    @SuppressWarnings("unchecked")
    public <T extends Node> T getParent(Class<T> nodeClass) {
        Node parent = getParent();
        if (nodeClass.isInstance(parent)) {
            return (T)parent;
        }

        return null;
    }

    public DocumentNode getDocument() {
        return findParent(DocumentNode.class);
    }

    public <T extends Node> T findParent(Class<T> type) {
        T parent = tryFindParent(type);
        if (parent == null) {
            throw new RuntimeException("Parent not found: " + type.getName());
        }

        return parent;
    }

    public <T extends Node> T tryFindParent(Class<T> type) {
        return tryFindParent(type, null);
    }

    @SuppressWarnings("unchecked")
    public <T extends Node> T tryFindParent(Class<T> type, @Nullable Predicate<T> predicate) {
        for (int i = parents.size() - 1; i >= 0; --i) {
            if (type.isInstance(parents.get(i))
                    && (predicate == null || predicate.test((T)parents.get(i)))) {
                return (T)parents.get(i);
            }
        }

        return null;
    }

}

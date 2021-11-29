// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import org.jfxcore.compiler.ast.EmitContext;
import org.jfxcore.compiler.ast.GeneratorEmitterNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.generate.Generator;
import org.jfxcore.compiler.generate.RuntimeContextGenerator;
import org.jfxcore.compiler.util.Action;
import org.jfxcore.compiler.util.ArrayStack;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.ExceptionHelper;
import org.jfxcore.compiler.util.Local;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class BytecodeEmitContext extends EmitContext<Bytecode> {

    private final BytecodeEmitContext parentContext;
    private final EmitInitializeRootNode rootNode;
    private final CtClass codeBehindClass;
    private final CtClass markupClass;
    private final ClassSet nestedClasses;
    private CtClass bindingContextClass;
    private CtClass runtimeContextClass;
    private int runtimeContextLocal;

    public BytecodeEmitContext(
            CtClass codeBehindClass,
            CtClass markupClass,
            EmitInitializeRootNode rootNode,
            List<String> imports,
            Bytecode code) {
        super(code);
        this.parentContext = null;
        this.rootNode = rootNode;
        this.bindingContextClass = codeBehindClass;
        this.codeBehindClass = codeBehindClass;
        this.markupClass = markupClass;
        this.nestedClasses = new ClassSet(markupClass);

        CompilationContext context = CompilationContext.getCurrent();
        context.setClassPool(markupClass.getClassPool());
        context.setImports(imports);
    }

    public BytecodeEmitContext(
            BytecodeEmitContext parentContext,
            CtClass bindingContextClass,
            EmitInitializeRootNode rootNode,
            int occupiedLocals,
            int runtimeContextLocal) {
        super(new Bytecode(bindingContextClass, occupiedLocals));
        this.parentContext = parentContext;
        this.rootNode = rootNode;
        this.bindingContextClass = bindingContextClass;
        this.codeBehindClass = parentContext.codeBehindClass;
        this.markupClass = parentContext.markupClass;
        this.nestedClasses = parentContext.nestedClasses;
        this.runtimeContextLocal = runtimeContextLocal;
    }

    public BytecodeEmitContext(
            BytecodeEmitContext parentContext,
            CtClass bindingContextClass,
            int occupiedLocals,
            int runtimeContextLocal) {
        super(new Bytecode(bindingContextClass, occupiedLocals), parentContext.getParents());
        this.parentContext = parentContext;
        this.rootNode = null;
        this.bindingContextClass = bindingContextClass;
        this.codeBehindClass = parentContext.codeBehindClass;
        this.markupClass = parentContext.markupClass;
        this.nestedClasses = parentContext.nestedClasses;
        this.runtimeContextLocal = runtimeContextLocal;
    }

    public int getRuntimeContextLocal() {
        if (runtimeContextLocal < 0) {
            throw new UnsupportedOperationException();
        }

        return runtimeContextLocal;
    }

    public CtClass getRuntimeContextClass() {
        if (parentContext != null) {
            return parentContext.getRuntimeContextClass();
        }

        if (runtimeContextClass == null) {
            boolean[] resourceSupport = new boolean[1];

            Visitor.visit(rootNode, new Visitor() {
                @Override
                protected Node onVisited(Node node) {
                    if (node instanceof EmitUrlNode) {
                        resourceSupport[0] = true;
                        return Visitor.STOP;
                    }

                    return node;
                }
            });

            runtimeContextClass = Generator.emit(this, new RuntimeContextGenerator(resourceSupport[0]));
        }

        return runtimeContextClass;
    }

    /**
     * The class that is the default target for bindings.
     *
     * In the root document, this corresponds to the code-behind class (if the FXML document has a code-behind class)
     * or the markup class (if the FXML document doesn't have a code-behind class).
     *
     * In a template sub-document, this corresponds to the local markup class.
     */
    public CtClass getBindingContextClass() {
        return bindingContextClass;
    }

    public void setBindingContextClass(CtClass contextClass) {
        this.bindingContextClass = contextClass;
    }

    /**
     * A user-specified class that inherits the compiler-generated markup class.
     * If the FXML document doesn't have a code-behind class, this is equivalent to the markup class.
     */
    public CtClass getCodeBehindClass() {
        if (codeBehindClass == null) {
            throw new UnsupportedOperationException();
        }

        return codeBehindClass;
    }

    /**
     * The class that contains compiler-generated markup.
     * If the FXML document has a code-behind class, this is the class which is inherited by the code-behind class.
     */
    public CtClass getMarkupClass() {
        if (markupClass == null) {
            throw new UnsupportedOperationException();
        }

        return markupClass;
    }

    /**
     * In the root document, this corresponds to the markup class.
     * In a template sub-document, this corresponds to the binding context class.
     */
    public CtClass getLocalMarkupClass() {
        if (rootNode.isTemplateRoot()) {
            return bindingContextClass;
        }

        return getMarkupClass();
    }

    public ClassSet getNestedClasses() {
        return nestedClasses;
    }

    public void emitRootNode() {
        if (rootNode == null) {
            throw new UnsupportedOperationException();
        }

        List<GeneratorEntry> generators = new ArrayList<>();

        Visitor.visit(rootNode, new Visitor() {
            final ArrayStack<Node> parents = new ArrayStack<>();

            @Override
            public Node onVisited(Node node) {
                if (node instanceof GeneratorEmitterNode) {
                    List<Generator> list = ((GeneratorEmitterNode)node).emitGenerators(BytecodeEmitContext.this);
                    if (list != null && !list.isEmpty()) {
                        generators.addAll(
                            list.stream()
                                .map(g -> new GeneratorEntry(node, new ArrayList<>(parents), g))
                                .collect(Collectors.toList()));
                    }
                }

                return node;
            }

            @Override
            protected void push(Node node) {
                parents.push(node);
            }

            @Override
            protected void pop() {
                parents.pop();
            }
        });

        Iterator<GeneratorEntry> it = generators.listIterator();
        while (it.hasNext()) {
            Generator generator = it.next().generator;
            CtClass clazz = getNestedClasses().find(generator.getClassName());

            if (clazz != null) {
                it.remove();
            }
        }

        List<GeneratorEntry> activeGenerators = new ArrayList<>();

        for (GeneratorEntry entry : generators) {
            runWithParentStack(entry.parents, () -> {
                String className = entry.generator.getClassName();
                if (activeGenerators.stream().noneMatch(g -> g.generator.getClassName().equals(className))) {
                    activeGenerators.add(entry);
                }
            });
        }

        for (GeneratorEntry entry : activeGenerators) {
            runWithParentStack(entry.parents, () -> entry.generator.emitClass(this));
        }

        for (GeneratorEntry entry : activeGenerators) {
            runWithParentStack(entry.parents, () -> entry.generator.emitFields(this));
        }

        for (GeneratorEntry entry : activeGenerators) {
            runWithParentStack(entry.parents, () -> entry.generator.emitMethods(this));
        }

        for (GeneratorEntry entry : activeGenerators) {
            runWithParentStack(entry.parents, () -> entry.generator.emitCode(this));
        }

        if (ContextHelper.needsContext(rootNode)) {
            Local contextLocal = getOutput().acquireLocal(false);
            this.runtimeContextLocal = contextLocal.getIndex();
            emit(rootNode);
            getOutput().releaseLocal(contextLocal);
        } else {
            emit(rootNode);
        }
    }

    public void emit(Node node) {
        if (!(node instanceof EmitterNode)) {
            throw new IllegalArgumentException();
        }

        getParents().push(node);
        ((EmitterNode)node).emit(this);
        getParents().pop();
    }

    private void runWithParentStack(List<Node> stack, Action runnable) {
        ArrayStack<Node> parents = getParents();

        for (Node node : stack) {
            parents.push(node);
        }

        ExceptionHelper.unchecked(parents.peek().getSourceInfo(), runnable);

        for (Node ignored : stack) {
            parents.pop();
        }
    }

    private static class GeneratorEntry {
        Node node;
        List<Node> parents;
        Generator generator;

        GeneratorEntry(Node node, List<Node> parents, Generator generator) {
            this.node = node;
            this.parents = parents;
            this.generator = generator;
        }
    }

    public static class ClassSet extends AbstractSet<CtClass> {
        private final HashMap<CtClass, Object> map = new HashMap<>();
        private final Object present = new Object();
        private final CtClass markupClass;

        ClassSet(CtClass markupClass) {
            this.markupClass = markupClass;
        }

        public boolean add(CtClass e) {
            return !contains(e.getSimpleName()) && map.put(e, present) == null;
        }

        public boolean contains(String simpleName) {
            return find(simpleName) != null;
        }

        public CtClass find(String simpleName) {
            simpleName = markupClass.getSimpleName() + "$" + simpleName;

            for (CtClass ctclass : this) {
                if (ctclass.getSimpleName().equals(simpleName)) {
                    return ctclass;
                }
            }

            return null;
        }

        @Override
        public Iterator<CtClass> iterator() {
            return map.keySet().iterator();
        }

        @Override
        public int size() {
            return map.size();
        }
    }

}

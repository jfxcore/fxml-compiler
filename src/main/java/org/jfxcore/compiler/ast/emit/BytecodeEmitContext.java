// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import org.jfxcore.compiler.ast.EmitContext;
import org.jfxcore.compiler.ast.GeneratorEmitterNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.generate.ClassGenerator;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BytecodeEmitContext extends EmitContext<Bytecode> {

    private final BytecodeEmitContext parentContext;
    private final EmitInitializeRootNode rootNode;
    private final CtClass codeBehindClass;
    private final CtClass markupClass;
    private final ClassSetImpl nestedClasses;
    private CtClass bindingContextClass;
    private CtClass runtimeContextClass;
    private int runtimeContextLocal;
    private List<GeneratorEntry> activeGenerators;

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
        this.nestedClasses = new ClassSetImpl(markupClass);

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
            boolean[] markupContextSupport = new boolean[1];

            Visitor.visit(rootNode, new Visitor() {
                @Override
                protected Node onVisited(Node node) {
                    if (node instanceof EmitResourceNode) {
                        resourceSupport[0] = true;
                        return Visitor.STOP;
                    }

                    return node;
                }
            });

            Visitor.visit(rootNode, new Visitor() {
                @Override
                protected Node onVisited(Node node) {
                    if (node instanceof EmitApplyMarkupExtensionNode) {
                        markupContextSupport[0] = true;
                        return Visitor.STOP;
                    }

                    return node;
                }
            });

            runtimeContextClass = ClassGenerator.emit(
                this, new RuntimeContextGenerator(resourceSupport[0], markupContextSupport[0]));
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

    public boolean isGeneratorActive(Class<? extends Generator> generatorClass) {
        return getActiveGenerators().stream().anyMatch(entry -> generatorClass.isInstance(entry.generator));
    }

    public void emitRootNode() {
        if (rootNode == null) {
            throw new UnsupportedOperationException();
        }

        List<GeneratorEntry> activeGenerators = getActiveGenerators();

        for (GeneratorEntry entry : activeGenerators) {
            if (entry.generator instanceof ClassGenerator classGenerator) {
                runWithParentStack(entry.parents, () -> classGenerator.emitClass(this));
            }
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

        if (RuntimeContextHelper.needsRuntimeContext(rootNode)) {
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

    private List<GeneratorEntry> getActiveGenerators() {
        if (activeGenerators == null) {
            activeGenerators = getActiveGeneratorsImpl();
        }

        return activeGenerators;
    }

    private List<GeneratorEntry> getActiveGeneratorsImpl() {
        List<GeneratorEntry> generators = new ArrayList<>();

        Visitor.visit(rootNode, new Visitor() {
            final ArrayStack<Node> parents = new ArrayStack<>();

            @Override
            public Node onVisited(Node node) {
                if (node instanceof GeneratorEmitterNode generatorEmitterNode) {
                    List<? extends Generator> list = generatorEmitterNode.emitGenerators(BytecodeEmitContext.this);
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

        List<GeneratorEntry> activeGenerators = new ArrayList<>();

        for (GeneratorEntry entry : generators) {
            activeGenerators.addAll(getActiveSubGeneratorsImpl(this, entry));
        }

        return activeGenerators;
    }

    private List<GeneratorEntry> getActiveSubGeneratorsImpl(BytecodeEmitContext context, GeneratorEntry entry) {
        if (entry.generator.consume(context)) {
            List<GeneratorEntry> result = new ArrayList<>();
            for (Generator subGenerator : entry.generator.getSubGenerators()) {
                GeneratorEntry newEntry = new GeneratorEntry(entry.node, entry.parents, subGenerator);
                result.addAll(getActiveSubGeneratorsImpl(context, newEntry));
            }

            result.add(entry);
            return result;
        }

        return Collections.emptyList();
    }

    public interface ClassSet extends Set<CtClass> {
        CtClass create(String simpleName);
        CtClass find(String simpleName);
    }

    private static class ClassSetImpl extends AbstractSet<CtClass> implements ClassSet {
        private final HashMap<CtClass, Object> map = new HashMap<>();
        private final Object present = new Object();
        private final CtClass markupClass;

        ClassSetImpl(CtClass markupClass) {
            this.markupClass = markupClass;
        }

        @Override
        public CtClass create(String simpleName) {
            CtClass newClass = markupClass.makeNestedClass(simpleName, true);
            map.put(newClass, present);
            return newClass;
        }

        @Override
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

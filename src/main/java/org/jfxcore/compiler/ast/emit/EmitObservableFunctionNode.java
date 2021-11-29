// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.bytecode.MethodInfo;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.GeneratorEmitterNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.expression.BindingContextNode;
import org.jfxcore.compiler.generate.Generator;
import org.jfxcore.compiler.generate.ObservableFunctionGenerator;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.jfxcore.compiler.util.Descriptors.*;

public class EmitObservableFunctionNode
        extends AbstractNode
        implements ValueEmitterNode, GeneratorEmitterNode, ParentStackInfo, NullableInfo {

    private final CtBehavior method;
    private final CtBehavior inverseMethod;
    private final BindingContextNode source;
    private final List<EmitMethodArgumentNode> arguments;
    private final ResolvedTypeNode type;
    private transient String compiledClassName;

    public EmitObservableFunctionNode(
            TypeInstance type,
            CtBehavior method,
            @Nullable CtBehavior inverseMethod,
            BindingContextNode source,
            Collection<? extends EmitMethodArgumentNode> arguments,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.type = new ResolvedTypeNode(checkNotNull(type), sourceInfo);
        this.method = checkNotNull(method);
        this.inverseMethod = inverseMethod;
        this.source = checkNotNull(source);
        this.arguments = new ArrayList<>(checkNotNull(arguments));
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public boolean needsParentStack() {
        return true;
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        acceptChildren(arguments, visitor);
    }

    @Override
    public List<Generator> emitGenerators(BytecodeEmitContext context) {
        String cachedClassName = getClassCache().get(this);
        if (cachedClassName != null) {
            compiledClassName = cachedClassName;
            return Collections.emptyList();
        }

        var generator = new ObservableFunctionGenerator(method, inverseMethod, source, arguments);
        compiledClassName = generator.getClassName();
        getClassCache().put(this, compiledClassName);

        return List.of(generator);
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        CtClass compiledClass = context.getNestedClasses().find(compiledClassName);

        code.anew(compiledClass)
            .dup()
            .aload(context.getRuntimeContextLocal())
            .invokespecial(
                compiledClass,
                MethodInfo.nameInit,
                function(CtClass.voidType, context.getRuntimeContextClass()));
    }

    @Override
    public EmitObservableFunctionNode deepClone() {
        return new EmitObservableFunctionNode(
            type.getTypeInstance(), method, inverseMethod, source.deepClone(), deepClone(arguments), getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitObservableFunctionNode that = (EmitObservableFunctionNode)o;
        return TypeHelper.equals(method, that.method) &&
            TypeHelper.equals(inverseMethod, that.inverseMethod) &&
            source.equals(that.source) &&
            arguments.equals(that.arguments) &&
            type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(TypeHelper.hashCode(method), TypeHelper.hashCode(inverseMethod), source, arguments, type);
    }

    @SuppressWarnings("unchecked")
    private Map<EmitObservableFunctionNode, String> getClassCache() {
        return (Map<EmitObservableFunctionNode, String>)CompilationContext.getCurrent()
            .computeIfAbsent(EmitObservableFunctionNode.class, key -> new HashMap<EmitObservableFunctionNode, String>());
    }

}

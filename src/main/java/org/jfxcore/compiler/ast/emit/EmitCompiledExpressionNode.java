// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.GeneratorEmitterNode;
import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.generate.CompiledExpressionGenerator;
import org.jfxcore.compiler.generate.Generator;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.util.Callable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Final emitter representation of a compiled-expression island. The helper method is declared by
 * {@link #emitGenerators(BytecodeEmitContext)} and its body is emitted by the returned generator.
 */
public final class EmitCompiledExpressionNode
        extends AbstractNode
        implements ValueEmitterNode, GeneratorEmitterNode, ParentStackInfo, NullableInfo {

    private final TypeDeclaration invocationContext;
    private final String helperName;
    private final TypeInstance resultType;
    private final TypeDeclaration[] parameterTypes;
    private final List<EmitMethodArgumentNode> arguments;
    private final boolean observable;
    private final ResolvedTypeNode type;

    private ValueEmitterNode body;

    private transient CompiledExpressionGenerator generator;
    private transient ValueEmitterNode delegate;

    public EmitCompiledExpressionNode(
            TypeDeclaration invocationContext,
            String helperName,
            TypeInstance resultType,
            TypeDeclaration[] parameterTypes,
            ValueEmitterNode body,
            Collection<? extends EmitMethodArgumentNode> arguments,
            boolean observable,
            SourceInfo sourceInfo) {
        super(sourceInfo);

        if (parameterTypes.length != arguments.size()) {
            throw new IllegalArgumentException("arguments");
        }

        this.invocationContext = checkNotNull(invocationContext);
        this.helperName = checkNotNull(helperName);
        this.resultType = checkNotNull(resultType);
        this.parameterTypes = parameterTypes.clone();
        this.body = checkNotNull(body);
        this.arguments = new ArrayList<>(checkNotNull(arguments));
        this.observable = observable;
        this.type = new ResolvedTypeNode(
            observable
                ? new Resolver(sourceInfo).getObservableClass(resultType)
                : resultType,
            sourceInfo);
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    public ValueEmitterNode getBody() {
        return body;
    }

    public List<EmitMethodArgumentNode> getArguments() {
        return List.copyOf(arguments);
    }

    public boolean isObservable() {
        return observable;
    }

    @Override
    public boolean needsParentStack() {
        return observable;
    }

    @Override
    public boolean isNullable() {
        return !observable;
    }

    @Override
    public List<? extends Generator> emitGenerators(BytecodeEmitContext context) {
        if (generator != null) {
            return Collections.emptyList();
        }

        generator = new CompiledExpressionGenerator(
            invocationContext,
            helperName,
            resultType.declaration(),
            parameterTypes,
            body);

        var function = new Callable(
            List.of(TypeInstance.of(invocationContext)),
            List.of(),
            ObservableDependencyKind.NONE,
            generator.getMethod(),
            getSourceInfo());

        delegate = observable
            ? new EmitObservableFunctionNode(
                type.getTypeInstance(),
                function,
                null,
                arguments,
                invocationContext,
                getSourceInfo())
            : new EmitMethodCallNode(
                generator.getMethod(),
                resultType,
                List.of(),
                arguments,
                getSourceInfo());

        return List.of(generator);
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        if (delegate == null) {
            throw new IllegalStateException("Compiled-expression generators have not been emitted");
        }

        context.emit(delegate);
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        body = (ValueEmitterNode)body.accept(visitor);

        if (delegate != null) {
            delegate = (ValueEmitterNode)delegate.accept(visitor);
        } else {
            acceptChildren(arguments, visitor, EmitMethodArgumentNode.class);
        }
    }

    @Override
    public EmitCompiledExpressionNode deepClone() {
        return new EmitCompiledExpressionNode(
            invocationContext,
            helperName,
            resultType,
            parameterTypes,
            body.deepClone(),
            deepClone(arguments),
            observable,
            getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof EmitCompiledExpressionNode that
            && observable == that.observable
            && invocationContext.equals(that.invocationContext)
            && helperName.equals(that.helperName)
            && resultType.equals(that.resultType)
            && Arrays.equals(parameterTypes, that.parameterTypes)
            && body.equals(that.body)
            && arguments.equals(that.arguments);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(invocationContext, helperName, resultType, body, arguments, observable);
        return 31 * result + Arrays.hashCode(parameterTypes);
    }
}

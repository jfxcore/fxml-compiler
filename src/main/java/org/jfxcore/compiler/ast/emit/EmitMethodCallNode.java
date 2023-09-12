// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtMethod;
import javassist.Modifier;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class EmitMethodCallNode extends AbstractNode implements ValueEmitterNode {

    private final CtMethod method;
    private final ResolvedTypeNode type;
    private final List<ValueEmitterNode> methodReceiver;
    private final List<ValueEmitterNode> arguments;

    public EmitMethodCallNode(
            CtMethod method,
            Collection<ValueEmitterNode> methodReceiver,
            Collection<? extends ValueEmitterNode> arguments,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        if (isStatic && methodReceiver.size() > 0 || !isStatic && methodReceiver.size() == 0){
            throw new IllegalArgumentException("methodReceiver");
        }

        this.method = checkNotNull(method);
        this.type = new ResolvedTypeNode(new Resolver(SourceInfo.none()).getTypeInstance(method, List.of()), sourceInfo);
        this.arguments = new ArrayList<>(checkNotNull(arguments));
        this.methodReceiver = new ArrayList<>(checkNotNull(methodReceiver));
    }

    private EmitMethodCallNode(
            CtMethod method,
            ResolvedTypeNode type,
            Collection<ValueEmitterNode> methodReceiver,
            Collection<? extends ValueEmitterNode> arguments,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.method = checkNotNull(method);
        this.type = checkNotNull(type);
        this.arguments = new ArrayList<>(checkNotNull(arguments));
        this.methodReceiver = new ArrayList<>(checkNotNull(methodReceiver));
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();

        for (ValueEmitterNode emitterNode : methodReceiver) {
            context.emit(emitterNode);
        }

        for (EmitterNode argument : arguments) {
            context.emit(argument);
        }

        code.ext_invoke(method);
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        acceptChildren(methodReceiver, visitor);
        acceptChildren(arguments, visitor);
    }

    @Override
    public EmitMethodCallNode deepClone() {
        return new EmitMethodCallNode(
            method,
            type.deepClone(),
            deepClone(methodReceiver),
            deepClone(arguments),
            getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitMethodCallNode that = (EmitMethodCallNode)o;
        return TypeHelper.equals(method, that.method) &&
            type.equals(that.type) &&
            arguments.equals(that.arguments) &&
            methodReceiver.equals(that.methodReceiver);
    }

    @Override
    public int hashCode() {
        return Objects.hash(TypeHelper.hashCode(method), type, arguments, methodReceiver);
    }

}

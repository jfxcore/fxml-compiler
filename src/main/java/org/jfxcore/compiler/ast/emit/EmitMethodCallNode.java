// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Label;
import org.jfxcore.compiler.util.Local;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class EmitMethodCallNode extends AbstractNode implements ValueEmitterNode {

    private final MethodDeclaration method;
    private final ResolvedTypeNode type;
    private final List<ValueEmitterNode> methodReceiver;
    private final List<ValueEmitterNode> arguments;

    public EmitMethodCallNode(
            MethodDeclaration method,
            TypeInstance returnType,
            Collection<ValueEmitterNode> methodReceiver,
            Collection<? extends ValueEmitterNode> arguments,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        boolean isStatic = method.isStatic();
        if (isStatic && !methodReceiver.isEmpty() || !isStatic && methodReceiver.isEmpty()){
            throw new IllegalArgumentException("methodReceiver");
        }

        this.method = checkNotNull(method);
        this.type = new ResolvedTypeNode(returnType, sourceInfo);
        this.arguments = new ArrayList<>(checkNotNull(arguments));
        this.methodReceiver = new ArrayList<>(checkNotNull(methodReceiver));
    }

    private EmitMethodCallNode(
            MethodDeclaration method,
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
        Local receiverLocal = null;
        Label endLabel = null;

        if (!method.isStatic()) {
            TypeInstance receiverType = TypeHelper.getTypeInstance(methodReceiver.get(methodReceiver.size() - 1));
            receiverLocal = code.acquireLocal(receiverType.declaration());

            for (ValueEmitterNode emitterNode : methodReceiver) {
                context.emit(emitterNode);
            }

            Label receiverIsNonNull = code
                .store(receiverType.declaration(), receiverLocal)
                .load(receiverType.declaration(), receiverLocal)
                .ifnonnull();

            code.defaultconst(type.getTypeDeclaration());
            endLabel = code.goto_label();
            receiverIsNonNull.resume();
            code.load(receiverType.declaration(), receiverLocal);
        } else {
            for (ValueEmitterNode emitterNode : methodReceiver) {
                context.emit(emitterNode);
            }
        }

        for (EmitterNode argument : arguments) {
            context.emit(argument);
        }

        code.invoke(method)
            .castconv(method.returnType(), type.getTypeDeclaration());

        if (endLabel != null) {
            endLabel.resume();
            code.releaseLocal(receiverLocal);
        }
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        acceptChildren(methodReceiver, visitor, ValueEmitterNode.class);
        acceptChildren(arguments, visitor, ValueEmitterNode.class);
    }

    @Override
    public EmitMethodCallNode deepClone() {
        return new EmitMethodCallNode(
            method,
            type.deepClone(),
            deepClone(methodReceiver),
            deepClone(arguments),
            getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitMethodCallNode that = (EmitMethodCallNode)o;
        return method.equals(that.method) &&
            type.equals(that.type) &&
            arguments.equals(that.arguments) &&
            methodReceiver.equals(that.methodReceiver);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, type, arguments, methodReceiver);
    }
}

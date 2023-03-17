// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Label;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.TypeHelper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Emits bytecodes that resolve a path expression to a value at runtime.
 * The type of the resolved value corresponds to the type of the last path segment.
 *
 * {@link EmitObservablePathNode} can be used to resolve a path expression to an observable value that
 * emits change notifications along the path.
 */
public class EmitInvariantPathNode extends AbstractNode implements ValueEmitterNode, NullableInfo {

    private final List<ValueEmitterNode> children;
    private ResolvedTypeNode type;

    public EmitInvariantPathNode(Collection<? extends ValueEmitterNode> children, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.children = new ArrayList<>(checkNotNull(children));
        this.type = new ResolvedTypeNode(TypeHelper.getTypeInstance(this.children.get(this.children.size() - 1)), sourceInfo);
    }

    @Override
    public boolean isNullable() {
        for (EmitterNode child : children) {
            if (NullableInfo.isNullable(child, true)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        CtClass type = this.type.getJvmType();
        boolean anyNullable = false;

        for (int i = 0; i < children.size() - 1; ++i) {
            if (NullableInfo.isNullable(children.get(i), false)) {
                anyNullable = true;
                break;
            }
        }

        if (anyNullable) {
            Local resultLocal = code.acquireLocal(type);
            Local tempLocal = code.acquireLocal(false);

            code.ext_defaultconst(type)
                .ext_store(type, resultLocal);

            List<Label> labels = new ArrayList<>();

            for (int i = 0; i < children.size(); ++i) {
                Node child = children.get(i);
                boolean nullable = NullableInfo.isNullable(child, false);
                boolean lastChild = i == children.size() - 1;

                context.emit(child);

                if (lastChild) {
                    code.ext_store(type, resultLocal);
                } else if (nullable) {
                    Label label = code
                        .astore(tempLocal)
                        .aload(tempLocal)
                        .ifnull();

                    code.aload(tempLocal);

                    labels.add(label);
                }
            }

            labels.forEach(Label::resume);

            code.ext_load(type, resultLocal);

            code.releaseLocal(resultLocal);
            code.releaseLocal(tempLocal);
        } else {
            for (Node child : children) {
                context.emit(child);
            }
        }
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        type = (ResolvedTypeNode)type.accept(visitor);
        acceptChildren(children, visitor);
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public EmitInvariantPathNode deepClone() {
        return new EmitInvariantPathNode(deepClone(children), getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitInvariantPathNode that = (EmitInvariantPathNode)o;
        return children.equals(that.children) && type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(children, type);
    }

}

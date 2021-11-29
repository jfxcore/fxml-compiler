// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.generate.RuntimeContextGenerator;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.jfxcore.compiler.util.Classes.*;

public class EmitGetParentNode
        extends AbstractNode
        implements ValueEmitterNode, NullableInfo, ParentStackInfo {

    private final TypeInstance searchType;
    private final Integer level;
    private ResolvedTypeNode type;
    private transient Local local;

    public EmitGetParentNode(
            TypeInstance type,
            @Nullable TypeInstance searchType,
            @Nullable Integer level,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.type = new ResolvedTypeNode(checkNotNull(type), sourceInfo);
        this.searchType = searchType;
        this.level = level;
    }

    public Local getLocal() {
        return local;
    }

    public void setLocal(Local local) {
        this.local = local;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        List<Node> parents = context.getParents().stream()
            .filter(node -> node instanceof EmitObjectNode)
            .collect(Collectors.toList());

        Bytecode code = context.getOutput();
        int parentIndex= -1;

        if (searchType == null) {
            parentIndex = level != null ? (parents.size() - level - 1) : 0;
        } else {
            for (int i = parents.size() - 1, match = 0; i >= 0; --i) {
                TypeInstance type = ((EmitObjectNode)parents.get(i)).getType().getTypeInstance();

                if (type.subtypeOf(searchType)) {
                    if (level != null) {
                        match++;

                        if (match == level) {
                            parentIndex = i;
                            break;
                        }
                    } else {
                        parentIndex = i;
                        break;
                    }
                }
            }

            if (parentIndex == -1) {
                throw BindingSourceErrors.parentTypeNotFound(getSourceInfo(), type.getName());
            }
        }

        code.aload(context.getRuntimeContextLocal())
            .getfield(
                context.getRuntimeContextClass(),
                RuntimeContextGenerator.PARENTS_FIELD,
                RuntimeContextGenerator.getParentArrayType())
            .iconst(parentIndex)
            .ext_arrayload(ObjectType())
            .checkcast(type.getJvmType());
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        type = (ResolvedTypeNode)type.accept(visitor);
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
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public EmitGetParentNode deepClone() {
        return new EmitGetParentNode(type.getTypeInstance(), searchType, level, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitGetParentNode that = (EmitGetParentNode)o;
        return Objects.equals(level, that.level) &&
            Objects.equals(searchType, that.searchType) &&
            type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, searchType, type);
    }

}

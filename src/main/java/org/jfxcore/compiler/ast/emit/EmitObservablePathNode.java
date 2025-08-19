// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javafx.beans.value.ObservableValue;
import javassist.CtClass;
import javassist.bytecode.MethodInfo;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.GeneratorEmitterNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.expression.path.ResolvedPath;
import org.jfxcore.compiler.generate.ClassGenerator;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Descriptors;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.ObservableKind;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Emits bytecodes that resolve a path expression to an observable value at runtime.
 * The runtime type of this node is an {@link ObservableValue} that can be used as a binding source.
 *
 * If no observable value is required, {@link EmitInvariantPathNode} can be used to resolve a path to a value
 * without the overhead of change notifications.
 */
public class EmitObservablePathNode
        extends AbstractNode implements ValueEmitterNode, GeneratorEmitterNode, NullableInfo {

    private final ResolvedPath path;
    private final int leadingInvariantSegments;
    private final boolean useCompiledPath;
    private final boolean bidirectional;
    private final transient List<ClassGenerator> generators;
    private ResolvedTypeNode type;
    private EmitInvariantPathNode invariantPath;
    private EmitValueWrapperNode leadingValueWrapper;
    private EmitValueWrapperNode constructorWrapper;

    public EmitObservablePathNode(ResolvedPath path, boolean bidirectional, SourceInfo sourceInfo) {
        this(path, bidirectional, null, sourceInfo);

        if (leadingInvariantSegments > 0) {
            this.invariantPath = new EmitInvariantPathNode(
                path.subPath(0, leadingInvariantSegments).toValueEmitters(true, sourceInfo), sourceInfo);
        }
    }

    private EmitObservablePathNode(
            ResolvedPath path,
            boolean bidirectional,
            @Nullable EmitInvariantPathNode invariantPath,
            SourceInfo sourceInfo) {
        super(sourceInfo);

        this.path = checkNotNull(path);
        this.leadingInvariantSegments = getLeadingInvariantSegments(path);
        int trailingSegments = path.size() - leadingInvariantSegments;
        this.useCompiledPath = trailingSegments > 0 &&
            (trailingSegments > 1 || path.get(leadingInvariantSegments).getObservableKind() == ObservableKind.NONE);
        this.invariantPath = invariantPath;
        this.bidirectional = bidirectional;

        if (this.useCompiledPath) {
            this.generators = path.fold().toGenerators();
            this.type = new ResolvedTypeNode(generators.get(0).getTypeInstance(), sourceInfo);
        } else {
            this.generators = Collections.emptyList();
            this.type = new ResolvedTypeNode(path.getTypeInstance(), sourceInfo);
        }

        if (leadingInvariantSegments > 1) {
            if (useCompiledPath) {
                TypeInstance type = path.getValueTypeInstance();
                constructorWrapper = new EmitValueWrapperNode(new EmitNopNode(type, getSourceInfo()));
            } else if (!bidirectional) {
                TypeInstance type = path.get(leadingInvariantSegments).getValueTypeInstance();
                leadingValueWrapper = new EmitValueWrapperNode(new EmitNopNode(type, getSourceInfo()));
            }
        }
    }

    public boolean isCompiledPath() {
        return useCompiledPath;
    }

    public ResolvedPath getPath() {
        return path;
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        type = (ResolvedTypeNode)type.accept(visitor);

        if (invariantPath != null) {
            invariantPath = (EmitInvariantPathNode)invariantPath.accept(visitor);
        }

        if (leadingValueWrapper != null) {
            leadingValueWrapper = (EmitValueWrapperNode)leadingValueWrapper.accept(visitor);
        }

        if (constructorWrapper != null) {
            constructorWrapper = (EmitValueWrapperNode)constructorWrapper.accept(visitor);
        }
    }

    @Override
    public boolean isNullable() {
        return !useCompiledPath && invariantPath.isNullable();
    }

    @Override
    public List<ClassGenerator> emitGenerators(BytecodeEmitContext context) {
        return generators;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        boolean mayReturnNull;
        Local constructorArgLocal;
        String compiledClassName;

        if (useCompiledPath) {
            compiledClassName = generators.get(0).getClassName();
            constructorArgLocal = code.acquireLocal(false);
            mayReturnNull = false;
        } else {
            compiledClassName = null;
            constructorArgLocal = null;
            mayReturnNull = bidirectional;
        }

        if (invariantPath != null) {
            context.emit(invariantPath);
        }

        if (leadingInvariantSegments > 1 && invariantPath != null && invariantPath.isNullable()) {
            Local local = code.acquireLocal(false);

            code.astore(local)
                .aload(local)
                .ifnonnull(
                    () -> {
                        code.aload(local);
                        context.emit(path.get(leadingInvariantSegments).toEmitter(false, getSourceInfo()));
                    },
                    () -> {
                        if (mayReturnNull || useCompiledPath) {
                            code.aconst_null();
                        } else {
                            code.ext_defaultconst(leadingValueWrapper.getValueType());
                            context.emit(leadingValueWrapper);
                        }
                    });

            code.releaseLocal(local);
        } else {
            context.emit(path.get(leadingInvariantSegments).toEmitter(false, getSourceInfo()));
        }

        if (useCompiledPath) {
            code.astore(constructorArgLocal);

            Runnable invokeConstructor = () -> {
                CtClass compiledClass = context.getNestedClasses().find(compiledClassName);

                code.anew(compiledClass)
                    .dup()
                    .aload(constructorArgLocal)
                    .invokespecial(
                        compiledClass,
                        MethodInfo.nameInit,
                        Descriptors.constructor(
                            unchecked(() -> compiledClass.getDeclaredConstructors()[0].getParameterTypes()[0])));
            };

            if (leadingInvariantSegments > 1) {
                code.aload(constructorArgLocal)
                    .ifnonnull(
                        invokeConstructor,
                        () -> {
                            code.ext_defaultconst(constructorWrapper.getValueType());
                            context.emit(constructorWrapper);
                        });
            } else {
                invokeConstructor.run();
            }
        }
    }

    private int getLeadingInvariantSegments(ResolvedPath path) {
        for (int i = 0; i < path.size(); ++i) {
            if (path.get(i).getObservableKind() != ObservableKind.NONE) {
                return i;
            }
        }

        return path.size();
    }

    @Override
    public EmitObservablePathNode deepClone() {
        return new EmitObservablePathNode(path, bidirectional, invariantPath.deepClone(), getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitObservablePathNode that = (EmitObservablePathNode)o;
        return bidirectional == that.bidirectional &&
            path.equals(that.path) &&
            Objects.equals(invariantPath, that.invariantPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, bidirectional, invariantPath);
    }
}

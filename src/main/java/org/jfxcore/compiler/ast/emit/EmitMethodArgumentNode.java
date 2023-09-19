// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a function argument. In case this is a varargs-style argument, also emits
 * bytecodes to store the argument value(s) into an array of the appropriate type.
 */
public class EmitMethodArgumentNode extends AbstractNode implements ValueEmitterNode {

    private final ResolvedTypeNode type;
    private final List<ValueEmitterNode> children;
    private final boolean varargs;
    private final boolean observable;

    public static EmitMethodArgumentNode newScalar(
            TypeInstance type,
            ValueEmitterNode node,
            boolean observable,
            SourceInfo sourceInfo) {
        return new EmitMethodArgumentNode(
            new ResolvedTypeNode(type, sourceInfo), List.of(node), false, observable, sourceInfo);
    }

    public static EmitMethodArgumentNode newVariadic(
            TypeInstance componentType,
            List<EmitMethodArgumentNode> children,
            SourceInfo sourceInfo) {
        return new EmitMethodArgumentNode(
            new ResolvedTypeNode(
                TypeInstance.of(new Resolver(sourceInfo).resolveClass(componentType.jvmType().getName() + "[]"))
                    .withArguments(componentType.getArguments())
                    .withSuperTypes(componentType.getSuperTypes()),
                sourceInfo),
            children.stream()
                .flatMap(n -> n.children.stream())
                .collect(Collectors.toCollection(ArrayList::new)),
            true,
            children.stream().anyMatch(EmitMethodArgumentNode::isObservable),
            sourceInfo);
    }

    private EmitMethodArgumentNode(
            ResolvedTypeNode type,
            Collection<? extends ValueEmitterNode> children,
            boolean varargs,
            boolean observable,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.type = type;
        this.children = new ArrayList<>(children);
        this.varargs = varargs;
        this.observable = observable;
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    public List<ValueEmitterNode> getChildren() {
        return children;
    }

    public boolean isVarargs() {
        return varargs;
    }

    public boolean isObservable() {
        return observable;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        acceptChildren(children, visitor);
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        boolean assignable = TypeHelper.getTypeInstance(children.get(0)).subtypeOf(type.getTypeInstance());

        if (varargs && !(children.size() == 1 && assignable)) {
            CtClass componentType = type.getTypeInstance().getComponentType().jvmType();
            code.newarray(componentType, children.size());

            for (int i = 0; i < children.size(); ++i) {
                code.dup().iconst(i);

                context.emit(children.get(i));

                code.ext_autoconv(getSourceInfo(), TypeHelper.getJvmType(children.get(i)), componentType)
                    .ext_arraystore(componentType);
            }
        } else {
            context.emit(children.get(0));
            code.ext_autoconv(getSourceInfo(), TypeHelper.getJvmType(children.get(0)), type.getJvmType());
        }
    }

    @Override
    public EmitMethodArgumentNode deepClone() {
        return new EmitMethodArgumentNode(type.deepClone(), deepClone(children), varargs, observable, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitMethodArgumentNode that = (EmitMethodArgumentNode)o;
        return varargs == that.varargs &&
            observable == that.observable &&
            type.equals(that.type) &&
            children.equals(that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, children, varargs, observable);
    }

}

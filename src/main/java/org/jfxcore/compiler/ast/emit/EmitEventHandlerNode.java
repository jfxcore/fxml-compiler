// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.generate.ClassGenerator;
import org.jfxcore.compiler.generate.EventHandlerGenerator;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import java.util.Objects;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

public class EmitEventHandlerNode extends AbstractNode implements ValueEmitterNode {

    private final CtClass declaringClass;
    private final CtClass eventType;
    private final String eventHandlerName;
    private final ResolvedTypeNode type;

    public EmitEventHandlerNode(CtClass declaringClass, CtClass eventType, String eventHandlerName, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.declaringClass = checkNotNull(declaringClass);
        this.eventType = checkNotNull(eventType);
        this.eventHandlerName = checkNotNull(eventHandlerName);
        this.type = new ResolvedTypeNode(new Resolver(sourceInfo).getTypeInstance(EventHandlerType()), sourceInfo);
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();

        var generator = new EventHandlerGenerator(
            context.getBindingContextClass(), eventType, eventHandlerName);

        CtClass handlerClass = ClassGenerator.emit(context, generator);

        code.anew(handlerClass)
            .dup()
            .aload(0)
            .checkcast(declaringClass)
            .invokespecial(handlerClass, MethodInfo.nameInit, constructor(declaringClass));
    }

    @Override
    public EmitEventHandlerNode deepClone() {
        return new EmitEventHandlerNode(declaringClass, eventType, eventHandlerName, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitEventHandlerNode that = (EmitEventHandlerNode)o;
        return TypeHelper.equals(declaringClass, that.declaringClass) &&
            TypeHelper.equals(eventType, that.eventType) &&
            eventHandlerName.equals(that.eventHandlerName) &&
            type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            TypeHelper.hashCode(declaringClass), TypeHelper.hashCode(eventType), eventHandlerName, type);
    }

}

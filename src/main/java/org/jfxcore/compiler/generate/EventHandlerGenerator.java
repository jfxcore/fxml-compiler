// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.Descriptors;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;

import java.util.Collections;
import java.util.List;

import static org.jfxcore.compiler.util.ExceptionHelper.*;

public class EventHandlerGenerator extends GeneratorBase {

    private static final String HANDLER_CLASS_REF = "$0";

    private final TypeInstance type;
    private final CtClass declaringClass;
    private final CtClass eventType;
    private final String eventHandlerName;
    private CtConstructor constructor;
    private CtMethod handleMethod;

    public EventHandlerGenerator(CtClass declaringClass, CtClass eventType, String eventHandlerName) {
        this.declaringClass = declaringClass;
        this.eventType = eventType;
        this.eventHandlerName = eventHandlerName;

        Resolver resolver = new Resolver(SourceInfo.none());
        this.type = resolver.getTypeInstance(Classes.EventHandlerType(), List.of(resolver.getTypeInstance(eventType)));
    }

    @Override
    public String getClassName() {
        return NameHelper.getUniqueName("EventHandler", this);
    }

    @Override
    public TypeInstance getTypeInstance() {
        return type;
    }

    @Override
    public void emitClass(BytecodeEmitContext context) {
        clazz = context.getMarkupClass().makeNestedClass(getClassName(), true);
        clazz.addInterface(Classes.EventHandlerType());
        clazz.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        context.getNestedClasses().add(clazz);
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField field = new CtField(declaringClass, HANDLER_CLASS_REF, clazz);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        clazz.addField(field);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        super.emitMethods(context);

        constructor = new CtConstructor(new CtClass[] {declaringClass}, clazz);
        constructor.setModifiers(Modifier.PUBLIC);
        clazz.addConstructor(constructor);

        handleMethod = new CtMethod(CtClass.voidType, "handle", new CtClass[] {Classes.EventTypent()}, clazz);
        handleMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        clazz.addMethod(handleMethod);
    }

    @Override
    public void emitCode(BytecodeEmitContext parentContext) throws Exception {
        super.emitCode(parentContext);

        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, clazz, 2, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .invokespecial(clazz.getSuperclass(), MethodInfo.nameInit, Descriptors.constructor())
            .aload(0)
            .aload(1)
            .putfield(clazz, HANDLER_CLASS_REF, declaringClass)
            .vreturn();

        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(clazz.getClassPool());

        context = new BytecodeEmitContext(parentContext, clazz, 2, -1);
        code = context.getOutput();
        CtMethod method = findMethod(context);

        code.aload(0)
            .getfield(clazz, HANDLER_CLASS_REF, declaringClass);

        if (method.getParameterTypes().length > 0) {
            code.aload(1)
                .checkcast(eventType);
        }

        code.ext_invoke(method)
            .vreturn();

        handleMethod.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        handleMethod.getMethodInfo().rebuildStackMap(clazz.getClassPool());
    }

    private CtMethod findMethod(BytecodeEmitContext context) {
        SourceInfo sourceInfo = context.getParent().getSourceInfo();
        Resolver resolver = new Resolver(sourceInfo);
        boolean[] matchesByName = new boolean[1];

        CtMethod[] methods = resolver.resolveMethods(declaringClass, method -> {
            if (!method.getName().equals(eventHandlerName)) {
                return false;
            }

            matchesByName[0] = true;

            if (unchecked(sourceInfo, () -> !TypeHelper.equals(method.getReturnType(), CtClass.voidType))) {
                return false;
            }

            TypeInstance[] paramTypes = resolver.getParameterTypes(method, Collections.emptyList());
            if (paramTypes.length == 0) {
                return true;
            }

            return paramTypes.length == 1 && unchecked(sourceInfo, () -> paramTypes[0].subtypeOf(eventType));
        });

        if (methods.length == 0) {
            if (matchesByName[0]) {
                throw PropertyAssignmentErrors.unsuitableEventHandler(sourceInfo, eventType, eventHandlerName);
            }

            throw SymbolResolutionErrors.methodNotFound(sourceInfo, declaringClass, eventHandlerName);
        }

        for (CtMethod method : methods) {
            if (resolver.getParameterTypes(method, Collections.emptyList()).length > 0) {
                return method;
            }
        }

        return methods[0];
    }

}

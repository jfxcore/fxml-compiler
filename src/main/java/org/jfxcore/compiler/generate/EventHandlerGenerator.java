// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
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
import org.jfxcore.compiler.util.AccessVerifier;
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

public class EventHandlerGenerator extends ClassGenerator {

    private static final String HANDLER_CLASS_REF = "$0";

    private final TypeInstance type;
    private final CtClass bindingContextClass;
    private final CtClass eventType;
    private final String eventHandlerName;
    private CtConstructor constructor;
    private CtMethod handleMethod;

    public EventHandlerGenerator(CtClass bindingContextClass, CtClass eventType, String eventHandlerName) {
        this.bindingContextClass = bindingContextClass;
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
        generatedClass = context.getNestedClasses().create(getClassName());
        generatedClass.addInterface(Classes.EventHandlerType());
        generatedClass.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField field = new CtField(bindingContextClass, HANDLER_CLASS_REF, generatedClass);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addField(field);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        super.emitMethods(context);

        constructor = new CtConstructor(new CtClass[] {bindingContextClass}, generatedClass);
        constructor.setModifiers(Modifier.PUBLIC);
        generatedClass.addConstructor(constructor);

        handleMethod = new CtMethod(CtClass.voidType, "handle", new CtClass[] {Classes.EventType()}, generatedClass);
        handleMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(handleMethod);
    }

    @Override
    public void emitCode(BytecodeEmitContext parentContext) throws Exception {
        super.emitCode(parentContext);

        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, generatedClass, 2, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .invokespecial(generatedClass.getSuperclass(), MethodInfo.nameInit, Descriptors.constructor())
            .aload(0)
            .aload(1)
            .putfield(generatedClass, HANDLER_CLASS_REF, bindingContextClass)
            .vreturn();

        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());

        context = new BytecodeEmitContext(parentContext, generatedClass, 2, -1);
        code = context.getOutput();
        CtMethod method = findMethod(context);

        code.aload(0)
            .getfield(generatedClass, HANDLER_CLASS_REF, bindingContextClass);

        if (method.getParameterTypes().length > 0) {
            code.aload(1)
                .checkcast(eventType);
        }

        code.ext_invoke(method)
            .vreturn();

        handleMethod.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        handleMethod.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    private CtMethod findMethod(BytecodeEmitContext context) {
        SourceInfo sourceInfo = context.getCurrent().getSourceInfo();
        Resolver resolver = new Resolver(sourceInfo);
        boolean[] matchesByName = new boolean[1];

        CtMethod[] methods = resolver.resolveMethods(bindingContextClass, method -> {
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

            throw SymbolResolutionErrors.memberNotFound(sourceInfo, bindingContextClass, eventHandlerName);
        }

        CtMethod selectedMethod = methods[0];

        for (CtMethod method : methods) {
            if (resolver.getParameterTypes(method, Collections.emptyList()).length > 0) {
                selectedMethod = method;
                break;
            }
        }

        AccessVerifier.verifyAccessible(selectedMethod, context.getMarkupClass(), sourceInfo);

        return selectedMethod;
    }

}

// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.AccessVerifier;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.jfxcore.compiler.type.KnownSymbols.*;

public class EventHandlerGenerator extends ClassGenerator {

    private static final String HANDLER_CLASS_REF = "$0";

    private final TypeInstance type;
    private final TypeDeclaration bindingContextClass;
    private final TypeDeclaration eventType;
    private final String eventHandlerName;
    private ConstructorDeclaration constructor;
    private MethodDeclaration handleMethod;

    public EventHandlerGenerator(TypeDeclaration bindingContextClass, TypeDeclaration eventType, String eventHandlerName) {
        this.bindingContextClass = bindingContextClass;
        this.eventType = eventType;
        this.eventHandlerName = eventHandlerName;

        TypeInvoker invoker = new TypeInvoker(SourceInfo.none());
        this.type = invoker.invokeType(EventHandlerDecl(), List.of(invoker.invokeType(eventType)));
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
    public TypeDeclaration emitClass(BytecodeEmitContext context) {
        return super.emitClass(context)
            .addInterface(EventHandlerDecl())
            .setModifiers(Modifier.PRIVATE | Modifier.FINAL);
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {
        createField(HANDLER_CLASS_REF, bindingContextClass).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) {
        super.emitMethods(context);

        constructor = createConstructor(bindingContextClass).setModifiers(Modifier.PUBLIC);
        handleMethod = createMethod("handle", voidDecl(), EventDecl()).setModifiers(Modifier.PUBLIC | Modifier.FINAL);
    }

    @Override
    public void emitCode(BytecodeEmitContext parentContext) {
        FieldDeclaration handlerClassRefField = requireDeclaredField(HANDLER_CLASS_REF);
        Bytecode code = new Bytecode(constructor);

        code.aload(0)
            .invoke(requireSuperClass().requireDeclaredConstructor())
            .aload(0)
            .aload(1)
            .putfield(handlerClassRefField)
            .vreturn();

        constructor.setCode(code);

        code = new Bytecode(handleMethod);
        MethodDeclaration method = findMethod(parentContext);

        code.aload(0)
            .getfield(handlerClassRefField);

        if (!method.parameters().isEmpty()) {
            code.aload(1)
                .checkcast(eventType);
        }

        code.invoke(method)
            .vreturn();

        handleMethod.setCode(code);
    }

    private MethodDeclaration findMethod(BytecodeEmitContext context) {
        SourceInfo sourceInfo = context.getCurrent().getSourceInfo();
        Resolver resolver = new Resolver(sourceInfo);
        TypeInvoker invoker = new TypeInvoker(sourceInfo);
        boolean[] matchesByName = new boolean[1];

        MethodDeclaration[] methods = resolver.resolveMethods(bindingContextClass, method -> {
            if (!method.name().equals(eventHandlerName)) {
                return false;
            }

            matchesByName[0] = true;

            if (!method.returnType().equals(voidDecl())) {
                return false;
            }

            TypeInstance[] paramTypes = invoker.invokeParameterTypes(method, List.of());
            if (paramTypes.length == 0) {
                return true;
            }

            return paramTypes.length == 1 && paramTypes[0].subtypeOf(eventType);
        });

        if (methods.length == 0) {
            if (matchesByName[0]) {
                throw PropertyAssignmentErrors.unsuitableEventHandler(sourceInfo, eventType, eventHandlerName);
            }

            throw SymbolResolutionErrors.memberNotFound(sourceInfo, bindingContextClass, eventHandlerName);
        }

        MethodDeclaration selectedMethod = methods[0];

        for (MethodDeclaration method : methods) {
            if (invoker.invokeParameterTypes(method, List.of()).length > 0) {
                selectedMethod = method;
                break;
            }
        }

        AccessVerifier.verifyAccessible(selectedMethod, context.getMarkupClass(), sourceInfo);

        return selectedMethod;
    }
}

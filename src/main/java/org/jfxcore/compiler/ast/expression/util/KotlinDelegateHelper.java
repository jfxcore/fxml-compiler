// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.ExceptionHelper;
import org.jfxcore.compiler.util.Resolver;

public class KotlinDelegateHelper {

    public static CtMethod getKotlinDelegateGetter(SourceInfo sourceInfo, CtField delegateField) {
        return ExceptionHelper.unchecked(sourceInfo, () -> getKotlinDelegateGetter(delegateField));
    }

    private static CtMethod getKotlinDelegateGetter(CtField delegateField) throws Exception {
        CtClass declaringClass = delegateField.getDeclaringClass();
        String getterName = delegateField.getName();

        // We need to disable the resolver cache because tryResolveGetter will return a different result
        // when calling it with the same arguments for a second time.
        Resolver resolver = new Resolver(SourceInfo.none(), false);
        CtMethod getter = resolver.tryResolveGetter(declaringClass, getterName, true, delegateField.getType());
        if (getter != null) {
            return getter;
        }

        return addKotlinDelegateGetter(delegateField, getterName);
    }

    private static CtMethod addKotlinDelegateGetter(CtField delegateField, String getterName) throws Exception {
        CtClass declaringClass = delegateField.getDeclaringClass();

        Bytecode code = new Bytecode(declaringClass, 1);
        code.aload(0)
            .getfield(declaringClass, delegateField.getName(), delegateField.getType())
            .areturn();

        CtMethod getter = new CtMethod(delegateField.getType(), getterName, new CtClass[0], declaringClass);
        getter.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        getter.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        getter.getMethodInfo().rebuildStackMap(declaringClass.getClassPool());
        declaringClass.addMethod(getter);

        CompilationContext.getCurrent().addModifiedClass(declaringClass);
        return getter;
    }

}

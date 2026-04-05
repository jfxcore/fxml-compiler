// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.CompilationContext;
import java.lang.reflect.Modifier;

public class KotlinDelegateHelper {

    public static MethodDeclaration getKotlinDelegateGetter(FieldDeclaration delegateField) {
        TypeDeclaration declaringClass = delegateField.declaringType();
        String getterName = delegateField.name();

        // We need to disable the resolver cache because tryResolveGetter will return a different result
        // when calling it with the same arguments for a second time.
        Resolver resolver = new Resolver(SourceInfo.none(), false);
        MethodDeclaration getter = resolver.tryResolveGetter(declaringClass, getterName, true, delegateField.type());
        if (getter != null) {
            return getter;
        }

        return addKotlinDelegateGetter(delegateField, getterName);
    }

    private static MethodDeclaration addKotlinDelegateGetter(FieldDeclaration delegateField, String getterName) {
        TypeDeclaration declaringClass = delegateField.declaringType();

        Bytecode code = new Bytecode(declaringClass, 1);
        code.aload(0)
            .getfield(declaringClass.requireDeclaredField(delegateField.name()))
            .areturn();

        MethodDeclaration getter = declaringClass
            .createMethod(getterName, delegateField.type())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL)
            .setCode(code);

        CompilationContext.getCurrent().addModifiedClass(declaringClass);
        return getter;
    }
}

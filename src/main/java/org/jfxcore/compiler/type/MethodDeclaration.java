// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.type;

import javassist.CtMethod;
import javassist.NotFoundException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.util.Bytecode;

public final class MethodDeclaration extends BehaviorDeclaration {

    public static MethodDeclaration of(CtMethod method) {
        return new MethodDeclaration(method, TypeDeclaration.of(method.getDeclaringClass()));
    }

    MethodDeclaration(CtMethod method, TypeDeclaration declaringType) {
        super(method, declaringType);
    }

    public TypeDeclaration returnType() {
        try {
            return TypeDeclaration.of(this.<CtMethod>member().getReturnType());
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.classNotFound(SourceInfo.none(), ex.getMessage());
        }
    }

    @Override
    public String displaySignature(boolean simpleNames, boolean paramNames) {
        return displaySignature(
            simpleNames ? name() : declaringType().javaName() + "." + name(),
            simpleNames, paramNames);
    }

    @Override
    public int occupiedSlots() {
        return parameters().stream().mapToInt(p -> p.type().slots()).sum() + (isStatic() ? 0 : 1);
    }

    @Override
    public MethodDeclaration setModifiers(int modifiers) {
        super.setModifiers(modifiers);
        declaringType().updateMethods();
        return this;
    }

    @Override
    public MethodDeclaration setCode(Bytecode code) {
        return (MethodDeclaration)super.setCode(code);
    }
}

// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.type;

import javassist.CtConstructor;
import org.jfxcore.compiler.util.Bytecode;
import java.util.Optional;

public final class ConstructorDeclaration extends BehaviorDeclaration {

    public static ConstructorDeclaration of(CtConstructor constructor) {
        return new ConstructorDeclaration(constructor, TypeDeclaration.of(constructor.getDeclaringClass()));
    }

    ConstructorDeclaration(CtConstructor constructor, TypeDeclaration declaringType) {
        super(constructor, declaringType);
    }

    public boolean requiresEnclosingInstance() {
        return !declaringType().isStatic() && declaringType().declaringType().isPresent();
    }

    public Optional<TypeDeclaration> enclosingInstanceType() {
        return requiresEnclosingInstance() ? declaringType().declaringType() : Optional.empty();
    }

    @Override
    public String displaySignature(boolean simpleNames, boolean paramNames) {
        return displaySignature(
            simpleNames ? declaringType().simpleName() : declaringType().javaName(),
            simpleNames, paramNames);
    }

    @Override
    public int occupiedSlots() {
        return parameters().stream().mapToInt(p -> p.type().slots()).sum() + 1;
    }

    @Override
    public ConstructorDeclaration setModifiers(int modifiers) {
        super.setModifiers(modifiers);
        declaringType().updateConstructors();
        return this;
    }

    @Override
    public ConstructorDeclaration setCode(Bytecode bytecode) {
        return (ConstructorDeclaration)super.setCode(bytecode);
    }
}

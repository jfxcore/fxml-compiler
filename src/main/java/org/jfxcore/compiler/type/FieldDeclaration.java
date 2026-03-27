// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.type;

import javassist.CtField;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.SyntheticAttribute;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;

public final class FieldDeclaration extends MemberDeclaration {

    FieldDeclaration(CtField field, TypeDeclaration declaringType) {
        super(field, declaringType);
    }

    public String signature() {
        return this.<CtField>member().getSignature();
    }

    public TypeDeclaration type() {
        try {
            return TypeDeclaration.of(this.<CtField>member().getType());
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.classNotFound(SourceInfo.none(), ex.getMessage());
        }
    }

    @Override
    public boolean isSynthetic() {
        return this.<CtField>member().getFieldInfo2().getAttribute(SyntheticAttribute.tag) != null;
    }

    @Override
    public FieldDeclaration setModifiers(int modifiers) {
        super.setModifiers(modifiers);
        declaringType().updateFields();
        return this;
    }

    @Override
    public int hashCode() {
        int result = 31 + declaringType().hashCode();
        return 31 * result + name().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FieldDeclaration other
            && declaringType().equals(other.declaringType())
            && name().equals(other.name());
    }

    @Override
    AnnotationsAttribute annotationsAttribute(String tag) {
        return (AnnotationsAttribute)this.<CtField>member().getFieldInfo2().getAttribute(tag);
    }
}

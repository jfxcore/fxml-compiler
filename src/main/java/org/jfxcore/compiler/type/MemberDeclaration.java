// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.type;

import javassist.CtMember;
import javassist.Modifier;
import javassist.bytecode.AnnotationsAttribute;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public abstract sealed class MemberDeclaration permits FieldDeclaration, BehaviorDeclaration {

    private final CtMember member;
    private final TypeDeclaration declaringType;
    private List<AnnotationDeclaration> annotations;
    private List<AnnotationDeclaration> unmodifiableAnnotations;

    MemberDeclaration(CtMember member, TypeDeclaration declaringType) {
        this.member = Objects.requireNonNull(member);
        this.declaringType = Objects.requireNonNull(declaringType);
    }

    @SuppressWarnings("unchecked")
    final <T extends CtMember> T member() {
        return (T)member;
    }

    public final String name() {
        return member.getName();
    }

    public final String genericSignature() {
        return member.getGenericSignature();
    }

    public final AccessModifier accessModifier() {
        int modifiers = member.getModifiers();
        if (Modifier.isPrivate(modifiers)) return AccessModifier.PRIVATE;
        if (Modifier.isProtected(modifiers)) return AccessModifier.PROTECTED;
        if (Modifier.isPublic(modifiers)) return AccessModifier.PUBLIC;
        return AccessModifier.PACKAGE;
    }

    public final boolean isStatic() {
        return Modifier.isStatic(member.getModifiers());
    }

    public final boolean isFinal() {
        return Modifier.isFinal(member.getModifiers());
    }

    public final boolean isAbstract() {
        return Modifier.isAbstract(member.getModifiers());
    }

    public abstract boolean isSynthetic();

    public final TypeDeclaration declaringType() {
        return declaringType;
    }

    public final List<AnnotationDeclaration> annotations() {
        if (unmodifiableAnnotations == null) {
            unmodifiableAnnotations = Collections.unmodifiableList(annotationsInternal());
        }

        return unmodifiableAnnotations;
    }

    public final Optional<AnnotationDeclaration> annotation(String typeName) {
        return annotations().stream()
            .filter(annotation -> annotation.typeName().equals(typeName))
            .findFirst();
    }

    private List<AnnotationDeclaration> annotationsInternal() {
        if (annotations == null) {
            annotations = AnnotationDeclaration.collect(this::annotationsAttribute, null);
        }

        return annotations;
    }

    abstract AnnotationsAttribute annotationsAttribute(String tag);

    public MemberDeclaration setModifiers(int modifiers) {
        member.setModifiers(modifiers);
        return this;
    }

    @Override
    public final String toString() {
        return declaringType().javaName() + "." + name();
    }
}

// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.type.AccessModifier;
import org.jfxcore.compiler.type.MemberDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import java.util.function.Predicate;

public class AccessVerifier {

    private AccessVerifier() {}

    public static void verifyAccessible(TypeDeclaration type, TypeDeclaration fromType, SourceInfo sourceInfo) {
        if (!isAccessible(type, fromType)) {
            throw SymbolResolutionErrors.classNotAccessible(sourceInfo, type.javaName());
        }
    }

    public static void verifyAccessible(MemberDeclaration member, TypeDeclaration fromType, SourceInfo sourceInfo) {
        if (!isAccessible(member, fromType)) {
            throw SymbolResolutionErrors.memberNotAccessible(sourceInfo, member);
        }
    }

    public static void verifyNestedAccessible(TypeDeclaration type, TypeDeclaration fromType, SourceInfo sourceInfo) {
        if (!isNestedAccessible(type, fromType)) {
            throw SymbolResolutionErrors.classNotAccessible(sourceInfo, type.javaName());
        }
    }

    public static void verifyNestedAccessible(MemberDeclaration member, TypeDeclaration fromType, SourceInfo sourceInfo) {
        if (!isNestedAccessible(member, fromType)) {
            throw SymbolResolutionErrors.memberNotAccessible(sourceInfo, member);
        }
    }

    public static boolean isAccessible(MemberDeclaration member, TypeDeclaration fromType) {
        TypeDeclaration declaringClass = member.declaringType();

        if (declaringClass.packageName().equals(fromType.packageName())) {
            return member.accessModifier() != AccessModifier.PRIVATE;
        }

        if (!isAccessible(declaringClass, fromType)) {
            return false;
        }

        if (member.accessModifier() == AccessModifier.PUBLIC) {
            return true;
        }

        if (fromType.subtypeOf(declaringClass)) {
            return member.accessModifier() == AccessModifier.PROTECTED;
        }

        return false;
    }

    public static boolean isAccessible(TypeDeclaration type, TypeDeclaration fromType) {
        if (type.packageName().equals(fromType.packageName())) {
            return allEnclosingMatch(type, t -> t.accessModifier() != AccessModifier.PRIVATE);
        }

        if (allEnclosingMatch(type, t -> t.accessModifier() == AccessModifier.PUBLIC)) {
            return true;
        }

        if (type.declaringType().filter(fromType::subtypeOf).isPresent()) {
            return type.accessModifier() == AccessModifier.PROTECTED;
        }

        return false;
    }

    public static boolean isNestedAccessible(MemberDeclaration member, TypeDeclaration fromType) {
        TypeDeclaration declaringClass = member.declaringType();

        if (declaringClass.packageName().equals(fromType.packageName())) {
            return member.accessModifier() != AccessModifier.PRIVATE;
        }

        if (!isNestedAccessible(declaringClass, fromType)) {
            return false;
        }

        return member.accessModifier() == AccessModifier.PUBLIC;
    }

    public static boolean isNestedAccessible(TypeDeclaration type, TypeDeclaration fromType) {
        if (type.packageName().equals(fromType.packageName())) {
            return allEnclosingMatch(type, t -> t.accessModifier() != AccessModifier.PRIVATE);
        }

        return allEnclosingMatch(type, t -> t.accessModifier() == AccessModifier.PUBLIC);
    }

    private static boolean allEnclosingMatch(TypeDeclaration type, Predicate<TypeDeclaration> predicate) {
        TypeDeclaration t = type;
        while (t != null) {
            if (!predicate.test(t)) {
                return false;
            }

            t = t.declaringType().orElse(null);
        }

        return true;
    }
}

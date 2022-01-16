// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CtClass;
import javassist.CtMember;
import javassist.Modifier;
import javassist.NotFoundException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import java.util.Objects;
import java.util.function.Predicate;

public class AccessVerifier {

    private AccessVerifier() {}

    public static void verifyAccessible(CtClass type, CtClass fromType, SourceInfo sourceInfo) {
        if (!isAccessible(type, fromType, sourceInfo)) {
            throw SymbolResolutionErrors.classNotAccessible(sourceInfo, NameHelper.getJavaClassName(sourceInfo, type));
        }
    }

    public static void verifyAccessible(CtMember member, CtClass fromType, SourceInfo sourceInfo) {
        if (!isAccessible(member, fromType, sourceInfo)) {
            throw SymbolResolutionErrors.memberNotAccessible(sourceInfo, member);
        }
    }

    public static void verifyNestedAccessible(CtClass type, CtClass fromType, SourceInfo sourceInfo) {
        if (!isNestedAccessible(type, fromType, sourceInfo)) {
            throw SymbolResolutionErrors.classNotAccessible(sourceInfo, NameHelper.getJavaClassName(sourceInfo, type));
        }
    }

    public static void verifyNestedAccessible(CtMember member, CtClass fromType, SourceInfo sourceInfo) {
        if (!isNestedAccessible(member, fromType, sourceInfo)) {
            throw SymbolResolutionErrors.memberNotAccessible(sourceInfo, member);
        }
    }

    public static boolean isAccessible(CtMember member, CtClass fromType, SourceInfo sourceInfo) {
        CtClass declaringClass = member.getDeclaringClass();

        if (Objects.equals(declaringClass.getPackageName(), fromType.getPackageName())) {
            return !Modifier.isPrivate(member.getModifiers());
        }

        if (!isAccessible(declaringClass, fromType, sourceInfo)) {
            return false;
        }

        try {
            if (Modifier.isPublic(member.getModifiers())) {
                return true;
            }

            if (fromType.subtypeOf(declaringClass)) {
                return Modifier.isProtected(member.getModifiers());
            }
        } catch (NotFoundException e) {
            throw SymbolResolutionErrors.classNotFound(sourceInfo, e.getMessage());
        }

        return false;
    }

    public static boolean isAccessible(CtClass type, CtClass fromType, SourceInfo sourceInfo) {
        if (Objects.equals(type.getPackageName(), fromType.getPackageName())) {
            return allEnclosingMatch(type, t -> !Modifier.isPrivate(t.getModifiers()), sourceInfo);
        }

        if (allEnclosingMatch(type, t -> Modifier.isPublic(t.getModifiers()), sourceInfo)) {
            return true;
        }

        try {
            CtClass declaringClass = type.getDeclaringClass();
            if (declaringClass != null && fromType.subtypeOf(declaringClass)) {
                return Modifier.isProtected(type.getModifiers());
            }
        } catch (NotFoundException e) {
            throw SymbolResolutionErrors.classNotFound(sourceInfo, e.getMessage());
        }

        return false;
    }

    public static boolean isNestedAccessible(CtMember member, CtClass fromType, SourceInfo sourceInfo) {
        CtClass declaringClass = member.getDeclaringClass();

        if (Objects.equals(declaringClass.getPackageName(), fromType.getPackageName())) {
            return !Modifier.isPrivate(member.getModifiers());
        }

        if (!isNestedAccessible(declaringClass, fromType, sourceInfo)) {
            return false;
        }

        return Modifier.isPublic(member.getModifiers());
    }

    public static boolean isNestedAccessible(CtClass type, CtClass fromType, SourceInfo sourceInfo) {
        if (Objects.equals(type.getPackageName(), fromType.getPackageName())) {
            return allEnclosingMatch(type, t -> !Modifier.isPrivate(t.getModifiers()), sourceInfo);
        }

        return allEnclosingMatch(type, t -> Modifier.isPublic(t.getModifiers()), sourceInfo);
    }

    private static boolean allEnclosingMatch(CtClass type, Predicate<CtClass> predicate, SourceInfo sourceInfo) {
        CtClass t = type;
        while (t != null) {
            if (!predicate.test(t)) {
                return false;
            }

            try {
                t = t.getDeclaringClass();
            } catch (NotFoundException e) {
                throw SymbolResolutionErrors.classNotFound(sourceInfo, e.getMessage());
            }
        }

        return true;
    }

}

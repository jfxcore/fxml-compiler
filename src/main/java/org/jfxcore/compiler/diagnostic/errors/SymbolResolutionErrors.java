// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic.errors;

import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.MemberDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;

public class SymbolResolutionErrors {

    public static MarkupException classNotFound(SourceInfo sourceInfo, String className) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CLASS_NOT_FOUND, className));
    }

    public static MarkupException classNotAccessible(SourceInfo sourceInfo, String className) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CLASS_NOT_ACCESSIBLE, className));
    }

    public static MarkupException memberNotFound(
            SourceInfo sourceInfo, TypeDeclaration declaringType, String memberName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.MEMBER_NOT_FOUND, declaringType.javaName(), memberName));
    }

    public static MarkupException memberNotAccessible(SourceInfo sourceInfo, MemberDeclaration member) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.MEMBER_NOT_ACCESSIBLE,
            member.declaringType().javaName(), member.name()));
    }

    public static MarkupException propertyNotFound(
            SourceInfo sourceInfo, TypeDeclaration declaringType, String propertyName) {
        return propertyNotFound(sourceInfo, declaringType.javaName(), propertyName);
    }

    public static MarkupException propertyNotFound(SourceInfo sourceInfo, String declaringType, String propertyName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.PROPERTY_NOT_FOUND, declaringType, propertyName));
    }

    public static MarkupException staticPropertyNotFound(
            SourceInfo sourceInfo, TypeDeclaration declaringType, String propertyName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnosticVariant(
            ErrorCode.PROPERTY_NOT_FOUND, "static", declaringType.javaName(), propertyName));
    }

    public static MarkupException invalidInvariantReference(
            SourceInfo sourceInfo, TypeDeclaration declaringType, String memberName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.INVALID_INVARIANT_REFERENCE, declaringType.javaName(), memberName));
    }

    public static MarkupException instanceMemberReferencedFromStaticContext(
            SourceInfo sourceInfo, MemberDeclaration member) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.INSTANCE_MEMBER_REFERENCED_FROM_STATIC_CONTEXT,
            member.declaringType().javaName(), member.name()));
    }

    public static MarkupException unnamedPackageNotSupported(SourceInfo sourceInfo, String intrinsicName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.UNNAMED_PACKAGE_NOT_SUPPORTED, intrinsicName));
    }

}

// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic.errors;

import javassist.CtClass;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;

public class SymbolResolutionErrors {

    public static MarkupException notFound(SourceInfo sourceInfo, String name) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.NOT_FOUND, name));
    }

    public static MarkupException classNotFound(SourceInfo sourceInfo, String className) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CLASS_NOT_FOUND, className));
    }

    public static MarkupException memberNotFound(SourceInfo sourceInfo, CtClass declaringType, String memberName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.MEMBER_NOT_FOUND, declaringType.getName(), memberName));
    }

    public static MarkupException methodNotFound(SourceInfo sourceInfo, CtClass declaringType, String methodName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.METHOD_NOT_FOUND, declaringType.getName(), methodName));
    }

    public static MarkupException propertyNotFound(SourceInfo sourceInfo, CtClass declaringType, String propertyName) {
        return propertyNotFound(sourceInfo, declaringType.getName(), propertyName);
    }

    public static MarkupException propertyNotFound(SourceInfo sourceInfo, String declaringType, String propertyName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.PROPERTY_NOT_FOUND, declaringType, propertyName));
    }

    public static MarkupException invalidInvariantReference(SourceInfo sourceInfo, CtClass declaringType, String memberName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.INVALID_INVARIANT_REFERENCE, declaringType.getName(), memberName));
    }

    public static MarkupException unnamedPackageNotSupported(SourceInfo sourceInfo, String intrinsicName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.UNNAMED_PACKAGE_NOT_SUPPORTED, intrinsicName));
    }

}

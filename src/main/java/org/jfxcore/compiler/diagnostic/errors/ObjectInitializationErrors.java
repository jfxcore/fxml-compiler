// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic.errors;

import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeDeclaration;

public class ObjectInitializationErrors {

    public static MarkupException constructorNotFound(SourceInfo sourceInfo, TypeDeclaration type) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CONSTRUCTOR_NOT_FOUND, type.javaName()));
    }

    public static MarkupException constructorNotFound(SourceInfo sourceInfo, TypeDeclaration type, Diagnostic[] causes) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnosticVariant(
            ErrorCode.CONSTRUCTOR_NOT_FOUND, "causes", causes, type.javaName()));
    }

    public static MarkupException valueOfMethodNotFound(SourceInfo sourceInfo, TypeDeclaration type, Diagnostic[] causes) {
        return new MarkupException(sourceInfo, causes.length > 0 ?
            Diagnostic.newDiagnosticVariant(ErrorCode.VALUEOF_METHOD_NOT_FOUND,
                "causes", causes, type.javaName()) :
            Diagnostic.newDiagnostic(ErrorCode.VALUEOF_METHOD_NOT_FOUND,
                type.javaName()));
    }

    public static MarkupException conflictingProperties(SourceInfo sourceInfo, String property1, String property2) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CONFLICTING_PROPERTIES, property1, property2));
    }

    public static MarkupException cannotParameterizeType(SourceInfo sourceInfo, TypeDeclaration type) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CANNOT_PARAMETERIZE_TYPE, type.javaName()));
    }

    public static MarkupException objectCannotHaveMultipleChildren(SourceInfo sourceInfo, TypeDeclaration type) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.OBJECT_CANNOT_HAVE_MULTIPLE_CHILDREN, type.javaName()));
    }

    public static MarkupException objectMustContainText(SourceInfo sourceInfo, TypeDeclaration type) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.OBJECT_MUST_CONTAIN_TEXT, type.javaName()));
    }

    public static MarkupException objectCannotHaveContent(SourceInfo sourceInfo, TypeDeclaration type) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.OBJECT_CANNOT_HAVE_CONTENT, type.javaName()));
    }

    public static MarkupException objectCannotHaveContent(SourceInfo sourceInfo, TypeDeclaration type, String intrinsicName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnosticVariant(
            ErrorCode.OBJECT_CANNOT_HAVE_CONTENT, "intrinsic",
            type.javaName(), intrinsicName));
    }

    public static MarkupException wildcardCannotBeInstantiated(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(ErrorCode.WILDCARD_CANNOT_BE_INSTANTIATED));
    }

    public static MarkupException invalidMarkupExtensionUsage(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(ErrorCode.INVALID_MARKUP_EXTENSION_USAGE));
    }
}

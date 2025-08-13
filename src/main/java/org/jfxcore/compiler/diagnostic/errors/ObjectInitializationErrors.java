// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic.errors;

import javassist.CtClass;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.NameHelper;

public class ObjectInitializationErrors {

    public static MarkupException constructorNotFound(SourceInfo sourceInfo, CtClass type) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CONSTRUCTOR_NOT_FOUND, NameHelper.getJavaClassName(sourceInfo, type)));
    }

    public static MarkupException constructorNotFound(SourceInfo sourceInfo, CtClass type, Diagnostic[] causes) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnosticVariant(
            ErrorCode.CONSTRUCTOR_NOT_FOUND, "causes", causes, NameHelper.getJavaClassName(sourceInfo, type)));
    }

    public static MarkupException valueOfMethodNotFound(SourceInfo sourceInfo, CtClass type, Diagnostic[] causes) {
        return new MarkupException(sourceInfo, causes.length > 0 ?
            Diagnostic.newDiagnosticVariant(ErrorCode.VALUEOF_METHOD_NOT_FOUND,
                "causes", causes, NameHelper.getJavaClassName(sourceInfo, type)) :
            Diagnostic.newDiagnostic(ErrorCode.VALUEOF_METHOD_NOT_FOUND,
                NameHelper.getJavaClassName(sourceInfo, type)));
    }

    public static MarkupException conflictingProperties(SourceInfo sourceInfo, String property1, String property2) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CONFLICTING_PROPERTIES, property1, property2));
    }

    public static MarkupException cannotParameterizeType(SourceInfo sourceInfo, CtClass type) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CANNOT_PARAMETERIZE_TYPE, NameHelper.getJavaClassName(sourceInfo, type)));
    }

    public static MarkupException objectCannotHaveMultipleChildren(SourceInfo sourceInfo, CtClass type) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.OBJECT_CANNOT_HAVE_MULTIPLE_CHILDREN, NameHelper.getJavaClassName(sourceInfo, type)));
    }

    public static MarkupException objectMustContainText(SourceInfo sourceInfo, CtClass type) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.OBJECT_MUST_CONTAIN_TEXT, NameHelper.getJavaClassName(sourceInfo, type)));
    }

    public static MarkupException objectCannotHaveContent(SourceInfo sourceInfo, CtClass type) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.OBJECT_CANNOT_HAVE_CONTENT, NameHelper.getJavaClassName(sourceInfo, type)));
    }

    public static MarkupException objectCannotHaveContent(SourceInfo sourceInfo, CtClass type, String intrinsicName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnosticVariant(
            ErrorCode.OBJECT_CANNOT_HAVE_CONTENT, "intrinsic",
            NameHelper.getJavaClassName(sourceInfo, type), intrinsicName));
    }

    public static MarkupException wildcardCannotBeInstantiated(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(ErrorCode.WILDCARD_CANNOT_BE_INSTANTIATED));
    }

    public static MarkupException invalidMarkupExtensionUsage(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(ErrorCode.INVALID_MARKUP_EXTENSION_USAGE));
    }
}

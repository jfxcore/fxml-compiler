// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic.errors;

import javassist.CtClass;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.TypeInstance;

public class ObjectInitializationErrors {

    public static MarkupException constructorNotFound(SourceInfo sourceInfo, CtClass type) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CONSTRUCTOR_NOT_FOUND, type.getName()));
    }

    public static MarkupException constructorNotFound(SourceInfo sourceInfo, CtClass type, Diagnostic[] causes) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnosticVariant(
            ErrorCode.CONSTRUCTOR_NOT_FOUND, "causes", causes, type.getName()));
    }

    public static MarkupException valueOfMethodNotFound(SourceInfo sourceInfo, CtClass type, CtClass supertype) {
        return new MarkupException(sourceInfo, supertype != null ?
            Diagnostic.newDiagnosticVariant(ErrorCode.VALUEOF_METHOD_NOT_FOUND, "superclass", type.getName(), supertype.getName()) :
            Diagnostic.newDiagnostic(ErrorCode.VALUEOF_METHOD_NOT_FOUND, type.getName()));
    }

    public static MarkupException valueOfCannotHaveContent(SourceInfo sourceInfo, CtClass type, String valuePropertyName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.VALUEOF_CANNOT_HAVE_CONTENT, type.getName(), valuePropertyName));
    }

    public static MarkupException conflictingProperties(SourceInfo sourceInfo, String property1, String property2) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CONFLICTING_PROPERTIES, property1, property2));
    }

    public static MarkupException cannotAssignConstant(
            SourceInfo sourceInfo, TypeInstance targetType, TypeInstance assignType) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CANNOT_ASSIGN_CONSTANT, targetType.getJavaName(), assignType.getJavaName()));
    }

    public static MarkupException constantCannotHaveContent(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CONSTANT_CANNOT_HAVE_CONTENT));
    }

    public static MarkupException constantCannotBeModified(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CONSTANT_CANNOT_BE_MODIFIED));
    }

    public static MarkupException cannotParameterizeType(SourceInfo sourceInfo, CtClass type) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CANNOT_PARAMETERIZE_TYPE, type.getName()));
    }

    public static MarkupException objectCannotHaveMultipleChildren(SourceInfo sourceInfo, CtClass type) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.OBJECT_CANNOT_HAVE_MULTIPLE_CHILDREN, type.getName()));
    }

    public static MarkupException objectMustContainText(SourceInfo sourceInfo, CtClass type) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.OBJECT_MUST_CONTAIN_TEXT, type.getName()));
    }

}

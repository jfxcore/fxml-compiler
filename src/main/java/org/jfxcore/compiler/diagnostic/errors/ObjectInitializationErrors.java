// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic.errors;

import javassist.CtClass;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.TypeInstance;

public class ObjectInitializationErrors {

    public static MarkupException constructorNotFound(SourceInfo sourceInfo, CtClass type) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CONSTRUCTOR_NOT_FOUND, NameHelper.getJavaClassName(sourceInfo, type)));
    }

    public static MarkupException constructorNotFound(SourceInfo sourceInfo, CtClass type, Diagnostic[] causes) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnosticVariant(
            ErrorCode.CONSTRUCTOR_NOT_FOUND, "causes", causes, NameHelper.getJavaClassName(sourceInfo, type)));
    }

    public static MarkupException valueOfMethodNotFound(SourceInfo sourceInfo, CtClass type, CtClass supertype) {
        return new MarkupException(sourceInfo, supertype != null ?
            Diagnostic.newDiagnosticVariant(ErrorCode.VALUEOF_METHOD_NOT_FOUND, "superclass",
                NameHelper.getJavaClassName(sourceInfo, type), NameHelper.getJavaClassName(sourceInfo, supertype)) :
            Diagnostic.newDiagnostic(ErrorCode.VALUEOF_METHOD_NOT_FOUND,
                NameHelper.getJavaClassName(sourceInfo, type)));
    }

    public static MarkupException valueOfCannotHaveContent(SourceInfo sourceInfo, CtClass type, String valuePropertyName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.VALUEOF_CANNOT_HAVE_CONTENT, NameHelper.getJavaClassName(sourceInfo, type), valuePropertyName));
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

    public static MarkupException constantCannotBeModified(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CONSTANT_CANNOT_BE_MODIFIED));
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

}

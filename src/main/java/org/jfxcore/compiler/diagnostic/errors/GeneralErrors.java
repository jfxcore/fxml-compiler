// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic.errors;

import javassist.CtClass;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.PropertyInfo;
import org.jfxcore.compiler.util.TypeInstance;

import static org.jfxcore.compiler.diagnostic.errors.FormatHelper.formatPropertyName;

public class GeneralErrors {

    public static MarkupException internalError() {
        return new MarkupException(SourceInfo.none(), Diagnostic.newDiagnosticMessage(
            ErrorCode.INTERNAL_ERROR, "Internal error"));
    }

    public static MarkupException internalError(String message) {
        return new MarkupException(SourceInfo.none(), Diagnostic.newDiagnosticMessage(
            ErrorCode.INTERNAL_ERROR, message));
    }

    public static MarkupException internalError(Throwable cause) {
        return new MarkupException(SourceInfo.none(), Diagnostic.newDiagnosticMessage(
            ErrorCode.INTERNAL_ERROR, "Internal error"), cause);
    }

    public static MarkupException internalError(String message, Throwable cause) {
        return new MarkupException(SourceInfo.none(), Diagnostic.newDiagnosticMessage(
            ErrorCode.INTERNAL_ERROR, message), cause);
    }

    public static MarkupException unsupported(String message) {
        return new MarkupException(SourceInfo.none(), Diagnostic.newDiagnosticMessage(
            ErrorCode.UNSUPPORTED, message));
    }

    public static MarkupException codeBehindClassNameMismatch(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CODEBEHIND_CLASS_NAME_MISMATCH));
    }

    public static MarkupException unknownIntrinsic(SourceInfo sourceInfo, String name) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.UNKNOWN_INTRINSIC, name));
    }

    public static MarkupException unexpectedIntrinsic(SourceInfo sourceInfo, String name) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.UNEXPECTED_INTRINSIC, name));
    }

    public static MarkupException duplicateId(SourceInfo sourceInfo, String id) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.DUPLICATE_ID, id));
    }

    public static MarkupException invalidId(SourceInfo sourceInfo, String id) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.INVALID_ID, id));
    }

    public static MarkupException invalidContentInStylesheet(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.INVALID_CONTENT_IN_STYLESHEET));
    }

    public static MarkupException stylesheetError(SourceInfo sourceInfo, String message) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnosticMessage(
            ErrorCode.STYLESHEET_ERROR, message));
    }

    public static MarkupException cannotAddItemIncompatibleType(
            SourceInfo sourceInfo, PropertyInfo propertyInfo, CtClass addType, CtClass requiredType) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CANNOT_ADD_ITEM_INCOMPATIBLE_TYPE,
            formatPropertyName(propertyInfo), addType.getName(), requiredType.getName()));
    }

    public static MarkupException cannotAddItemIncompatibleType(
            SourceInfo sourceInfo, CtClass collectionType, CtClass addType, CtClass requiredType) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CANNOT_ADD_ITEM_INCOMPATIBLE_TYPE,
            collectionType.getName(), addType.getName(), requiredType.getName()));
    }

    public static MarkupException cannotAddItemIncompatibleValue(
            SourceInfo sourceInfo, CtClass collectionType, String value) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CANNOT_ADD_ITEM_INCOMPATIBLE_VALUE, collectionType.getName(), value));
    }

    public static MarkupException cannotAddItemIncompatibleValue(
            SourceInfo sourceInfo, CtClass declaringType, String propertyName, String value) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CANNOT_ADD_ITEM_INCOMPATIBLE_VALUE,
            declaringType.getSimpleName(), propertyName, value));
    }

    public static MarkupException unsupportedMapKeyType(SourceInfo sourceInfo, PropertyInfo propertyInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.UNSUPPORTED_MAP_KEY_TYPE, formatPropertyName(propertyInfo)));
    }

    public static MarkupException unsupportedMapKeyType(SourceInfo sourceInfo, CtClass mapType) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.UNSUPPORTED_MAP_KEY_TYPE, mapType.getName()));
    }

    public static MarkupException typeArgumentOutOfBound(SourceInfo sourceInfo, TypeInstance typeArg, TypeInstance requiredType) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.TYPE_ARGUMENT_OUT_OF_BOUND, typeArg.getName(), requiredType.getName()));
    }

    public static MarkupException numTypeArgumentsMismatch(
            SourceInfo sourceInfo, CtClass declaringType, int expected, int actual) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.NUM_TYPE_ARGUMENTS_MISMATCH, declaringType.getName(), expected, actual));
    }

    public static MarkupException rootClassCannotBeFinal(SourceInfo sourceInfo, CtClass rootClass) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.ROOT_CLASS_CANNOT_BE_FINAL, rootClass.getName()));
    }

    public static MarkupException cannotAssignFunctionArgument(
            SourceInfo sourceInfo, String methodName, int argumentIndex, String sourceType) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, methodName, argumentIndex + 1, sourceType));
    }

    public static MarkupException numFunctionArgumentsMismatch(
            SourceInfo sourceInfo, String methodName, int expected, int actual) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, methodName, expected, actual));
    }

    public static MarkupException expressionNotApplicable(
            SourceInfo sourceInfo, boolean allowAssignment) {
        return new MarkupException(sourceInfo, allowAssignment ?
            Diagnostic.newDiagnosticVariant(ErrorCode.EXPRESSION_NOT_APPLICABLE, "assign") :
            Diagnostic.newDiagnostic(ErrorCode.EXPRESSION_NOT_APPLICABLE));
    }

}

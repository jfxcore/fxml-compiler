// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic.errors;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.NameHelper;
import java.util.Arrays;

import static org.jfxcore.compiler.diagnostic.errors.FormatHelper.formatPropertyName;

public class BindingSourceErrors {

    public static MarkupException sourceTypeMismatch(SourceInfo sourceInfo, String sourceType, String targetType) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.SOURCE_TYPE_MISMATCH, sourceType, targetType));
    }

    public static MarkupException cannotConvertSourceType(SourceInfo sourceInfo, String sourceType, String targetType) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, sourceType, targetType));
    }

    public static MarkupException invalidContentAssignmentSource(SourceInfo sourceInfo, CtClass declaringType, String propertyName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.INVALID_CONTENT_ASSIGNMENT_SOURCE, formatPropertyName(declaringType, propertyName)));
    }

    public static MarkupException invalidContentBindingSource(
            SourceInfo sourceInfo, CtClass declaringType, String propertyName, boolean bidirectional, boolean assignHint) {
        if (bidirectional) {
            return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
                ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, formatPropertyName(declaringType, propertyName)));
        }

        return new MarkupException(sourceInfo, assignHint ?
            Diagnostic.newDiagnosticVariant(
                ErrorCode.INVALID_CONTENT_BINDING_SOURCE, "assignHint", formatPropertyName(declaringType, propertyName)) :
            Diagnostic.newDiagnostic(
                ErrorCode.INVALID_CONTENT_BINDING_SOURCE, formatPropertyName(declaringType, propertyName)));
    }

    public static MarkupException invalidBidirectionalBindingSource(SourceInfo sourceInfo, CtClass declaringType, String propertyName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, formatPropertyName(declaringType, propertyName)));
    }

    public static MarkupException invalidBidirectionalBindingSource(SourceInfo sourceInfo, CtClass sourceType, boolean contentHint) {
        return new MarkupException(sourceInfo, contentHint ?
            Diagnostic.newDiagnosticVariant(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, "contentHint", sourceType.getName()) :
            Diagnostic.newDiagnostic(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, sourceType.getName()));
    }

    public static MarkupException expressionNotInvertible(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.EXPRESSION_NOT_INVERTIBLE));
    }

    public static MarkupException invalidBidirectionalMethodParamCount(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.INVALID_BIDIRECTIONAL_METHOD_PARAM_COUNT));
    }

    public static MarkupException invalidBidirectionalMethodParamKind(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.INVALID_BIDIRECTIONAL_METHOD_PARAM_KIND));
    }

    public static MarkupException invalidBindingExpression(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.INVALID_BINDING_EXPRESSION));
    }

    public static MarkupException invalidBindingContext(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.INVALID_BINDING_CONTEXT, sourceInfo.getText()));
    }

    public static MarkupException parentTypeNotFound(SourceInfo sourceInfo, String name) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.PARENT_TYPE_NOT_FOUND, name));
    }

    public static MarkupException parentIndexOutOfBounds(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.PARENT_INDEX_OUT_OF_BOUNDS));
    }

    public static MarkupException cannotBindFunction(SourceInfo sourceInfo, Diagnostic[] causes) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CANNOT_BIND_FUNCTION, causes));
    }

    public static MarkupException methodNotInvertible(SourceInfo sourceInfo, CtMethod method) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.METHOD_NOT_INVERTIBLE, method.getLongName()));
    }

    public static MarkupException invalidInverseMethod(
            SourceInfo sourceInfo, CtMethod method, CtBehavior[] inverseMethods) {
        if (inverseMethods.length == 1) {
            return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
                ErrorCode.INVALID_INVERSE_METHOD,
                inverseMethods[0].getLongName(),
                NameHelper.getShortMethodSignature(method)));
        }

        return new MarkupException(sourceInfo, Diagnostic.newDiagnosticVariantCauses(
            ErrorCode.INVALID_INVERSE_METHOD, "overloaded",
            Arrays.stream(inverseMethods).map(CtBehavior::getLongName).toArray(String[]::new),
            NameHelper.getShortMethodSignature(method)));
    }

    public static MarkupException bindingNotSupported(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.BINDING_NOT_SUPPORTED));
    }

}
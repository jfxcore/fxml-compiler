// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic.errors;

import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.BehaviorDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;

import static org.jfxcore.compiler.util.NameHelper.*;

public class BindingSourceErrors {

    public static MarkupException sourceTypeMismatch(SourceInfo sourceInfo, String sourceType, String targetType) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.SOURCE_TYPE_MISMATCH, sourceType, targetType));
    }

    public static MarkupException cannotConvertSourceType(SourceInfo sourceInfo, String sourceType, String targetType) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, sourceType, targetType));
    }

    public static MarkupException invalidContentAssignmentSource(
            SourceInfo sourceInfo, TypeDeclaration declaringType, String propertyName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.INVALID_CONTENT_ASSIGNMENT_SOURCE, formatPropertyName(declaringType, propertyName)));
    }

    public static MarkupException invalidContentBindingSource(
            SourceInfo sourceInfo, TypeDeclaration declaringType, String propertyName, TypeInstance requiredType,
            boolean bidirectional, boolean assignHint) {
        if (bidirectional) {
            return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
                ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, formatPropertyName(declaringType, propertyName)));
        }

        return new MarkupException(sourceInfo, assignHint ?
            Diagnostic.newDiagnosticVariant(
                ErrorCode.INVALID_CONTENT_BINDING_SOURCE, "assignHint",
                formatPropertyName(declaringType, propertyName), requiredType.javaName()) :
            Diagnostic.newDiagnostic(
                ErrorCode.INVALID_CONTENT_BINDING_SOURCE,
                formatPropertyName(declaringType, propertyName), requiredType.javaName()));
    }

    public static MarkupException invalidUnidirectionalBindingSource(SourceInfo sourceInfo, TypeDeclaration declaringType,
                                                                     String propertyName, boolean function) {
        return new MarkupException(sourceInfo, function
            ? Diagnostic.newDiagnosticVariant(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE,
                                              "function", formatPropertyName(declaringType, propertyName))
            : Diagnostic.newDiagnostic(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE,
                                       formatPropertyName(declaringType, propertyName)));
    }

    public static MarkupException invalidBidirectionalBindingSource(
            SourceInfo sourceInfo, TypeDeclaration declaringType, String propertyName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, formatPropertyName(declaringType, propertyName)));
    }

    public static MarkupException invalidBidirectionalBindingSource(
            SourceInfo sourceInfo, TypeInstance sourceType, boolean contentHint) {
        return new MarkupException(sourceInfo, contentHint ?
            Diagnostic.newDiagnosticVariant(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, "contentHint",
                sourceType.javaName()) :
            Diagnostic.newDiagnostic(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE,
                sourceType.javaName()));
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

    public static MarkupException bindingContextNotApplicable(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.BINDING_CONTEXT_NOT_APPLICABLE));
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

    public static MarkupException methodNotInvertible(SourceInfo sourceInfo, BehaviorDeclaration method) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.METHOD_NOT_INVERTIBLE, method.displaySignature(false, false)));
    }

    public static MarkupException invalidInverseMethod(
            SourceInfo sourceInfo, BehaviorDeclaration method, Diagnostic[] causes) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.INVALID_INVERSE_METHOD, causes, method.displaySignature(false, false)));
    }

    public static MarkupException invalidInverseMethodAnnotationValue(
            SourceInfo sourceInfo, BehaviorDeclaration behavior) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.INVALID_INVERSE_METHOD_ANNOTATION_VALUE, behavior.displaySignature(false, false)));
    }

    public static MarkupException stringConversionNotApplicable(SourceInfo sourceInfo, String propertyName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.STRING_CONVERSION_NOT_APPLICABLE, propertyName));
    }
}

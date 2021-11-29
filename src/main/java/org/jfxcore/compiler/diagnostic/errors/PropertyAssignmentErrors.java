// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic.errors;

import javassist.CtClass;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.PropertyInfo;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.ArrayList;
import java.util.List;

import static org.jfxcore.compiler.diagnostic.errors.FormatHelper.formatPropertyName;

public class PropertyAssignmentErrors {

    public static MarkupException incompatiblePropertyType(
            SourceInfo sourceInfo, PropertyInfo propertyInfo, TypeInstance assignType) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.INCOMPATIBLE_PROPERTY_TYPE, formatPropertyName(propertyInfo),
            propertyInfo.getValueTypeInstance().getName(), assignType != null ? assignType.getName() : "'null'"));
    }

    public static MarkupException incompatiblePropertyType(
            SourceInfo sourceInfo, CtClass declaringClass, String propertyName, CtClass requiredType, TypeInstance assignType) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.INCOMPATIBLE_PROPERTY_TYPE, formatPropertyName(declaringClass, propertyName),
            requiredType.getName(), assignType != null ? assignType.getName() : "'null'"));
    }

    public static MarkupException incompatiblePropertyItems(SourceInfo sourceInfo, PropertyInfo propertyInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnosticVariant(
            ErrorCode.INCOMPATIBLE_PROPERTY_TYPE, "items",
            formatPropertyName(propertyInfo), propertyInfo.getValueType().getName()));
    }

    public static MarkupException cannotCoercePropertyValue(
            SourceInfo sourceInfo, PropertyInfo propertyInfo, String value, boolean raw) {
        return new MarkupException(sourceInfo, raw ?
            Diagnostic.newDiagnosticVariant(
                ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, "raw", propertyInfo.getValueTypeInstance().getName()) :
            Diagnostic.newDiagnostic(
                ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, formatPropertyName(propertyInfo), value));
    }

    public static MarkupException cannotCoercePropertyValue(
            SourceInfo sourceInfo, String propertyName, String value) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, propertyName, value));
    }

    public static MarkupException cannotCoercePropertyValue(
        SourceInfo sourceInfo, String declaringType, String propertyName, String value) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, formatPropertyName(declaringType, propertyName), value));
    }

    public static MarkupException propertyCannotBeEmpty(SourceInfo sourceInfo, CtClass declaringType, String propertyName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.PROPERTY_CANNOT_BE_EMPTY, formatPropertyName(declaringType, propertyName)));
    }

    public static MarkupException propertyMustContainText(SourceInfo sourceInfo, CtClass declaringType, String propertyName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.PROPERTY_MUST_CONTAIN_TEXT, formatPropertyName(declaringType, propertyName)));
    }

    public static MarkupException propertyMustContainText(SourceInfo sourceInfo, String declaringType, String propertyName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.PROPERTY_MUST_CONTAIN_TEXT, formatPropertyName(declaringType, propertyName)));
    }

    public static MarkupException propertyCannotHaveMultipleValues(SourceInfo sourceInfo, CtClass declaringType, String propertyName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.PROPERTY_CANNOT_HAVE_MULTIPLE_VALUES, formatPropertyName(declaringType, propertyName)));
    }

    public static MarkupException propertyMustBeSpecified(SourceInfo sourceInfo, String declaringType, String propertyName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.PROPERTY_MUST_BE_SPECIFIED, formatPropertyName(declaringType, propertyName)));
    }

    public static MarkupException duplicateProperty(SourceInfo sourceInfo, String declaringType, String propertyName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.DUPLICATE_PROPERTY, formatPropertyName(declaringType, propertyName)));
    }

    public static MarkupException unsuitableEventHandler(SourceInfo sourceInfo, CtClass eventType, String methodName) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.UNSUITABLE_EVENT_HANDLER, eventType.getName(), methodName));
    }

    public static MarkupException cannotModifyReadOnlyProperty(SourceInfo sourceInfo, PropertyInfo propertyInfo) {
        List<String> variants = new ArrayList<>();

        if (propertyInfo.isContentBindable(BindingMode.CONTENT)) {
            variants.add("content");
        }

        if (propertyInfo.isContentBindable(BindingMode.UNIDIRECTIONAL_CONTENT)) {
            variants.add("bindContent");
        }

        if (propertyInfo.isContentBindable(BindingMode.BIDIRECTIONAL_CONTENT)) {
            variants.add("bindContentBidirectional");
        }

        return new MarkupException(sourceInfo, !variants.isEmpty() ?
            Diagnostic.newDiagnosticVariant(
                ErrorCode.CANNOT_MODIFY_READONLY_PROPERTY, String.join("_", variants), formatPropertyName(propertyInfo)) :
            Diagnostic.newDiagnostic(
                ErrorCode.CANNOT_MODIFY_READONLY_PROPERTY, formatPropertyName(propertyInfo)));
    }

    public static MarkupException invalidBindingTarget(SourceInfo sourceInfo, PropertyInfo propertyInfo) {
        return new MarkupException(sourceInfo, propertyInfo.isReadOnly() ?
            Diagnostic.newDiagnostic(
                ErrorCode.INVALID_BINDING_TARGET, formatPropertyName(propertyInfo)) :
            Diagnostic.newDiagnosticVariant(
                ErrorCode.INVALID_BINDING_TARGET, "assignHint", formatPropertyName(propertyInfo)));
    }

    public static MarkupException invalidContentBindingTarget(
            SourceInfo sourceInfo, PropertyInfo propertyInfo, BindingMode mode) {
        ErrorCode code;
        switch (mode) {
            case CONTENT:
                code = ErrorCode.INVALID_CONTENT_ASSIGNMENT_TARGET;
                break;
            case UNIDIRECTIONAL_CONTENT:
                code = ErrorCode.INVALID_CONTENT_BINDING_TARGET;
                break;
            case BIDIRECTIONAL_CONTENT:
                code = ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET;
                break;
            default:
                throw new IllegalArgumentException("mode");
        }

        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(code, formatPropertyName(propertyInfo)));
    }

}

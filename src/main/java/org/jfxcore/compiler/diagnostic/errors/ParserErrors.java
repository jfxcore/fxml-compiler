// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic.errors;

import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.parse.AbstractToken;

public class ParserErrors {

    public static MarkupException unexpectedToken(AbstractToken<?> token) {
        return unexpectedToken(token.getSourceInfo());
    }

    public static MarkupException unexpectedToken(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.UNEXPECTED_TOKEN));
    }

    public static MarkupException unexpectedEndOfFile(SourceInfo sourceInfo) {
        sourceInfo = new SourceInfo(sourceInfo.getEnd().getLine(), sourceInfo.getEnd().getColumn());
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.UNEXPECTED_END_OF_FILE));
    }

    public static MarkupException expectedIdentifier(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.EXPECTED_IDENTIFIER));
    }

    public static MarkupException expectedToken(SourceInfo sourceInfo, String value) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.EXPECTED_TOKEN, value));
    }

    public static MarkupException unmatchedTag(SourceInfo sourceInfo, String expected) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.UNMATCHED_TAG, expected));
    }

    public static MarkupException unknownNamespace(SourceInfo sourceInfo, String namespace) {
        throw new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.UNKNOWN_NAMESPACE, namespace));
    }

    public static MarkupException invalidExpression(SourceInfo sourceInfo) {
        throw new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.INVALID_EXPRESSION));
    }

    public static MarkupException unexpectedExpression(SourceInfo sourceInfo) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.UNEXPECTED_EXPRESSION));
    }

    public static MarkupException unexpectedMarkupExtension(SourceInfo sourceInfo, String type) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(
            ErrorCode.UNEXPECTED_MARKUP_EXTENSION, type));
    }
}

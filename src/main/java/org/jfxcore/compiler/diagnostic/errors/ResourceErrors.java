// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic.errors;

import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.resource.EmbeddedResource;

public final class ResourceErrors {

    private ResourceErrors() {}

    public static MarkupException invalidDeclaration(SourceInfo sourceInfo) {
        return error(sourceInfo, ErrorCode.INVALID_RESOURCE_DECLARATION);
    }

    public static MarkupException missingName(SourceInfo sourceInfo) {
        return error(sourceInfo, ErrorCode.MISSING_RESOURCE_NAME);
    }

    public static MarkupException invalidName(SourceInfo sourceInfo, String name) {
        return error(sourceInfo, ErrorCode.INVALID_RESOURCE_NAME, name);
    }

    public static MarkupException duplicateDeclaration(SourceInfo sourceInfo, String name, EmbeddedResource previous) {
        SourceInfo nameSourceInfo = previous.nameSourceInfo().toOriginal().toOneBased();
        return error(sourceInfo, ErrorCode.DUPLICATE_RESOURCE_DECLARATION, name, nameSourceInfo.getStart());
    }

    public static MarkupException invalidMediaType(SourceInfo sourceInfo, String name) {
        return error(sourceInfo, ErrorCode.INVALID_RESOURCE_MEDIA_TYPE, name);
    }

    public static MarkupException duplicateMediaTypeParameter(
            SourceInfo sourceInfo, String resourceName, String parameterName) {
        return error(sourceInfo, ErrorCode.DUPLICATE_RESOURCE_MEDIA_TYPE_PARAMETER, resourceName, parameterName);
    }

    public static MarkupException unsupportedCharset(
            SourceInfo sourceInfo, String name, String charset, Throwable cause) {
        return new MarkupException(
            sourceInfo, Diagnostic.newDiagnostic(ErrorCode.UNSUPPORTED_RESOURCE_CHARSET, charset, name), cause);
    }

    public static MarkupException unrepresentableCharacter(
            SourceInfo sourceInfo, String name, String charset) {
        return error(sourceInfo, ErrorCode.UNREPRESENTABLE_RESOURCE_CHARACTER, name, charset);
    }

    public static MarkupException resourceFileCollision(
            SourceInfo sourceInfo, String path, String firstOwner, String secondOwner) {
        return error(sourceInfo, ErrorCode.RESOURCE_FILE_COLLISION, path, firstOwner, secondOwner);
    }

    private static MarkupException error(SourceInfo sourceInfo, ErrorCode code, Object... arguments) {
        return new MarkupException(sourceInfo, Diagnostic.newDiagnostic(code, arguments));
    }
}

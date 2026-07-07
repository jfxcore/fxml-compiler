// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.runner;

import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import java.io.File;

final class ExceptionHelper {

    private final Class<?> markupExceptionClass;
    private final Class<?> diagnosticClass;
    private final Class<?> errorCodeClass;

    ExceptionHelper(CompilerClassLoader classLoader) {
        markupExceptionClass = classLoader.findClass(MarkupException.class);
        diagnosticClass = classLoader.findClass(Diagnostic.class);
        errorCodeClass = classLoader.findClass(ErrorCode.class);
    }

    boolean isMarkupException(Throwable ex) {
        return markupExceptionClass.isInstance(ex);
    }

    boolean isInternalError(Throwable ex) {
        if (!isMarkupException(ex)) {
            return false;
        }

        try {
            Object diagnostic = ex.getClass().getMethod("getDiagnostic").invoke(ex);
            Object errorCode = diagnosticClass.getMethod("getCode").invoke(diagnostic);
            return (int)errorCodeClass.getMethod("ordinal").invoke(errorCode) == 0;
        } catch (ReflectiveOperationException ex2) {
            throwUnchecked(ex2);
            return false;
        }
    }

    String format(Throwable ex) {
        if (!markupExceptionClass.isInstance(ex)) {
            return null;
        }

        try {
            Class<?> cls = ex.getClass();
            File sourceFile = (File)cls.getMethod("getSourceFile").invoke(ex);
            String message = (String)cls.getMethod("getMessageWithSourceInfo").invoke(ex);
            Object sourceInfo = cls.getMethod("getSourceInfo").invoke(ex);
            Object sourceOffset = cls.getMethod("getSourceOffset").invoke(ex);
            Object location = sourceInfo.getClass().getMethod("getStart").invoke(sourceInfo);
            int line = (int)location.getClass().getMethod("getLine").invoke(location);
            int lineOffset = sourceOffset != null
                ? (int)sourceOffset.getClass().getMethod("getLine").invoke(sourceOffset)
                : 0;

            return String.format("%s:%s: %s", sourceFile != null
                ? sourceFile.toString()
                : "<null>", line + lineOffset + 1, message);
        } catch (ReflectiveOperationException ex2) {
            throwUnchecked(ex2);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static <E extends Throwable> void throwUnchecked(Throwable e) throws E {
        throw (E)e;
    }
}

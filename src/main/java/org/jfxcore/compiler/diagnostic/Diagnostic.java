// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Diagnostic {

    public static Diagnostic newDiagnostic(ErrorCode code) {
        return new Diagnostic(code, new Diagnostic[0], SR.getString(code));
    }

    public static Diagnostic newDiagnostic(ErrorCode code, Diagnostic[] causes) {
        return new Diagnostic(code, causes, SR.getString(code));
    }

    public static Diagnostic newDiagnostic(ErrorCode code, Object... args) {
        return new Diagnostic(code, new Diagnostic[0], String.format(SR.getString(code), args));
    }

    public static Diagnostic newDiagnosticMessage(ErrorCode code, String message) {
        return new Diagnostic(code, new Diagnostic[0], message);
    }

    public static Diagnostic newDiagnosticVariant(ErrorCode code, String variant, Object... args) {
        return new Diagnostic(code, new Diagnostic[0], String.format(SR.getString(code, variant), args));
    }

    public static Diagnostic newDiagnosticVariant(ErrorCode code, String variant, Diagnostic[] causes, Object... args) {
        return new Diagnostic(code, causes, String.format(SR.getString(code, variant), args));
    }

    public static Diagnostic newDiagnosticFormat(ErrorCode code, String format, Object... args) {
        return new Diagnostic(code, new Diagnostic[0], String.format(format, String.format(SR.getString(code), args)));
    }

    public static Diagnostic newDiagnosticCauses(ErrorCode code, String[] causes) {
        return new Diagnostic(code, new Diagnostic[0], SR.getString(code) + formatCauses(causes));
    }

    public static Diagnostic newDiagnosticCauses(ErrorCode code, String[] causes, Object... args) {
        return new Diagnostic(code, new Diagnostic[0], String.format(SR.getString(code), args) + formatCauses(causes));
    }

    public static Diagnostic newDiagnosticVariantCauses(ErrorCode code, String variant, String[] causes, Object... args) {
        return new Diagnostic(code, new Diagnostic[0], String.format(SR.getString(code, variant), args) + formatCauses(causes));
    }

    private Diagnostic(ErrorCode code, Diagnostic[] causes, String message) {
        this.code = code;
        this.causes = causes;
        this.message = message;
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getMessage() {
        return message != null ? (message + formatCauses(causes)) : "";
    }

    public Diagnostic[] getCauses() {
        return causes;
    }
    
    private static String formatCauses(Object[] causes) {
        if (causes.length == 0) {
            return "";
        }

        return DELIMITER + Arrays.stream(causes)
            .map(x -> x instanceof Diagnostic ? ((Diagnostic)x).getMessage() : x.toString())
            .collect(Collectors.joining(DELIMITER));
    }

    private final ErrorCode code;
    private final Diagnostic[] causes;
    private final String message;

    private static final String DELIMITER = "\r\n\t> ";

}
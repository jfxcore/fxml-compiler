// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MarkupException extends RuntimeException {

    private final Diagnostic diagnostic;
    private final SourceInfo sourceInfo;
    private Map<Object, Object> properties;
    private File sourceFile;

    public MarkupException(SourceInfo sourceInfo, Diagnostic diagnostic) {
        super(diagnostic.getMessage());
        this.diagnostic = diagnostic;
        this.sourceInfo = sourceInfo;
    }

    public MarkupException(SourceInfo sourceInfo, Diagnostic diagnostic, Throwable cause) {
        super(diagnostic.getMessage(), cause);
        this.diagnostic = diagnostic;
        this.sourceInfo = sourceInfo;
    }

    public String getMessageWithSourceInfo() {
        StringBuilder builder = new StringBuilder(getMessage());
        String lineText = sourceInfo.getLineText();

        if (lineText != null) {
            int cols = sourceInfo.getStart().getLine() == sourceInfo.getEnd().getLine() ?
                sourceInfo.getEnd().getColumn() - sourceInfo.getStart().getColumn() :
                lineText.length() - sourceInfo.getStart().getColumn();

            builder.append(System.lineSeparator()).append(System.lineSeparator());
            builder.append(lineText).append(System.lineSeparator());
            builder.append(" ".repeat(sourceInfo.getStart().getColumn()));
            builder.append("^".repeat(cols + 1));
        }

        return builder.toString();
    }

    public Diagnostic getDiagnostic() {
        return diagnostic;
    }

    public SourceInfo getSourceInfo() {
        return sourceInfo;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(File file) {
        this.sourceFile = file;
    }

    public Map<Object, Object> getProperties() {
        if (properties == null) {
            properties = new HashMap<>();
        }

        return properties;
    }

}

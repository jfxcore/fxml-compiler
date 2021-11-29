// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic;

public class DiagnosticInfo {

    private final Diagnostic diagnostic;
    private final SourceInfo sourceInfo;

    public DiagnosticInfo(Diagnostic diagnostic, SourceInfo sourceInfo) {
        this.diagnostic = diagnostic;
        this.sourceInfo = sourceInfo;
    }

    public Diagnostic getDiagnostic() {
        return diagnostic;
    }

    public SourceInfo getSourceInfo() {
        return sourceInfo;
    }

}

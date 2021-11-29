// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;

/**
 * The exception that is thrown when FXML parsing is aborted.
 * If this exception is encountered during compilation, the current FXML file will be skipped.
 */
public class FxmlParseAbortException extends MarkupException {

    public FxmlParseAbortException(SourceInfo sourceInfo, Diagnostic diagnostic) {
        super(sourceInfo, diagnostic);
    }

}

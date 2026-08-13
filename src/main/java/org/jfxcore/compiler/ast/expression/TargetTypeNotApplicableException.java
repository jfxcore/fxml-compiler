// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jfxcore.compiler.diagnostic.MarkupException;

/**
 * Thrown when an invocation is valid but cannot produce the requested type.
 * The caller may then try another target type.
 */
public final class TargetTypeNotApplicableException extends MarkupException {

    public TargetTypeNotApplicableException(MarkupException cause) {
        super(cause.getSourceInfo(), cause.getDiagnostic(), cause);
        getProperties().putAll(cause.getProperties());
    }
}

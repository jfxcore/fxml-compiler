// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import org.jfxcore.compiler.diagnostic.MarkupException;

public class InconvertibleArgumentException extends RuntimeException {

    private final String typeName;

    public InconvertibleArgumentException(String typeName) {
        this.typeName = typeName;
    }

    public InconvertibleArgumentException(String typeName, MarkupException cause) {
        super(cause);
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }

}

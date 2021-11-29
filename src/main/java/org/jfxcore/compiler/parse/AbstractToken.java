// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.SourceInfo;

public abstract class AbstractToken<T extends TokenType> {

    private final String line;
    private final T type;
    private String value;
    private SourceInfo sourceInfo;

    AbstractToken(T type, String value, String line, SourceInfo sourceInfo) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.sourceInfo = sourceInfo;
    }

    public T getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public String getLine() {
        return line;
    }

    public SourceInfo getSourceInfo() {
        return sourceInfo;
    }

    public void setContent(String value, SourceInfo sourceInfo) {
        this.value = value;
        this.sourceInfo = sourceInfo;
    }

    @Override
    public String toString() {
        return value;
    }

}

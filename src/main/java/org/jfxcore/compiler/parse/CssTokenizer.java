// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.SourceInfo;

public class CssTokenizer extends CurlyTokenizer<CurlyToken> {

    public CssTokenizer(String text, Location sourceOffset) {
        super(CurlyToken.class, text, sourceOffset);
    }

    @Override
    protected CurlyToken parseToken(String value, String line, SourceInfo sourceInfo) {
        return CurlyToken.parse(value, line, sourceInfo);
    }

    @Override
    protected CurlyToken newToken(CurlyTokenType type, String value, String line, SourceInfo sourceInfo) {
        return new CurlyToken(type, value, line, sourceInfo);
    }

}

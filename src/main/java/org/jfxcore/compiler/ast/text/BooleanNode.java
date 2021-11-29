// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.TypeNode;

public class BooleanNode extends TextNode {

    public BooleanNode(String text, SourceInfo sourceInfo) {
        super(text, sourceInfo);
    }

    private BooleanNode(String text, TypeNode type, SourceInfo sourceInfo) {
        super(text, false, type, sourceInfo);
    }

    @Override
    public BooleanNode deepClone() {
        return new BooleanNode(getText(), getType(), getSourceInfo());
    }

}

// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.codebehind;

import org.jfxcore.compiler.ast.EmitContext;
import org.jfxcore.compiler.ast.Node;

public class JavaEmitContext extends EmitContext<StringBuilder> {

    public JavaEmitContext(StringBuilder output) {
        super(output);
    }

    @Override
    public void emit(Node node) {
        if (node instanceof JavaEmitterNode emitterNode) {
            getParents().push(emitterNode);
            emitterNode.emit(this);
            getParents().pop();
        }
    }

}

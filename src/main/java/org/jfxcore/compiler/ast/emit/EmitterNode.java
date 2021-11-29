// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.Node;

/**
 * Identifies a node that emits Java bytecode.
 */
public interface EmitterNode extends Node {

    void emit(BytecodeEmitContext context);

    @Override
    EmitterNode deepClone();

}

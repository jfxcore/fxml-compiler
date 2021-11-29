// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.codebehind;

import org.jfxcore.compiler.ast.Node;

/**
 * Identifies a node that emits Java code or bytecode.
 */
public interface JavaEmitterNode extends Node {

    default void emit(JavaEmitContext context) {}

    @Override
    JavaEmitterNode deepClone();

}

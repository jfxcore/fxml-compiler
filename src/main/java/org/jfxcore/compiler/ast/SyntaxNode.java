// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

/**
 * A parse-time node whose type is not known yet.
 * <p>
 * Syntax nodes must be resolved to typed values before bytecode emission.
 */
public interface SyntaxNode extends Node {

    /**
     * Returns a source-language representation of this node.
     */
    String format();

    @Override
    SyntaxNode deepClone();
}

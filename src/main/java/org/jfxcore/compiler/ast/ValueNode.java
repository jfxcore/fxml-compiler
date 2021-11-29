// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

/**
 * A node that represents a typed value.
 */
public interface ValueNode extends Node {

    TypeNode getType();

    ValueNode deepClone();

}

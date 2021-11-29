// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import java.util.List;

/**
 * Represents the root node of a FXML document or a template sub-document.
 */
public interface RootNode extends Node {

    List<Node> getPreamble();

}

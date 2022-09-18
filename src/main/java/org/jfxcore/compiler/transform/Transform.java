// Copyright (c) 2021, 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform;

import org.jfxcore.compiler.ast.Node;

public interface Transform {

    Node transform(TransformContext context, Node node);

}

// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform;

import org.jfxcore.compiler.ast.Node;
import java.util.Collections;
import java.util.Set;

public interface Transform {

    Node transform(TransformContext context, Node node);

    default Set<Class<? extends Transform>> getDependsOn() {
        return Collections.emptySet();
    }

}

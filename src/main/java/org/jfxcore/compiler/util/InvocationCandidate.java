// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.type.BehaviorDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.List;
import java.util.Objects;

/**
 * An overload candidate with the invocation metadata needed to instantiate its generic parameter and
 * result types. Different candidates may have different invocation contexts and witness lists.
 */
public record InvocationCandidate(
        BehaviorDeclaration behavior,
        List<TypeInstance> invocationContext,
        List<TypeInstance> typeWitnesses,
        boolean staticInvocation,
        @Nullable TypeInstance resultType) {

    public InvocationCandidate {
        Objects.requireNonNull(behavior);
        invocationContext = List.copyOf(invocationContext);
        typeWitnesses = List.copyOf(typeWitnesses);
    }
}

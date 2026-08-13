// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.type.TypeInstance;
import java.util.List;
import java.util.Objects;

/**
 * A candidate that is applicable in the selected overload phase.
 */
public record ApplicableInvocationCandidate(
        InvocationCandidate candidate,
        List<TypeInstance> parameterTypes,
        TypeInstance resultType,
        int phase,
        boolean expandedVarargs,
        List<ArgumentConversion> argumentConversions) {

    public ApplicableInvocationCandidate {
        Objects.requireNonNull(candidate);
        parameterTypes = List.copyOf(parameterTypes);
        Objects.requireNonNull(resultType);
        argumentConversions = List.copyOf(argumentConversions);
    }
}

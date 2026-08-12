// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.List;
import java.util.Objects;

public record ArgumentConversion(
        TypeInstance formalType,
        List<TypeInstance> sourceTypes,
        ConversionCategory category,
        SourceInfo sourceInfo) {

    public ArgumentConversion {
        Objects.requireNonNull(formalType);
        sourceTypes = List.copyOf(sourceTypes);
        Objects.requireNonNull(category);
        Objects.requireNonNull(sourceInfo);
    }
}

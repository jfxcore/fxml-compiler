// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.markup.processor;

import com.google.devtools.ksp.processing.SymbolProcessor;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import com.google.devtools.ksp.processing.SymbolProcessorProvider;

public final class MarkupSymbolProcessorProvider implements SymbolProcessorProvider {

    @Override
    public SymbolProcessor create(SymbolProcessorEnvironment environment) {
        return new MarkupSymbolProcessor(environment);
    }
}

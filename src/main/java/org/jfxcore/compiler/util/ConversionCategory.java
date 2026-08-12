// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

/**
 * Conversion evidence retained for an already-applicable argument.
 */
public enum ConversionCategory {
    IDENTITY,
    STRICT,
    LOOSE,
    TARGET
}

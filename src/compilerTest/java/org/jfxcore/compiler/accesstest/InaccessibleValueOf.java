// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.accesstest;

@SuppressWarnings("unused")
public class InaccessibleValueOf {

    static InaccessibleValueOf valueOf(String value) { return null; }
}

// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

public class Local {

    private final int index;
    private final boolean wide;

    Local(int index, boolean wide) {
        this.index = index;
        this.wide = wide;
    }

    public int getIndex() {
        return index;
    }

    boolean isWide() {
        return wide;
    }

    @Override
    public String toString() {
        return Integer.toString(index);
    }

}

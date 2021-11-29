// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

public class Label {

    private final Bytecode code;
    private final int position;
    private final boolean wide;
    private boolean adjusted;

    Label(Bytecode code, boolean wide) {
        this.code = code;
        this.position = code.bytecode.getSize();
        this.wide = wide;

        code.bytecode.addIndex(0);

        if (wide) {
            code.bytecode.addIndex(0);
        }
    }

    boolean isWide() {
        return wide;
    }

    public Bytecode resume() {
        if (adjusted) {
            throw new UnsupportedOperationException("Cannot reuse label.");
        }

        if (wide) {
            code.bytecode.write32bit(position, code.bytecode.getSize() - position + 1);
        } else {
            code.bytecode.write16bit(position, code.bytecode.getSize() - position + 1);
        }

        adjusted = true;

        return code;
    }

}

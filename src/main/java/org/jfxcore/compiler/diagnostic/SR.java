// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic;

import java.util.ResourceBundle;

class SR {

    private static final ResourceBundle resourceBundle = ResourceBundle.getBundle(SR.class.getName());

    public static String getString(ErrorCode code) {
        return resourceBundle.getString(code.name());
    }

    public static String getString(ErrorCode code, String variant) {
        return resourceBundle.getString(code.name() + "_" + variant);
    }

}

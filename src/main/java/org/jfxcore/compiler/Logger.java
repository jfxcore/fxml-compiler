// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

public interface Logger {

    void fine(String message);
    void info(String message);
    void warning(String message);
    void error(String message);

}

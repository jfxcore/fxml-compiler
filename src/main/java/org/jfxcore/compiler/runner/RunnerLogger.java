// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.runner;

public interface RunnerLogger {
    void error(String message);
    void warn(String message);
    void info(String message);
    void fine(String message);
}

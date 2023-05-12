// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

public class VersionInfo {
    private static final String GROUP = "${group}";
    private static final String NAME = "${name}";
    private static final String VERSION = "${version}";

    public static String getGroup() {
        return GROUP;
    }

    public static String getName() {
        return NAME;
    }

    public static String getVersion() {
        return VERSION;
    }
}
// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.accesstest;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.layout.Pane;

@SuppressWarnings("unused")
public class PropertyTestPane extends Pane {

    final StringProperty labelText1 = new SimpleStringProperty("foo");

    private final StringProperty labelText2 = new SimpleStringProperty("bar");

    StringProperty labelText2Property() { return labelText2; }
}
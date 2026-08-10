// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.accesstest;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.Pane;
import org.jfxcore.markup.InverseMethod;

@SuppressWarnings("unused")
public class PublicTestPane extends Pane {

    private final ObjectProperty<Object> result = new SimpleObjectProperty<>();

    public ObjectProperty<Object> resultProperty() { return result; }

    public double getResultValue() { return ((ProtectedMember)result.get()).value; }

    @InverseMethod("protectedMethod")
    protected double protectedMethod(double d) { return d; }

    double packagePrivateMethod(double d) { return d; }

    private double privateMethod(double d) { return d; }

    protected class ProtectedMember {
        public final double value;
        public ProtectedMember(double value) { this.value = value; }
    }

    protected static class Nested {

        public double publicMethod(double d) { return d; }

        protected double protectedMethod(double d) { return d; }

        protected static double staticProtectedMethod(double d) { return d; }
    }
}

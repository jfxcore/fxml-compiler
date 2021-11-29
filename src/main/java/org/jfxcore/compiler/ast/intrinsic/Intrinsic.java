// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.intrinsic;

import javassist.CtClass;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class Intrinsic {

    private final String name;
    private final Supplier<CtClass> type;
    private final Usage usage;
    private final List<IntrinsicProperty> properties;

    public Intrinsic(String name, Supplier<CtClass> type, Usage usage, IntrinsicProperty... properties) {
        this.name = name;
        this.type = type;
        this.usage = usage;
        this.properties = Arrays.asList(properties);
    }

    public String getName() {
        return name;
    }

    public CtClass getType() {
        return type.get();
    }

    public Usage getUsage() {
        return usage;
    }

    public List<IntrinsicProperty> getProperties() {
        return properties;
    }

    public IntrinsicProperty getDefaultProperty() {
        for (IntrinsicProperty property : properties) {
            if (property.isDefault()) {
                return property;
            }
        }

        return null;
    }

    public IntrinsicProperty findProperty(String name) {
        for (IntrinsicProperty property : properties) {
            if (property.getName().equals(name)) {
                return property;
            }
        }

        return null;
    }

}

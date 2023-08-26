// Copyright (c) 2022, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.intrinsic;

import javassist.CtClass;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class Intrinsic {

    public enum Kind {
        OBJECT, PROPERTY
    }

    public enum Placement {
        ANY, ROOT, NOT_ROOT
    }

    private final String name;
    private final Supplier<CtClass> type;
    private final Kind kind;
    private final Placement placement;
    private final List<IntrinsicProperty> properties;
    private TypeInstance cachedTypeInstance;

    public Intrinsic(String name, Kind kind, Placement placement, IntrinsicProperty... properties) {
        this(name, kind, placement, () -> new CtClass("<no-type>") {}, properties);
    }

    public Intrinsic(String name, Kind kind, Placement placement, Supplier<CtClass> type, IntrinsicProperty... properties) {
        this.name = name;
        this.type = type;
        this.kind = kind;
        this.placement = placement;
        this.properties = Arrays.asList(properties);

        for (IntrinsicProperty property : properties) {
            property.intrinsic = this;
        }
    }

    public String getName() {
        return name;
    }

    /**
     * Gets the type of the intrinsic.
     *
     * @param typeNode the {@code TypeNode} in the AST that represents the intrinsic type
     */
    public TypeInstance getType(TypeNode typeNode) {
        if (cachedTypeInstance == null) {
            cachedTypeInstance = new Resolver(typeNode.getSourceInfo()).getTypeInstance(type.get());
        }

        return cachedTypeInstance;
    }

    public Kind getKind() {
        return kind;
    }

    public Placement getPlacement() {
        return placement;
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

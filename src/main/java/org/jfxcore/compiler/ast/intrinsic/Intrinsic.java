// Copyright (c) 2022, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.intrinsic;

import javassist.CtClass;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.PropertyInfo;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class Intrinsic {

    public enum Kind {
        ANY, OBJECT, PROPERTY
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
     * Gets the type of the intrinsic, which may depend on other nodes in the AST.
     * For example, an in-context fx:value node depends on the property type to which it is assigned.
     *
     * @param context the {@code TransformContext}
     * @param typeNode the {@code TypeNode} in the AST that represents the intrinsic type
     */
    public TypeInstance getType(TransformContext context, TypeNode typeNode) {
        if (kind == Kind.ANY
                && context.getParent(typeNode) instanceof ObjectNode objectNode
                && context.getParent(objectNode) instanceof PropertyNode propertyNode
                && context.getParent(propertyNode) instanceof ObjectNode parentNode) {
            PropertyInfo propertyInfo = new Resolver(propertyNode.getSourceInfo())
                .resolveProperty(TypeHelper.getTypeInstance(parentNode), false, propertyNode.getNames());
            return propertyInfo.getType();
        }

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

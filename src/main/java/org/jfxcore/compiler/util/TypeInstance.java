// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CtClass;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.jfxcore.compiler.util.ExceptionHelper.unchecked;

/**
 * Represents the instantiation tree of a type, in which all generic arguments are replaced with concrete types.
 */
public class TypeInstance {

    public enum WildcardType {
        NONE,
        ANY,
        LOWER,
        UPPER;

        public static WildcardType of(char wildcard) {
            return switch (wildcard) {
                case ' ' -> NONE;
                case '*' -> ANY;
                case '+' -> UPPER;
                case '-' -> LOWER;
                default -> throw new IllegalArgumentException("wildcard");
            };
        }
    }

    private final CtClass type;
    private final List<TypeInstance> arguments;
    private final List<TypeInstance> superTypes;
    private final int dimensions;
    private final WildcardType wildcard;

    public TypeInstance(CtClass type) {
        this(type, WildcardType.NONE);
    }

    public TypeInstance(CtClass type, WildcardType wildcard) {
        int dimensions = 0;
        while (type.isArray()) {
            type = unchecked(SourceInfo.none(), type::getComponentType);
            ++dimensions;
        }

        this.dimensions = dimensions;
        this.type = type;
        this.arguments = Collections.emptyList();
        this.superTypes = Collections.emptyList();
        this.wildcard = wildcard;
    }

    public TypeInstance(CtClass type, List<TypeInstance> arguments) {
        this(type, arguments, WildcardType.NONE);
    }

    public TypeInstance(CtClass type, List<TypeInstance> arguments, WildcardType wildcard) {
        int dimensions = 0;
        while (type.isArray()) {
            type = unchecked(SourceInfo.none(), type::getComponentType);
            ++dimensions;
        }

        this.dimensions = dimensions;
        this.type = type;
        this.arguments = arguments;
        this.superTypes = Collections.emptyList();
        this.wildcard = wildcard;
    }

    public TypeInstance(CtClass type, List<TypeInstance> arguments, List<TypeInstance> superTypes) {
        this(type, arguments, superTypes, WildcardType.NONE);
    }

    public TypeInstance(CtClass type, List<TypeInstance> arguments, List<TypeInstance> superTypes, WildcardType wildcard) {
        int dimensions = 0;
        while (type.isArray()) {
            type = unchecked(SourceInfo.none(), type::getComponentType);
            ++dimensions;
        }

        this.dimensions = dimensions;
        this.type = type;
        this.arguments = arguments;
        this.superTypes = superTypes;
        this.wildcard = wildcard;
    }

    public TypeInstance(CtClass type, int dimensions, List<TypeInstance> arguments, List<TypeInstance> superTypes) {
        this(type, dimensions, arguments, superTypes, WildcardType.NONE);
    }

    public TypeInstance(CtClass type, int dimensions, List<TypeInstance> arguments, List<TypeInstance> superTypes, WildcardType wildcard) {
        this.type = type;
        this.dimensions = dimensions;
        this.arguments = arguments;
        this.superTypes = superTypes;
        this.wildcard = wildcard;
    }

    public boolean isArray() {
        return dimensions > 0;
    }

    public int getDimensions() {
        return dimensions;
    }

    public boolean isPrimitive() {
        return type.isPrimitive();
    }

    public CtClass jvmType() {
        return type;
    }

    public String getName() {
        return toString(false, false);
    }

    public String getJavaName() {
        return toString(false, true);
    }

    public String getSimpleName() {
        return toString(true, false);
    }

    public List<TypeInstance> getArguments() {
        return arguments;
    }

    public List<TypeInstance> getSuperTypes() {
        return superTypes;
    }

    public WildcardType getWildcardType() {
        return wildcard;
    }

    public TypeInstance getComponentType() {
        if (!isArray()) {
            return this;
        }

        return new TypeInstance(type, 0, arguments, superTypes, wildcard);
    }

    public boolean isConvertibleFrom(TypeInstance from) {
        if (TypeHelper.isNumeric(type) && TypeHelper.isNumeric(from.type)) {
            return true;
        }

        if (isPrimitive() && !from.isPrimitive()) {
            return unchecked(SourceInfo.none(), () -> from.type.subtypeOf(TypeHelper.getBoxedType(type)));
        }

        if (!isPrimitive() && from.isPrimitive()) {
            return unchecked(SourceInfo.none(), () -> TypeHelper.getBoxedType(from.type).subtypeOf(type));
        }

        return isAssignableFrom(from);
    }

    public boolean isAssignableFrom(TypeInstance from) {
        if (type.isPrimitive()) {
            return arguments.isEmpty()
                && from.arguments.isEmpty()
                && dimensions == from.dimensions
                && TypeHelper.equals(type, from.type);
        }

        if (from.type.isPrimitive()) {
            return dimensions == 0 && TypeHelper.equals(type, Classes.ObjectType());
        }

        if (dimensions != from.dimensions && (dimensions > 0 || !equals(Classes.ObjectType()))) {
            return false;
        }

        if (arguments.size() > 0 && arguments.size() != from.arguments.size()) {
            for (TypeInstance fromSuperType : from.superTypes) {
                if (isAssignableFrom(fromSuperType)) {
                    return true;
                }
            }

            return false;
        }

        if (!unchecked(SourceInfo.none(), () -> from.type.subtypeOf(type))) {
            return false;
        }

        for (int i = 0; i < arguments.size(); ++i) {
            WildcardType wildcard = arguments.get(i).wildcard;

            if (wildcard == WildcardType.LOWER && !arguments.get(i).subtypeOf(from.arguments.get(i))) {
                return false;
            }

            if (wildcard == WildcardType.UPPER && !from.arguments.get(i).subtypeOf(arguments.get(i))) {
                return false;
            }

            if (wildcard == WildcardType.NONE && !arguments.get(i).equals(from.arguments.get(i))) {
                return false;
            }
        }

        return true;
    }

    public boolean subtypeOf(TypeInstance other) {
        return unchecked(SourceInfo.none(), () -> {
            if (other.dimensions == 0 && other.equals(Classes.ObjectType())) {
                return true;
            }

            return other.dimensions == dimensions && type.subtypeOf(other.type);
        });
    }

    public boolean subtypeOf(CtClass other) {
        return unchecked(SourceInfo.none(), () -> {
            int otherDimensions = 0;
            CtClass o = other;

            while (o.isArray()) {
                o = o.getComponentType();
                ++otherDimensions;
            }

            if (otherDimensions == 0 && other.equals(Classes.ObjectType())) {
                return true;
            }

            return dimensions == otherDimensions && type.subtypeOf(o);
        });
    }

    public boolean equals(CtClass other) {
        return TypeHelper.equals(type, other);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeInstance that = (TypeInstance)o;
        if (arguments.size() != that.arguments.size()) return false;
        return arguments.isEmpty() ? TypeHelper.equals(type, that.type) : equals(new HashSet<>(), that);
    }

    private boolean equals(Set<TypeInstance> set, TypeInstance other) {
        if (set.contains(this)) {
            return true;
        }

        set.add(this);

        if (!TypeHelper.equals(type, other.type) || arguments.size() != other.arguments.size()) {
            return false;
        }

        for (int i = 0; i < arguments.size(); ++i) {
            if (!arguments.get(i).equals(set, other.arguments.get(i))) {
                return false;
            }
        }

        return true;
    }

    protected String toString(boolean simpleNames, boolean javaNames) {
        if (wildcard == WildcardType.ANY) {
            return "?";
        }

        StringBuilder builder = new StringBuilder();
        switch (wildcard) {
            case LOWER -> builder.append("? super ");
            case UPPER -> builder.append("? extends ");
        }

        if (javaNames) {
            builder.append(NameHelper.getJavaClassName(SourceInfo.none(), type));
        } else if (simpleNames) {
            builder.append(type.getSimpleName());
        } else {
            builder.append(type.getName());
        }

        if (arguments.size() > 0) {
            builder.append('<');

            for (int i = 0; i < arguments.size(); ++i) {
                if (i > 0) {
                    builder.append(',');
                }

                builder.append(arguments.get(i).toString(simpleNames, javaNames));
            }

            builder.append('>');
        }

        builder.append("[]".repeat(dimensions));

        return builder.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(TypeHelper.hashCode(type), arguments.size(), superTypes.size());
    }

    @Override
    public String toString() {
        return getSimpleName();
    }

}

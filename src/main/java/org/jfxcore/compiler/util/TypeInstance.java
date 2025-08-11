// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CtClass;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.jfxcore.compiler.util.ExceptionHelper.*;

/**
 * Represents the instantiation tree of a type, in which all generic arguments are replaced with concrete types.
 */
public class TypeInstance {

    private static class ErasedTypeInstance extends TypeInstance {
        ErasedTypeInstance(TypeInstance source) {
            super(source.jvmType(), source.getArguments(), source.getSuperTypes(), source.getWildcardType());
        }

        ErasedTypeInstance(CtClass type,
                           int dimensions,
                           List<TypeInstance> arguments,
                           List<TypeInstance> superTypes,
                           WildcardType wildcard) {
            super(type, dimensions, arguments, superTypes, wildcard);
        }
    }

    private static class NullTypeInstance extends TypeInstance {
        NullTypeInstance() {
            super(Classes.ObjectType(), 0, List.of(), List.of(), WildcardType.NONE);
        }

        @Override
        protected String toString(boolean simpleNames, boolean javaNames) {
            return "<null>";
        }

        @Override
        public boolean isAssignableFrom(TypeInstance from, AssignmentContext context) {
            return from instanceof NullTypeInstance;
        }
    }

    public static TypeInstance booleanType() { return resolveTypeInstance(CtClass.booleanType); }
    public static TypeInstance charType() { return resolveTypeInstance(CtClass.charType); }
    public static TypeInstance byteType() { return resolveTypeInstance(CtClass.byteType); }
    public static TypeInstance shortType() { return resolveTypeInstance(CtClass.shortType); }
    public static TypeInstance intType() { return resolveTypeInstance(CtClass.intType); }
    public static TypeInstance longType() { return resolveTypeInstance(CtClass.longType); }
    public static TypeInstance floatType() { return resolveTypeInstance(CtClass.floatType); }
    public static TypeInstance doubleType() { return resolveTypeInstance(CtClass.doubleType); }
    public static TypeInstance BooleanType() { return resolveTypeInstance(Classes.BooleanType()); }
    public static TypeInstance CharacterType() { return resolveTypeInstance(Classes.CharacterType()); }
    public static TypeInstance ByteType() { return resolveTypeInstance(Classes.ByteType()); }
    public static TypeInstance ShortType() { return resolveTypeInstance(Classes.ShortType()); }
    public static TypeInstance IntegerType() { return resolveTypeInstance(Classes.IntegerType()); }
    public static TypeInstance LongType() { return resolveTypeInstance(Classes.LongType()); }
    public static TypeInstance FloatType() { return resolveTypeInstance(Classes.FloatType()); }
    public static TypeInstance DoubleType() { return resolveTypeInstance(Classes.DoubleType()); }
    public static TypeInstance StringType() { return resolveTypeInstance(Classes.StringType()); }
    public static TypeInstance ObjectType() { return resolveTypeInstance(Classes.ObjectType()); }

    public static TypeInstance nullType() {
        TypeInstance typeInstance = getClassCache().get(null);
        if (typeInstance == null) {
            typeInstance = new NullTypeInstance();
            getClassCache().put(null, typeInstance);
        }

        return typeInstance;
    }

    private static TypeInstance resolveTypeInstance(CtClass clazz) {
        Objects.requireNonNull(clazz);
        TypeInstance typeInstance = getClassCache().get(clazz);
        if (typeInstance == null) {
            typeInstance = new TypeInvoker(SourceInfo.none()).invokeType(clazz);
            getClassCache().put(clazz, typeInstance);
        }

        return typeInstance;
    }

    @SuppressWarnings("unchecked")
    private static Map<CtClass, TypeInstance> getClassCache() {
        return (Map<CtClass, TypeInstance>)CompilationContext.getCurrent()
            .computeIfAbsent(TypeInstance.class, key -> new HashMap<CtClass, TypeInstance>());
    }

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

    public enum AssignmentContext {
        STRICT,
        LOOSE
    }

    private final CtClass type;
    private final int dimensions;
    private final WildcardType wildcard;
    private List<TypeInstance> arguments;
    private List<TypeInstance> superTypes;
    private TypeInstance componentType;

    public static TypeInstance of(CtClass type) {
        return resolveTypeInstance(type);
    }

    static TypeInstance ofErased(TypeInstance type) {
        return new ErasedTypeInstance(type);
    }

    TypeInstance(CtClass type,
                 List<TypeInstance> arguments,
                 List<TypeInstance> superTypes,
                 WildcardType wildcard) {
        this.type = type;
        this.dimensions = TypeHelper.getDimensions(type);
        this.arguments = arguments;
        this.superTypes = superTypes;
        this.wildcard = wildcard;

        if (arguments.stream().anyMatch(TypeInstance::isPrimitive)) {
            throw new IllegalArgumentException("arguments");
        }
    }

    private TypeInstance(CtClass type,
                         int dimensions,
                         List<TypeInstance> arguments,
                         List<TypeInstance> superTypes,
                         WildcardType wildcard) {
        this.type = type;
        this.dimensions = dimensions;
        this.arguments = arguments;
        this.superTypes = superTypes;
        this.wildcard = wildcard;

        if (arguments.stream().anyMatch(TypeInstance::isPrimitive)) {
            throw new IllegalArgumentException("arguments");
        }
    }

    TypeInstance freeze(SourceInfo sourceInfo) {
        superTypes = List.copyOf(superTypes);

        if (arguments != (arguments = List.copyOf(arguments))) {
            for (TypeInstance argument : arguments) {
                if (argument.isPrimitive()) {
                    throw GeneralErrors.typeArgumentNotReference(sourceInfo, type, argument);
                }

                argument.freeze(sourceInfo);
            }
        }

        for (TypeInstance superType : superTypes) {
            superType.freeze(sourceInfo);
        }

        return this;
    }

    public TypeInstance withDimensions(int dimensions) {
        if (this.dimensions == dimensions) {
            return this;
        }

        CtClass type = new Resolver(SourceInfo.none())
            .resolveClass(this.type.getName() + "[]".repeat(dimensions));

        return this instanceof ErasedTypeInstance ?
            new ErasedTypeInstance(type, dimensions, arguments, superTypes, wildcard) :
            new TypeInstance(type, dimensions, arguments, superTypes, wildcard);
    }

    public TypeInstance withWildcard(WildcardType wildcard) {
        if (this.wildcard == wildcard) {
            return this;
        }

        return this instanceof ErasedTypeInstance ?
            new ErasedTypeInstance(type, dimensions, arguments, superTypes, wildcard) :
            new TypeInstance(type, dimensions, arguments, superTypes, wildcard);
    }

    public boolean isRaw() {
        for (int i = 0, max = arguments.size(); i < max; ++i) {
            if (arguments.get(i) instanceof ErasedTypeInstance) {
                return true;
            }
        }

        return false;
    }

    public boolean isArray() {
        return dimensions > 0;
    }

    public int getDimensions() {
        return dimensions;
    }

    public boolean isPrimitive() {
        return type.isPrimitive() && dimensions == 0;
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

        if (componentType != null) {
            return componentType;
        }

        return componentType = new TypeInstance(
            unchecked(SourceInfo.none(), this.type::getComponentType), arguments, superTypes, wildcard);
    }

    /**
     * Determines whether the specified type can be converted to this type via any of the conversions
     * specified by {@link #isAssignableFrom(TypeInstance, AssignmentContext)}, assuming a loose
     * assignment context.
     */
    public boolean isAssignableFrom(TypeInstance from) {
        return isAssignableFrom(from, AssignmentContext.LOOSE);
    }

    /**
     * Determines whether the specified type can be converted to this type via any of the
     * following conversions:
     * <ol>
     *     <li>an identity conversion
     *     <li>a widening primitive conversion
     *     <li>a widening reference conversion
     * </ol>
     *
     * In a loose assignment context, the following conversions are also permitted:
     * <ol>
     *     <li>a boxing conversion, optionally followed by a widening reference conversion
     *     <li>an unboxing conversion, optionally followed by a widening primitive conversion
     * </ol>
     */
    public boolean isAssignableFrom(TypeInstance from, AssignmentContext context) {
        // Identity conversion
        if (equals(from) || !isPrimitive() && from instanceof NullTypeInstance) {
            return true;
        }

        // Widening primitive conversion
        if (dimensions == 0 && from.dimensions == 0
                && TypeHelper.isNumericPrimitive(type) && TypeHelper.isNumeric(from.type)) {
            // In a loose assignment context, we assume an unboxing conversion has occurred
            CtClass fromType = context == AssignmentContext.LOOSE ? TypeHelper.getPrimitiveType(from.type) : from.type;

            if (TypeHelper.equals(type, CtClass.charType)) {
                return TypeHelper.equals(fromType, CtClass.charType);
            }

            if (TypeHelper.equals(type, CtClass.byteType)) {
                return TypeHelper.equals(fromType, CtClass.byteType);
            }

            if (TypeHelper.equals(type, CtClass.shortType)) {
                return TypeHelper.equals(fromType, CtClass.shortType)
                    || TypeHelper.equals(fromType, CtClass.byteType);
            }

            if (TypeHelper.equals(type, CtClass.intType)) {
                return TypeHelper.equals(fromType, CtClass.intType)
                    || TypeHelper.equals(fromType, CtClass.shortType)
                    || TypeHelper.equals(fromType, CtClass.charType)
                    || TypeHelper.equals(fromType, CtClass.byteType);
            }

            if (TypeHelper.equals(type, CtClass.longType)) {
                return TypeHelper.equals(fromType, CtClass.longType)
                    || TypeHelper.equals(fromType, CtClass.intType)
                    || TypeHelper.equals(fromType, CtClass.shortType)
                    || TypeHelper.equals(fromType, CtClass.charType)
                    || TypeHelper.equals(fromType, CtClass.byteType);
            }

            if (TypeHelper.equals(type, CtClass.floatType)) {
                return TypeHelper.equals(fromType, CtClass.floatType)
                    || TypeHelper.equals(fromType, CtClass.longType)
                    || TypeHelper.equals(fromType, CtClass.intType)
                    || TypeHelper.equals(fromType, CtClass.shortType)
                    || TypeHelper.equals(fromType, CtClass.charType)
                    || TypeHelper.equals(fromType, CtClass.byteType);
            }

            if (TypeHelper.equals(type, CtClass.doubleType)) {
                return TypeHelper.equals(fromType, CtClass.doubleType)
                    || TypeHelper.equals(fromType, CtClass.floatType)
                    || TypeHelper.equals(fromType, CtClass.longType)
                    || TypeHelper.equals(fromType, CtClass.intType)
                    || TypeHelper.equals(fromType, CtClass.shortType)
                    || TypeHelper.equals(fromType, CtClass.charType)
                    || TypeHelper.equals(fromType, CtClass.byteType);
            }

            return false;
        }

        // Unboxing conversion
        if (context == AssignmentContext.LOOSE && TypeHelper.isPrimitiveBox(from.type, type)) {
            return TypeHelper.equals(type, TypeHelper.getPrimitiveType(from.type));
        }

        // Boxing conversion, followed by optional widening reference conversion
        if (context == AssignmentContext.LOOSE && from.isPrimitive()) {
            return dimensions == 0 && unchecked(SourceInfo.none(),
                () -> TypeHelper.getBoxedType(from.type).subtypeOf(type));
        }

        if (dimensions != from.dimensions) {
            return dimensions == 0 || equals(Classes.ObjectType());
        }

        if (!unchecked(SourceInfo.none(), () -> from.type.subtypeOf(type))) {
            return false;
        }

        if (isRaw() || from.isRaw()) {
            return true;
        }

        if (TypeHelper.equals(type, from.type)) {
            if (arguments.size() != from.arguments.size()) {
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
        } else if (isArray() && from.isArray()) {
            return getComponentType().isAssignableFrom(from.getComponentType(), context);
        }

        for (TypeInstance fromSuperType : from.superTypes) {
            if (isAssignableFrom(fromSuperType)) {
                return true;
            }
        }

        return false;
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

    public TypeInstance boxed() {
        if (equals(booleanType())) return BooleanType();
        if (equals(byteType())) return ByteType();
        if (equals(shortType())) return ShortType();
        if (equals(intType())) return IntegerType();
        if (equals(longType())) return LongType();
        if (equals(floatType())) return FloatType();
        if (equals(doubleType())) return DoubleType();
        if (equals(charType())) return CharacterType();
        return this;
    }

    public boolean equals(CtClass other) {
        return TypeHelper.equals(type, other);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TypeInstance that)) return false;
        if (arguments.size() != that.arguments.size()) return false;
        if (dimensions != that.dimensions) return false;
        if (wildcard != that.wildcard) return false;
        return arguments.isEmpty() ? TypeHelper.equals(type, that.type) : equals(new HashSet<>(), that);
    }

    private boolean equals(Set<TypeInstance> set, TypeInstance other) {
        if (set.contains(this)) {
            return true;
        }

        set.add(this);

        if (!TypeHelper.equals(type, other.type)
                || arguments.size() != other.arguments.size()
                || wildcard != other.wildcard) {
            return false;
        }

        if (!isRaw() && !other.isRaw()) {
            for (int i = 0; i < arguments.size(); ++i) {
                if (!arguments.get(i).equals(set, other.arguments.get(i))) {
                    return false;
                }
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

        String className;

        if (javaNames) {
            className = NameHelper.getJavaClassName(SourceInfo.none(), type);
        } else if (simpleNames) {
            className = type.getSimpleName();
        } else {
            className = type.getName();
        }

        while (className.endsWith("[]")) {
            className = className.substring(0, className.length() - 2);
        }

        builder.append(className);

        if (!isRaw() && arguments.size() > 0) {
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
        return Objects.hash(TypeHelper.hashCode(type), arguments.size(), superTypes.size(), dimensions, wildcard);
    }

    @Override
    public String toString() {
        return getSimpleName();
    }
}

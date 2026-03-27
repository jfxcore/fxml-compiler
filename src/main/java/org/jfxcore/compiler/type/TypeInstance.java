// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.type;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.util.CompilationContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * Represents the instantiation tree of a type, in which all generic arguments are replaced with concrete types.
 */
public class TypeInstance {

    private static final class ErasedTypeInstance extends TypeInstance {
        ErasedTypeInstance(TypeInstance source) {
            super(source.declaration(), source.arguments(), source.superTypes(), source.wildcardType());
        }

        ErasedTypeInstance(TypeDeclaration type,
                           int dimensions,
                           List<TypeInstance> arguments,
                           List<TypeInstance> superTypes,
                           WildcardType wildcard) {
            super(type, dimensions, arguments, superTypes, wildcard);
        }
    }

    private static final class NullTypeInstance extends TypeInstance {
        NullTypeInstance() {
            super(ObjectDecl(), 0, List.of(), List.of(), WildcardType.NONE);
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

    private static final class UnionTypeInstance extends TypeInstance {
        final List<TypeInstance> types;

        UnionTypeInstance(List<TypeInstance> types) {
            super(voidDecl(), List.of(), List.of(), WildcardType.NONE);
            this.types = types;
        }

        @Override
        public boolean isAssignableFrom(TypeInstance from, AssignmentContext context) {
            return false;
        }

        @Override
        public boolean subtypeOf(TypeInstance other) {
            return false;
        }

        @Override
        public boolean subtypeOf(TypeDeclaration other) {
            return false;
        }

        @Override
        protected String toString(boolean simpleNames, boolean javaNames) {
            return "<union>";
        }
    }

    public static TypeInstance voidType() { return resolveTypeInstance(voidDecl()); }
    public static TypeInstance booleanType() { return resolveTypeInstance(booleanDecl()); }
    public static TypeInstance charType() { return resolveTypeInstance(charDecl()); }
    public static TypeInstance byteType() { return resolveTypeInstance(byteDecl()); }
    public static TypeInstance shortType() { return resolveTypeInstance(shortDecl()); }
    public static TypeInstance intType() { return resolveTypeInstance(intDecl()); }
    public static TypeInstance longType() { return resolveTypeInstance(longDecl()); }
    public static TypeInstance floatType() { return resolveTypeInstance(floatDecl()); }
    public static TypeInstance doubleType() { return resolveTypeInstance(doubleDecl()); }
    public static TypeInstance BooleanType() { return resolveTypeInstance(BooleanDecl()); }
    public static TypeInstance CharacterType() { return resolveTypeInstance(CharacterDecl()); }
    public static TypeInstance ByteType() { return resolveTypeInstance(ByteDecl()); }
    public static TypeInstance ShortType() { return resolveTypeInstance(ShortDecl()); }
    public static TypeInstance IntegerType() { return resolveTypeInstance(IntegerDecl()); }
    public static TypeInstance LongType() { return resolveTypeInstance(LongDecl()); }
    public static TypeInstance FloatType() { return resolveTypeInstance(FloatDecl()); }
    public static TypeInstance DoubleType() { return resolveTypeInstance(DoubleDecl()); }
    public static TypeInstance NumberType() { return resolveTypeInstance(NumberDecl()); }
    public static TypeInstance StringType() { return resolveTypeInstance(StringDecl()); }
    public static TypeInstance ObjectType() { return resolveTypeInstance(ObjectDecl()); }
    public static TypeInstance bottomType() { return resolveTypeInstance(BottomTypeDecl()); }
    public static TypeInstance nullType() {
        TypeInstance typeInstance = getClassCache().get(null);
        if (typeInstance == null) {
            typeInstance = new NullTypeInstance();
            getClassCache().put(null, typeInstance);
        }

        return typeInstance;
    }

    private static TypeInstance resolveTypeInstance(TypeDeclaration declaration) {
        Objects.requireNonNull(declaration);
        TypeInstance typeInstance = getClassCache().get(declaration);
        if (typeInstance == null) {
            typeInstance = new TypeInvoker(SourceInfo.none()).invokeType(declaration);
            getClassCache().put(declaration, typeInstance);
        }

        return typeInstance;
    }

    @SuppressWarnings("unchecked")
    private static Map<TypeDeclaration, TypeInstance> getClassCache() {
        return (Map<TypeDeclaration, TypeInstance>) CompilationContext.getCurrent()
            .computeIfAbsent(TypeInstance.class, key -> new HashMap<TypeDeclaration, TypeInstance>());
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

    private final TypeDeclaration type;
    private final int dimensions;
    private final WildcardType wildcard;
    private List<TypeInstance> arguments;
    private List<TypeInstance> superTypes;
    private TypeInstance componentType;

    public static TypeInstance of(TypeDeclaration type) {
        return resolveTypeInstance(type);
    }

    public static TypeInstance ofUnion(List<TypeInstance> types) {
        if (types.isEmpty()) throw new IllegalArgumentException("types");
        if (types.size() == 1) return types.get(0);
        return new UnionTypeInstance(types);
    }

    static TypeInstance ofErased(TypeInstance type) {
        return new ErasedTypeInstance(type);
    }

    TypeInstance(TypeDeclaration type,
                 List<TypeInstance> arguments,
                 List<TypeInstance> superTypes,
                 WildcardType wildcard) {
        this.type = Objects.requireNonNull(type);
        this.dimensions = type.dimensions();
        this.arguments = arguments;
        this.superTypes = superTypes;
        this.wildcard = wildcard;

        if (arguments.stream().anyMatch(TypeInstance::isPrimitive)) {
            throw new IllegalArgumentException("arguments");
        }
    }

    private TypeInstance(TypeDeclaration type,
                         int dimensions,
                         List<TypeInstance> arguments,
                         List<TypeInstance> superTypes,
                         WildcardType wildcard) {
        this.type = Objects.requireNonNull(type);
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

        TypeDeclaration type = new Resolver(SourceInfo.none())
            .resolveClass(this.type.name() + "[]".repeat(dimensions));

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

    public int dimensions() {
        return dimensions;
    }

    public boolean isPrimitive() {
        return type.isPrimitive() && dimensions == 0;
    }

    public TypeDeclaration declaration() {
        return type;
    }

    public String name() {
        return toString(false, false);
    }

    public String javaName() {
        return toString(false, true);
    }

    public String simpleName() {
        return toString(true, false);
    }

    public List<TypeInstance> arguments() {
        return arguments;
    }

    public List<TypeInstance> superTypes() {
        return superTypes;
    }

    public WildcardType wildcardType() {
        return wildcard;
    }

    public TypeInstance componentType() {
        if (!isArray()) {
            return this;
        }

        if (componentType != null) {
            return componentType;
        }

        return componentType = new TypeInstance(
            type.requireComponentType(), arguments, superTypes, wildcard);
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
        // Any type is assignable from the bottom type
        if (from.equals(BottomTypeDecl())) {
            return true;
        }

        // Reference types are always assignable from the null type
        if (!isPrimitive() && from instanceof NullTypeInstance) {
            return true;
        }

        if (from instanceof UnionTypeInstance union) {
            for (TypeInstance type : union.types) {
                if (isAssignableFrom(type, context)) {
                    return true;
                }
            }

            return false;
        }

        // Identity conversion
        if (equals(from)) {
            return true;
        }

        // Widening primitive conversion
        if (dimensions == 0 && from.dimensions == 0
                && type.isNumericPrimitive() && from.type.isNumeric()) {
            // In a loose assignment context, we assume an unboxing conversion has occurred
            TypeDeclaration fromType = context == AssignmentContext.LOOSE ?
                from.declaration().primitive().orElse(null) : from.declaration();

            if (charDecl().equals(type)) {
                return charDecl().equals(fromType);
            }

            if (byteDecl().equals(type)) {
                return byteDecl().equals(fromType);
            }

            if (shortDecl().equals(type)) {
                return shortDecl().equals(fromType)
                    || byteDecl().equals(fromType);
            }

            if (intDecl().equals(type)) {
                return intDecl().equals(fromType)
                    || shortDecl().equals(fromType)
                    || charDecl().equals(fromType)
                    || byteDecl().equals(fromType);
            }

            if (longDecl().equals(type)) {
                return longDecl().equals(fromType)
                    || intDecl().equals(fromType)
                    || shortDecl().equals(fromType)
                    || charDecl().equals(fromType)
                    || byteDecl().equals(fromType);
            }

            if (floatDecl().equals(type)) {
                return floatDecl().equals(fromType)
                    || longDecl().equals(fromType)
                    || intDecl().equals(fromType)
                    || shortDecl().equals(fromType)
                    || charDecl().equals(fromType)
                    || byteDecl().equals(fromType);
            }

            if (doubleDecl().equals(type)) {
                return doubleDecl().equals(fromType)
                    || floatDecl().equals(fromType)
                    || longDecl().equals(fromType)
                    || intDecl().equals(fromType)
                    || shortDecl().equals(fromType)
                    || charDecl().equals(fromType)
                    || byteDecl().equals(fromType);
            }

            return false;
        }

        // Unboxing conversion
        if (context == AssignmentContext.LOOSE && from.type.isBoxOf(type)) {
            return type.equals(from.type.primitive().orElse(null));
        }

        // Boxing conversion, followed by optional widening reference conversion
        if (context == AssignmentContext.LOOSE && from.isPrimitive()) {
            return dimensions == 0 && from.type.boxed().subtypeOf(type);
        }

        if (dimensions != from.dimensions) {
            return dimensions == 0 && (equals(ObjectDecl()) || equals(CloneableDecl()) || equals(SerializableDecl()));
        }

        if (!from.type.subtypeOf(type)) {
            return false;
        }

        if (isRaw() || from.isRaw()) {
            return true;
        }

        if (type.equals(from.type)) {
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
            return componentType().isAssignableFrom(from.componentType(), context);
        }

        for (TypeInstance fromSuperType : from.superTypes) {
            if (isAssignableFrom(fromSuperType)) {
                return true;
            }
        }

        return false;
    }

    public boolean subtypeOf(TypeInstance other) {
        if (other instanceof UnionTypeInstance union) {
            for (TypeInstance type : union.types) {
                if (subtypeOf(type)) {
                    return true;
                }
            }

            return false;
        }

        if (other.dimensions == 0 && other.equals(ObjectDecl())) {
            return true;
        }

        return other.dimensions == dimensions && type.subtypeOf(other.type);
    }

    public boolean subtypeOf(TypeDeclaration other) {
        int otherDimensions = 0;

        while (other.isArray()) {
            other = other.componentType().orElseThrow();
            ++otherDimensions;
        }

        if (otherDimensions == 0 && other.equals(ObjectDecl())) {
            return true;
        }

        return dimensions == otherDimensions && type.subtypeOf(other);
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

    public boolean equals(TypeDeclaration other) {
        return type.equals(other);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TypeInstance that)) return false;
        if (arguments.size() != that.arguments.size()) return false;
        if (dimensions != that.dimensions) return false;
        if (wildcard != that.wildcard) return false;
        return arguments.isEmpty() ? type.equals(that.type) : equals(new HashSet<>(), that);
    }

    private boolean equals(Set<TypeInstance> set, TypeInstance other) {
        if (set.contains(this)) {
            return true;
        }

        set.add(this);

        if (!type.equals(other.type)
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
            className = type.javaName();
        } else if (simpleNames) {
            className = type.simpleName();
        } else {
            className = type.name();
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
        return Objects.hash(type, arguments.size(), superTypes.size(), dimensions, wildcard);
    }

    @Override
    public String toString() {
        return simpleName();
    }
}

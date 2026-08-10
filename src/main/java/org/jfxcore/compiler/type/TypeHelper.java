// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.type;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.type.TypeInstance.AssignmentContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TypeHelper {

    /**
     * Returns the exact primitive represented by a numeric primitive or its standard wrapper.
     * General {@code Number} declarations and user-defined subclasses are intentionally excluded.
     */
    public static @Nullable TypeDeclaration getExactNumericPrimitive(TypeInstance type) {
        if (type.isArray()) {
            return null;
        }

        TypeDeclaration declaration = type.declaration();
        if (declaration.isNumericPrimitive()) {
            return declaration;
        }

        TypeDeclaration primitive = declaration.primitive().orElse(null);
        return primitive != null && primitive.isNumericPrimitive() ? primitive : null;
    }

    public static TypeDeclaration promoteNumeric(TypeDeclaration left, TypeDeclaration right) {
        if (left.equals(KnownSymbols.doubleDecl()) || right.equals(KnownSymbols.doubleDecl())) {
            return KnownSymbols.doubleDecl();
        } else if (left.equals(KnownSymbols.floatDecl()) || right.equals(KnownSymbols.floatDecl())) {
            return KnownSymbols.floatDecl();
        } else if (left.equals(KnownSymbols.longDecl()) || right.equals(KnownSymbols.longDecl())) {
            return KnownSymbols.longDecl();
        }

        return KnownSymbols.intDecl();
    }

    public static TypeDeclaration promoteNumeric(TypeDeclaration type) {
        if (type.equals(KnownSymbols.byteDecl())
                || type.equals(KnownSymbols.shortDecl())
                || type.equals(KnownSymbols.charDecl())) {
            return KnownSymbols.intDecl();
        }

        return type;
    }

    /**
     * Determines whether two reference operands may be compared by identity. In addition to
     * ordinary widening relationships, this admits Java-style class/interface side casts while
     * rejecting parameterizations that have a provably distinct common generic supertype.
     */
    public static boolean areReferenceIdentityComparable(TypeInstance left, TypeInstance right) {
        return areReferenceIdentityComparable(left, right, new IdentityHashMap<>());
    }

    private static boolean areReferenceIdentityComparable(
            TypeInstance left,
            TypeInstance right,
            Map<TypeInstance, Set<TypeInstance>> activeComparisons) {
        if (left.isPrimitive() || right.isPrimitive()) {
            return false;
        }

        if (left.equals(TypeInstance.nullType()) || right.equals(TypeInstance.nullType())) {
            return true;
        }

        if (left.isAssignableFrom(right, AssignmentContext.STRICT)
                || right.isAssignableFrom(left, AssignmentContext.STRICT)) {
            return true;
        }

        Set<TypeInstance> activeRights = activeComparisons.computeIfAbsent(
            left, key -> Collections.newSetFromMap(new IdentityHashMap<>()));
        if (!activeRights.add(right)) {
            return true;
        }

        try {
            if (left.isArray() || right.isArray()) {
                if (!left.isArray() || !right.isArray()) {
                    return false;
                }

                TypeInstance leftComponent = left.componentType();
                TypeInstance rightComponent = right.componentType();
                if (leftComponent.isPrimitive() || rightComponent.isPrimitive()) {
                    return leftComponent.equals(rightComponent);
                }

                return areReferenceIdentityComparable(
                    leftComponent, rightComponent, activeComparisons);
            }

            if (haveProvablyDistinctCommonSupertype(left, right, activeComparisons)) {
                return false;
            }

            TypeDeclaration leftDeclaration = left.declaration();
            TypeDeclaration rightDeclaration = right.declaration();
            if (leftDeclaration.subtypeOf(rightDeclaration)
                    || rightDeclaration.subtypeOf(leftDeclaration)) {
                return true;
            }

            if (leftDeclaration.isInterface() && rightDeclaration.isInterface()) {
                return true;
            }

            if (leftDeclaration.isInterface()) {
                return !rightDeclaration.isFinal();
            }

            return rightDeclaration.isInterface() && !leftDeclaration.isFinal();
        } finally {
            activeRights.remove(right);
            if (activeRights.isEmpty()) {
                activeComparisons.remove(left);
            }
        }
    }

    private static boolean haveProvablyDistinctCommonSupertype(
            TypeInstance left,
            TypeInstance right,
            Map<TypeInstance, Set<TypeInstance>> activeComparisons) {
        Map<TypeDeclaration, TypeInstance> leftHierarchy = new HashMap<>();
        collectHierarchy(left, leftHierarchy, Collections.newSetFromMap(new IdentityHashMap<>()));

        Map<TypeDeclaration, TypeInstance> rightHierarchy = new HashMap<>();
        collectHierarchy(right, rightHierarchy, Collections.newSetFromMap(new IdentityHashMap<>()));

        for (Map.Entry<TypeDeclaration, TypeInstance> entry : leftHierarchy.entrySet()) {
            TypeInstance rightType = rightHierarchy.get(entry.getKey());
            if (rightType != null
                    && areParameterizationsProvablyDistinct(
                        entry.getValue(), rightType, activeComparisons)) {
                return true;
            }
        }

        return false;
    }

    private static void collectHierarchy(
            TypeInstance type,
            Map<TypeDeclaration, TypeInstance> result,
            Set<TypeInstance> visited) {
        if (!visited.add(type)) {
            return;
        }

        result.putIfAbsent(type.declaration(), type);

        for (TypeInstance superType : type.superTypes()) {
            collectHierarchy(superType, result, visited);
        }
    }

    private static boolean areParameterizationsProvablyDistinct(
            TypeInstance left,
            TypeInstance right,
            Map<TypeInstance, Set<TypeInstance>> activeComparisons) {
        if (left.isRaw() || right.isRaw()) {
            return false;
        }

        TypeInstance leftOwner = left.owner();
        TypeInstance rightOwner = right.owner();

        if (leftOwner != null && rightOwner != null
                && leftOwner.declaration().equals(rightOwner.declaration())
                && areParameterizationsProvablyDistinct(
                    leftOwner, rightOwner, activeComparisons)) {
            return true;
        }

        if (left.arguments().size() != right.arguments().size()) {
            return true;
        }

        for (int i = 0; i < left.arguments().size(); ++i) {
            if (areTypeArgumentsProvablyDistinct(
                    left.arguments().get(i), right.arguments().get(i), activeComparisons)) {
                return true;
            }
        }

        return false;
    }

    private static boolean areTypeArgumentsProvablyDistinct(
            TypeInstance left,
            TypeInstance right,
            Map<TypeInstance, Set<TypeInstance>> activeComparisons) {
        TypeInstance.WildcardType leftWildcard = left.wildcardType();
        TypeInstance.WildcardType rightWildcard = right.wildcardType();

        if (leftWildcard == TypeInstance.WildcardType.ANY || rightWildcard == TypeInstance.WildcardType.ANY) {
            return false;
        }

        TypeInstance leftBound = left.withWildcard(TypeInstance.WildcardType.NONE);
        TypeInstance rightBound = right.withWildcard(TypeInstance.WildcardType.NONE);

        if (leftWildcard == TypeInstance.WildcardType.NONE) {
            return switch (rightWildcard) {
                case NONE -> !leftBound.equals(rightBound);
                case UPPER -> !rightBound.isAssignableFrom(leftBound, AssignmentContext.STRICT);
                case LOWER -> !leftBound.isAssignableFrom(rightBound, AssignmentContext.STRICT);
                case ANY -> false;
            };
        }

        if (leftWildcard == TypeInstance.WildcardType.UPPER) {
            return switch (rightWildcard) {
                case NONE, LOWER -> !leftBound.isAssignableFrom(rightBound, AssignmentContext.STRICT);
                case UPPER -> !areReferenceIdentityComparable(leftBound, rightBound, activeComparisons);
                case ANY -> false;
            };
        }

        return switch (rightWildcard) {
            case NONE, UPPER -> !rightBound.isAssignableFrom(leftBound, AssignmentContext.STRICT);
            case LOWER, ANY -> false;
        };
    }

    /** Finds the fully substituted occurrence of {@code targetType} in an instantiated type hierarchy. */
    public static @Nullable TypeInstance findSuperType(TypeInstance type, TypeDeclaration targetType) {
        if (type.equals(targetType)) {
            return type;
        }

        for (TypeInstance superType : type.superTypes()) {
            TypeInstance result = findSuperType(superType, targetType);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    public static List<TypeInstance> getTypeArguments(TypeInstance type, TypeDeclaration targetType) {
        if (type.equals(targetType)) {
            return type.arguments();
        }

        for (TypeInstance superType : type.superTypes()) {
            List<TypeInstance> arguments = getTypeArguments(superType, targetType);
            if (!arguments.isEmpty()) {
                return arguments;
            }
        }

        return List.of();
    }

    public static TypeInstance getTypeInstance(Node node) {
        if (!(node instanceof ValueNode)) {
            throw new RuntimeException("Expected " + ValueNode.class.getSimpleName());
        }

        TypeNode typeNode = ((ValueNode)node).getType();
        if (!(typeNode instanceof ResolvedTypeNode)) {
            throw new RuntimeException("Expected " + ResolvedTypeNode.class.getSimpleName());
        }

        return ((ResolvedTypeNode)typeNode).getTypeInstance();
    }

    public static TypeDeclaration getTypeDeclaration(Node node) {
        return getTypeInstance(node).declaration();
    }
}

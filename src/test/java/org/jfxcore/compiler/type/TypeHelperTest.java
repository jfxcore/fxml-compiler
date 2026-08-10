// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.type;

import org.jfxcore.compiler.TestBase;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import static org.jfxcore.compiler.type.KnownSymbols.*;
import static org.junit.jupiter.api.Assertions.*;

public class TypeHelperTest extends TestBase {

    public static class GenericComparable<T> implements Comparable<T> {
        @Override
        public int compareTo(T value) {
            return 0;
        }
    }

    public static class StringComparable extends GenericComparable<String> {}

    public interface FirstGeneric<T> {}

    public interface SecondGeneric<T> {}

    @Test
    public void Exact_Numeric_Classifier_Accepts_Only_Primitives_And_Standard_Wrappers() {
        List<TypeInstance> types = List.of(
            TypeInstance.byteType(), TypeInstance.charType(), TypeInstance.shortType(), TypeInstance.intType(),
            TypeInstance.longType(), TypeInstance.floatType(), TypeInstance.doubleType(),
            TypeInstance.ByteType(), TypeInstance.CharacterType(), TypeInstance.ShortType(), TypeInstance.IntegerType(),
            TypeInstance.LongType(), TypeInstance.FloatType(), TypeInstance.DoubleType());

        List<TypeDeclaration> expected = List.of(
            byteDecl(), charDecl(), shortDecl(), intDecl(), longDecl(), floatDecl(), doubleDecl(),
            byteDecl(), charDecl(), shortDecl(), intDecl(), longDecl(), floatDecl(), doubleDecl());

        for (int i = 0; i < types.size(); ++i) {
            assertEquals(expected.get(i), TypeHelper.getExactNumericPrimitive(types.get(i)));
        }

        Resolver resolver = new Resolver(SourceInfo.none());
        assertNull(TypeHelper.getExactNumericPrimitive(TypeInstance.NumberType()));
        assertNull(TypeHelper.getExactNumericPrimitive(TypeInstance.ObjectType()));
        assertNull(TypeHelper.getExactNumericPrimitive(TypeInstance.BooleanType()));
        assertNull(TypeHelper.getExactNumericPrimitive(TypeInstance.of(resolver.resolveClass(BigDecimal.class.getName()))));
        assertNull(TypeHelper.getExactNumericPrimitive(TypeInstance.of(resolver.resolveClass("int[]"))));
    }

    @Test
    public void Binary_Numeric_Promotion_Covers_Every_Primitive_Pair() {
        TypeDeclaration[] types = {
            byteDecl(), charDecl(), shortDecl(), intDecl(), longDecl(), floatDecl(), doubleDecl()
        };

        for (TypeDeclaration left : types) {
            for (TypeDeclaration right : types) {
                TypeDeclaration expected = expectedPromotion(left, right);
                assertEquals(expected, TypeHelper.promoteNumeric(left, right));
                assertEquals(expected, TypeHelper.promoteNumeric(right, left));
            }
        }
    }

    @Test
    public void FindSuperType_Preserves_Substituted_Comparable_Argument() {
        Resolver resolver = new Resolver(SourceInfo.none());
        TypeInstance type = TypeInstance.of(resolver.resolveClass(StringComparable.class.getName()));
        TypeInstance comparable = TypeHelper.findSuperType(type, ComparableDecl());

        assertNotNull(comparable);
        assertFalse(comparable.isRaw());
        assertEquals(List.of(TypeInstance.StringType()), comparable.arguments());
    }

    @Test
    public void Reference_Identity_Comparability_Rejects_Only_Provably_Distinct_Parameterizations() {
        Resolver resolver = new Resolver(SourceInfo.none());
        TypeInvoker invoker = new TypeInvoker(SourceInfo.none());
        TypeDeclaration listType = resolver.resolveClass(List.class.getName());
        TypeDeclaration collectionType = resolver.resolveClass(Collection.class.getName());
        TypeInstance listOfString = invoker.invokeType(listType, List.of(TypeInstance.StringType()));
        TypeInstance listOfInteger = invoker.invokeType(listType, List.of(TypeInstance.IntegerType()));
        TypeInstance collectionOfInteger = invoker.invokeType(
            collectionType, List.of(TypeInstance.IntegerType()));
        TypeInstance listExtendsNumber = invoker.invokeType(
            listType,
            List.of(TypeInstance.NumberType().withWildcard(TypeInstance.WildcardType.UPPER)));
        TypeInstance listSuperInteger = invoker.invokeType(
            listType,
            List.of(TypeInstance.IntegerType().withWildcard(TypeInstance.WildcardType.LOWER)));
        TypeInstance listOfNumber = invoker.invokeType(listType, List.of(TypeInstance.NumberType()));
        TypeInstance firstOfString = invoker.invokeType(
            resolver.resolveClass(FirstGeneric.class.getName()), List.of(TypeInstance.StringType()));
        TypeInstance secondOfInteger = invoker.invokeType(
            resolver.resolveClass(SecondGeneric.class.getName()), List.of(TypeInstance.IntegerType()));

        assertFalse(TypeHelper.areReferenceIdentityComparable(listOfString, listOfInteger));
        assertFalse(TypeHelper.areReferenceIdentityComparable(listOfString, collectionOfInteger));
        assertTrue(TypeHelper.areReferenceIdentityComparable(listExtendsNumber, listOfInteger));
        assertTrue(TypeHelper.areReferenceIdentityComparable(listSuperInteger, listOfNumber));
        assertTrue(TypeHelper.areReferenceIdentityComparable(firstOfString, secondOfInteger));
        assertFalse(TypeHelper.areReferenceIdentityComparable(
            TypeInstance.StringType(), TypeInstance.of(resolver.resolveClass(Runnable.class.getName()))));
    }

    private TypeDeclaration expectedPromotion(TypeDeclaration left, TypeDeclaration right) {
        if (left.equals(doubleDecl()) || right.equals(doubleDecl())) {
            return doubleDecl();
        }

        if (left.equals(floatDecl()) || right.equals(floatDecl())) {
            return floatDecl();
        }

        if (left.equals(longDecl()) || right.equals(longDecl())) {
            return longDecl();
        }

        return intDecl();
    }
}

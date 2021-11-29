// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CtMethod;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.parse.TypeParser;
import org.junit.jupiter.api.Test;
import org.jfxcore.compiler.TestBase;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unused")
public class TypeInstanceTest extends TestBase {

    public static class Type1<A> {}
    public static class Type2<B> extends Type1<B> {}
    public static class Type3 extends Type2<String> {}

    @Test
    public void Generic_Invocation_Tree_Is_Resolved() {
        Resolver resolver = new Resolver(SourceInfo.none());
        TypeInstance typeInstance = resolver.getTypeInstance(resolver.resolveClass(Type3.class.getName()));

        assertEquals("TypeInstanceTest$Type3", typeInstance.toString());
        assertEquals("TypeInstanceTest$Type2<String>", typeInstance.getSuperTypes().get(0).toString());
        assertEquals("TypeInstanceTest$Type1<String>", typeInstance.getSuperTypes().get(0).getSuperTypes().get(0).toString());
    }

    public static class RecurringType<T> implements Comparable<RecurringType<String>> {
        @Override
        public int compareTo(RecurringType<String> o) {
            return 0;
        }
    }

    @Test
    public void Recurring_Generic_Type_Is_Resolved() {
        Resolver resolver = new Resolver(SourceInfo.none());
        TypeInstance typeInstance = resolver.getTypeInstance(resolver.resolveClass(RecurringType.class.getName()));

        assertEquals("TypeInstanceTest$RecurringType", typeInstance.toString());
        assertEquals("Object", typeInstance.getSuperTypes().get(0).toString());
        assertEquals("Comparable<TypeInstanceTest$RecurringType<String>>", typeInstance.getSuperTypes().get(1).toString());
    }

    public static class Type4<D, S> {}
    public static class Type5<S, D> extends Type4<D, S> {}

    @Test
    public void Parameterize_Generic_Type() {
        Resolver resolver = new Resolver(SourceInfo.none());
        TypeInstance typeInstance = resolver.getTypeInstance(
            resolver.resolveClass(Type5.class.getName()),
            List.of(resolver.getTypeInstance(Classes.StringType()), resolver.getTypeInstance(Classes.DoubleType())));

        assertEquals("TypeInstanceTest$Type5<String,Double>", typeInstance.toString());
        assertEquals("TypeInstanceTest$Type4<Double,String>", typeInstance.getSuperTypes().get(0).toString());
    }

    @Test
    public void IsAssignableFrom_CompatibleGenericTypes() {
        TypeInstance t0 = new TypeParser("java.lang.Comparable<java.lang.String>").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Comparable<java.lang.String>").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_CompatibleGenericArrayTypes() {
        TypeInstance t0 = new TypeParser("java.lang.Comparable<java.lang.String[]>").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Comparable<java.lang.String[]>").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_IncompatibleGenericTypes() {
        TypeInstance t0 = new TypeParser("java.lang.Comparable<java.lang.String>").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Comparable<java.lang.Double>").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_IncompatibleGenericArrayTypes() {
        TypeInstance t0 = new TypeParser("java.lang.Comparable<java.lang.String[]>").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Comparable<java.lang.Double[]>").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_RawType_GenericType() {
        TypeInstance t0 = new TypeParser("java.lang.Comparable").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Comparable<java.lang.Double>").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_Wildcard_GenericType() {
        TypeInstance t0 = new TypeParser("java.lang.Comparable<?>").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Comparable<java.lang.Double>").parse().get(0);
        assertEquals("java.lang.Comparable<?>", t0.getJavaName());
        assertEquals("java.lang.Comparable<java.lang.Double>", t1.getJavaName());
        assertTrue(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_Float_Double() {
        TypeInstance t0 = new TypeParser("float").parse().get(0);
        TypeInstance t1 = new TypeParser("double").parse().get(0);
        assertEquals("float", t0.getJavaName());
        assertEquals("double", t1.getJavaName());
        assertFalse(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_Double_Float() {
        TypeInstance t0 = new TypeParser("double").parse().get(0);
        TypeInstance t1 = new TypeParser("float").parse().get(0);
        assertEquals("double", t0.getJavaName());
        assertEquals("float", t1.getJavaName());
        assertFalse(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_Object_DoubleArray() {
        TypeInstance t0 = new TypeParser("java.lang.Object").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Double[]").parse().get(0);
        assertEquals("java.lang.Object", t0.getJavaName());
        assertEquals("java.lang.Double[]", t1.getJavaName());
        assertTrue(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_Object_DoubleMultiArray() {
        TypeInstance t0 = new TypeParser("java.lang.Object").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Double[][][]").parse().get(0);
        assertEquals("java.lang.Object", t0.getJavaName());
        assertEquals("java.lang.Double[][][]", t1.getJavaName());
        assertTrue(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_Object_primitiveDoubleArray() {
        TypeInstance t0 = new TypeParser("java.lang.Object").parse().get(0);
        TypeInstance t1 = new TypeParser("double[]").parse().get(0);
        assertEquals("java.lang.Object", t0.getJavaName());
        assertEquals("double[]", t1.getJavaName());
        assertTrue(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_Object_primitiveDoubleMultiArray() {
        TypeInstance t0 = new TypeParser("java.lang.Object").parse().get(0);
        TypeInstance t1 = new TypeParser("double[][][]").parse().get(0);
        assertEquals("java.lang.Object", t0.getJavaName());
        assertEquals("double[][][]", t1.getJavaName());
        assertTrue(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_ObjectArray_DoubleArray() {
        TypeInstance t0 = new TypeParser("java.lang.Object[]").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Double[]").parse().get(0);
        assertEquals("java.lang.Object[]", t0.getJavaName());
        assertEquals("java.lang.Double[]", t1.getJavaName());
        assertTrue(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_ObjectArray_DoubleMultiArray() {
        TypeInstance t0 = new TypeParser("java.lang.Object[][][]").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Double[][][]").parse().get(0);
        assertEquals("java.lang.Object[][][]", t0.getJavaName());
        assertEquals("java.lang.Double[][][]", t1.getJavaName());
        assertTrue(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_ObjectArray_primitiveDoubleArray() {
        TypeInstance t0 = new TypeParser("java.lang.Object[]").parse().get(0);
        TypeInstance t1 = new TypeParser("double[]").parse().get(0);
        assertEquals("java.lang.Object[]", t0.getJavaName());
        assertEquals("double[]", t1.getJavaName());
        assertFalse(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_ObjectArray_primitiveDoubleMultiArray() {
        TypeInstance t0 = new TypeParser("java.lang.Object[][][]").parse().get(0);
        TypeInstance t1 = new TypeParser("double[][][]").parse().get(0);
        assertEquals("java.lang.Object[][][]", t0.getJavaName());
        assertEquals("double[][][]", t1.getJavaName());
        assertFalse(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_primitiveDoubleArray_primitiveDoubleArray() {
        TypeInstance t0 = new TypeParser("double[]").parse().get(0);
        TypeInstance t1 = new TypeParser("double[]").parse().get(0);
        assertEquals("double[]", t0.getJavaName());
        assertEquals("double[]", t1.getJavaName());
        assertTrue(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_primitiveDoubleMultiArray2_primitiveDoubleMultiArray3() {
        TypeInstance t0 = new TypeParser("double[][]").parse().get(0);
        TypeInstance t1 = new TypeParser("double[][][]").parse().get(0);
        assertEquals("double[][]", t0.getJavaName());
        assertEquals("double[][][]", t1.getJavaName());
        assertFalse(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsConvertibleFrom_float_double() {
        TypeInstance t0 = new TypeParser("float").parse().get(0);
        TypeInstance t1 = new TypeParser("double").parse().get(0);
        assertTrue(t0.isConvertibleFrom(t1));
    }

    @Test
    public void IsConvertibleFrom_int_long() {
        TypeInstance t0 = new TypeParser("int").parse().get(0);
        TypeInstance t1 = new TypeParser("long").parse().get(0);
        assertTrue(t0.isConvertibleFrom(t1));
    }

    @Test
    public void IsConvertibleFrom_Float_double() {
        TypeInstance t0 = new TypeParser("java.lang.Float").parse().get(0);
        TypeInstance t1 = new TypeParser("double").parse().get(0);
        assertTrue(t0.isConvertibleFrom(t1));
    }

    @Test
    public void IsConvertibleFrom_int_Float() {
        TypeInstance t0 = new TypeParser("int").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Float").parse().get(0);
        assertTrue(t0.isConvertibleFrom(t1));
    }

    public static class Type6<T> {}
    public static class Type7 extends Type6<String> {}

    @Test
    public void IsAssignableFrom_Subtype() {
        Resolver resolver = new Resolver(SourceInfo.none());
        TypeInstance t6 = resolver.getTypeInstance(
            resolver.resolveClass(Type6.class.getName()), List.of(resolver.getTypeInstance(Classes.StringType())));
        TypeInstance t7 = resolver.getTypeInstance(resolver.resolveClass(Type7.class.getName()));
        assertTrue(t6.isAssignableFrom(t7));
    }

    @Test
    public void IsAssignableFrom_Subtype_ArgUpperBound() {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtMethod method = resolver.tryResolveMethod(Classes.ParentType(), m -> m.getName().equals("getChildrenUnmodifiable"));
        TypeInstance t0 = resolver.getTypeInstance(method, Collections.emptyList()).getArguments().get(0);
        TypeInstance t1 = new TypeParser("javafx.scene.Parent").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
    }

    @Test
    public void Scalar_Not_SubtypeOf_Array() {
        Resolver resolver = new Resolver(SourceInfo.none());
        TypeInstance t0 = new TypeParser("java.lang.Object[]").parse().get(0);
        TypeInstance t1 = resolver.getTypeInstance(Classes.NodeType());
        assertFalse(t1.subtypeOf(t0));
    }

    public static class Type8<T extends String> {}

    @Test
    public void TypeArgument_Out_Of_Bounds_Throws() {
        Resolver resolver = new Resolver(SourceInfo.none());
        MarkupException ex = assertThrows(MarkupException.class, () -> resolver.getTypeInstance(
            resolver.resolveClass(Type8.class.getName()), List.of(resolver.getTypeInstance(Classes.DoubleType()))));
        assertEquals(ErrorCode.TYPE_ARGUMENT_OUT_OF_BOUND, ex.getDiagnostic().getCode());
    }

}
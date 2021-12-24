// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javassist.CtConstructor;
import javassist.CtMethod;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.junit.jupiter.api.Test;
import org.jfxcore.compiler.TestBase;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"unused", "rawtypes"})
public class ResolverTest extends TestBase {

    public static void nonGenericParamTypes(int a, String b, Comparable c) {}
    public static void genericParamTypes(int a, Comparable<String> b, ObservableValue<Comparable<String>> c, Comparable<?> d) {}

    @Test
    public void GetParameterTypes_Of_NonGeneric_Method() throws Exception {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtMethod method = resolver.resolveClass(ResolverTest.class.getName()).getDeclaredMethod("nonGenericParamTypes");
        TypeInstance[] paramTypes = resolver.getParameterTypes(method, Collections.emptyList());

        assertEquals(3, paramTypes.length);
        assertEquals("int", paramTypes[0].toString());
        assertEquals("String", paramTypes[1].toString());
        assertEquals("Comparable", paramTypes[2].toString());
    }

    @Test
    public void GetParameterTypes_Of_Generic_Method() throws Exception {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtMethod method = resolver.resolveClass(ResolverTest.class.getName()).getDeclaredMethod("genericParamTypes");
        TypeInstance[] paramTypes = resolver.getParameterTypes(method, Collections.emptyList());

        assertEquals(4, paramTypes.length);
        assertEquals("int", paramTypes[0].toString());
        assertEquals("Comparable<String>", paramTypes[1].toString());
        assertEquals("ObservableValue<Comparable<String>>", paramTypes[2].toString());
        assertEquals("Comparable<?>", paramTypes[3].toString());
    }

    public static class ParamsTestClass {
        public ParamsTestClass(int a, String b, Comparable c) {}
    }

    public static class GenericParamsTestClass {
        public GenericParamsTestClass(int a, Comparable<String> b, ObservableValue<Comparable<String>> c, Comparable<?> d) {}
    }

    @Test
    public void GetParameterTypes_Of_NonGeneric_Constructor() {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtConstructor ctor = resolver.resolveClass(ParamsTestClass.class.getName()).getDeclaredConstructors()[0];
        TypeInstance[] paramTypes = resolver.getParameterTypes(ctor, Collections.emptyList());

        assertEquals(3, paramTypes.length);
        assertEquals("int", paramTypes[0].toString());
        assertEquals("String", paramTypes[1].toString());
        assertEquals("Comparable", paramTypes[2].toString());
    }

    @Test
    public void GetParameterTypes_Of_Generic_Constructor() {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtConstructor ctor = resolver.resolveClass(GenericParamsTestClass.class.getName()).getDeclaredConstructors()[0];
        TypeInstance[] paramTypes = resolver.getParameterTypes(ctor, Collections.emptyList());

        assertEquals(4, paramTypes.length);
        assertEquals("int", paramTypes[0].toString());
        assertEquals("Comparable<String>", paramTypes[1].toString());
        assertEquals("ObservableValue<Comparable<String>>", paramTypes[2].toString());
        assertEquals("Comparable<?>", paramTypes[3].toString());
    }

    public static String nonGenericReturnType() { return null; }
    public static ObservableValue<Comparable<String>> genericReturnType() { return null; }

    @Test
    public void GetReturnType_Of_NonGeneric_Method() throws Exception {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtMethod method = resolver.resolveClass(ResolverTest.class.getName()).getDeclaredMethod("nonGenericReturnType");
        TypeInstance returnType = resolver.getReturnType(method);

        assertEquals("String", returnType.toString());
    }

    @Test
    public void GetReturnType_Of_Generic_Method() throws Exception {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtMethod method = resolver.resolveClass(ResolverTest.class.getName()).getDeclaredMethod("genericReturnType");
        TypeInstance returnType = resolver.getReturnType(method);

        assertEquals("ObservableValue<Comparable<String>>", returnType.toString());
    }


    public static class ReturnTypeTestClass {
        public ReturnTypeTestClass() {}
    }

    public static class GenericReturnTypeTestClass<T extends String> {
        public GenericReturnTypeTestClass() {}
    }

    @Test
    public void GetReturnType_Of_NonGeneric_Constructor() {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtConstructor method = resolver.resolveClass(ReturnTypeTestClass.class.getName()).getDeclaredConstructors()[0];
        TypeInstance returnType = resolver.getReturnType(method);

        assertEquals("ResolverTest$ReturnTypeTestClass", returnType.toString());
    }

    @Test
    public void GetReturnType_Of_Generic_Constructor() {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtConstructor ctor = resolver.resolveClass(GenericReturnTypeTestClass.class.getName()).getConstructors()[0];
        TypeInstance returnType = resolver.getReturnType(ctor);

        assertEquals("ResolverTest$GenericReturnTypeTestClass", returnType.toString());
    }

    public static class GenericClass<T> {}
    public static class GenericClassWithStringBound<T extends String> {}

    @Test
    public void GetTypeInstance_With_Class_And_Arguments() {
        Resolver resolver = new Resolver(SourceInfo.none());
        TypeInstance typeInstance = resolver.getTypeInstance(
            resolver.resolveClass(GenericClass.class.getName()),
            List.of(new TypeInstance(Classes.StringType())));

        assertEquals("ResolverTest$GenericClass<String>", typeInstance.toString());
    }

    @Test
    public void GetTypeInstance_With_Class_Fails_With_Incompatible_TypeArgument() {
        MarkupException ex = assertThrows(MarkupException.class, () -> {
            Resolver resolver = new Resolver(SourceInfo.none());
            resolver.getTypeInstance(
                resolver.resolveClass(GenericClassWithStringBound.class.getName()),
                List.of(new TypeInstance(Classes.DoubleType())));
        });

        assertEquals(ErrorCode.TYPE_ARGUMENT_OUT_OF_BOUND, ex.getDiagnostic().getCode());
    }

    public static class AttachedPropertyHolder {
        public static String getFoo(Node node) { return null; }
        public static void setFoo(Node node, String value) {}

        public static String getBar(Node node) { return null; }
        public static void setBar(Node node, String value) {}
        public static StringProperty barProperty(Node node) { return null; }
    }

    @Test
    public void Detect_Attached_NonObservable_Property() {
        var resolver = new Resolver(SourceInfo.none());
        var property = resolver.resolveProperty(
            null, AttachedPropertyHolder.class.getName() + ".foo");
        assertTrue(property.isAttached());
        assertFalse(property.isObservable());
        assertEquals("foo", property.getName());
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void Detect_Attached_Observable_Property() {
        var resolver = new Resolver(SourceInfo.none());
        var property = resolver.resolveProperty(
            null, AttachedPropertyHolder.class.getName() + ".bar");
        assertTrue(property.isAttached());
        assertTrue(property.isObservable());
        assertEquals("barProperty", property.getPropertyGetter().getName());
        assertEquals("bar", property.getName());
    }

}

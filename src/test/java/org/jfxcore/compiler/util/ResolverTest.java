// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CtConstructor;
import javassist.CtMethod;
import org.jfxcore.compiler.TestBase;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"unused", "rawtypes"})
public class ResolverTest extends TestBase {

    private List<String> imports;

    @BeforeEach
    public void setup() {
        imports = CompilationContext.getCurrent().getImports();
    }

    @AfterEach
    public void restore() {
        CompilationContext.getCurrent().setImports(imports);
    }

    @Test
    public void Resolve_Array_Types() {
        Resolver resolver = new Resolver(SourceInfo.none());
        assertEquals("java.lang.Double[]", resolver.resolveClassAgainstImports("Double[]").getName());
        assertEquals("java.lang.Double[][]", resolver.resolveClassAgainstImports("Double[][]").getName());
        assertEquals("java.lang.Double[][][]", resolver.resolveClassAgainstImports("Double[][][]").getName());
    }

    @Test
    public void Resolve_Generic_Array_Types() {
        Resolver resolver = new Resolver(SourceInfo.none());
        assertEquals("java.lang.Comparable[]", resolver.resolveClassAgainstImports("Comparable<Double>[]").getName());
        assertEquals("java.lang.Comparable[][]", resolver.resolveClassAgainstImports("Comparable<Double>[][]").getName());
        assertEquals("java.lang.Comparable[][][]", resolver.resolveClassAgainstImports("Comparable<Double>[][][]").getName());
    }

    public static class Foo {
        public static class Bar {
            public static class Baz {}
        }
        public static class Double {}
    }

    @Test
    public void Resolve_Nested_Class() {
        Resolver resolver = new Resolver(SourceInfo.none());
        CompilationContext.getCurrent().setImports(
            List.of(ResolverTest.class.getName() + ".*", Foo.class.getName() + ".*", Foo.Bar.class.getName() + ".*"));

        assertEquals(
            "org.jfxcore.compiler.util.ResolverTest$Foo",
            resolver.resolveClassAgainstImports("Foo").getName());
        assertEquals(
            "org.jfxcore.compiler.util.ResolverTest$Foo$Bar",
            resolver.resolveClassAgainstImports("Bar").getName());
        assertEquals(
            "org.jfxcore.compiler.util.ResolverTest$Foo$Bar$Baz",
            resolver.resolveClassAgainstImports("Baz").getName());
    }

    @Test
    public void Resolve_Nested_Class_With_JavaLang_SimpleName() {
        Resolver resolver = new Resolver(SourceInfo.none());
        CompilationContext.getCurrent().setImports(
            List.of(ResolverTest.class.getName() + ".*", Foo.class.getName() + ".*"));

        assertEquals(
            "org.jfxcore.compiler.util.ResolverTest$Foo$Double",
            resolver.resolveClassAgainstImports("Double").getName());
    }

    @Test
    public void Resolve_Class_With_JavaLang_SimpleName() {
        Resolver resolver = new Resolver(SourceInfo.none());
        assertEquals("java.lang.Double", resolver.resolveClassAgainstImports("Double").getName());
    }

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

    public record PropertyTestRun(
        String propertyName,
        boolean readOnly,
        boolean observable,
        boolean hasPropertyGetter,
        boolean hasGetter,
        boolean hasSetter) {}

    public static class PropertyArgumentsProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                Arguments.of(new PropertyTestRun("prop1", true, false, false, true, false)),
                Arguments.of(new PropertyTestRun("prop2", true, true, true, false, false)),
                Arguments.of(new PropertyTestRun("prop3", true, true, true, true, false)),
                Arguments.of(new PropertyTestRun("prop4", false, false, false, true, true)),
                Arguments.of(new PropertyTestRun("prop5", false, true, true, false, false)),
                Arguments.of(new PropertyTestRun("prop6", false, true, true, true, false)),
                Arguments.of(new PropertyTestRun("prop7", false, true, true, true, true))
            );
        }
    }

    public static class PropertyHolder {
        // read-only, not observable
        public String getProp1() { return null; }

        // read-only, observable
        public ReadOnlyStringProperty prop2Property() { return null; }

        // read-only, observable + getter
        public String getProp3() { return null; }
        public ReadOnlyStringProperty prop3Property() { return null; }

        // writable, not observable
        public String getProp4() { return null; }
        public void setProp4(String value) {}

        // writable, observable
        public StringProperty prop5Property() { return null; }

        // writable, observable + getter
        public String getProp6() { return null; }
        public StringProperty prop6Property() { return null; }

        // writable, observable + getter + setter
        public String getProp7() { return null; }
        public void setProp7(String value) {}
        public StringProperty prop7Property() { return null; }
    }

    public static class StaticPropertyHolder {
        // read-only, not observable
        public static String getProp1(Node node) { return null; }

        // read-only, observable
        public static ReadOnlyStringProperty prop2Property(Node node) { return null; }

        // read-only, observable + getter
        public static String getProp3(Node node) { return null; }
        public static ReadOnlyStringProperty prop3Property(Node node) { return null; }

        // writable, not observable
        public static String getProp4(Node node) { return null; }
        public static void setProp4(Node node, String value) {}

        // writable, observable
        public static StringProperty prop5Property(Node node) { return null; }

        // writable, observable + getter
        public static String getProp6(Node node) { return null; }
        public static StringProperty prop6Property(Node node) { return null; }

        // writable, observable + getter + setter
        public static String getProp7(Node node) { return null; }
        public static void setProp7(Node node, String value) {}
        public static StringProperty prop7Property(Node node) { return null; }
    }

    public static class MixedPropertyHolder extends PropertyHolder {
        // read-only, not observable
        public static String getProp1(Node node) { return null; }

        // read-only, observable
        public static ReadOnlyStringProperty prop2Property(Node node) { return null; }

        // read-only, observable + getter
        public static String getProp3(Node node) { return null; }
        public static ReadOnlyStringProperty prop3Property(Node node) { return null; }

        // writable, not observable
        public static String getProp4(Node node) { return null; }
        public static void setProp4(Node node, String value) {}

        // writable, observable
        public static StringProperty prop5Property(Node node) { return null; }

        // writable, observable + getter
        public static String getProp6(Node node) { return null; }
        public static StringProperty prop6Property(Node node) { return null; }

        // writable, observable + getter + setter
        public static String getProp7(Node node) { return null; }
        public static void setProp7(Node node, String value) {}
        public static StringProperty prop7Property(Node node) { return null; }
    }

    private void assertPropertyInfo(PropertyTestRun testRun, PropertyInfo property) {
        assertEquals(testRun.propertyName, property.getName());
        assertEquals(testRun.observable, property.isObservable());
        assertEquals(testRun.readOnly, property.isReadOnly());
        assertEquals(testRun.hasPropertyGetter, property.getPropertyGetter() != null);
        assertEquals(testRun.hasGetter, property.getGetter() != null);
        assertEquals(testRun.hasSetter, property.getSetter() != null);
    }

    @ParameterizedTest
    @ArgumentsSource(PropertyArgumentsProvider.class)
    public void Detect_Qualified_Property_Names_Test(PropertyTestRun testRun) {
        var resolver = new Resolver(SourceInfo.none());
        var declaringClass = resolver.getTypeInstance(resolver.resolveClass(PropertyHolder.class.getName()));
        var property = resolver.resolveProperty(
            declaringClass, true, PropertyHolder.class.getName(), testRun.propertyName);

        assertFalse(property.isStatic());
        assertPropertyInfo(testRun, property);
    }

    @ParameterizedTest
    @ArgumentsSource(PropertyArgumentsProvider.class)
    public void Detect_Unqualified_Property_Names_Test(PropertyTestRun testRun) {
        var resolver = new Resolver(SourceInfo.none());
        var declaringClass = resolver.getTypeInstance(resolver.resolveClass(PropertyHolder.class.getName()));
        var property = resolver.resolveProperty(declaringClass, true, testRun.propertyName);

        assertFalse(property.isStatic());
        assertPropertyInfo(testRun, property);
    }

    @ParameterizedTest
    @ArgumentsSource(PropertyArgumentsProvider.class)
    public void Detect_Unqualified_Property_Names_With_Same_Name_As_Static_Properties_Test(PropertyTestRun testRun) {
        var resolver = new Resolver(SourceInfo.none());
        var declaringClass = resolver.getTypeInstance(resolver.resolveClass(MixedPropertyHolder.class.getName()));
        var property = resolver.resolveProperty(declaringClass, true, testRun.propertyName);

        assertFalse(property.isStatic());
        assertPropertyInfo(testRun, property);
    }

    @ParameterizedTest
    @ArgumentsSource(PropertyArgumentsProvider.class)
    public void Detect_Static_Property_Names_Test(PropertyTestRun testRun) {
        var resolver = new Resolver(SourceInfo.none());
        var declaringClass = resolver.getTypeInstance(resolver.resolveClass(StaticPropertyHolder.class.getName()));
        var property = resolver.resolveProperty(
            declaringClass, true, StaticPropertyHolder.class.getName(), testRun.propertyName);

        assertTrue(property.isStatic());
        assertPropertyInfo(testRun, property);
    }

    @ParameterizedTest
    @ArgumentsSource(PropertyArgumentsProvider.class)
    public void Detect_Static_Property_Names_With_Same_Name_As_Local_Properties_Test(PropertyTestRun testRun) {
        var resolver = new Resolver(SourceInfo.none());
        var declaringClass = resolver.getTypeInstance(resolver.resolveClass(MixedPropertyHolder.class.getName()));
        var property = resolver.resolveProperty(
            declaringClass, true, MixedPropertyHolder.class.getName(), testRun.propertyName);

        assertTrue(property.isStatic());
        assertPropertyInfo(testRun, property);
    }

}

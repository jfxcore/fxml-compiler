// Copyright (c) 2022, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CtClass;
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
import javafx.beans.property.ListProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.layout.Pane;
import javafx.util.Pair;
import java.util.ArrayList;
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

    public static class ArrayTest {
        public <T> Comparable<T>[] property1() { return null; }
        public <T> Comparable<T>[][] property2() { return null; }
        public <T> Comparable<T>[][][] property3() { return null; }
    }

    @Test
    public void Resolve_Array_Return_Type() {
        Resolver resolver = new Resolver(SourceInfo.none());
        TypeInstance paneType = resolver.getTypeInstance(resolver.resolveClass(ArrayTest.class.getName()));

        PropertyInfo propertyInfo = resolver.resolveProperty(paneType, false, "property1");
        assertEquals("Comparable<Object>[]", propertyInfo.getType().toString());

        propertyInfo = resolver.resolveProperty(paneType, false, "property2");
        assertEquals("Comparable<Object>[][]", propertyInfo.getType().toString());

        propertyInfo = resolver.resolveProperty(paneType, false, "property3");
        assertEquals("Comparable<Object>[][][]", propertyInfo.getType().toString());
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

    public static class GenericConstructorWithRawUsage<T extends String> {
        public GenericConstructorWithRawUsage(T first, Comparable<T> second) {}

        @SafeVarargs
        public GenericConstructorWithRawUsage(T first, Comparable<T>... second) {}

        @SafeVarargs
        public GenericConstructorWithRawUsage(boolean first, T second, Comparable<? super T>... third) {}
    }

    @Test
    public void GetParameterTypes_Of_Generic_Constructor_With_RawUsage() {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtClass clazz = resolver.resolveClass(GenericConstructorWithRawUsage.class.getName());
        CtConstructor ctor = clazz.getDeclaredConstructors()[0];
        TypeInstance[] paramTypes = resolver.getParameterTypes(ctor, List.of(TypeInstance.of(clazz)));

        assertEquals(2, paramTypes.length);
        assertFalse(paramTypes[0].isRaw());
        assertEquals("java.lang.String", paramTypes[0].getJavaName());
        assertTrue(paramTypes[1].isRaw());
        assertEquals("java.lang.Comparable", paramTypes[1].getJavaName());
    }

    @Test
    public void GetParameterTypes_Of_Generic_Varargs_Constructor_With_RawUsage() {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtClass clazz = resolver.resolveClass(GenericConstructorWithRawUsage.class.getName());
        CtConstructor ctor = clazz.getDeclaredConstructors()[1];
        TypeInstance[] paramTypes = resolver.getParameterTypes(ctor, List.of(TypeInstance.of(clazz)));

        assertEquals(2, paramTypes.length);
        assertFalse(paramTypes[0].isRaw());
        assertEquals("java.lang.String", paramTypes[0].getJavaName());
        assertTrue(paramTypes[1].isRaw());
        assertEquals("java.lang.Comparable[]", paramTypes[1].getJavaName());
    }

    @Test
    public void GetParameterTypes_Of_LowerBound_Generic_Varargs_Constructor_With_RawUsage() {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtClass clazz = resolver.resolveClass(GenericConstructorWithRawUsage.class.getName());
        CtConstructor ctor = clazz.getDeclaredConstructors()[2];
        TypeInstance[] paramTypes = resolver.getParameterTypes(ctor, List.of(TypeInstance.of(clazz)));

        assertEquals(3, paramTypes.length);
        assertFalse(paramTypes[0].isRaw());
        assertEquals("boolean", paramTypes[0].getJavaName());
        assertFalse(paramTypes[1].isRaw());
        assertEquals("java.lang.String", paramTypes[1].getJavaName());
        assertTrue(paramTypes[2].isRaw());
        assertEquals("java.lang.Comparable[]", paramTypes[2].getJavaName());
    }

    public static String nonGenericReturnType() { return null; }
    public static ObservableValue<Comparable<String>> genericReturnType() { return null; }
    public static ObservableValue<Comparable<? super String>> genericReturnTypeWithLowerBound() { return null; }
    public static ObservableValue<Comparable<? extends String>> genericReturnTypeWithUpperBound() { return null; }

    @Test
    public void GetReturnType_Of_NonGeneric_Method() throws Exception {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtMethod method = resolver.resolveClass(ResolverTest.class.getName()).getDeclaredMethod("nonGenericReturnType");
        TypeInstance returnType = resolver.getTypeInstance(method, List.of());

        assertEquals("String", returnType.toString());
    }

    @Test
    public void GetReturnType_Of_Generic_Method() throws Exception {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtMethod method = resolver.resolveClass(ResolverTest.class.getName()).getDeclaredMethod("genericReturnType");
        TypeInstance returnType = resolver.getTypeInstance(method, List.of());

        assertEquals("ObservableValue<Comparable<String>>", returnType.toString());
    }

    @Test
    public void GetReturnType_Of_Generic_Method_With_Lower_Bound() throws Exception {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtMethod method = resolver.resolveClass(
            ResolverTest.class.getName()).getDeclaredMethod("genericReturnTypeWithLowerBound");
        TypeInstance returnType = resolver.getTypeInstance(method, List.of());

        assertEquals("ObservableValue<Comparable<? super String>>", returnType.toString());
    }

    @Test
    public void GetReturnType_Of_Generic_Method_With_Upper_Bound() throws Exception {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtMethod method = resolver.resolveClass(
            ResolverTest.class.getName()).getDeclaredMethod("genericReturnTypeWithUpperBound");
        TypeInstance returnType = resolver.getTypeInstance(method, List.of());

        assertEquals("ObservableValue<Comparable<? extends String>>", returnType.toString());
    }

    public static class ReturnTypeTestClass {
        public ReturnTypeTestClass() {}
    }

    public static class GenericReturnTypeTestClass<T extends String> {
        public GenericReturnTypeTestClass() {}
        public ObservableValue<? super T> methodWithLowerBound() { return null; }
        public ObservableValue<? extends T> methodWithUpperBound() { return null; }
    }

    @Test
    public void GetReturnType_Of_NonGeneric_Constructor() {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtConstructor method = resolver.resolveClass(ReturnTypeTestClass.class.getName()).getDeclaredConstructors()[0];
        TypeInstance returnType = resolver.getTypeInstance(method, List.of());

        assertEquals("ResolverTest$ReturnTypeTestClass", returnType.toString());
    }

    @Test
    public void GetReturnType_Of_Generic_Constructor() {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtConstructor ctor = resolver.resolveClass(GenericReturnTypeTestClass.class.getName()).getConstructors()[0];
        TypeInstance returnType = resolver.getTypeInstance(ctor, List.of());

        assertEquals("ResolverTest$GenericReturnTypeTestClass", returnType.toString());
    }

    @Test
    public void GetReturnType_Of_Generic_Method_With_Lower_Bound_Of_Type_Parameter() throws Exception {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtClass clazz = resolver.resolveClass(GenericReturnTypeTestClass.class.getName());
        CtMethod method = clazz.getDeclaredMethod("methodWithLowerBound");
        TypeInstance classTypeInst = resolver.getTypeInstance(clazz, List.of(TypeInstance.StringType()));
        TypeInstance returnType = resolver.getTypeInstance(method, List.of(classTypeInst));

        assertEquals("ObservableValue<? super String>", returnType.toString());
    }

    @Test
    public void GetReturnType_Of_Generic_Method_With_Upper_Bound_Of_Type_Parameter() throws Exception {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtClass clazz = resolver.resolveClass(GenericReturnTypeTestClass.class.getName());
        CtMethod method = clazz.getDeclaredMethod("methodWithUpperBound");
        TypeInstance classTypeInst = resolver.getTypeInstance(clazz, List.of(TypeInstance.StringType()));
        TypeInstance returnType = resolver.getTypeInstance(method, List.of(classTypeInst));

        assertEquals("ObservableValue<? extends String>", returnType.toString());
    }

    public static class GenericClass<T> {}
    public static class GenericClassWithStringBound<T extends String> {}

    @Test
    public void GetTypeInstance_With_Class_And_Arguments() {
        Resolver resolver = new Resolver(SourceInfo.none());
        TypeInstance typeInstance = resolver.getTypeInstance(
            resolver.resolveClass(GenericClass.class.getName()),
            List.of(TypeInstance.StringType()));

        assertEquals("ResolverTest$GenericClass<String>", typeInstance.toString());
    }

    @Test
    public void GetTypeInstance_With_Class_Fails_With_Incompatible_TypeArgument() {
        MarkupException ex = assertThrows(MarkupException.class, () -> {
            Resolver resolver = new Resolver(SourceInfo.none());
            resolver.getTypeInstance(
                resolver.resolveClass(GenericClassWithStringBound.class.getName()),
                List.of(TypeInstance.DoubleType()));
        });

        assertEquals(ErrorCode.TYPE_ARGUMENT_OUT_OF_BOUND, ex.getDiagnostic().getCode());
    }

    public static class StaticPropertyWithGenericNode {
        public static <T extends Node> ObservableList<? super T> getList1(T node) { return null; }
        public static <T extends Node> ObservableList<Comparable<? super T>> getList1b(T node) { return null;}
        public static <T extends Node> ListProperty<? super T> list2Property(T node) { return null; }
        public static <T extends Pane> ObservableList<? super T> getList3(T node) { return null; }
        public static <T extends TableView<String>> ObservableList<? super T> getList4(T node) { return null; }
        public static <T> ObservableList<Pair<? super T, ?>> getList5(T node) { return null; }
    }

    @Test
    public void GetTypeInstance_Of_Static_Property_With_Generic_Node() {
        Resolver resolver = new Resolver(SourceInfo.none());
        TypeInstance paneType = resolver.getTypeInstance(resolver.resolveClass(Pane.class.getName()));
        var names = new ArrayList<>(List.of(StaticPropertyWithGenericNode.class.getName().split("\\.")));
        names.add("list1");
        PropertyInfo propertyInfo = resolver.resolveProperty(paneType, true, names.toArray(String[]::new));
        assertEquals("ObservableList<? super Pane>", propertyInfo.getType().toString());
    }

    @Test
    public void GetTypeInstance_Of_Static_Property_With_Generic_Node_2() {
        Resolver resolver = new Resolver(SourceInfo.none());
        TypeInstance paneType = resolver.getTypeInstance(resolver.resolveClass(Pane.class.getName()));
        var names = new ArrayList<>(List.of(StaticPropertyWithGenericNode.class.getName().split("\\.")));
        names.add("list1b");
        PropertyInfo propertyInfo = resolver.resolveProperty(paneType, true, names.toArray(String[]::new));
        assertEquals("ObservableList<Comparable<? super Pane>>", propertyInfo.getType().toString());
    }

    @Test
    public void GetTypeInstance_Of_Static_ListProperty_With_Generic_Node() {
        Resolver resolver = new Resolver(SourceInfo.none());
        TypeInstance paneType = resolver.getTypeInstance(resolver.resolveClass(Pane.class.getName()));
        var names = new ArrayList<>(List.of(StaticPropertyWithGenericNode.class.getName().split("\\.")));
        names.add("list2");
        PropertyInfo propertyInfo = resolver.resolveProperty(paneType, true, names.toArray(String[]::new));
        assertEquals("ObservableList<? super Pane>", propertyInfo.getType().toString());
    }

    @Test
    public void GetTypeInstance_Of_Static_Property_With_Generic_Node_Out_Of_Bounds() {
        Resolver resolver = new Resolver(SourceInfo.none());
        TypeInstance buttonType = resolver.getTypeInstance(resolver.resolveClass(Button.class.getName()));
        var names = new ArrayList<>(List.of(StaticPropertyWithGenericNode.class.getName().split("\\.")));
        names.add("list3");

        // TODO: PROPERTY_NOT_FOUND is not a good diagnostic when the generic argument is out of bounds
        MarkupException ex = assertThrows(MarkupException.class,
            () -> resolver.resolveProperty(buttonType, true, names.toArray(String[]::new)));
        assertEquals(ErrorCode.PROPERTY_NOT_FOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void GetTypeInstance_Of_Static_Property_With_Generic_Node_Out_Of_Bounds_For_TableView_Argument() {
        Resolver resolver = new Resolver(SourceInfo.none());
        TypeInstance tableViewType = resolver.getTypeInstance(
            resolver.resolveClass(TableView.class.getName()), List.of(TypeInstance.DoubleType()));
        var names = new ArrayList<>(List.of(StaticPropertyWithGenericNode.class.getName().split("\\.")));
        names.add("list4");

        MarkupException ex = assertThrows(MarkupException.class,
            () -> resolver.resolveProperty(tableViewType, true, names.toArray(String[]::new)));
        assertEquals(ErrorCode.TYPE_ARGUMENT_OUT_OF_BOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void GetTypeInstance_Of_Static_Property_With_Wildcard_Argument() {
        Resolver resolver = new Resolver(SourceInfo.none());
        TypeInstance buttonType = resolver.getTypeInstance(resolver.resolveClass(Button.class.getName()));
        var names = new ArrayList<>(List.of(StaticPropertyWithGenericNode.class.getName().split("\\.")));
        names.add("list5");

        PropertyInfo propertyInfo = resolver.resolveProperty(buttonType, true, names.toArray(String[]::new));
        assertEquals("ObservableList<Pair<? super Button,?>>", propertyInfo.getType().toString());
    }

    public static class PropertyInGenericClass<T> {
        public static <T extends Node> ObservableList<? super T> getList1(T node) {
            return null;
        }

        public <U extends Node> ObservableList<? super U> getList2(T node) {
            return null;
        }
    }

    @Test
    public void GetTypeInstance_Of_Static_Property_In_Generic_Class() {
        Resolver resolver = new Resolver(SourceInfo.none());
        TypeInstance buttonType = resolver.getTypeInstance(resolver.resolveClass(Button.class.getName()));
        var names = new ArrayList<>(List.of(PropertyInGenericClass.class.getName().split("\\.")));
        names.add("list1");

        PropertyInfo propertyInfo = resolver.resolveProperty(buttonType, true, names.toArray(String[]::new));
        assertEquals("ObservableList<? super Button>", propertyInfo.getType().toString());
    }

    @Test
    public void GetTypeInstance_Of_Not_A_Static_Property_In_Generic_Class_Fails() {
        Resolver resolver = new Resolver(SourceInfo.none());
        TypeInstance buttonType = resolver.getTypeInstance(resolver.resolveClass(Button.class.getName()));
        var names = new ArrayList<>(List.of(PropertyInGenericClass.class.getName().split("\\.")));
        names.add("list2");

        MarkupException ex = assertThrows(MarkupException.class,
            () -> resolver.resolveProperty(buttonType, true, names.toArray(String[]::new)));

        assertEquals(ErrorCode.PROPERTY_NOT_FOUND, ex.getDiagnostic().getCode());
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
                Arguments.of(new PropertyTestRun("prop7", false, true, true, true, true)),
                Arguments.of(new PropertyTestRun("prop8", true, true, true, false, false)),
                Arguments.of(new PropertyTestRun("prop9", false, true, true, false, false))
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

        // read-only, observable, but looks like a regular getter
        public ReadOnlyStringProperty getProp8() { return null; }

        // writable, observable, but looks like a regular getter
        public StringProperty getProp9() { return null; }
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

        // read-only, observable, but looks like a regular getter
        public static ReadOnlyStringProperty getProp8(Node node) { return null; }

        // writable, observable, but looks like a regular getter
        public static StringProperty getProp9(Node node) { return null; }
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

        // read-only, observable, but looks like a regular getter
        public static ReadOnlyStringProperty getProp8(Node node) { return null; }

        // writable, observable, but looks like a regular getter
        public static StringProperty getProp9(Node node) { return null; }
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
        var declaringClass = resolver.getTypeInstance(resolver.resolveClass(Node.class.getName()));
        var property = resolver.resolveProperty(
            declaringClass, true, StaticPropertyHolder.class.getName(), testRun.propertyName);

        assertTrue(property.isStatic());
        assertPropertyInfo(testRun, property);
    }

    @ParameterizedTest
    @ArgumentsSource(PropertyArgumentsProvider.class)
    public void Detect_Static_Property_Names_With_Same_Name_As_Local_Properties_Test(PropertyTestRun testRun) {
        var resolver = new Resolver(SourceInfo.none());
        var declaringClass = resolver.getTypeInstance(resolver.resolveClass(Node.class.getName()));
        var property = resolver.resolveProperty(
            declaringClass, true, MixedPropertyHolder.class.getName(), testRun.propertyName);

        assertTrue(property.isStatic());
        assertPropertyInfo(testRun, property);
    }

    public static class ContextualPropertyHolder {
        public static String getProp1(Object node) { return null; }
        public static void setProp1(Node node, String value) {}

        public static String getProp2(Object node) { return null; }
        public static StringProperty prop2Property(Node node) { return null; }
    }

    @Test
    public void Detect_Contextual_Setter() {
        var resolver = new Resolver(SourceInfo.none());
        var declaringClass = resolver.getTypeInstance(resolver.resolveClass(Object.class.getName()));
        var property = resolver.resolveProperty(
            declaringClass, true, ContextualPropertyHolder.class.getName(), "prop1");
        assertTrue(property.isStatic());
        assertTrue(property.isReadOnly());
        assertFalse(property.isObservable());
        assertNull(property.getPropertyGetter());
        assertNotNull(property.getGetter());
        assertNull(property.getSetter());

        declaringClass = resolver.getTypeInstance(resolver.resolveClass(Node.class.getName()));
        property = resolver.resolveProperty(
            declaringClass, true, ContextualPropertyHolder.class.getName(), "prop1");
        assertTrue(property.isStatic());
        assertFalse(property.isReadOnly());
        assertFalse(property.isObservable());
        assertNull(property.getPropertyGetter());
        assertNotNull(property.getGetter());
        assertNotNull(property.getSetter());
    }

    @Test
    public void Detect_Contextual_ObservableProperty() {
        var resolver = new Resolver(SourceInfo.none());
        var declaringClass = resolver.getTypeInstance(resolver.resolveClass(Object.class.getName()));
        var property = resolver.resolveProperty(
            declaringClass, true, ContextualPropertyHolder.class.getName(), "prop2");
        assertTrue(property.isStatic());
        assertTrue(property.isReadOnly());
        assertFalse(property.isObservable());
        assertNull(property.getPropertyGetter());
        assertNotNull(property.getGetter());
        assertNull(property.getSetter());

        declaringClass = resolver.getTypeInstance(resolver.resolveClass(Node.class.getName()));
        property = resolver.resolveProperty(
            declaringClass, true, ContextualPropertyHolder.class.getName(), "prop2");
        assertTrue(property.isStatic());
        assertFalse(property.isReadOnly());
        assertTrue(property.isObservable());
        assertNotNull(property.getPropertyGetter());
        assertNotNull(property.getGetter());
        assertNull(property.getSetter());
    }

}

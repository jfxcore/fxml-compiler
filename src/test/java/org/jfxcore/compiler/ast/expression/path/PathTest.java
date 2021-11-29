// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import javafx.beans.property.ListProperty;
import javafx.beans.property.Property;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import org.jfxcore.compiler.TestBase;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unused")
public class PathTest extends TestBase {

    public static class Baz1<T> {
        public T quxField;
        public T quxGetter() { return null; }
        public Property<T> quxProperty() { return null; }
    }

    public static class Bar1<T> {
        public Baz1<T> bazField;
        public Baz1<T> bazGetter() { return null; }
        public Property<Baz1<T>> bazProperty() { return null; }
    }

    public static class Foo1 {
        public Bar1<String> barField;
        public Bar1<String> barGetter() { return null;}
        public Property<Bar1<String>> barProperty() { return null;}
    }

    @Test
    public void Path_Of_Generic_Fields_Is_Resolved_Correctly() {
        Resolver resolver = new Resolver(SourceInfo.none());
        String[] segments = new String[] {"barField", "bazField", "quxField"};
        Segment firstSegment = new ParentSegment(new TypeInstance(resolver.resolveClass(Foo1.class.getName())), null, -1);
        ResolvedPath path = ResolvedPath.parse(firstSegment, segments, true, SourceInfo.none());

        assertEquals("PathTest$Foo1", path.get(0).getTypeInstance().toString());
        assertEquals("PathTest$Bar1<String>", path.get(1).getTypeInstance().toString());
        assertEquals("PathTest$Baz1<String>", path.get(2).getTypeInstance().toString());
        assertEquals("String", path.get(3).getTypeInstance().toString());
    }

    @Test
    public void Path_Of_Generic_Getters_Is_Resolved_Correctly() {
        Resolver resolver = new Resolver(SourceInfo.none());
        String[] segments = new String[] {"barGetter", "bazGetter", "quxGetter"};
        Segment firstSegment = new ParentSegment(new TypeInstance(resolver.resolveClass(Foo1.class.getName())), null, -1);
        ResolvedPath path = ResolvedPath.parse(firstSegment, segments, true, SourceInfo.none());

        assertEquals("PathTest$Foo1", path.get(0).getTypeInstance().toString());
        assertEquals("PathTest$Bar1<String>", path.get(1).getTypeInstance().toString());
        assertEquals("PathTest$Baz1<String>", path.get(2).getTypeInstance().toString());
        assertEquals("String", path.get(3).getTypeInstance().toString());
    }

    @Test
    public void Path_Of_Generic_PropertyGetters_Is_Resolved_Correctly() {
        Resolver resolver = new Resolver(SourceInfo.none());
        String[] segments = new String[] {"barProperty", "bazProperty", "quxProperty"};
        Segment firstSegment = new ParentSegment(new TypeInstance(resolver.resolveClass(Foo1.class.getName())), null, -1);
        ResolvedPath path = ResolvedPath.parse(firstSegment, segments, true, SourceInfo.none());

        assertEquals("PathTest$Foo1", path.get(0).getValueTypeInstance().toString());
        assertEquals("PathTest$Bar1<String>", path.get(1).getValueTypeInstance().toString());
        assertEquals("PathTest$Baz1<String>", path.get(2).getValueTypeInstance().toString());
        assertEquals("String", path.get(3).getValueTypeInstance().toString());
    }

    public static class ClassBoundInClassSignature<T extends String> {
        public <S extends T> Property<S> testGetter_Property() { return null; }
        public Property<T> testField_Property;

        public <S extends T> Comparable<S> testGetter_NonProperty() { return null; }
        public Comparable<T> testField_NonProperty;
    }

    @Test
    public void RawTypeUse_Hides_ClassBound_In_ClassSignature() {
        Resolver resolver = new Resolver(SourceInfo.none());
        Segment firstSegment = new ParentSegment(new TypeInstance(resolver.resolveClass(ClassBoundInClassSignature.class.getName())), null, -1);

        ResolvedPath getterPath = ResolvedPath.parse(firstSegment, new String[] {"testGetter_Property"}, true, SourceInfo.none());
        ResolvedPath fieldPath = ResolvedPath.parse(firstSegment, new String[] {"testField_Property"}, true, SourceInfo.none());
        assertEquals("PathTest$ClassBoundInClassSignature", getterPath.get(0).getTypeInstance().toString());
        assertEquals("Object", getterPath.get(1).getValueTypeInstance().toString());
        assertEquals("PathTest$ClassBoundInClassSignature", fieldPath.get(0).getTypeInstance().toString());
        assertEquals("Object", fieldPath.get(1).getValueTypeInstance().toString());

        getterPath = ResolvedPath.parse(firstSegment, new String[] {"testGetter_NonProperty"}, true, SourceInfo.none());
        fieldPath = ResolvedPath.parse(firstSegment, new String[] {"testField_NonProperty"}, true, SourceInfo.none());
        assertEquals("PathTest$ClassBoundInClassSignature", getterPath.get(0).getTypeInstance().toString());
        assertEquals("Comparable", getterPath.get(1).getTypeInstance().toString());
        assertEquals("PathTest$ClassBoundInClassSignature", fieldPath.get(0).getTypeInstance().toString());
        assertEquals("Comparable", fieldPath.get(1).getTypeInstance().toString());
    }

    public static class InterfaceBoundInClassSignature<T extends AutoCloseable> {
        public <S extends T> Property<S> testGetter_Property() { return null; }
        public Property<T> testField_Property;

        public <S extends T> Comparable<S> testGetter_NonProperty() { return null; }
        public Comparable<T> testField_NonProperty;
    }

    @Test
    public void RawTypeUsage_Hides_InterfaceBound_In_ClassSignature() {
        Resolver resolver = new Resolver(SourceInfo.none());
        Segment firstSegment = new ParentSegment(new TypeInstance(resolver.resolveClass(InterfaceBoundInClassSignature.class.getName())), null, -1);

        ResolvedPath getterPath = ResolvedPath.parse(firstSegment, new String[] {"testGetter_Property"}, true, SourceInfo.none());
        ResolvedPath fieldPath = ResolvedPath.parse(firstSegment, new String[] {"testField_Property"}, true, SourceInfo.none());
        assertEquals("PathTest$InterfaceBoundInClassSignature", getterPath.get(0).getTypeInstance().toString());
        assertEquals("Property", getterPath.get(1).getTypeInstance().toString());
        assertEquals("Object", getterPath.get(1).getValueTypeInstance().toString());
        assertEquals("PathTest$InterfaceBoundInClassSignature", fieldPath.get(0).getTypeInstance().toString());
        assertEquals("Property", fieldPath.get(1).getTypeInstance().toString());
        assertEquals("Object", fieldPath.get(1).getValueTypeInstance().toString());

        getterPath = ResolvedPath.parse(firstSegment, new String[] {"testGetter_NonProperty"}, true, SourceInfo.none());
        fieldPath = ResolvedPath.parse(firstSegment, new String[] {"testField_NonProperty"}, true, SourceInfo.none());
        assertEquals("PathTest$InterfaceBoundInClassSignature", getterPath.get(0).getTypeInstance().toString());
        assertEquals("Comparable", getterPath.get(1).getTypeInstance().toString());
        assertEquals("PathTest$InterfaceBoundInClassSignature", fieldPath.get(0).getTypeInstance().toString());
        assertEquals("Comparable", fieldPath.get(1).getTypeInstance().toString());
    }

    public static class ClassBoundInMethodSignature {
        public <S extends String> Property<S> testGetter_Property() { return null; }
        public <S extends String> Comparable<S> testGetter_NonProperty() { return null; }
    }

    @Test
    public void ClassBound_Is_Identified_In_MethodSignature() {
        Resolver resolver = new Resolver(SourceInfo.none());
        Segment firstSegment = new ParentSegment(new TypeInstance(resolver.resolveClass(ClassBoundInMethodSignature.class.getName())), null, -1);

        ResolvedPath path = ResolvedPath.parse(firstSegment, new String[] {"testGetter_Property"}, true, SourceInfo.none());
        assertEquals("PathTest$ClassBoundInMethodSignature", path.get(0).getTypeInstance().toString());
        assertEquals("Property<String>", path.get(1).getTypeInstance().toString());
        assertEquals("String", path.get(1).getValueTypeInstance().toString());

        path = ResolvedPath.parse(firstSegment, new String[] {"testGetter_NonProperty"}, true, SourceInfo.none());
        assertEquals("PathTest$ClassBoundInMethodSignature", path.get(0).getTypeInstance().toString());
        assertEquals("Comparable<String>", path.get(1).getTypeInstance().toString());
    }

    public static class InterfaceBoundInMethodSignature {
        public <S extends AutoCloseable> Property<S> testGetter_Property() { return null; }
        public <S extends AutoCloseable> Comparable<S> testGetter_NonProperty() { return null; }
    }

    @Test
    public void InterfaceBound_Is_Identified_In_MethodSignature() {
        Resolver resolver = new Resolver(SourceInfo.none());
        Segment firstSegment = new ParentSegment(new TypeInstance(resolver.resolveClass(InterfaceBoundInMethodSignature.class.getName())), null, -1);

        ResolvedPath path = ResolvedPath.parse(firstSegment, new String[] {"testGetter_Property"}, true, SourceInfo.none());
        assertEquals("PathTest$InterfaceBoundInMethodSignature", path.get(0).getTypeInstance().toString());
        assertEquals("Property<AutoCloseable>", path.get(1).getTypeInstance().toString());
        assertEquals("AutoCloseable", path.get(1).getValueTypeInstance().toString());

        path = ResolvedPath.parse(firstSegment, new String[] {"testGetter_NonProperty"}, true, SourceInfo.none());
        assertEquals("PathTest$InterfaceBoundInMethodSignature", path.get(0).getTypeInstance().toString());
        assertEquals("Comparable<AutoCloseable>", path.get(1).getTypeInstance().toString());
    }

    public static class Type1<A> {}
    public static class Type2<B> extends Type1<B> {}
    public static class TypeInvocationTest1 {
        public Property<Type2<String>> testProp_Property() { return null; }
        public Type2<String> testProp_NonProperty() { return null; }
    }

    @Test
    public void Inherited_Generic_Types_Are_Invoked_With_TypeArgument() {
        Resolver resolver = new Resolver(SourceInfo.none());
        Segment firstSegment = new ParentSegment(new TypeInstance(resolver.resolveClass(TypeInvocationTest1.class.getName())), null, -1);

        ResolvedPath path = ResolvedPath.parse(firstSegment, new String[] {"testProp_Property"}, true, SourceInfo.none());
        assertEquals("PathTest$TypeInvocationTest1", path.get(0).getTypeInstance().toString());
        assertEquals("PathTest$Type2<String>", path.get(1).getValueTypeInstance().toString());
        assertEquals("PathTest$Type1<String>", path.get(1).getValueTypeInstance().getSuperTypes().get(0).toString());

        path = ResolvedPath.parse(firstSegment, new String[] {"testProp_NonProperty"}, true, SourceInfo.none());
        assertEquals("PathTest$TypeInvocationTest1", path.get(0).getTypeInstance().toString());
        assertEquals("PathTest$Type2<String>", path.get(1).getTypeInstance().toString());
        assertEquals("PathTest$Type1<String>", path.get(1).getTypeInstance().getSuperTypes().get(0).toString());
    }

    public static class DerivedPropertyTypeTestClass {
        public StringProperty testProp_Getter() { return null; }
        public StringProperty testProp_Field;
    }

    @Test
    public void Type_Contained_In_Derived_Property_Is_Identified_Correctly() {
        Resolver resolver = new Resolver(SourceInfo.none());
        Segment firstSegment = new ParentSegment(new TypeInstance(resolver.resolveClass(DerivedPropertyTypeTestClass.class.getName())), null, -1);

        ResolvedPath path = ResolvedPath.parse(firstSegment, new String[] {"testProp_Getter"}, true, SourceInfo.none());
        assertEquals("PathTest$DerivedPropertyTypeTestClass", path.get(0).getTypeInstance().toString());
        assertEquals("StringProperty", path.get(1).getTypeInstance().toString());
        assertEquals("String", path.get(1).getValueTypeInstance().toString());

        path = ResolvedPath.parse(firstSegment, new String[] {"testProp_Field"}, true, SourceInfo.none());
        assertEquals("PathTest$DerivedPropertyTypeTestClass", path.get(0).getTypeInstance().toString());
        assertEquals("StringProperty", path.get(1).getTypeInstance().toString());
        assertEquals("String", path.get(1).getValueTypeInstance().toString());
    }

    public static class RecurringType<T extends Enum<T>> implements Comparable<RecurringType<T>> {
        @Override
        public int compareTo(RecurringType<T> o) {
            return 0;
        }
    }

    public enum TestEnum {}

    public static class RecurringTestClass {
        RecurringType<TestEnum> testProp;
    }

    @Test
    public void Recurring_Generic_Type_Is_Resolved() {
        Resolver resolver = new Resolver(SourceInfo.none());
        Segment firstSegment = new ParentSegment(new TypeInstance(resolver.resolveClass(RecurringTestClass.class.getName())), null, -1);
        ResolvedPath path = ResolvedPath.parse(firstSegment, new String[] {"testProp"}, true, SourceInfo.none());

        assertEquals("PathTest$RecurringTestClass", path.get(0).getTypeInstance().toString());
        assertEquals("PathTest$RecurringType<PathTest$TestEnum>", path.get(1).getTypeInstance().toString());
        assertEquals("Object", path.get(1).getTypeInstance().getSuperTypes().get(0).toString());
        assertEquals("Comparable<PathTest$RecurringType<PathTest$TestEnum>>", path.get(1).getTypeInstance().getSuperTypes().get(1).toString());
    }

    public static class GenericListTestClass {
        public ListProperty<String> target;
        public ObservableList<String> source1;
        public ObservableList<Integer> source2;
    }

    @Test
    public void Generic_Lists_With_Equal_Arguments_Are_Compatible() {
        Resolver resolver = new Resolver(SourceInfo.none());
        Segment firstSegment = new ParentSegment(new TypeInstance(resolver.resolveClass(GenericListTestClass.class.getName())), null, -1);
        ResolvedPath target = ResolvedPath.parse(firstSegment, new String[] {"target"}, true, SourceInfo.none());
        ResolvedPath source = ResolvedPath.parse(firstSegment, new String[] {"source1"}, true, SourceInfo.none());

        assertTrue(target.getValueTypeInstance().isConvertibleFrom(source.getValueTypeInstance()));
    }

    @Test
    public void Generic_Lists_With_Unequal_Arguments_Are_Incompatible() {
        Resolver resolver = new Resolver(SourceInfo.none());
        Segment firstSegment = new ParentSegment(new TypeInstance(resolver.resolveClass(GenericListTestClass.class.getName())), null, -1);
        ResolvedPath target = ResolvedPath.parse(firstSegment, new String[] {"target"}, true, SourceInfo.none());
        ResolvedPath source = ResolvedPath.parse(firstSegment, new String[] {"source2"}, true, SourceInfo.none());

        assertFalse(target.getValueTypeInstance().isConvertibleFrom(source.getValueTypeInstance()));
    }

    public interface OverrideMethodBase {
        Object value();
    }

    public static class OverrideMethodDerived implements OverrideMethodBase {
        @Override
        public String value() { return null; }
    }

    @Test
    public void Resolver_Detects_Narrowed_Return_Type() {
        Resolver resolver = new Resolver(SourceInfo.none());
        Segment firstSegment = new ParentSegment(new TypeInstance(resolver.resolveClass(OverrideMethodDerived.class.getName())), null, -1);
        ResolvedPath target = ResolvedPath.parse(firstSegment, new String[] {"value"}, true, SourceInfo.none());

        assertEquals("java.lang.String", target.getTypeInstance().jvmType().getName());
    }

    public static class Type9<T> {
        public Comparable<T> getValue() { return null; }
        public void setValue(Comparable<T> value) {}
    }

    public static class Type10<T> extends Type9<T> {}

    @Test
    public void Property_Of_RawType_Contains_No_TypeArguments() {
        Resolver resolver = new Resolver(SourceInfo.none());
        Segment firstSegment = new ParentSegment(resolver.getTypeInstance(resolver.resolveClass(Type9.class.getName())), null, -1);
        ResolvedPath target = ResolvedPath.parse(firstSegment, new String[] {"value"}, true, SourceInfo.none());
        assertEquals("PathTest$Type9", target.get(0).getTypeInstance().toString());
        assertEquals("Comparable", target.get(1).getTypeInstance().toString());
    }

    @Test
    public void Property_Of_RawType_Derived_Contains_No_TypeArguments() {
        Resolver resolver = new Resolver(SourceInfo.none());
        Segment firstSegment = new ParentSegment(resolver.getTypeInstance(resolver.resolveClass(Type10.class.getName())), null, -1);
        ResolvedPath target = ResolvedPath.parse(firstSegment, new String[] {"value"}, true, SourceInfo.none());
        assertEquals("PathTest$Type10", target.get(0).getTypeInstance().toString());
        assertEquals("Comparable", target.get(1).getTypeInstance().toString());
    }

}

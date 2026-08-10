// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"HttpUrlsUsage", "ClassCanBeRecord", "InnerClassMayBeStatic", "unused"})
@ExtendWith(TestExtension.class)
public class MemberClassConstructionTest extends CompilerTestBase {

    public static class TestPane extends Pane {
        static int argumentCalls;
        static int innerConstructions;
        static int identityCalls;

        public final StringBuilder order = new StringBuilder();
        public final Outer<String> outer = new Outer<>(this, "outer");
        public final DerivedOuter derivedOuter = new DerivedOuter(this, "derived");
        public Outer<String> nullOuter;
        public final ObjectProperty<Outer<String>> observableOuter = new SimpleObjectProperty<>(new Outer<>(this, "first"));
        public final ObjectProperty<MemberBox> memberBox = new SimpleObjectProperty<>(new MemberBox(5));
        private final ObjectProperty<Object> result = new SimpleObjectProperty<>();

        public ObjectProperty<Object> resultProperty() {
            return result;
        }

        public Outer<String> orderedOuter() {
            order.append('q');
            return outer;
        }

        public Outer<String> observableIdentity(Outer<String> value) {
            identityCalls++;
            return value;
        }

        public Object Collision(String value) {
            return "method:" + value;
        }

        public Object ReceiverCollision(String value) {
            return "method:" + value;
        }

        public Object ConstructorPreferred(Object value) {
            return "method:" + value;
        }

        public Object MethodPreferred(String value) {
            return "method:" + value;
        }

        public Object MethodSetWins(String value) {
            return "string:" + value;
        }

        public Object MethodSetWins(Integer value) {
            return "integer:" + value;
        }

        Object AccessibleConstructor(String value) {
            return "method:" + value;
        }

        public Object InaccessibleConstructor(String value) {
            return "method:" + value;
        }

        public String pick(Object value) {
            return "object:" + value;
        }

        String pick(String value) {
            return "string:" + value;
        }

        public String orderedArgument() {
            argumentCalls++;
            order.append('a');
            return "argument";
        }

        public int orderedIntegerArgument() {
            argumentCalls++;
            order.append('a');
            return 7;
        }

        public double memberBoxToDouble(MemberBox value) {
            return value.value;
        }

        public class MemberBox {
            public final TestPane owner = TestPane.this;
            public final double value;
            public MemberBox(double value) { this.value = value; }
        }

        public class ReceiverCollision {
            public final String value;
            public ReceiverCollision(String value) { this.value = value; }
        }
    }

    public static class GenericBox<T> {
        public final T value;
        public GenericBox(T value) { this.value = value; }
    }

    public static class NumberBox<T extends Number> {
        public final T value;
        public NumberBox(T value) { this.value = value; }
    }

    public interface InterfaceBox {}

    public abstract static class AbstractBox {
        public AbstractBox() {}
    }

    private static class InaccessibleBox {
        public InaccessibleBox() {}
    }

    public static class GenericConstructor<T> {
        public final T value;
        public final Number witness;

        public <W extends Number> GenericConstructor(T value, W witness) {
            this.value = value;
            this.witness = witness;
        }
    }

    public static class VarargsBox {
        public final String[] values;
        public VarargsBox(String... values) { this.values = values; }
    }

    public static class Collision {
        public final String value;
        public Collision(String value) { this.value = value; }
    }

    public static class ConstructorPreferred {
        public final String value;
        public ConstructorPreferred(String value) { this.value = value; }
    }

    public static class MethodPreferred {
        public final Object value;
        public MethodPreferred(Object value) { this.value = value; }
    }

    public static class MethodSetWins {
        public final Object value;
        public MethodSetWins(Object value) { this.value = value; }
    }

    public static class AccessibleConstructor {
        public final String value;
        public AccessibleConstructor(String value) { this.value = value; }
    }

    public static class InaccessibleConstructor {
        public final String value;
        InaccessibleConstructor(String value) { this.value = value; }
    }

    public static class SameCategoryConstructor {
        public final String selected;
        public SameCategoryConstructor(Object value) { selected = "object:" + value; }
        SameCategoryConstructor(String value) { selected = "string:" + value; }
    }

    public static class Outer<X> {
        public final TestPane host;
        public final X outerValue;
        public final String fieldOnly = "field";
        public final String genericValue = "field";

        public Outer(TestPane host, X outerValue) {
            this.host = host;
            this.outerValue = outerValue;
        }

        public String getPropertyOnly() {
            return "property:" + outerValue;
        }

        public String exactOnly() {
            return "exact:" + outerValue;
        }

        @SuppressWarnings("unchecked")
        public <T> T getGenericValue() {
            return (T)("generic:" + outerValue);
        }

        public class PlainInner {
            public final Outer<X> owner = Outer.this;
            public final String value;

            public PlainInner(String value) {
                this.value = value;
                host.order.append('c');
            }
        }

        public class Inner<T> {
            public final Outer<X> owner = Outer.this;
            public final X inheritedValue = outerValue;
            public final T value;

            public Inner(T value) {
                this.value = value;
                TestPane.innerConstructions++;
            }

            public class Deep<U> {
                public final Inner<T> owner = Inner.this;
                public final U value;
                public Deep(U value) { this.value = value; }
            }
        }

        public class WitnessInner<T> {
            public final Outer<X> owner = Outer.this;
            public final T value;
            public final Number witness;

            public <W extends Number> WitnessInner(T value, W witness) {
                this.value = value;
                this.witness = witness;
            }
        }

        public class OwnerValueInner {
            public final X value;

            public OwnerValueInner(X value) {
                this.value = value;
            }
        }

        public class VarargsInner {
            public final String[] values;
            public VarargsInner(String... values) { this.values = values; }
        }

        public static class StaticNested<T> {
            public final T value;
            public StaticNested(T value) { this.value = value; }
        }
    }

    public static class DerivedOuter extends Outer<String> {
        public DerivedOuter(TestPane host, String outerValue) {
            super(host, outerValue);
        }
    }

    public static class OtherOuter {
        public class OtherInner {
            public OtherInner() {}
        }
    }

    @BeforeEach
    public void resetCounters() {
        TestPane.argumentCalls = 0;
        TestPane.innerConstructions = 0;
        TestPane.identityCalls = 0;
    }

    @Test
    public void Leading_Construction_Separates_Class_Arguments_And_Constructor_Witnesses() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$GenericConstructor<String, Integer>('foo', 7)"/>
        """);

        GenericConstructor<?> result = assertInstanceOf(GenericConstructor.class, root.resultProperty().get());
        assertEquals("foo", result.value);
        assertEquals(7, result.witness);
    }

    @Test
    public void Xml_Entity_Decoded_Generic_Construction_Compiles_Semantically() {
        TestPane leading = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$GenericConstructor&lt;String, Integer>('foo', 7)"/>
        """, "Leading", null);

        GenericConstructor<?> leadingResult = assertInstanceOf(GenericConstructor.class, leading.resultProperty().get());
        assertEquals("foo", leadingResult.value);
        assertEquals(7, leadingResult.witness);

        TestPane qualified = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$outer.WitnessInner&lt;Integer, Long>(7, 8L)"/>
        """, "Qualified", null);

        Outer<?>.WitnessInner<?> qualifiedResult = assertInstanceOf(Outer.WitnessInner.class, qualified.resultProperty().get());
        assertSame(qualified.outer, qualifiedResult.owner);
        assertEquals(7, qualifiedResult.value);
        assertEquals(8L, qualifiedResult.witness);
    }

    @Test
    public void Leading_Construction_Accepts_Static_Nested_And_Rejects_Member_Class() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$Outer.StaticNested<String>('foo')"/>
        """);

        Outer.StaticNested<?> result = assertInstanceOf(Outer.StaticNested.class, root.resultProperty().get());
        assertEquals("foo", result.value);

        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$Outer.Inner<String>('foo')"/>
        """));
        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void Qualified_Construction_Preserves_The_Parameterized_Owner() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$outer.Inner<Integer>(7)"/>
        """);

        Outer<?>.Inner<?> result = assertInstanceOf(Outer.Inner.class, root.resultProperty().get());
        assertSame(root.outer, result.owner);
        assertEquals("outer", result.inheritedValue);
        assertEquals(7, result.value);
    }

    @Test
    public void Qualified_Construction_Substitutes_Owner_Type_Arguments_In_Constructor_Parameters() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$outer.OwnerValueInner('value')"/>
        """);

        Outer<?>.OwnerValueInner result = assertInstanceOf(Outer.OwnerValueInner.class, root.resultProperty().get());
        assertEquals("value", result.value);

        MarkupException exception = assertConstructionFails("outer.OwnerValueInner(7)", "OwnerParameterMismatch");
        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, exception.getDiagnostic().getCode());
    }

    @Test
    public void Qualified_Construction_Resolves_An_Inherited_Member_Class() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$derivedOuter.PlainInner('foo')"/>
        """);

        Outer<?>.PlainInner result = assertInstanceOf(Outer.PlainInner.class, root.resultProperty().get());
        assertSame(root.derivedOuter, result.owner);
        assertEquals("foo", result.value);
    }

    @Test
    public void Qualified_Construction_Rejects_A_Static_Nested_Class() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$outer.StaticNested<String>('foo')"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void Qualified_Construction_Separates_Class_Arguments_And_Constructor_Witnesses() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$outer.WitnessInner<Integer, Long>(7, 8L)"/>
        """);

        Outer<?>.WitnessInner<?> result = assertInstanceOf(Outer.WitnessInner.class, root.resultProperty().get());
        assertSame(root.outer, result.owner);
        assertEquals(7, result.value);
        assertEquals(8L, result.witness);
    }

    @Test
    public void Constructed_Class_Arguments_Are_Validated_At_The_Class_Position() {
        MarkupException arity = assertConstructionFails("GenericBox<String, Integer>('foo')", "ClassArity");
        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, arity.getDiagnostic().getCode());

        MarkupException bounds = assertConstructionFails("NumberBox<String>('foo')", "ClassBounds");
        assertEquals(ErrorCode.TYPE_ARGUMENT_OUT_OF_BOUND, bounds.getDiagnostic().getCode());
        assertCodeHighlight("NumberBox", bounds);

        MarkupException primitive = assertConstructionFails("GenericBox<int>(1)", "ClassPrimitive");
        assertEquals(ErrorCode.TYPE_ARGUMENT_NOT_REFERENCE, primitive.getDiagnostic().getCode());
        assertCodeHighlight("GenericBox", primitive);
    }

    @Test
    public void Constructor_Witnesses_Are_Validated_At_The_Invocation_Position() {
        String aritySource = "GenericConstructor<String, Integer, Long>('foo', 7)";
        MarkupException arity = assertConstructionFails(aritySource, "WitnessArity");
        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, arity.getDiagnostic().getCode());
        assertEquals(ErrorCode.NUM_TYPE_ARGUMENTS_MISMATCH, arity.getDiagnostic().getCauses()[0].getCode());
        assertCodeHighlight(aritySource, arity);

        String boundsSource = "GenericConstructor<String, String>('foo', 'bar')";
        MarkupException bounds = assertConstructionFails(boundsSource, "WitnessBounds");
        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, bounds.getDiagnostic().getCode());
        assertEquals(ErrorCode.TYPE_ARGUMENT_OUT_OF_BOUND, bounds.getDiagnostic().getCauses()[0].getCode());
        assertCodeHighlight(boundsSource, bounds);

        String primitiveSource = "GenericConstructor<String, int>('foo', 7)";
        MarkupException primitive = assertConstructionFails(primitiveSource, "WitnessPrimitive");
        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, primitive.getDiagnostic().getCode());
        assertEquals(ErrorCode.TYPE_ARGUMENT_NOT_REFERENCE, primitive.getDiagnostic().getCauses()[0].getCode());
        assertCodeHighlight(primitiveSource, primitive);

        String omittedSource = "GenericConstructor<String>('foo', 7)";
        MarkupException omitted = assertConstructionFails(omittedSource, "WitnessOmitted");
        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, omitted.getDiagnostic().getCode());
        assertEquals(ErrorCode.NUM_TYPE_ARGUMENTS_MISMATCH, omitted.getDiagnostic().getCauses()[0].getCode());
        assertTrue(omitted.getDiagnostic().getCauses()[0].getMessage().contains(
            "required 1 type argument(s), but 0 were provided"));
    }

    @Test
    public void Construction_Rejects_Nonconstructible_Target_Kinds() {
        for (String source : new String[] {"InterfaceBox()", "AbstractBox()"}) {
            MarkupException ex = assertConstructionFails(source, source.replaceAll("\\W", ""));
            assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, ex.getDiagnostic().getCode());
        }

        MarkupException primitive = assertConstructionFails("int()", "PrimitiveTarget");
        assertEquals(ErrorCode.UNEXPECTED_TOKEN, primitive.getDiagnostic().getCode());
        assertCodeHighlight("int", primitive);

        MarkupException inaccessible = assertConstructionFails("InaccessibleBox()", "Inaccessible");
        assertEquals(ErrorCode.CLASS_NOT_ACCESSIBLE, inaccessible.getDiagnostic().getCode());
        assertCodeHighlight("InaccessibleBox", inaccessible);
    }

    @Test
    public void Qualified_Construction_Requires_A_Matching_Enclosing_Type() {
        MarkupException ex = assertConstructionFails("outer.OtherInner()", "IncompatibleOwner");

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("outer.OtherInner", ex);
    }

    @Test
    public void Context_Element_And_Grouped_Qualifiers_Reach_Construction_Resolution() {
        TestPane contextQualifier = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$:context.MemberBox(6)"/>
        """, "ContextQualifier", null);

        TestPane.MemberBox contextMember = assertInstanceOf(
            TestPane.MemberBox.class, contextQualifier.resultProperty().get());
        assertSame(contextQualifier, contextMember.owner);
        assertEquals(6, contextMember.value);

        TestPane directElementQualifier = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$:element.MemberBox(7)"/>
        """, "DirectElementQualifier", null);

        TestPane.MemberBox elementMember = assertInstanceOf(
            TestPane.MemberBox.class, directElementQualifier.resultProperty().get());
        assertSame(directElementQualifier, elementMember.owner);

        TestPane rootQualifier = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <TestPane result="$:root.MemberBox(8)"/>
            </TestPane>
        """, "RootQualifier", null);

        TestPane rootChild = (TestPane)rootQualifier.getChildren().get(0);
        TestPane.MemberBox rootMember = assertInstanceOf(
            TestPane.MemberBox.class, rootChild.resultProperty().get());
        assertSame(rootQualifier, rootMember.owner);

        TestPane parentQualifier = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <TestPane result="$:parent.MemberBox(9)"/>
            </TestPane>
        """, "ParentQualifier", null);

        TestPane parentChild = (TestPane)parentQualifier.getChildren().get(0);
        TestPane.MemberBox parentMember = assertInstanceOf(
            TestPane.MemberBox.class, parentChild.resultProperty().get());
        assertSame(parentQualifier, parentMember.owner);

        TestPane elementQualifier = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$:element.outer.PlainInner('element')"/>
        """, "ElementQualifier", null);

        Outer<?>.PlainInner elementResult = assertInstanceOf(Outer.PlainInner.class, elementQualifier.resultProperty().get());
        assertSame(elementQualifier.outer, elementResult.owner);

        TestPane groupedQualifier = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$(outer).PlainInner('grouped')"/>
        """, "GroupedQualifier", null);

        Outer<?>.PlainInner groupedResult = assertInstanceOf(Outer.PlainInner.class, groupedQualifier.resultProperty().get());
        assertSame(groupedQualifier.outer, groupedResult.owner);
    }

    @Test
    public void Generic_Construction_Without_Class_Arguments_Uses_Raw_Type_Behavior() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$outer.Inner(7)"/>
        """);

        Outer<?>.Inner<?> result = assertInstanceOf(Outer.Inner.class, root.resultProperty().get());
        assertSame(root.outer, result.owner);
        assertEquals(7, result.value);
    }

    @Test
    public void Leading_Construction_Emits_Empty_Varargs() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$VarargsBox()"/>
        """);

        VarargsBox box = assertInstanceOf(VarargsBox.class, root.resultProperty().get());
        assertArrayEquals(new String[0], box.values);
    }

    @Test
    public void Qualified_Construction_Emits_Explicit_Varargs_Without_The_Hidden_Outer_Parameter() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$outer.VarargsInner('first', 'second')"/>
        """);

        Outer<?>.VarargsInner inner = assertInstanceOf(Outer.VarargsInner.class, root.resultProperty().get());
        assertArrayEquals(new String[] {"first", "second"}, inner.values);
    }

    @Test
    public void Qualified_Construction_Normalizes_A_NonGeneric_Constructor_Descriptor() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$outer.PlainInner('foo')"/>
        """);

        Outer<?>.PlainInner result = assertInstanceOf(Outer.PlainInner.class, root.resultProperty().get());
        assertSame(root.outer, result.owner);
        assertEquals("foo", result.value);
    }

    @Test
    public void Qualified_Construction_Can_Repeat() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$outer.Inner<Integer>(7).Deep<String>('foo')"/>
        """);

        Outer<?>.Inner<?>.Deep<?> result = assertInstanceOf(Outer.Inner.Deep.class, root.resultProperty().get());
        assertSame(root.outer, result.owner.owner);
        assertEquals(7, result.owner.value);
        assertEquals("foo", result.value);
    }

    @Test
    public void Qualified_Construction_Evaluates_Qualifier_Before_Arguments() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$orderedOuter().PlainInner(orderedArgument())"/>
        """);

        assertEquals("qac", root.order.toString());
        assertEquals(1, TestPane.argumentCalls);
    }

    @Test
    public void Computed_Receiver_Supports_Property_Selection_And_Exact_Invocation() {
        TestPane property = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$orderedOuter().propertyOnly"/>
        """, "ComputedProperty", null);

        assertEquals("property:outer", property.resultProperty().get());
        assertEquals("q", property.order.toString());

        TestPane invocation = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$orderedOuter().exactOnly()"/>
        """, "ComputedInvocation", null);

        assertEquals("exact:outer", invocation.resultProperty().get());
        assertEquals("q", invocation.order.toString());

        MarkupException ex = assertConstructionFails(
            "orderedOuter().propertyOnly()", "ComputedExactName");
        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("propertyOnly", ex);
    }

    @Test
    public void Computed_Receiver_Supports_Witnessed_Property_Selection() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$orderedOuter().genericValue<String>"/>
        """);

        assertEquals("generic:outer", root.resultProperty().get());
        assertEquals("q", root.order.toString());
    }

    @Test
    public void Witnessed_Property_Selection_Rejects_A_Field_That_Cannot_Consume_The_List() {
        MarkupException ex = assertConstructionFails(
            "orderedOuter().fieldOnly<String>", "WitnessedField");

        assertEquals(ErrorCode.NUM_TYPE_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCode());
    }

    @Test
    public void Mixed_Selected_Member_Chain_Preserves_Receiver_Order() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$orderedOuter().Inner<Integer>(7).owner.outerValue"/>
        """);

        assertEquals("outer", root.resultProperty().get());
        assertEquals("q", root.order.toString());
    }

    @Test
    public void Observable_Computed_Receiver_Reselects_From_Current_Value() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="${observableIdentity(observableOuter).outerValue}"/>
        """);

        assertEquals("first", root.resultProperty().get());
        int callsAfterFirstRead = TestPane.identityCalls;
        assertTrue(callsAfterFirstRead > 0);

        root.observableOuter.set(new Outer<>(root, "second"));
        assertEquals(callsAfterFirstRead, TestPane.identityCalls);
        assertEquals("second", root.resultProperty().get());
        assertEquals(callsAfterFirstRead + 1, TestPane.identityCalls);
    }

    @Test
    public void Grouped_Null_Receiver_Short_Circuits_Selected_Member() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$(nullOuter).outerValue"/>
        """);

        assertNull(root.resultProperty().get());
    }

    @Test
    public void Null_Qualifier_Suppresses_Explicit_Argument_Evaluation() {
        assertThrows(NullPointerException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$nullOuter.PlainInner(orderedArgument())"/>
        """));

        assertEquals(0, TestPane.argumentCalls);
    }

    @Test
    public void Observable_Qualifier_Reconstructs_With_The_Current_Enclosing_Instance() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="${observableOuter.Inner<Integer>(7)}"/>
        """);

        Outer<?>.Inner<?> first = assertInstanceOf(Outer.Inner.class, root.resultProperty().get());
        assertSame(root.observableOuter.get(), first.owner);
        int constructionsAfterFirstRead = TestPane.innerConstructions;

        assertSame(first, root.resultProperty().get());
        assertEquals(constructionsAfterFirstRead, TestPane.innerConstructions);

        Outer<String> secondOwner = new Outer<>(root, "second");
        root.observableOuter.set(secondOwner);
        assertEquals(constructionsAfterFirstRead, TestPane.innerConstructions);

        Outer<?>.Inner<?> second = assertInstanceOf(Outer.Inner.class, root.resultProperty().get());
        assertSame(secondOwner, second.owner);
        assertNotSame(first, second);
        assertEquals(constructionsAfterFirstRead + 1, TestPane.innerConstructions);

        assertSame(second, root.resultProperty().get());
        assertEquals(constructionsAfterFirstRead + 1, TestPane.innerConstructions);
    }

    @Test
    public void Observable_FxContext_Reconstructs_Direct_Member_Class_From_Current_Context() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      fx:context="${observableOuter}"
                      result="${:context.Inner<Integer>(host.orderedIntegerArgument())}"/>
        """, "ObservableFxContextQualifier", null);

        Outer<?>.Inner<?> first = assertInstanceOf(Outer.Inner.class, root.resultProperty().get());
        assertSame(root.observableOuter.get(), first.owner);
        assertEquals(1, TestPane.argumentCalls);

        Outer<String> secondOwner = new Outer<>(root, "second");
        root.observableOuter.set(secondOwner);
        Outer<?>.Inner<?> second = assertInstanceOf(Outer.Inner.class, root.resultProperty().get());
        assertSame(secondOwner, second.owner);
        assertEquals(2, TestPane.argumentCalls);

        root.observableOuter.set(null);
        assertNull(root.resultProperty().get());
        assertEquals(2, TestPane.argumentCalls);
    }

    @Test
    public void Neutral_Invocation_Syntax_Resolves_Construction() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$GenericBox<String>('foo')"/>
        """);

        GenericBox<?> result = assertInstanceOf(GenericBox.class, root.resultProperty().get());
        assertEquals("foo", result.value);

        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$:context.GenericBox<String>('foo')"/>
        """));
        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void Method_And_Constructor_Candidates_Are_Selected_Together() {
        MarkupException ambiguous = assertConstructionFails("Collision('value')", "AmbiguousCall");
        assertEquals(ErrorCode.AMBIGUOUS_METHOD_OR_CONSTRUCTOR_CALL, ambiguous.getDiagnostic().getCode());
        assertCodeHighlight("Collision", ambiguous);

        TestPane constructorPreferred = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$ConstructorPreferred('value')"/>
        """, "ConstructorPreferredCall", null);

        ConstructorPreferred constructed = assertInstanceOf(
            ConstructorPreferred.class, constructorPreferred.resultProperty().get());
        assertEquals("value", constructed.value);

        TestPane methodPreferred = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$MethodPreferred('value')"/>
        """, "MethodPreferredCall", null);

        assertEquals("method:value", methodPreferred.resultProperty().get());
    }

    @Test
    public void Inaccessible_Callables_Are_Excluded_From_Joint_Overload_Resolution() {
        TestPane constructorSelected = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$AccessibleConstructor('value')"/>
        """, "AccessibleConstructorCall", null);

        AccessibleConstructor constructed = assertInstanceOf(
            AccessibleConstructor.class, constructorSelected.resultProperty().get());
        assertEquals("value", constructed.value);

        TestPane methodSelected = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$InaccessibleConstructor('value')"/>
        """, "InaccessibleConstructorCall", null);

        assertEquals("method:value", methodSelected.resultProperty().get());
    }

    @Test
    public void Accessible_Method_Overload_Is_Preserved_During_Emission() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$pick('value')"/>
        """, "AccessibleMethodOverload", null);

        assertEquals("object:value", root.resultProperty().get());
    }

    @Test
    public void Accessible_Constructor_Overload_Is_Preserved_During_Emission() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$SameCategoryConstructor('value')"/>
        """, "AccessibleConstructorOverload", null);

        SameCategoryConstructor result = assertInstanceOf(
            SameCategoryConstructor.class, root.resultProperty().get());
        assertEquals("object:value", result.selected);
    }

    @Test
    public void Explicit_Context_And_Observable_Terminal_Exclude_Imported_Construction() {
        TestPane explicit = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$:context.Collision('value')"/>
        """, "ExplicitContextMethod", null);
        assertEquals("method:value", explicit.resultProperty().get());

        TestPane observableTerminal = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$::Collision('value')"/>
        """, "ObservableTerminalMethod", null);
        assertEquals("method:value", observableTerminal.resultProperty().get());

        MarkupException sameReceiverCollision = assertConstructionFails(
            ":context.ReceiverCollision('value')", "SameReceiverCollision");
        assertEquals(
            ErrorCode.AMBIGUOUS_METHOD_OR_CONSTRUCTOR_CALL,
            sameReceiverCollision.getDiagnostic().getCode());
        assertCodeHighlight("ReceiverCollision", sameReceiverCollision);
    }

    @Test
    public void Dominated_Constructor_Does_Not_Replace_An_Ambiguous_Method_Set() {
        MarkupException ex = assertConstructionFails("MethodSetWins(null)", "MethodSetWinsCall");

        assertEquals(ErrorCode.AMBIGUOUS_METHOD_CALL, ex.getDiagnostic().getCode());
        assertCodeHighlight("MethodSetWins", ex);
    }

    @Test
    public void Observable_Selection_Does_Not_Resolve_A_Constructor() {
        MarkupException ex = assertConstructionFails(
            "outer::PlainInner('value')", "ObservableConstruction");

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("outer::PlainInner", ex);
    }

    @Test
    public void Inverse_Constructor_Name_Rejects_A_Member_Class() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="#{memberBoxToDouble(memberBox); inverseMethod=TestPane.MemberBox}"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void Inverse_Callable_Name_Rejects_Invocation_Syntax() {
        assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="#{memberBoxToDouble(memberBox); inverseMethod=new TestPane.MemberBox}"/>
        """));

        assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="#{memberBoxToDouble(memberBox); inverseMethod=TestPane.MemberBox()}"/>
        """));
    }

    @Test
    public void Hidden_Enclosing_Parameter_Is_Not_Counted_In_Source_Arity() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$outer.PlainInner()"/>
        """));

        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, ex.getDiagnostic().getCode());
        assertEquals(1, ex.getDiagnostic().getCauses().length);
        assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[0].getCode());
        assertTrue(ex.getDiagnostic().getCauses()[0].getMessage().contains("required 1 argument(s)"));
    }

    @Test
    public void Generic_Signature_Also_Excludes_The_Hidden_Enclosing_Parameter() {
        MarkupException ex = assertConstructionFails(
            "outer.WitnessInner<Integer, Long>()", "GenericHiddenArity");

        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, ex.getDiagnostic().getCode());
        assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH,
            ex.getDiagnostic().getCauses()[0].getCode());
        assertTrue(ex.getDiagnostic().getCauses()[0].getMessage().contains("required 2 argument(s)"));
    }

    private MarkupException assertConstructionFails(String expression, String suffix) {
        return assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$%s"/>
        """.formatted(expression), suffix, null));
    }
}

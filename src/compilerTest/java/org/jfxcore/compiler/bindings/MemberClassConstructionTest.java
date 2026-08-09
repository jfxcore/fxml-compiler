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

@SuppressWarnings({"HttpUrlsUsage", "unused"})
@ExtendWith(TestExtension.class)
public class MemberClassConstructionTest extends CompilerTestBase {

    public static class TestPane extends Pane {
        static int argumentCalls;
        static int innerConstructions;

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

        public String orderedArgument() {
            argumentCalls++;
            order.append('a');
            return "argument";
        }

        public double memberBoxToDouble(MemberBox value) {
            return value.value;
        }

        public class MemberBox {
            public final double value;
            public MemberBox(double value) { this.value = value; }
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

    public static class Outer<X> {
        public final TestPane host;
        public final X outerValue;

        public Outer(TestPane host, X outerValue) {
            this.host = host;
            this.outerValue = outerValue;
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
    }

    @Test
    public void Leading_Construction_Separates_Class_Arguments_And_Constructor_Witnesses() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$new <Integer> GenericConstructor<String>('foo', 7)"/>
        """);

        GenericConstructor<?> result = assertInstanceOf(GenericConstructor.class, root.resultProperty().get());
        assertEquals("foo", result.value);
        assertEquals(7, result.witness);
    }

    @Test
    public void Xml_Entity_Decoded_Generic_Construction_Compiles_Semantically() {
        TestPane leading = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$new &lt;Integer> GenericConstructor&lt;String>('foo', 7)"/>
        """, "Leading", null);

        GenericConstructor<?> leadingResult = assertInstanceOf(GenericConstructor.class, leading.resultProperty().get());
        assertEquals("foo", leadingResult.value);
        assertEquals(7, leadingResult.witness);

        TestPane qualified = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$outer.new &lt;Long> WitnessInner&lt;Integer>(7, 8L)"/>
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
                      result="$new Outer.StaticNested<String>('foo')"/>
        """);

        Outer.StaticNested<?> result = assertInstanceOf(Outer.StaticNested.class, root.resultProperty().get());
        assertEquals("foo", result.value);

        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$new Outer.Inner<String>('foo')"/>
        """));
        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void Qualified_Construction_Preserves_The_Parameterized_Owner() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$outer.new Inner<Integer>(7)"/>
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
                      result="$outer.new OwnerValueInner('value')"/>
        """);

        Outer<?>.OwnerValueInner result = assertInstanceOf(Outer.OwnerValueInner.class, root.resultProperty().get());
        assertEquals("value", result.value);

        MarkupException exception = assertConstructionFails("outer.new OwnerValueInner(7)", "OwnerParameterMismatch");
        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, exception.getDiagnostic().getCode());
    }

    @Test
    public void Qualified_Construction_Resolves_An_Inherited_Member_Class() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$derivedOuter.new PlainInner('foo')"/>
        """);

        Outer<?>.PlainInner result = assertInstanceOf(Outer.PlainInner.class, root.resultProperty().get());
        assertSame(root.derivedOuter, result.owner);
        assertEquals("foo", result.value);
    }

    @Test
    public void Qualified_Construction_Rejects_A_Static_Nested_Class() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$outer.new StaticNested<String>('foo')"/>
        """));

        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void Qualified_Construction_Separates_Class_Arguments_And_Constructor_Witnesses() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$outer.new <Long> WitnessInner<Integer>(7, 8L)"/>
        """);

        Outer<?>.WitnessInner<?> result = assertInstanceOf(Outer.WitnessInner.class, root.resultProperty().get());
        assertSame(root.outer, result.owner);
        assertEquals(7, result.value);
        assertEquals(8L, result.witness);
    }

    @Test
    public void Constructed_Class_Arguments_Are_Validated_At_The_Class_Position() {
        MarkupException arity = assertConstructionFails("new GenericBox<String, Integer>('foo')", "ClassArity");
        assertEquals(ErrorCode.NUM_TYPE_ARGUMENTS_MISMATCH, arity.getDiagnostic().getCode());
        assertCodeHighlight("GenericBox", arity);

        MarkupException bounds = assertConstructionFails("new NumberBox<String>('foo')", "ClassBounds");
        assertEquals(ErrorCode.TYPE_ARGUMENT_OUT_OF_BOUND, bounds.getDiagnostic().getCode());
        assertCodeHighlight("NumberBox", bounds);

        MarkupException primitive = assertConstructionFails("new GenericBox<int>(1)", "ClassPrimitive");
        assertEquals(ErrorCode.TYPE_ARGUMENT_NOT_REFERENCE, primitive.getDiagnostic().getCode());
        assertCodeHighlight("GenericBox", primitive);
    }

    @Test
    public void Constructor_Witnesses_Are_Validated_At_The_Invocation_Position() {
        String aritySource = "new <Integer, Long> GenericConstructor<String>('foo', 7)";
        MarkupException arity = assertConstructionFails(aritySource, "WitnessArity");
        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, arity.getDiagnostic().getCode());
        assertEquals(ErrorCode.NUM_TYPE_ARGUMENTS_MISMATCH, arity.getDiagnostic().getCauses()[0].getCode());
        assertCodeHighlight(aritySource, arity);

        String boundsSource = "new <String> GenericConstructor<String>('foo', 'bar')";
        MarkupException bounds = assertConstructionFails(boundsSource, "WitnessBounds");
        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, bounds.getDiagnostic().getCode());
        assertEquals(ErrorCode.TYPE_ARGUMENT_OUT_OF_BOUND, bounds.getDiagnostic().getCauses()[0].getCode());
        assertCodeHighlight(boundsSource, bounds);

        String primitiveSource = "new <int> GenericConstructor<String>('foo', 7)";
        MarkupException primitive = assertConstructionFails(primitiveSource, "WitnessPrimitive");
        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, primitive.getDiagnostic().getCode());
        assertEquals(ErrorCode.TYPE_ARGUMENT_NOT_REFERENCE, primitive.getDiagnostic().getCauses()[0].getCode());
        assertCodeHighlight(primitiveSource, primitive);

        String omittedSource = "new GenericConstructor<String>('foo', 7)";
        MarkupException omitted = assertConstructionFails(omittedSource, "WitnessOmitted");
        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, omitted.getDiagnostic().getCode());
        assertEquals(ErrorCode.NUM_TYPE_ARGUMENTS_MISMATCH, omitted.getDiagnostic().getCauses()[0].getCode());
        assertTrue(omitted.getDiagnostic().getCauses()[0].getMessage().contains(
            "required 1 type argument(s), but 0 were provided"));
    }

    @Test
    public void Construction_Rejects_Nonconstructible_Target_Kinds() {
        for (String source : new String[] {"new InterfaceBox()", "new AbstractBox()"}) {
            MarkupException ex = assertConstructionFails(source, source.replaceAll("\\W", ""));
            assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, ex.getDiagnostic().getCode());
        }

        MarkupException primitive = assertConstructionFails("new int()", "PrimitiveTarget");
        assertEquals(ErrorCode.EXPECTED_IDENTIFIER, primitive.getDiagnostic().getCode());
        assertCodeHighlight("int", primitive);

        MarkupException inaccessible = assertConstructionFails("new InaccessibleBox()", "Inaccessible");
        assertEquals(ErrorCode.CLASS_NOT_ACCESSIBLE, inaccessible.getDiagnostic().getCode());
        assertCodeHighlight("InaccessibleBox", inaccessible);
    }

    @Test
    public void Qualified_Construction_Requires_A_Matching_Enclosing_Type() {
        MarkupException ex = assertConstructionFails("outer.new OtherInner()", "IncompatibleOwner");

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("OtherInner", ex);
    }

    @Test
    public void This_Context_And_Grouped_Qualifiers_Reach_Construction_Resolution() {
        TestPane thisQualifier = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$this.new MemberBox(6)"/>
        """, "ThisQualifier", null);

        assertEquals(6, assertInstanceOf(TestPane.MemberBox.class, thisQualifier.resultProperty().get()).value);

        TestPane contextQualifier = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$:self.outer.new PlainInner('context')"/>
        """, "ContextQualifier", null);

        Outer<?>.PlainInner contextResult = assertInstanceOf(Outer.PlainInner.class, contextQualifier.resultProperty().get());
        assertSame(contextQualifier.outer, contextResult.owner);

        TestPane groupedQualifier = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$(outer).new PlainInner('grouped')"/>
        """, "GroupedQualifier", null);

        Outer<?>.PlainInner groupedResult = assertInstanceOf(Outer.PlainInner.class, groupedQualifier.resultProperty().get());
        assertSame(groupedQualifier.outer, groupedResult.owner);
    }

    @Test
    public void Generic_Construction_Without_Class_Arguments_Uses_Raw_Type_Behavior() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$outer.new Inner(7)"/>
        """);

        Outer<?>.Inner<?> result = assertInstanceOf(Outer.Inner.class, root.resultProperty().get());
        assertSame(root.outer, result.owner);
        assertEquals(7, result.value);
    }

    @Test
    public void Leading_Construction_Emits_Empty_Varargs() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$new VarargsBox()"/>
        """);

        VarargsBox box = assertInstanceOf(VarargsBox.class, root.resultProperty().get());
        assertArrayEquals(new String[0], box.values);
    }

    @Test
    public void Qualified_Construction_Emits_Explicit_Varargs_Without_The_Hidden_Outer_Parameter() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$outer.new VarargsInner('first', 'second')"/>
        """);

        Outer<?>.VarargsInner inner = assertInstanceOf(Outer.VarargsInner.class, root.resultProperty().get());
        assertArrayEquals(new String[] {"first", "second"}, inner.values);
    }

    @Test
    public void Qualified_Construction_Normalizes_A_NonGeneric_Constructor_Descriptor() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$outer.new PlainInner('foo')"/>
        """);

        Outer<?>.PlainInner result = assertInstanceOf(Outer.PlainInner.class, root.resultProperty().get());
        assertSame(root.outer, result.owner);
        assertEquals("foo", result.value);
    }

    @Test
    public void Qualified_Construction_Can_Repeat() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$outer.new Inner<Integer>(7).new Deep<String>('foo')"/>
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
                      result="$orderedOuter().new PlainInner(orderedArgument())"/>
        """);

        assertEquals("qac", root.order.toString());
        assertEquals(1, TestPane.argumentCalls);
    }

    @Test
    public void Null_Qualifier_Suppresses_Explicit_Argument_Evaluation() {
        assertThrows(NullPointerException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$nullOuter.new PlainInner(orderedArgument())"/>
        """));

        assertEquals(0, TestPane.argumentCalls);
    }

    @Test
    public void Observable_Qualifier_Reconstructs_With_The_Current_Enclosing_Instance() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="${observableOuter.new Inner<Integer>(7)}"/>
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
    public void Ordinary_Function_Syntax_Does_Not_Fall_Back_To_Construction() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$GenericBox('foo')"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());

        ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      result="$this.<String>GenericBox('foo')"/>
        """));
        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
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
                      result="$outer.new PlainInner()"/>
        """));

        assertEquals(ErrorCode.CONSTRUCTOR_NOT_FOUND, ex.getDiagnostic().getCode());
        assertEquals(1, ex.getDiagnostic().getCauses().length);
        assertEquals(ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCauses()[0].getCode());
        assertTrue(ex.getDiagnostic().getCauses()[0].getMessage().contains("required 1 argument(s)"));
    }

    @Test
    public void Generic_Signature_Also_Excludes_The_Hidden_Enclosing_Parameter() {
        MarkupException ex = assertConstructionFails(
            "outer.new <Long> WitnessInner<Integer>()", "GenericHiddenArity");

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

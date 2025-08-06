// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javassist.ClassPool;
import javassist.bytecode.SignatureAttribute;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.Reflection;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class GenericsTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class GenericObject<T> extends Pane {
        private final ObjectProperty<T> prop = new SimpleObjectProperty<>();
        public ObjectProperty<T> propProperty() { return prop; }
        public T getProp() { return prop.get(); }
        public void setProp(T value) { prop.set(value); }

        private T prop2;
        public T getProp2() { return prop2; }
        public void setProp2(T value) { prop2 = value; }

        @SuppressWarnings("unchecked")
        public <S> S m1(T value) {
            return (S)(Double)Double.parseDouble((String)value);
        }

        @SuppressWarnings("TypeParameterHidesVisibleType")
        public <T> T m2(T value) {
            return value;
        }
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void GenericObject_Without_TypeArguments_Is_Instantiated() {
        GridPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GenericObject fx:id="obj" prop="foo" prop2="foo"/>
            </GridPane>
        """);

        assertEquals("foo", ((GenericObject)Reflection.getFieldValue(root, "obj")).getProp());
        assertEquals("foo", ((GenericObject)Reflection.getFieldValue(root, "obj")).getProp2());
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void GenericObject_With_TypeArguments_Is_Instantiated() {
        GridPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GenericObject fx:typeArguments="java.lang.String" fx:id="obj" prop="foo" prop2="foo"/>
            </GridPane>
        """);

        assertEquals("foo", ((GenericObject)Reflection.getFieldValue(root, "obj")).getProp());
        assertEquals("foo", ((GenericObject)Reflection.getFieldValue(root, "obj")).getProp2());
    }

    @Test
    public void GenericRootObject_With_TypeArguments_Is_Instantiated() throws Exception {
        GenericObject<String> root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GenericObject xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                           fx:typeArguments="java.lang.String" prop="foo" prop2="foo"/>
        """);

        var classPool = new ClassPool();
        classPool.appendSystemPath();
        classPool.appendClassPath(root.getClass().getProtectionDomain().getCodeSource().getLocation().toExternalForm());
        String signature = classPool.get(root.getClass().getName()).getGenericSignature();
        var classSignature = SignatureAttribute.toClassSignature(signature);
        var type = ((SignatureAttribute.ClassType)classSignature.getSuperClass().getTypeArguments()[0].getType()).getName();
        assertEquals("java.lang.String", type);
    }

    @Test
    public void GenericRootObject_Without_TypeArguments_Is_Instantiated_As_RawType() throws Exception {
        GenericObject<String> root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GenericObject xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                           prop="foo" prop2="foo"/>
        """);

        var classPool = new ClassPool();
        classPool.appendSystemPath();
        classPool.appendClassPath(root.getClass().getProtectionDomain().getCodeSource().getLocation().toExternalForm());
        assertNull(classPool.get(root.getClass().getName()).getGenericSignature());
    }

    @Test
    public void GenericObject_With_Upper_Wildcard_TypeArgument_Cannot_Be_Instantiated() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GenericObject fx:typeArguments="? extends String"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.WILDCARD_CANNOT_BE_INSTANTIATED, ex.getDiagnostic().getCode());
        assertCodeHighlight("? extends String", ex);
    }

    @Test
    public void GenericObject_With_Lower_Wildcard_TypeArgument_Cannot_Be_Instantiated() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GenericObject fx:typeArguments="? super String"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.WILDCARD_CANNOT_BE_INSTANTIATED, ex.getDiagnostic().getCode());
        assertCodeHighlight("? super String", ex);
    }

    @Test
    public void GenericObject_With_Uncoercible_PropertyValue_Throws_Exception() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GenericObject fx:typeArguments="java.lang.Integer" fx:id="obj" prop="foo"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo", ex);
    }

    @Test
    public void GenericObject_With_Uncoercible_SetterValue_Throws_Exception() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GenericObject fx:typeArguments="java.lang.Integer" fx:id="obj" prop2="foo"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo", ex);
    }

    @Test
    public void GenericObject_With_Invalid_TypeArguments_Throws_Exception() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GenericObject fx:typeArguments="foobar" prop="foo"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.CLASS_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("foobar", ex);
    }

    @Test
    public void GenericObject_With_Empty_TypeArguments_Throws_Exception() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GenericObject fx:typeArguments="" prop="foo"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.PROPERTY_CANNOT_BE_EMPTY, ex.getDiagnostic().getCode());
        assertCodeHighlight("""
            fx:typeArguments=""
        """.trim(), ex);
    }

    @Test
    public void GenericObject_With_Empty_TypeArguments_And_FxId_Throws_Exception() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GenericObject fx:typeArguments="" fx:id="obj" prop="foo"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.PROPERTY_CANNOT_BE_EMPTY, ex.getDiagnostic().getCode());
        assertCodeHighlight("""
            fx:typeArguments=""
        """.trim(), ex);
    }

    @Test
    public void GenericObject_With_Wrong_Number_Of_TypeArguments_Throws_Exception() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GenericObject fx:typeArguments="java.lang.String, java.lang.Integer" prop="foo"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.NUM_TYPE_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCode());
        assertCodeHighlight("GenericObject".trim(), ex);
    }

    @Test
    public void Incompatible_Class_TypeParameter_Is_Rejected() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <GenericObject xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                           fx:typeArguments="String" minWidth="$<Double>m1(123.5)"/>
        """));

        assertEquals(ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, ex.getDiagnostic().getCode());
        assertTrue(ex.getDiagnostic().getMessage().contains("double"));
        assertTrue(ex.getDiagnostic().getMessage().contains("String"));
        assertCodeHighlight("123.5", ex);
    }

    @Test
    public void TypeParameter_Hides_VisibleType_Is_Acceptable() {
        GenericObject<String> root = compileAndRun("""
            <GenericObject xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                           fx:typeArguments="String" minWidth="$<Double>m2(123.5)"/>
        """);

        assertEquals(123.5, root.getMinWidth());
    }

    @SuppressWarnings({"unused", "unchecked"})
    public static class GenericStringObject<B extends String, S extends B> extends Pane {
        private final ObjectProperty<S> prop = new SimpleObjectProperty<>();
        public <U extends S, T extends U> ObjectProperty<T> propProperty() { return (ObjectProperty<T>)prop; }
        public <U extends S, T extends U> T getProp() { return ((ObjectProperty<T>)prop).get(); }
        public <U extends S, T extends U> void setProp(T value) { prop.set(value); }

        private S prop2;
        public <U extends S, T extends U> T getProp2() { return (T)prop2; }
        public <U extends S, T extends U> void setProp2(T value) { prop2 = value; }
    }

    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public static class GenericDoubleObject<T extends Double> extends Pane {
        private final ObjectProperty<T> prop = new SimpleObjectProperty<>();
        public ObjectProperty<T> propProperty() { return prop; }
        public T getProp() { return prop.get(); }
        public void setProp(T value) { prop.set(value); }

        private T prop2;
        public T getProp2() { return prop2; }
        public void setProp2(T value) { prop2 = value; }
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void GenericStringObject_Without_TypeArguments_Is_Instantiated() {
        GridPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GenericStringObject fx:id="obj" prop="foo" prop2="foo"/>
            </GridPane>
        """);

        assertEquals("foo", ((GenericStringObject)Reflection.getFieldValue(root, "obj")).getProp());
        assertEquals("foo", ((GenericStringObject)Reflection.getFieldValue(root, "obj")).getProp2());
    }

    @Test
    public void GenericDoubleObject_With_RawUsage_Cannot_Assign_Incompatible_Value_To_Erased_Property() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GenericDoubleObject fx:id="obj" prop="foo"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo", ex);
    }

    @Test
    public void GenericDoubleObject_With_RawUsage_Can_Assign_Value_To_Erased_Property() {
        GridPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GenericDoubleObject fx:id="obj" prop="123.0"/>
            </GridPane>
        """);

        GenericDoubleObject<?> obj = (GenericDoubleObject<?>)root.getChildren().get(0);
        assertEquals(123.0, obj.getProp(), 0.001);
    }

    @Test
    public void GenericDoubleObject_With_OutOfBound_TypeArgument_Throws_Exception() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GenericDoubleObject fx:typeArguments="java.lang.String" fx:id="obj" prop="foo"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.TYPE_ARGUMENT_OUT_OF_BOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("GenericDoubleObject", ex);
    }

    @Test
    public void NonGenericObject_With_TypeArguments_Throws_Exception() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GridPane fx:typeArguments="java.lang.String"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.CANNOT_PARAMETERIZE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("GridPane", ex);
    }

    @SuppressWarnings({"unused", "unchecked"})
    public static class GenericMethodsObject extends Pane {
        private final ObjectProperty<String> prop = new SimpleObjectProperty<>();
        public <T extends String> ObjectProperty<T> propProperty() { return (ObjectProperty<T>)prop; }
        public <T extends String> T getProp() { return ((ObjectProperty<T>)prop).get(); }
        public <T extends String> void setProp(T value) { prop.set(value); }

        public <T extends String> T getProp2() { return ((ObjectProperty<T>)prop).get(); }
        public <T extends String> void setProp2(T value) { prop.set(value); }

        public <T> T getProp3() { return (T)prop.get(); }
        public <T> void setProp3(T value) { prop.set((String)value); }

        public String getProp4() { return prop.get(); }
        public void setProp4(String value) { prop.set(value); }

        public <T> T m1(String s) { return (T)(Double)Double.parseDouble(s); }
    }

    @Test
    public void Generic_Method_ReturnType_Resolves_To_TypeBound() {
        GridPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GenericMethodsObject fx:id="obj" prop="foo" prop2="foo"/>
            </GridPane>
        """);

        assertEquals("foo", ((GenericMethodsObject)Reflection.getFieldValue(root, "obj")).getProp());
        assertEquals("foo", ((GenericMethodsObject)Reflection.getFieldValue(root, "obj")).getProp2());
    }

    @Test
    public void Generic_Method_ReturnType_Is_Not_Assignable_To_Generic_ArgumentType() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GenericMethodsObject fx:id="obj" prop3="foo" prop2="$self/prop3"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("$self/prop3", ex);
    }

    @Test
    public void Generic_Method_ReturnType_With_TypeWitness_Is_Assignable_To_Generic_ArgumentType() {
        GridPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GenericMethodsObject fx:id="obj" prop3="foo" prop2="$self/<String>prop3"/>
            </GridPane>
        """);

        assertEquals("foo", ((GenericMethodsObject)Reflection.getFieldValue(root, "obj")).getProp());
        assertEquals("foo", ((GenericMethodsObject)Reflection.getFieldValue(root, "obj")).getProp2());
    }

    @Test
    public void Generic_Method_ReturnType_With_TypeWitness_Is_Out_Of_Bounds() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GenericMethodsObject fx:id="obj" prop2="foo" prop3="$self/<Comparable<String>>prop2"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.TYPE_ARGUMENT_OUT_OF_BOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("self/<Comparable<String>>prop2", ex);
    }

    @Test
    public void TypeWitness_Is_Invalid_For_NonGeneric_Method() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <GenericMethodsObject fx:id="obj" prop3="foo" prop2="$self/<String>prop4"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.NUM_TYPE_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCode());
        assertCodeHighlight("self/<String>prop4", ex);
    }

    @Test
    public void Bind_Once_To_Method_With_TypeWitness() {
        Pane root = compileAndRun("""
            <GenericMethodsObject xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                  minWidth="$<Double>m1('123.5')"/>
        """);

        assertEquals(123.5, root.getMinWidth());
    }

    @Test
    public void Bind_Unidirectional_To_Method_With_TypeWitness() {
        Pane root = compileAndRun("""
            <GenericMethodsObject xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                  id="1" minWidth="${<Double>m1(id)}"/>
        """);

        assertEquals(1, root.getMinWidth());
        root.setId("123.5");
        assertEquals(123.5, root.getMinWidth());
        root.setId("5");
        assertEquals(5, root.getMinWidth());
    }
}

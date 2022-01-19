// Copyright (c) 2022, JFXcore. All rights reserved.
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
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void GenericObject_Without_TypeArguments_Is_Instantiated() {
        GridPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
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
            <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <GenericObject fx:typeArguments="java.lang.String" fx:id="obj" prop="foo" prop2="foo"/>
            </GridPane>
        """);

        assertEquals("foo", ((GenericObject)Reflection.getFieldValue(root, "obj")).getProp());
        assertEquals("foo", ((GenericObject)Reflection.getFieldValue(root, "obj")).getProp2());
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void CurlySyntax_GenericObject_With_TypeArguments_Is_Instantiated() {
        GridPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                      children="{GenericObject fx:typeArguments=String; fx:id=obj; prop=foo; prop2=foo}"/>
        """);

        assertEquals("foo", ((GenericObject)Reflection.getFieldValue(root, "obj")).getProp());
        assertEquals("foo", ((GenericObject)Reflection.getFieldValue(root, "obj")).getProp2());
    }

    @Test
    public void GenericRootObject_With_TypeArguments_Is_Instantiated() throws Exception {
        GenericObject<String> root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GenericObject xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
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
            <GenericObject xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                           prop="foo" prop2="foo"/>
        """);

        var classPool = new ClassPool();
        classPool.appendSystemPath();
        classPool.appendClassPath(root.getClass().getProtectionDomain().getCodeSource().getLocation().toExternalForm());
        assertNull(classPool.get(root.getClass().getName()).getGenericSignature());
    }

    @Test
    public void GenericObject_With_Uncoercible_PropertyValue_Throws_Exception() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
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
            <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
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
            <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
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
            <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
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
            <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
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
            <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <GenericObject fx:typeArguments="java.lang.String, java.lang.Integer" prop="foo"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.NUM_TYPE_ARGUMENTS_MISMATCH, ex.getDiagnostic().getCode());
        assertCodeHighlight("GenericObject".trim(), ex);
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
            <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <GenericStringObject fx:id="obj" prop="foo" prop2="foo"/>
            </GridPane>
        """);

        assertEquals("foo", ((GenericStringObject)Reflection.getFieldValue(root, "obj")).getProp());
        assertEquals("foo", ((GenericStringObject)Reflection.getFieldValue(root, "obj")).getProp2());
    }

    @Test
    public void GenericDoubleObject_With_RawUsage_Compiles_But_Throws_ClassCastException() {
        GridPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <GenericDoubleObject fx:id="obj" prop="foo"/>
            </GridPane>
        """);

        GenericDoubleObject<?> obj = (GenericDoubleObject<?>)root.getChildren().get(0);
        assertThrows(ClassCastException.class, obj::getProp);
    }

    @Test
    public void GenericDoubleObject_With_OutOfBound_TypeArgument_Throws_Exception() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
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
            <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
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
    }

    @Test
    public void Generic_Method_ReturnType_Resolves_To_TypeBound() {
        GridPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <GenericMethodsObject fx:id="obj" prop="foo" prop2="foo"/>
            </GridPane>
        """);

        assertEquals("foo", ((GenericMethodsObject)Reflection.getFieldValue(root, "obj")).getProp());
        assertEquals("foo", ((GenericMethodsObject)Reflection.getFieldValue(root, "obj")).getProp2());
    }

}

// Copyright (c) 2021, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
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
public class DefineBlockTest extends CompilerTestBase {

    @Test
    public void Define_Single_Primitive_Value() {
        GridPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <fx:define><String fx:id="str0">Hello!</String></fx:define>
            </GridPane>
        """);

        assertEquals("Hello!", Reflection.getFieldValue(root, "str0"));
    }

    @Test
    public void Define_Multiple_Primitive_Values() {
        GridPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <fx:define>
                    <String fx:id="v1">Hello1</String>
                    <Byte fx:id="v2">127</Byte>
                    <Short fx:id="v3">123</Short>
                    <Integer fx:id="v4">456</Integer>
                    <Long fx:id="v5">789</Long>
                    <Float fx:id="v6">42.5</Float>
                    <Double fx:id="v7">42.6</Double>
                    <Character fx:id="v8">c</Character>
                    <Boolean fx:id="v9">true</Boolean>
                </fx:define>
            </GridPane>
        """);

        assertEquals("Hello1", Reflection.getFieldValue(root, "v1"));
        assertEquals(127, (byte)Reflection.getFieldValue(root, "v2"));
        assertEquals(123, (short)Reflection.getFieldValue(root, "v3"));
        assertEquals(456, (int)Reflection.getFieldValue(root, "v4"));
        assertEquals(789, (long)Reflection.getFieldValue(root, "v5"));
        //noinspection RedundantCast
        assertEquals(42.5f, (float)Reflection.getFieldValue(root, "v6"), 0.001f);
        assertEquals(42.6, Reflection.getFieldValue(root, "v7"), 0.001);
        assertEquals('c', (char)Reflection.getFieldValue(root, "v8"));
        assertTrue((boolean)Reflection.getFieldValue(root, "v9"));
    }

    @Test
    public void Reference_Value_In_DefineBlock() {
        Label label = compileAndRun("""
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" text="$str">
                <fx:define><String fx:id="str">Hello1</String></fx:define>
            </Label>
        """);

        assertEquals("Hello1", label.getText());
    }

    @Test
    public void Reference_Boxed_Value_In_DefineBlock() {
        Label label = compileAndRun("""
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" prefWidth="$d">
                <fx:define><Double fx:id="d">123</Double></fx:define>
            </Label>
        """);

        assertEquals(123, label.getPrefWidth(), 0.001);
    }

    @Test
    public void Reference_Value_In_Nested_Define_Block() {
        GridPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Button text="$str">
                    <fx:define><String fx:id="str">Hello1</String></fx:define>
                </Button>
                <Button/>
            </GridPane>
        """);

        assertEquals("Hello1", ((Button)root.getChildren().get(0)).getText());
    }

    @Test
    public void Reference_Element_Value_In_Define_Block() {
        GridPane root = compileAndRun("""
            <?import javafx.geometry.*?>
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <fx:define>
                    <Insets fx:id="insets0">1,2,3,4</Insets>
                </fx:define>
                <GridPane GridPane.margin="$insets0"/>
            </GridPane>
        """);

        assertEquals(new Insets(1, 2, 3, 4), GridPane.getMargin(root.getChildren().get(0)));
    }

    @Test
    public void Duplicate_FxId_Throws_Exception() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <fx:define>
                    <String fx:id="m0">Hello 1</String>
                    <String fx:id="m0">Hello 2</String>
                </fx:define>
            </GridPane>
        """));

        assertEquals(ErrorCode.DUPLICATE_ID, ex.getDiagnostic().getCode());
        assertCodeHighlight("m0", ex);
    }

    @Test
    public void Duplicate_FxId_Outside_Of_DefineBlock_Throws_Exception() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <fx:define>
                    <String fx:id="m0">Hello 1</String>
                </fx:define>
                <GridPane fx:id="m0"/>
            </GridPane>
        """));

        assertEquals(ErrorCode.DUPLICATE_ID, ex.getDiagnostic().getCode());
        assertCodeHighlight("m0", ex);
    }

}

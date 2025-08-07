// Copyright (c) 2024, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;
import java.text.DecimalFormat;
import java.text.Format;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"HttpUrlsUsage", "DuplicatedCode"})
@ExtendWith(TestExtension.class)
public class StringConversionBindingTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class TestPane extends Pane {
        public static Format FMT = DecimalFormat.getInstance();
        public static DoubleStringConverter DBL_CONVERTER = new DoubleStringConverter();
        public static IntegerStringConverter INT_CONVERTER = new IntegerStringConverter();

        private final ObjectProperty<StringConverter<Double>> myConverter = new SimpleObjectProperty<>();
        public ObjectProperty<StringConverter<Double>> myConverterProperty() { return myConverter; }
    }

    @ParameterizedTest
    @CsvSource({"fx:once", "fx:bind", "fx:content", "fx:bindContent", "fx:bindContentBidirectional"})
    public void Converter_Property_Is_Only_Available_In_BidirectionalBinding(String bindMode) {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.util.converter.*?>
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" prefWidth="10">
                <Label text="{%s prefWidth; converter=DBL_CONVERTER}"/>
            </TestPane>
        """.formatted(bindMode)));

        assertEquals(ErrorCode.PROPERTY_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("converter=DBL_CONVERTER", ex);
    }

    @ParameterizedTest
    @CsvSource({"fx:once", "fx:bind", "fx:content", "fx:bindContent", "fx:bindContentBidirectional"})
    public void Format_Property_Is_Only_Available_In_BidirectionalBinding(String bindMode) {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.util.converter.*?>
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" prefWidth="10">
                <Label text="{%s prefWidth; format=FMT}"/>
            </TestPane>
        """.formatted(bindMode)));

        assertEquals(ErrorCode.PROPERTY_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("format=FMT", ex);
    }

    @Test
    public void Compatible_StringConverter_Binding() {
        Pane root = compileAndRun("""
            <?import javafx.util.converter.*?>
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" prefWidth="10">
                <Label text="#{prefWidth; converter=DBL_CONVERTER}"/>
            </TestPane>
        """);

        var label = (Label)root.getChildren().get(0);
        assertEquals("10.0", label.getText());

        label.setText("5");
        assertEquals(5.0, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Incompatible_Bidirectional_StringConverter_Binding() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.util.converter.*?>
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" prefWidth="10">
                <Label text="#{prefWidth; converter=INT_CONVERTER}"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("INT_CONVERTER", ex);
    }

    @Test
    public void Compatible_Format_Binding() {
        Pane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" prefWidth="10">
                <Label text="#{prefWidth; format=FMT}"/>
            </TestPane>
        """);

        var label = (Label)root.getChildren().get(0);
        assertEquals("10", label.getText());

        label.setText("5");
        assertEquals(5.0, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Incompatible_Format_Binding() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.util.*?>
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" prefWidth="10">
                <Label text="#{prefWidth; format=DBL_CONVERTER}"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("DBL_CONVERTER", ex);
    }

    @Test
    public void Format_And_StringConverter_Cannot_Be_Used_At_Same_Time() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import java.text.*?>
            <?import javafx.util.converter.*?>
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" prefWidth="10">
                <Label text="#{prefWidth; converter=DBL_CONVERTER; format=FMT}"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.CONFLICTING_PROPERTIES, ex.getDiagnostic().getCode());
        assertCodeHighlight("format=FMT", ex);
    }

    @Test
    public void Format_And_InverseMethod_Cannot_Be_Used_At_Same_Time() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import java.text.*?>
            <?import javafx.util.converter.*?>
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" prefWidth="10">
                <Label text="#{prefWidth; converter=DBL_CONVERTER; inverseMethod=foo}"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.CONFLICTING_PROPERTIES, ex.getDiagnostic().getCode());
        assertCodeHighlight("inverseMethod=foo", ex);
    }

    @Test
    public void StringConverter_On_Parent_Pane_Is_Null_At_Time_Of_Binding() {
        NullPointerException ex = assertThrows(NullPointerException.class, () -> compileAndRun("""
            <?import javafx.util.converter.*?>
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" prefWidth="10">
                <myConverter>
                    <DoubleStringConverter/>
                </myConverter>
                <Label text="#{prefWidth; converter=myConverter}"/>
            </TestPane>
        """));

        assertEquals("Converter cannot be null", ex.getMessage());
    }

    @Test
    public void Converter_Is_Only_Applicable_To_StringProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.util.converter.*?>
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" prefWidth="10">
                <Label prefWidth="#{prefWidth; converter=DBL_CONVERTER}"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.STRING_CONVERSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("#{prefWidth; converter=DBL_CONVERTER}", ex);
    }

    @Test
    public void Format_Is_Only_Applicable_To_StringProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import java.text.*?>
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" prefWidth="10">
                <Label prefWidth="#{prefWidth; format=FMT}"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.STRING_CONVERSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("#{prefWidth; format=FMT}", ex);
    }
}

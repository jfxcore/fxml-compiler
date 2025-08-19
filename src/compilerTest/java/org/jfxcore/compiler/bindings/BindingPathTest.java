// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.ArrayList;
import java.util.List;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"HttpUrlsUsage", "DuplicatedCode"})
@ExtendWith(TestExtension.class)
public class BindingPathTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class SimpleGetterSetterTestPane extends Pane {
        public List<String> calledMethods = new ArrayList<>();

        public DoubleProperty fooProperty() { calledMethods.add("fooProperty"); return new SimpleDoubleProperty(); }
        public double getFoo() { calledMethods.add("getFoo"); return 0; }
        public void setFoo(double foo) { calledMethods.add("setFoo"); }

        public DoubleProperty barProperty() { calledMethods.add("barProperty"); return new SimpleDoubleProperty(); }
        public double getBar() { calledMethods.add("getBar"); return 0; }
        public void setBar(double foo) { calledMethods.add("setBar"); }
    }

    @Test
    public void Bind_Once_Is_Bound_To_Simple_Getter_And_Setter() {
        SimpleGetterSetterTestPane root = compileAndRun("""
            <SimpleGetterSetterTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                  foo="$bar"/>
        """);

        assertEquals(2, root.calledMethods.size());
        assertEquals("getBar", root.calledMethods.get(0));
        assertEquals("setFoo", root.calledMethods.get(1));
        assertFalse(root.fooProperty().isBound());
        assertFalse(root.barProperty().isBound());
    }

    @SuppressWarnings("unused")
    public static class TestContext {
        private final BooleanProperty boolVal = new SimpleBooleanProperty(true);
        private final DoubleProperty doubleVal = new SimpleDoubleProperty(123);

        public BooleanProperty boolValProperty() {
            return boolVal;
        }

        public DoubleProperty doubleValProperty() {
            return doubleVal;
        }

        public boolean invariantBoolVal = true;
        public Boolean invariantBoolBox = true;
        public double invariantDoubleVal = 234;
        public Double invariantDoubleBox = null;
        public short invariantShortVal = 6;
        public Short invariantShortBox = 7;

        public List<String> invariantList = List.of("foo", "bar", "baz");
        public ObservableList<String> observableList = FXCollections.observableArrayList("foo", "bar", "baz");

        public Insets margin = new Insets(1, 2, 3, 4);

        public Double nullValue = null;
    }

    @SuppressWarnings("unused")
    public static class TestPane extends Pane {
        private final ObjectProperty<TestContext> context = new SimpleObjectProperty<>(new TestContext());

        public ObjectProperty<TestContext> contextProperty() {
            return context;
        }

        public TestContext invariantContext = new TestContext();
        public TestContext nullContext;

        public final DoubleProperty simpleProp = new SimpleDoubleProperty(1);
        public double simpleDoubleVal = 2;
        public Double simpleDoubleBox = 3.0;
        public short simpleShortVal = 4;
        public Short simpleShortBox = 5;
        public ObservableValue<Boolean> observableBoolean = new SimpleBooleanProperty(true);
        public ObservableValue<Double> observableDouble = new ObservableValue<>() {
            @Override public void addListener(ChangeListener<? super Double> listener) {}
            @Override public void removeListener(ChangeListener<? super Double> listener) {}
            @Override public Double getValue() { return 123.0; }
            @Override public void addListener(InvalidationListener listener) {}
            @Override public void removeListener(InvalidationListener listener) {}
        };

        @SuppressWarnings("rawtypes")
        public ObservableValue rawProp = new SimpleBooleanProperty(true);

        public ReadOnlyBooleanProperty notBindableProperty() { return null; }

        public record NestedClass(String value) {
            public static final NestedClass INSTANCE = new NestedClass("testValue");
        }
    }

    @Test
    public void Bindings_With_Explicit_Intrinsic_Syntax() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      managed="{fx:once context.boolVal}"
                      prefWidth="{fx:bind context.doubleVal}"
                      prefHeight="{fx:bindBidirectional context.doubleVal}"/>
        """);

        assertFalse(root.managedProperty().isBound());
        assertTrue(root.isManaged());

        assertTrue(root.prefWidthProperty().isBound());
        assertEquals(123.0, root.getPrefWidth(), 0.001);

        assertFalse(root.prefHeightProperty().isBound());
        assertEquals(123.0, root.getPrefHeight(), 0.001);
    }

    @Test
    public void Bindings_With_Element_Syntax() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <managed><fx:once path="context.boolVal"/></managed>
                <prefWidth><fx:bind path="context.doubleVal"/></prefWidth>
                <prefHeight><fx:bindBidirectional path="context.doubleVal"/></prefHeight>
            </TestPane>
        """);

        assertFalse(root.managedProperty().isBound());
        assertTrue(root.isManaged());

        assertTrue(root.prefWidthProperty().isBound());
        assertEquals(123.0, root.getPrefWidth(), 0.001);

        assertFalse(root.prefHeightProperty().isBound());
        assertEquals(123.0, root.getPrefHeight(), 0.001);
    }

    @Test
    public void PathExpression_Not_Supported_In_Element_Content() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <managed>
                    <fx:once>context.boolVal</fx:once>
                </managed>
            </TestPane>
        """));

        assertEquals(ErrorCode.INVALID_EXPRESSION, ex.getDiagnostic().getCode());
        assertCodeHighlight("context.boolVal", ex);
    }

    @Test
    public void Explicit_Path_Property_Of_Binding_Intrinsic() {
        Pane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" id="foo">
                <Label text="${path=id}"/>
            </Pane>
        """);

        var label = (Label)root.getChildren().get(0);
        assertEquals("foo", label.getText());
    }

    @Test
    public void Invalid_Property_In_Binding_Intrinsic_Throws() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Label text="${path=style; foo=bar}"/>
            </Pane>
        """));

        assertEquals(ErrorCode.PROPERTY_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo=bar", ex);
    }

    @Test
    public void Bind_Once_To_Nonexistent_Property_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="$nonexistent"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("nonexistent", ex);
    }

    @Test
    public void Bind_Once_To_RawType_Property_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="$rawProp"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("rawProp", ex);
    }

    @Test
    public void Bind_Once_To_Interface_Method() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="$context.invariantList.isEmpty"
                      managed="$context.observableList.isEmpty"/>
        """);

        assertFalse(root.visibleProperty().isBound());
        assertFalse(root.isVisible());

        assertFalse(root.managedProperty().isBound());
        assertFalse(root.isManaged());
    }

    @Test
    public void Bind_Once_To_Single_Invariant_Property() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      visible="$observableBoolean"
                      prefWidth="$simpleDoubleVal"
                      prefHeight="$simpleDoubleBox"
                      minWidth="$simpleShortVal"
                      minHeight="$simpleShortBox"
                      maxHeight="$observableDouble">
            </TestPane>
        """);

        assertFalse(root.visibleProperty().isBound());
        assertTrue(root.isVisible());

        assertFalse(root.prefWidthProperty().isBound());
        assertEquals(2.0, root.getPrefWidth(), 0.001);

        assertFalse(root.prefHeightProperty().isBound());
        assertEquals(3.0, root.getPrefHeight(), 0.001);

        assertFalse(root.minWidthProperty().isBound());
        assertEquals(4.0, root.getMinWidth(), 0.001);

        assertFalse(root.minHeightProperty().isBound());
        assertEquals(5.0, root.getMinHeight(), 0.001);

        assertFalse(root.maxHeightProperty().isBound());
        assertEquals(123.0, root.getMaxHeight(), 0.001);
    }

    @Test
    public void Bind_Once_To_Invariant_Properties() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      managed="$invariantContext.invariantBoolVal"
                      prefWidth="$invariantContext.invariantDoubleVal"
                      prefHeight="$invariantContext.invariantDoubleBox"
                      minWidth="$invariantContext.invariantShortVal"
                      minHeight="$invariantContext.invariantShortBox"/>
        """);

        assertFalse(root.managedProperty().isBound());
        assertTrue(root.isManaged());

        assertFalse(root.prefWidthProperty().isBound());
        assertEquals(234.0, root.getPrefWidth(), 0.001);

        assertFalse(root.prefHeightProperty().isBound());
        assertEquals(0.0, root.getPrefHeight(), 0.001);

        assertFalse(root.minWidthProperty().isBound());
        assertEquals(6.0, root.getMinWidth(), 0.001);

        assertFalse(root.minHeightProperty().isBound());
        assertEquals(7.0, root.getMinHeight(), 0.001);
    }

    @Test
    public void Bind_Once_To_Observable_Properties() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      managed="$context.boolVal" prefWidth="$context.doubleVal"/>
        """);

        assertFalse(root.managedProperty().isBound());
        assertTrue(root.isManaged());

        assertFalse(root.prefWidthProperty().isBound());
        assertEquals(123.0, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Once_To_Observable_And_Invariant_Properties() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      managed="$context.invariantBoolVal"
                      prefWidth="$context.invariantDoubleVal" prefHeight="$context.invariantDoubleBox"
                      minWidth="$context.invariantShortVal" minHeight="$context.invariantShortBox"/>
        """);

        assertFalse(root.managedProperty().isBound());
        assertTrue(root.isManaged());

        assertFalse(root.prefWidthProperty().isBound());
        assertEquals(234.0, root.getPrefWidth(), 0.001);

        assertFalse(root.prefHeightProperty().isBound());
        assertEquals(0.0, root.getPrefHeight(), 0.001);

        assertFalse(root.minWidthProperty().isBound());
        assertEquals(6, root.getMinWidth(), 0.001);

        assertFalse(root.minHeightProperty().isBound());
        assertEquals(7.0, root.getMinHeight(), 0.001);
    }

    @Test
    public void Bind_Once_To_Invariant_And_Observable_Properties() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      managed="$invariantContext.boolVal" prefWidth="$invariantContext.doubleVal"/>
        """);

        assertFalse(root.managedProperty().isBound());
        assertTrue(root.isManaged());

        assertFalse(root.prefWidthProperty().isBound());
        assertEquals(123.0, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Once_To_Invariant_Null_Context_Throws_NPE() {
        NullPointerException ex = assertThrows(NullPointerException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      managed="$nullContext.boolVal"/>
        """));

        assertEquals("nullContext", ex.getMessage());
    }

    @Test
    public void Bind_Once_To_Invariant_Null_Value() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="$context.nullValue"/>
        """);

        assertEquals(0, root.getPrefWidth());
    }

    @Test
    public void Bind_Once_Fails_For_ReadOnlyProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      notBindable="$invariantContext.boolVal"/>
        """));

        assertEquals(ErrorCode.CANNOT_MODIFY_READONLY_PROPERTY, ex.getDiagnostic().getCode());
        assertCodeHighlight("""
            notBindable="$invariantContext.boolVal"
        """.trim(), ex);
    }

    @Test
    public void Bind_Once_To_Static_Property() {
        TestPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      GridPane.margin="$context.margin"/>
        """);

        assertEquals(new Insets(1, 2, 3, 4), GridPane.getMargin(root));
    }

    @Test
    public void Bind_Once_To_Instance_Member_In_Static_Context_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$Double.toString"/>
        """));

        assertEquals(ErrorCode.INSTANCE_MEMBER_REFERENCED_FROM_STATIC_CONTEXT, ex.getDiagnostic().getCode());
        assertCodeHighlight("Double.toString", ex);
    }

    @Test
    public void Bind_Once_To_Static_Field_In_NestedClass() {
        TestPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="$TestPane.NestedClass.INSTANCE.value"/>
        """);

        assertEquals("testValue", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_Interface_Method() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          visible="${context.invariantList.isEmpty}"
                          managed="${context.observableList.isEmpty}"/>
        """);

        assertTrue(root.visibleProperty().isBound());
        assertFalse(root.isVisible());

        assertTrue(root.managedProperty().isBound());
        assertFalse(root.isManaged());
    }

    @Test
    public void Bind_Unidirectional_To_Single_Invariant_Property_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="${simpleDoubleVal}"/>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("simpleDoubleVal", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Invariant_Properties() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      managed="${invariantContext.invariantBoolVal}"/>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("invariantContext.invariantBoolVal", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Observable_Properties() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      managed="${context.boolVal}" prefWidth="${context.doubleVal}"/>
        """);

        assertTrue(root.managedProperty().isBound());
        assertTrue(root.isManaged());
        root.contextProperty().get().boolValProperty().set(false);
        assertFalse(root.isManaged());

        assertTrue(root.prefWidthProperty().isBound());
        assertEquals(123.0, root.getPrefWidth(), 0.001);
        root.contextProperty().get().doubleValProperty().set(0);
        assertEquals(0.0, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_Observable_And_Invariant_Properties() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      managed="${context.invariantBoolVal}"
                      prefWidth="${context.invariantDoubleVal}" prefHeight="${context.invariantDoubleBox}"
                      minWidth="${context.invariantShortVal}" minHeight="${context.invariantShortBox}"/>
        """);

        assertTrue(root.managedProperty().isBound());
        assertTrue(root.isManaged());

        assertTrue(root.prefWidthProperty().isBound());
        assertEquals(234.0, root.getPrefWidth(), 0.001);

        assertTrue(root.prefHeightProperty().isBound());
        assertEquals(0.0, root.getPrefHeight(), 0.001);

        assertTrue(root.minWidthProperty().isBound());
        assertEquals(6.0, root.getMinWidth(), 0.001);

        assertTrue(root.minHeightProperty().isBound());
        assertEquals(7.0, root.getMinHeight(), 0.001);

        TestContext newCtx = new TestContext();
        newCtx.invariantBoolVal = false;
        newCtx.invariantDoubleVal = 0;
        newCtx.invariantDoubleBox = 1.0;
        newCtx.invariantShortVal = 2;
        newCtx.invariantShortBox = 3;
        root.contextProperty().set(newCtx);

        assertFalse(root.isManaged());
        assertEquals(0.0, root.getPrefWidth(), 0.001);
        assertEquals(1.0, root.getPrefHeight(), 0.001);
        assertEquals(2.0, root.getMinWidth(), 0.001);
        assertEquals(3.0, root.getMinHeight(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_Invariant_And_Observable_Properties() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      managed="${invariantContext.boolVal}" prefWidth="${invariantContext.doubleVal}"/>
        """);

        assertTrue(root.managedProperty().isBound());
        assertTrue(root.isManaged());
        root.invariantContext.boolValProperty().set(false);
        assertFalse(root.isManaged());

        assertTrue(root.prefWidthProperty().isBound());
        assertEquals(123.0, root.getPrefWidth(), 0.001);
        root.invariantContext.doubleValProperty().set(0);
        assertEquals(0.0, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_Invariant_Null_Context_Throws_NPE() {
        NullPointerException ex = assertThrows(NullPointerException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      managed="${nullContext.boolVal}"/>
        """));

        assertEquals("nullContext", ex.getMessage());
    }

    @Test
    public void Bind_Unidirectional_Fails_For_ReadOnlyProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      notBindable="${invariantContext.boolVal}"/>
        """));

        assertEquals(ErrorCode.CANNOT_MODIFY_READONLY_PROPERTY, ex.getDiagnostic().getCode());
        assertCodeHighlight("""
            notBindable="${invariantContext.boolVal}"
        """.trim(), ex);
    }

    @Test
    public void Bind_Unidirectional_To_Static_Property_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      GridPane.margin="${context.margin}"/>
        """));

        assertEquals(ErrorCode.INVALID_BINDING_TARGET, ex.getDiagnostic().getCode());
        assertCodeHighlight("""
            GridPane.margin="${context.margin}"
        """.trim(), ex);
    }

    @Test
    public void Bind_Unidirectional_To_Instance_Member_In_Static_Context_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${Double.toString}"/>
        """));

        assertEquals(ErrorCode.INSTANCE_MEMBER_REFERENCED_FROM_STATIC_CONTEXT, ex.getDiagnostic().getCode());
        assertCodeHighlight("Double.toString", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Static_Field_In_NestedClass() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      id="${TestPane.NestedClass.INSTANCE.value}"/>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("TestPane.NestedClass.INSTANCE.value", ex);
    }

    @Test
    public void Bind_Bidirectional_To_Single_Invariant_Property_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="#{simpleDoubleVal}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("simpleDoubleVal", ex);
    }

    @Test
    public void Bind_Bidirectional_To_Invariant_Properties_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      managed="#{invariantContext.invariantBoolVal}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("invariantContext.invariantBoolVal", ex);
    }

    @Test
    public void Bind_Bidirectional_To_Observable_Properties() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      managed="#{context.boolVal}"
                      prefWidth="#{context.doubleVal}"/>
        """);

        assertFalse(root.managedProperty().isBound()); // bidirectional binding doesn't set isBound()==true
        assertTrue(root.isManaged());
        root.setManaged(false);
        assertFalse(root.contextProperty().get().boolValProperty().get());

        assertFalse(root.prefWidthProperty().isBound());
        assertEquals(123.0, root.getPrefWidth(), 0.001);
        root.setPrefWidth(456);
        assertEquals(456.0, root.contextProperty().get().doubleValProperty().get(), 0.001);
    }

    @Test
    public void Bind_Bidirectional_To_Observable_Properties_Works_When_Path_Changes() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      managed="#{context.boolVal}"
                      prefWidth="#{context.doubleVal}"/>
        """);

        assertTrue(root.isManaged());
        root.context.set(null);
        assertFalse(root.isManaged());

        TestContext ctx = new TestContext();
        ctx.boolVal.set(false);
        root.context.set(ctx);

        assertFalse(root.isManaged());
        root.setManaged(true);
        assertTrue(ctx.boolVal.get());
    }

    @Test
    public void Bind_Bidirectional_To_Observable_And_Invariant_Properties_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      managed="#{context.invariantBoolVal}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("context.invariantBoolVal", ex);
    }

    @Test
    public void Bind_Bidirectional_To_Invariant_And_Observable_Properties() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      managed="#{invariantContext.boolVal}"
                      prefWidth="#{invariantContext.doubleVal}"/>
        """);

        assertFalse(root.managedProperty().isBound());
        assertTrue(root.isManaged());
        root.invariantContext.boolValProperty().set(false);
        assertFalse(root.isManaged());

        assertFalse(root.prefWidthProperty().isBound());
        assertEquals(123.0, root.getPrefWidth(), 0.001);
        root.invariantContext.doubleValProperty().set(0);
        assertEquals(0.0, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Bidirectional_To_Invariant_Null_Context() {
        NullPointerException ex = assertThrows(NullPointerException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      managed="#{nullContext.boolVal}"/>
        """));

        assertEquals("nullContext", ex.getMessage());
    }

    @Test
    public void Bind_Bidirectional_Fails_For_ReadOnlyProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      notBindable="#{invariantContext.boolVal}"/>
        """));

        assertEquals(ErrorCode.CANNOT_MODIFY_READONLY_PROPERTY, ex.getDiagnostic().getCode());
        assertCodeHighlight("""
            notBindable="#{invariantContext.boolVal}"
        """.trim(), ex);
    }

    @Test
    public void Bind_Bidirectional_To_Static_Property_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      GridPane.margin="#{context.margin}"/>
        """));

        assertEquals(ErrorCode.INVALID_BINDING_TARGET, ex.getDiagnostic().getCode());
        assertCodeHighlight("""
            GridPane.margin="#{context.margin}"
        """.trim(), ex);
    }
}

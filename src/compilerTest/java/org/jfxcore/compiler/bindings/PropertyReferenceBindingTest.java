// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class PropertyReferenceBindingTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class DoublePropertyEx extends SimpleDoubleProperty {
        private final ListProperty<String> subProp = new SimpleListProperty<>(FXCollections.observableArrayList());

        public ListProperty<String> subPropProperty() {
            return subProp;
        }

        public ObservableList<String> getSubProp() {
            return subProp.get();
        }
    }

    @SuppressWarnings("unused")
    public static class TestContext {
        public boolean boolVal = true;
        public BooleanProperty boolProp = new SimpleBooleanProperty(this, "boolProp");
        public DoublePropertyEx doublePropEx = new DoublePropertyEx();

        private final ListProperty<String> listProp =
            new SimpleListProperty<>(this, "listProp", FXCollections.observableArrayList());

        public ListProperty<String> listPropProperty() {
            return listProp;
        }
    }

    @SuppressWarnings("unused")
    public static class TestPane extends Pane {
        private final ObjectProperty<TestContext> context = new SimpleObjectProperty<>(
            this, "context", new TestContext());

        public ObjectProperty<TestContext> contextProperty() {
            return context;
        }

        public final TestContext invariantContext = new TestContext();
    }

    @Test
    public void Bind_Unidirectional_To_Invariant_PropertyReference_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                      visible="{fx:bind !!context::boolVal}"/>
        """));

        assertEquals(ErrorCode.INVALID_INVARIANT_REFERENCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("boolVal", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Invariant_Property() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                      id="{fx:bind context::boolProp.name}"/>
        """);

        assertTrue(root.idProperty().isBound());
        assertEquals("boolProp", root.getId());
    }

    @Test
    public void Bind_Unidirectional_To_Invariant_Property_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                      id="{fx:bind context::boolProp::name}"/>
        """));

        assertEquals(ErrorCode.INVALID_INVARIANT_REFERENCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("name", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Observable_Property() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                      prefWidth="{fx:bind context::listProp.size}"
                      visible="{fx:bind context::listProp.empty}"/>
        """);

        assertTrue(root.isVisible());
        assertEquals(0, root.getPrefWidth(), 0.001);

        root.context.get().listProp.addAll("foo", "bar", "baz");
        assertFalse(root.isVisible());
        assertEquals(3, root.getPrefWidth(), 0.001);
    }

    @Test
    @Disabled
    public void Bind_Unidirectional_To_Property_Of_Property() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                      visible="{fx:bind !!invariantContext::doublePropEx.subProp}"/>
        """);

        assertTrue(root.isVisible());
    }

    @Test
    public void Bind_Unidirectional_To_Property_Of_Property_Of_Property() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                      visible="{fx:bind invariantContext::doublePropEx::subProp.empty}"/>
        """);

        assertTrue(root.isVisible());
        root.invariantContext.doublePropEx.subProp.add("foo");
        assertFalse(root.isVisible());
    }

    @Test
    public void Select_PropertyReference_Directly() {
        TestPane root = compileAndRun("""
            <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                      id="{fx:bind ::context.name}"/>
        """);

        assertEquals("context", root.getId());
    }

}

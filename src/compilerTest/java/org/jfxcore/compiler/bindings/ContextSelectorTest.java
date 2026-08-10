// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class ContextSelectorTest extends CompilerTestBase {

    public static class ContextValue {
        public final String value;
        private final StringProperty observableValue =
            new SimpleStringProperty(this, "observableValue");

        public ContextValue(String value) {
            this.value = value;
            this.observableValue.set(value);
        }

        public StringProperty observableValueProperty() {
            return observableValue;
        }

        public String getObservableValue() {
            return observableValue.get();
        }

        public String append(String suffix) {
            return value + suffix;
        }
    }

    @SuppressWarnings("unused")
    public static class ContextPane extends Pane {
        public final String root = "root";
        public final String self = "self";
        public final String parent = "parent";

        private final ObjectProperty<Object> result = new SimpleObjectProperty<>();
        private final ObjectProperty<Object> secondResult = new SimpleObjectProperty<>();
        private final ObjectProperty<Object> thirdResult = new SimpleObjectProperty<>();
        private final ObjectProperty<Object> fourthResult = new SimpleObjectProperty<>();
        private final StringProperty textResult = new SimpleStringProperty(this, "textResult");
        private final StringProperty secondTextResult = new SimpleStringProperty(this, "secondTextResult");
        private final ObjectProperty<ContextValue> context = new SimpleObjectProperty<>(this, "context", new ContextValue("context-1"));
        private final StringProperty value = new SimpleStringProperty(this, "value");

        public ObjectProperty<Object> resultProperty() {
            return result;
        }

        public ObjectProperty<Object> secondResultProperty() {
            return secondResult;
        }

        public ObjectProperty<Object> thirdResultProperty() {
            return thirdResult;
        }

        public ObjectProperty<Object> fourthResultProperty() {
            return fourthResult;
        }

        public StringProperty textResultProperty() {
            return textResult;
        }

        public StringProperty secondTextResultProperty() {
            return secondTextResult;
        }

        public ObjectProperty<ContextValue> contextProperty() {
            return context;
        }

        public ContextValue getContext() {
            return context.get();
        }

        public String getThis() {
            return "ordinary-this";
        }

        public StringProperty valueProperty() {
            return value;
        }

        public String getValue() {
            return value.get();
        }

        public void setValue(String value) {
            this.value.set(value);
        }
    }

    @Test
    public void Terminal_Contexts_Produce_Their_Context_Objects() {
        ContextPane root = compileAndRun("""
            <ContextPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         result="$:element">
                <ContextPane result="$:parent" secondResult="$:root"
                             thirdResult="$:parent(0)" fourthResult="$:parent(1)"/>
            </ContextPane>
        """);

        ContextPane child = (ContextPane)root.getChildren().get(0);
        assertSame(root, root.resultProperty().get());
        assertSame(root, child.resultProperty().get());
        assertSame(root, child.secondResultProperty().get());
        assertSame(child, child.thirdResultProperty().get());
        assertSame(child.resultProperty().get(), child.fourthResultProperty().get());
    }

    @Test
    public void Explicit_And_Implicit_Default_Context_Agree_On_Root_Fallback() {
        ContextPane root = compileAndRun("""
            <ContextPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         textResult="${value}"
                         secondTextResult="${:context.value}"
                         result="$::value"
                         secondResult="$:context::value"/>
        """);

        root.setValue("root-value");
        assertEquals("root-value", root.textResultProperty().get());
        assertEquals("root-value", root.secondTextResultProperty().get());
        assertSame(root.valueProperty(), root.resultProperty().get());
        assertSame(root.valueProperty(), root.secondResultProperty().get());
    }

    @Test
    public void Explicit_And_Implicit_Default_Context_Reselect_Together() {
        ContextPane root = compileAndRun("""
            <ContextPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         fx:context="${context}">
                <ContextPane textResult="${value}"
                             secondTextResult="${:context.value}"
                             thirdResult="${::observableValue}"
                             fourthResult="${:context::observableValue}"/>
            </ContextPane>
        """);

        ContextPane child = (ContextPane)root.getChildren().get(0);
        ContextValue first = root.getContext();
        assertEquals("context-1", child.textResultProperty().get());
        assertEquals("context-1", child.secondTextResultProperty().get());
        assertSame(first.observableValueProperty(), child.thirdResultProperty().get());
        assertSame(first.observableValueProperty(), child.fourthResultProperty().get());

        ContextValue second = new ContextValue("context-2");
        root.contextProperty().set(second);
        assertEquals("context-2", child.textResultProperty().get());
        assertEquals("context-2", child.secondTextResultProperty().get());
        assertSame(second.observableValueProperty(), child.thirdResultProperty().get());
        assertSame(second.observableValueProperty(), child.fourthResultProperty().get());
    }

    @Test
    public void Explicit_And_Implicit_Methods_Use_The_Current_Observable_Context_Value() {
        ContextPane root = compileAndRun("""
            <ContextPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         fx:context="${context}">
                <ContextPane textResult="${append('!')}"
                             secondTextResult="${:context.append('?')}"/>
            </ContextPane>
        """);

        ContextPane child = (ContextPane)root.getChildren().get(0);
        assertEquals("context-1!", child.textResultProperty().get());
        assertEquals("context-1?", child.secondTextResultProperty().get());

        root.contextProperty().set(new ContextValue("context-2"));
        assertEquals("context-2!", child.textResultProperty().get());
        assertEquals("context-2?", child.secondTextResultProperty().get());
    }

    @Test
    public void Root_Context_And_Element_Are_Distinct_When_Context_Is_Replaced() {
        ContextPane root = compileAndRun("""
            <ContextPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         fx:context="${context}">
                <ContextPane result="$:context"
                             secondResult="$:root"
                             thirdResult="$:element"/>
            </ContextPane>
        """);

        ContextPane child = (ContextPane)root.getChildren().get(0);
        assertSame(root.getContext(), child.resultProperty().get());
        assertSame(root, child.secondResultProperty().get());
        assertSame(child, child.thirdResultProperty().get());
    }

    @Test
    public void Context_Selector_In_FxContext_Value_Uses_Root_Fallback() {
        ContextPane root = compileAndRun("""
            <ContextPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         fx:context="$:context.context">
                <ContextPane result="$:context"/>
            </ContextPane>
        """);

        ContextPane child = (ContextPane)root.getChildren().get(0);
        assertSame(root.getContext(), child.resultProperty().get());
    }

    @Test
    public void This_Is_Resolved_As_An_Ordinary_Property() {
        ContextPane root = compileAndRun("""
            <ContextPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         result="$this"/>
        """);

        assertEquals("ordinary-this", root.resultProperty().get());
    }

    @Test
    public void Selected_Contexts_Observe_The_Selected_Object() {
        ContextPane root = compileAndRun("""
            <ContextPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         value="root-value">
                <ContextPane value="parent-value">
                    <ContextPane value="self-value"
                                 result="${:root.value}"
                                 secondResult="${:parent.value}"
                                 thirdResult="${:element.value}"/>
                </ContextPane>
            </ContextPane>
        """);

        ContextPane parent = (ContextPane)root.getChildren().get(0);
        ContextPane self = (ContextPane)parent.getChildren().get(0);
        assertEquals("root-value", self.resultProperty().get());
        assertEquals("parent-value", self.secondResultProperty().get());
        assertEquals("self-value", self.thirdResultProperty().get());

        root.setValue("root-2");
        parent.setValue("parent-2");
        self.setValue("self-2");
        assertEquals("root-2", self.resultProperty().get());
        assertEquals("parent-2", self.secondResultProperty().get());
        assertEquals("self-2", self.thirdResultProperty().get());
    }

    @Test
    public void Typed_Parent_Depth_Counts_Matching_Ancestors() {
        ContextPane root = compileAndRun("""
            <?import javafx.scene.layout.Pane?>
            <ContextPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <ContextPane>
                    <Pane>
                        <ContextPane result="$:parent(2)"
                                     secondResult="$:parent<ContextPane>(2)"
                                     thirdResult="$:parent<ContextPane>(0)"/>
                    </Pane>
                </ContextPane>
            </ContextPane>
        """);

        ContextPane first = (ContextPane)root.getChildren().get(0);
        Pane intermediate = (Pane)first.getChildren().get(0);
        ContextPane leaf = (ContextPane)intermediate.getChildren().get(0);
        assertSame(first, leaf.resultProperty().get());
        assertSame(root, leaf.secondResultProperty().get());
        assertSame(leaf, leaf.thirdResultProperty().get());
    }

    @Test
    public void Context_Names_Without_A_Colon_Are_Ordinary_Members() {
        ContextPane root = compileAndRun("""
            <ContextPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         result="$root"
                         secondResult="$self"
                         thirdResult="$parent"/>
        """);

        assertEquals("root", root.resultProperty().get());
        assertEquals("self", root.secondResultProperty().get());
        assertEquals("parent", root.thirdResultProperty().get());
    }
}

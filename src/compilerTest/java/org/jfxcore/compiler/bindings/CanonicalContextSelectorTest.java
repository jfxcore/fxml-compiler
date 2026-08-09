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
public class CanonicalContextSelectorTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class ContextPane extends Pane {
        public final String root = "root";
        public final String self = "self";
        public final String parent = "parent";

        private final ObjectProperty<Object> result = new SimpleObjectProperty<>();
        private final ObjectProperty<Object> secondResult = new SimpleObjectProperty<>();
        private final ObjectProperty<Object> thirdResult = new SimpleObjectProperty<>();
        private final ObjectProperty<Object> fourthResult = new SimpleObjectProperty<>();
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
                         result="$:self">
                <ContextPane result="$:parent" secondResult="$:root"
                             thirdResult="$:parent(0)"/>
            </ContextPane>
        """);

        ContextPane child = (ContextPane)root.getChildren().get(0);
        assertSame(root, root.resultProperty().get());
        assertSame(root, child.resultProperty().get());
        assertSame(root, child.secondResultProperty().get());
        assertSame(child.resultProperty().get(), child.thirdResultProperty().get());
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
                                 thirdResult="${:self.value}"/>
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
                        <ContextPane result="$:parent(1)"
                                     secondResult="$:parent(ContextPane, 1)"/>
                    </Pane>
                </ContextPane>
            </ContextPane>
        """);

        ContextPane first = (ContextPane)root.getChildren().get(0);
        Pane intermediate = (Pane)first.getChildren().get(0);
        ContextPane leaf = (ContextPane)intermediate.getChildren().get(0);
        assertSame(first, leaf.resultProperty().get());
        assertSame(root, leaf.secondResultProperty().get());
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

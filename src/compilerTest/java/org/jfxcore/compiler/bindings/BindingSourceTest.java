// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.Reflection;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class BindingSourceTest extends CompilerTestBase {

    @Test
    public void Bind_Once_To_Parent_Property_With_Indexed_Parent_Selector_Does_Not_Apply_Latest_Value() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                  prefHeight="123">
                <Pane fx:id="pane" prefWidth="234">
                    <Pane prefWidth="$parent[0]/prefWidth"
                          prefHeight="$parent[1]/prefHeight"/>
                </Pane>
            </Pane>
        """);

        var pane = (Pane)root.getChildren().get(0);
        var pane2 = (Pane)((Pane)root.getChildren().get(0)).getChildren().get(0);
        assertEquals(123, root.getPrefHeight(), 0.001);
        assertEquals(234, pane.getPrefWidth(), 0.001);
        assertEquals(-1, pane2.getPrefHeight(), 0.001);
        assertEquals(-1, pane2.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Once_To_Parent_Property_With_Typed_Parent_Selector_Does_Not_Apply_Latest_Value() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="123">
                <Pane prefWidth="$parent[Pane]/prefWidth"/>
            </Pane>
        """);

        Pane pane = (Pane)root.getChildren().get(0);
        assertEquals(-1, pane.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Once_To_Unqualified_Static_Constant() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Pane maxWidth="$Region.USE_PREF_SIZE"/>
            </Pane>
        """);

        assertFieldAccess(root, "javafx.scene.layout.Region", "USE_PREF_SIZE", "D");
    }

    @Test
    public void Bind_Once_To_Qualified_Static_Constant() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Pane maxWidth="$javafx.scene.layout.Region.USE_PREF_SIZE"/>
            </Pane>
        """);

        assertFieldAccess(root, "javafx.scene.layout.Region", "USE_PREF_SIZE", "D");
    }

    @Test
    public void Bind_Unidirectional_To_Parent_Property_With_Indexed_Parent_Selector() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                  prefHeight="123">
                <Pane fx:id="pane" prefWidth="234">
                    <Pane prefWidth="${parent[0]/prefWidth}"
                          prefHeight="${parent[1]/prefHeight}"/>
                </Pane>
            </Pane>
        """);

        var pane = (Pane)root.getChildren().get(0);
        var pane2 = (Pane)((Pane)root.getChildren().get(0)).getChildren().get(0);
        assertEquals(123, root.getPrefHeight(), 0.001);
        assertEquals(234, pane.getPrefWidth(), 0.001);
        assertEquals(123, pane2.getPrefHeight(), 0.001);
        assertEquals(234, pane2.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_Parent_Property_With_NonIndexed_Parent_Selector() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Pane fx:id="pane" prefWidth="123">
                    <Pane prefWidth="${parent/prefWidth}"/>
                </Pane>
            </Pane>
        """);

        Pane pane = (Pane)((Pane)root.getChildren().get(0)).getChildren().get(0);
        assertEquals(123, pane.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_Parent_Property_With_Indexed_Parent_Selector_And_Named_Element() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Pane fx:id="pane" prefWidth="123">
                    <Pane prefWidth="${parent[1]/pane.prefWidth}"/>
                </Pane>
            </Pane>
        """);

        Pane pane = (Pane)((Pane)root.getChildren().get(0)).getChildren().get(0);
        assertEquals(123, pane.getPrefWidth(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_Parent_Property_With_Typed_Parent_Selector() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <StackPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       prefHeight="123">
                <Pane prefWidth="234">
                    <Pane prefWidth="${parent[Pane]/prefWidth}"
                          prefHeight="${parent[StackPane]/prefHeight}"/>
                </Pane>
            </StackPane>
        """);

        Pane pane = (Pane)((Pane)root.getChildren().get(0)).getChildren().get(0);
        assertEquals(234, pane.getPrefWidth(), 0.001);
        assertEquals(123, pane.getPrefHeight(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_Parent_Property_With_Typed_And_Indexed_Parent_Selector() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       prefHeight="123">
                <Pane prefWidth="234">
                    <Pane prefWidth="${parent[Pane:0]/prefWidth}"
                          prefHeight="${parent[Pane:1]/prefHeight}"/>
                </Pane>
            </Pane>
        """);

        Pane pane = (Pane)((Pane)root.getChildren().get(0)).getChildren().get(0);
        assertEquals(234, pane.getPrefWidth(), 0.001);
        assertEquals(123, pane.getPrefHeight(), 0.001);
    }

    @Test
    public void Bind_Unidirectional_To_Unqualified_Static_Constant() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Pane maxWidth="${Region.USE_PREF_SIZE}"/>
            </Pane>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("Region.USE_PREF_SIZE", ex);
    }

    @Test
    public void Bind_Unidirectional_To_Qualified_Static_Constant() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Pane maxWidth="${javafx.scene.layout.Region.USE_PREF_SIZE}"/>
            </Pane>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("javafx.scene.layout.Region.USE_PREF_SIZE", ex);
    }

    @Test
    public void Bind_To_NotFound_Parent_Member_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Label prefWidth="$parent/notfound"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("notfound", ex);
    }

    @Test
    public void Bind_To_Invalid_Parent_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      prefWidth="123">
                <Label prefWidth="$parent[Button]/prefWidth"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.PARENT_TYPE_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("parent[Button]", ex);
    }

    @Test
    public void Bind_To_Negative_Parent_Index_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Label prefWidth="$parent[-1]/prefWidth"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.PARENT_INDEX_OUT_OF_BOUNDS, ex.getDiagnostic().getCode());
        assertCodeHighlight("parent[-1]", ex);
    }

    @Test
    public void Bind_To_Parent_Index_Out_Of_Bounds_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.compiler.bindings.BindingPathTest.TestPane?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Label prefWidth="${parent[1]/prefWidth}"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.PARENT_INDEX_OUT_OF_BOUNDS, ex.getDiagnostic().getCode());
        assertCodeHighlight("parent[1]", ex);
    }

    @Test
    public void Parent_Of_Non_Node_Is_Correctly_Determined() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.geometry.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" prefWidth="123">
                <rotationAxis>
                    <Point3D x="$parent/prefWidth" y="0" z="0"/>
                </rotationAxis>
            </Pane>
        """);

        assertEquals(123, root.getRotationAxis().getX(), 0.001);
    }

    @Test
    public void Parent_With_Invalid_Identifier_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Label text="${parent/0123}"/>
            </Pane>
        """));

        assertEquals(ErrorCode.EXPECTED_IDENTIFIER, ex.getDiagnostic().getCode());
        assertCodeHighlight("0123", ex);
    }

    @Test
    public void Bind_Parent_With_This_Syntax() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Button graphic="$parent/this"/>
            </Pane>
        """);

        assertSame(root, ((Button)root.getChildren().get(0)).getGraphic());
    }

    @Test
    public void Invalid_Selector_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Pane prefHeight="${foobar/prefWidth}"/>
            </Pane>
        """));

        assertEquals(ErrorCode.UNEXPECTED_EXPRESSION, ex.getDiagnostic().getCode());
        assertCodeHighlight("foobar", ex);
    }

    @Test
    public void Bind_To_Property_With_Self_Selector() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Pane prefWidth="123" prefHeight="${self/prefWidth}"/>
            </Pane>
        """);

        Pane pane = (Pane)root.getChildren().get(0);
        assertEquals(123D, pane.getPrefHeight(), 0.001);
        assertEquals(123D, pane.getPrefWidth(), 0.001);
    }

    @Test
    public void Self_Selector_Cannot_Be_Used_With_SearchLevel() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Pane prefWidth="123" prefHeight="${self[2]/prefWidth}"/>
            </Pane>
        """));

        assertEquals(ErrorCode.UNEXPECTED_EXPRESSION, ex.getDiagnostic().getCode());
        assertCodeHighlight("2", ex);
    }

    @Nested
    public class BeanAndName extends CompilerTestBase {

        @SuppressWarnings("unused")
        public static class IndirectContext {
            public final ObjectProperty<String> stringProp1 = new SimpleObjectProperty<>(this, "doubleProp1");
            public final ObservableValue<String> obsStringValue1 = new SimpleObjectProperty<>(this, "obsStringValue1");
        }

        @SuppressWarnings("unused")
        public static class TestPane extends Pane {
            public final ObjectProperty<IndirectContext> indirect = new SimpleObjectProperty<>(new IndirectContext());
            private final ObjectProperty<ObservableValue<String>> target = new SimpleObjectProperty<>();
            public ObjectProperty<ObservableValue<String>> targetProperty() { return target; }
        }

        @Test
        @SuppressWarnings("unchecked")
        public void Bean_And_Name_Are_Forwarded_To_SourceProperty() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          target="${indirect::stringProp1}"/>
            """);

            var source = (Property<String>)Reflection.getFieldValue(root.target, "observable");
            assertEquals("doubleProp1", source.getName());
            assertSame(root.indirect.get(), source.getBean());
        }

        @Test
        @SuppressWarnings("unchecked")
        public void Bean_And_Name_Are_Forwarded_To_Source_ObservableValue() {
            TestPane root = compileAndRun("""
                <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          target="${indirect::obsStringValue1}"/>
            """);

            var source = (Property<String>)Reflection.getFieldValue(root.target, "observable");
            assertEquals("obsStringValue1", source.getName());
            assertSame(root.indirect.get(), source.getBean());
        }
    }

    @Nested
    public class BindingContext extends CompilerTestBase {

        public static class BindingContextTestPane extends Pane {
            public final Button button = new Button("foo-non-observable");
            public final ObjectProperty<Button> obsButton = new SimpleObjectProperty<>(new Button("foo-observable"));

            public BindingContextTestPane() {
                button.setId("btn-id");
            }
        }

        @Test
        public void Bind_Once_To_Once_Bound_NonObservable_BindingContext() {
            BindingContextTestPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <BindingContextTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                        fx:context="$button">
                    <Pane id="$text"/>
                </BindingContextTestPane>
            """);

            Pane pane = (Pane)root.getChildren().get(0);
            assertEquals("foo-non-observable", pane.getId());
            root.button.setText("baz");
            assertEquals("foo-non-observable", pane.getId());
        }

        @Test
        public void Bind_Once_To_Unidirectional_Bound_NonObservable_BindingContext() {
            BindingContextTestPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <BindingContextTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                        fx:context="${button}">
                    <Pane id="$text"/>
                </BindingContextTestPane>
            """);

            Pane pane = (Pane)root.getChildren().get(0);
            assertEquals("foo-non-observable", pane.getId());
            root.button.setText("baz");
            assertEquals("foo-non-observable", pane.getId());
        }

        @Test
        public void Bind_Unidirectional_To_Once_Bound_NonObservable_BindingContext() {
            BindingContextTestPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <BindingContextTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                        fx:context="$button">
                    <Pane id="${text}"/>
                </BindingContextTestPane>
            """);

            Pane pane = (Pane)root.getChildren().get(0);
            assertEquals("foo-non-observable", pane.getId());
            root.button.setText("baz");
            assertEquals("baz", pane.getId());
        }

        @Test
        public void Bind_Unidirectional_To_Unidirectional_Bound_NonObservable_BindingContext() {
            BindingContextTestPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <BindingContextTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                        fx:context="${button}">
                    <Pane id="${text}"/>
                </BindingContextTestPane>
            """);

            Pane pane = (Pane)root.getChildren().get(0);
            assertEquals("foo-non-observable", pane.getId());
            root.button.setText("baz");
            assertEquals("baz", pane.getId());
        }

        @Test
        public void Bind_Once_To_Once_Bound_Observable_BindingContext() {
            BindingContextTestPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <BindingContextTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                        fx:context="$obsButton">
                    <Pane id="$text"/>
                </BindingContextTestPane>
            """);

            Pane pane = (Pane)root.getChildren().get(0);
            assertEquals("foo-observable", pane.getId());
            root.obsButton.get().setText("baz");
            assertEquals("foo-observable", pane.getId());
            root.obsButton.set(new Button("qux"));
            assertEquals("foo-observable", pane.getId());
        }

        @Test
        public void Bind_Once_To_Unidirectional_Bound_Observable_BindingContext() {
            BindingContextTestPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <BindingContextTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                        fx:context="${obsButton}">
                    <Pane id="$text"/>
                </BindingContextTestPane>
            """);

            Pane pane = (Pane)root.getChildren().get(0);
            assertEquals("foo-observable", pane.getId());
            root.obsButton.get().setText("baz");
            assertEquals("foo-observable", pane.getId());
            root.obsButton.set(new Button("qux"));
            assertEquals("foo-observable", pane.getId());
        }

        @Test
        public void Bind_Unidirectional_To_Once_Bound_Observable_BindingContext() {
            BindingContextTestPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <BindingContextTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                        fx:context="$obsButton">
                    <Pane id="${text}"/>
                </BindingContextTestPane>
            """);

            Pane pane = (Pane)root.getChildren().get(0);
            assertEquals("foo-observable", pane.getId());
            root.obsButton.get().setText("baz");
            assertEquals("baz", pane.getId());
            root.obsButton.set(new Button("qux"));
            assertEquals("baz", pane.getId());
        }

        @Test
        public void Bind_Unidirectional_To_Unidirectional_Bound_Observable_BindingContext() {
            BindingContextTestPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <BindingContextTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                        fx:context="${obsButton}">
                    <Pane id="${text}"/>
                </BindingContextTestPane>
            """);

            Pane pane = (Pane)root.getChildren().get(0);
            assertEquals("foo-observable", pane.getId());
            root.obsButton.get().setText("baz");
            assertEquals("baz", pane.getId());
            root.obsButton.set(new Button("qux"));
            assertEquals("qux", pane.getId());
        }

        @Test
        public void BindingContext_Accepts_Function_Expression() {
            Pane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <BindingContextTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                        fx:context="$String.format('%s', 'foo')">
                    <Pane id="$Integer.toString(length)"/>
                </BindingContextTestPane>
            """);

            Pane pane = (Pane)root.getChildren().get(0);
            assertEquals("3", pane.getId());
        }

        @Test
        public void BindingContext_Does_Not_Accept_Bidirectional_Binding() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <BindingContextTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                        fx:context="#{button}"/>
            """));

            assertEquals(ErrorCode.EXPRESSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("#{button}", ex);
        }

        @Test
        public void BindingContext_Does_Not_Accept_Content_Binding() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <BindingContextTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                        fx:context="${..button}"/>
            """));

            assertEquals(ErrorCode.EXPRESSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("${..button}", ex);
        }

        @Test
        public void BindingContext_Does_Not_Accept_Bidirectional_Content_Binding() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <BindingContextTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                        fx:context="#{..button}"/>
            """));

            assertEquals(ErrorCode.EXPRESSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
            assertCodeHighlight("#{..button}", ex);
        }

        @Test
        public void BindingContext_Accepts_ElementNode() {
            Pane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <?import javafx.scene.control.*?>
                <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <fx:context>
                        <Button text="foo"/>
                    </fx:context>
                    <Pane id="$text"/>
                    <Pane id="$text"/>
                </Pane>
            """);

            Pane pane1 = (Pane)root.getChildren().get(0);
            Pane pane2 = (Pane)root.getChildren().get(1);
            assertEquals("foo", pane1.getId());
            assertEquals("foo", pane2.getId());
        }

        @Test
        public void BindingContext_Cannot_Contain_Multiple_Values() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <?import javafx.scene.control.*?>
                <Pane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <fx:context>
                        <Button/>
                        <Button/>
                    </fx:context>
                </Pane>
            """));

            assertEquals(ErrorCode.PROPERTY_CANNOT_HAVE_MULTIPLE_VALUES, ex.getDiagnostic().getCode());
            assertCodeHighlight("<fx:context>", ex);
        }

        @Test
        public void Root_Selector_Overrides_BindingContext() {
            Pane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <BindingContextTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                        fx:context="$button" id="baz">
                    <Pane id="${id}"/>
                    <Pane id="${root/id}"/>
                </BindingContextTestPane>
            """);

            assertEquals("btn-id", root.getChildren().get(0).getId());
            assertEquals("baz", root.getChildren().get(1).getId());
        }

        @Test
        public void Cannot_Use_ObservableSelector_To_Select_NonObservable_Context() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <?import javafx.scene.control.*?>
                <BindingContextTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                        fx:context="$::button"/>
            """));

            assertEquals(ErrorCode.INVALID_INVARIANT_REFERENCE, ex.getDiagnostic().getCode());
            assertCodeHighlight("button", ex);
        }

        @Test
        public void BindingContext_Can_Be_Selected_With_ObservableSelector() {
            Pane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <BindingContextTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                        fx:context="$::obsButton">
                    <Pane id="${Boolean.toString(isNull)}"/>
                    <Pane id="${Boolean.toString(isNotNull)}"/>
                </BindingContextTestPane>
            """);

            assertEquals("false", root.getChildren().get(0).getId());
            assertEquals("true", root.getChildren().get(1).getId());
        }
    }
}

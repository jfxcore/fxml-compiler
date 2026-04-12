// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.extensions;

import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.jfxcore.markup.resource.ResourceContext;
import org.jfxcore.markup.resource.ResourceContextProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import javafx.beans.NamedArg;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unused")
@ExtendWith(TestExtension.class)
public class StaticResourceExtensionTest extends CompilerTestBase {

    private static ResourceBundle bundle(Object[][] contents, Locale locale) {
        return new ListResourceBundle() {
            @Override protected Object[][] getContents() { return contents; }
            @Override public Locale getLocale() { return locale; }
        };
    }

    private static ResourceContext resourceContext;

    public static class LocalizedLabel extends Label implements ResourceContextProvider {
        private final DoubleProperty amount = new SimpleDoubleProperty(1234.5);
        public final DoubleProperty amountProperty() { return amount; }
        public final double getAmount() { return amount.get(); }
        public final void setAmount(double value) { amount.set(value); }

        @Override
        public ResourceContext getResourceContext() {
            return resourceContext;
        }
    }

    public static class SetterTarget extends Pane implements ResourceContextProvider {
        private boolean active;
        private Orientation orientation;

        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }

        public Orientation getOrientation() { return orientation; }
        public void setOrientation(Orientation orientation) { this.orientation = orientation; }

        @Override
        public ResourceContext getResourceContext() {
            return resourceContext;
        }
    }

    public static class ConstructorTarget extends Pane implements ResourceContextProvider {
        final int count;
        final boolean active;

        public ConstructorTarget(@NamedArg("count") int count, @NamedArg("active") boolean active) {
            this.count = count;
            this.active = active;
        }

        @Override
        public ResourceContext getResourceContext() {
            return resourceContext;
        }
    }

    public static class CollectionTarget extends Pane implements ResourceContextProvider {
        private final ObservableList<String> items = FXCollections.observableArrayList();
        public ObservableList<String> getItems() { return items; }

        @Override
        public ResourceContext getResourceContext() {
            return resourceContext;
        }
    }

    public static class NonStringTarget extends Pane implements ResourceContextProvider {
        private final ObjectProperty<CharSequence> charSequence = new SimpleObjectProperty<>();
        public final ObjectProperty<CharSequence> charSequenceProperty() { return charSequence; }
        public final CharSequence getCharSequence() { return charSequence.get(); }

        private final ObjectProperty<Object> objectValue = new SimpleObjectProperty<>();
        public final ObjectProperty<Object> objectValueProperty() { return objectValue; }
        public final Object getObjectValue() { return objectValue.get(); }

        @Override
        public ResourceContext getResourceContext() {
            return resourceContext;
        }
    }

    @Test
    public void String_Is_Assigned_Correctly() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "greeting", "Hello World" }
            }, Locale.getDefault()));

        LocalizedLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <LocalizedLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            text="{StaticResource greeting}"/>
        """);

        assertEquals("Hello World", root.getText());
    }

    @Test
    public void Prefix_Syntax_Is_Assigned_Correctly() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "greeting", "Hello World" }
            }, Locale.getDefault()));

        LocalizedLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <?prefix % = StaticResource?>
            <LocalizedLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            text="% greeting"/>
        """);

        assertEquals("Hello World", root.getText());
    }

    @Test
    public void Fully_Qualified_Prefix_Syntax_Is_Assigned_Correctly() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "greeting", "Hello World" }
            }, Locale.getDefault()));

        LocalizedLabel root = compileAndRun("""
            <?prefix % = org.jfxcore.markup.resource.StaticResource?>
            <LocalizedLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            text="%greeting"/>
        """);

        assertEquals("Hello World", root.getText());
    }

    @Test
    public void Builtin_Prefix_Syntax_Is_Assigned_Correctly_Without_Imports_Or_Declarations() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "greeting", "Hello World" }
            }, Locale.getDefault()));

        LocalizedLabel root = compileAndRun("""
            <LocalizedLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            text="% greeting"/>
        """);

        assertEquals("Hello World", root.getText());
    }

    @Test
    public void String_Is_Formatted_Correctly() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "message", "Hello {0}, amount = {1,number,#,##0.0}" }
            }, Locale.US));

        LocalizedLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <LocalizedLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            text="{StaticResource message; formatArguments=World, $arg}">
                <fx:define>
                    <Double fx:id="arg">1234.5</Double>
                </fx:define>
            </LocalizedLabel>
        """);

        assertEquals("Hello World, amount = 1,234.5", root.getText());
    }

    @Test
    public void String_Is_Formatted_With_Element_Notation() {
        resourceContext = ResourceContext.ofResourceBundle(
                bundle(new Object[][] {
                        { "message", "Hello {0}, amount = {1,number,#,##0.0}" }
                }, Locale.US));

        LocalizedLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <LocalizedLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <text>
                    <StaticResource key="message">
                        <formatArguments>
                            World
                            <Double>1234.5</Double>
                        </formatArguments>
                    </StaticResource>
                </text>
            </LocalizedLabel>
        """);

        assertEquals("Hello World, amount = 1,234.5", root.getText());
    }

    @Test
    public void Formatted_String_Is_Assigned_Correctly_To_NonString_Targets() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "message", "Hello {0}, amount = {1,number,#,##0.0}" }
            }, Locale.US));

        NonStringTarget root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <NonStringTarget xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                             charSequence="{StaticResource message; formatArguments=World, $arg}"
                             objectValue="{StaticResource message; formatArguments=World, $arg}">
                <fx:define>
                    <Double fx:id="arg">1234.5</Double>
                </fx:define>
            </NonStringTarget>
        """);

        assertEquals("Hello World, amount = 1,234.5", root.getCharSequence().toString());
        assertEquals("Hello World, amount = 1,234.5", root.getObjectValue());
    }

    @Test
    public void ObservableValue_Argument_Is_Not_Supported() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <LocalizedLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            text="{StaticResource message; formatArguments=World, ${amount}}"/>
        """));

        assertEquals("StaticResource does not support observable format arguments", ex.getMessage());
    }

    @Test
    public void String_Does_Not_Update_When_Formatting_Locale_Changes() {
        var locale = new SimpleObjectProperty<>(Locale.US);

        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "message", "Amount = {0,number,#,##0.0}" }
            }, Locale.US), locale);

        LocalizedLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <LocalizedLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            text="{StaticResource message; formatArguments=$arg}">
                <fx:define>
                    <Double fx:id="arg">1234.5</Double>
                </fx:define>
            </LocalizedLabel>
        """);

        assertEquals("Amount = 1,234.5", root.getText());
        locale.set(Locale.GERMAN);
        assertEquals("Amount = 1,234.5", root.getText());
    }

    @Test
    public void Boolean_Is_Assigned_Correctly_To_Setter_Property() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "active", "true" }
            }, Locale.getDefault()));

        SetterTarget root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <SetterTarget xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          active="{StaticResource active}"/>
        """);

        assertTrue(root.isActive());
    }

    @Test
    public void Enum_Is_Assigned_Correctly_To_Setter_Property() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "orientation", Orientation.VERTICAL }
            }, Locale.getDefault()));

        SetterTarget root = compileAndRun("""
            <?import javafx.geometry.Orientation?>
            <?import org.jfxcore.markup.resource.*?>
            <SetterTarget xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          orientation="{StaticResource orientation}"/>
        """);

        assertEquals(Orientation.VERTICAL, root.getOrientation());
    }

    @Test
    public void Constructor_Argument_Is_Assigned_Correctly() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "count", 42 },
                { "active", true }
            }, Locale.getDefault()));

        LocalizedLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <LocalizedLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <graphic>
                    <ConstructorTarget count="{StaticResource count}" active="{StaticResource active}"/>
                </graphic>
            </LocalizedLabel>
        """);

        var target = (ConstructorTarget)root.getGraphic();
        assertEquals(42, target.count);
        assertTrue(target.active);
    }

    @Test
    public void Collection_Item_Is_Added_Correctly() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "item1", "First" },
                { "item2", "Second" }
            }, Locale.getDefault()));

        CollectionTarget root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <CollectionTarget xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                              items="{StaticResource item1}"/>
        """);

        assertEquals(java.util.List.of("First"), root.getItems());
    }

    @Test
    public void Collection_Items_Are_Added_Correctly() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "item1", "First" },
                { "item2", "Second" }
            }, Locale.getDefault()));

        CollectionTarget root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <CollectionTarget xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <items>
                    <StaticResource key="item1"/>
                    <StaticResource key="item2"/>
                </items>
            </CollectionTarget>
        """);

        assertEquals(java.util.List.of("First", "Second"), root.getItems());
    }

    @Test
    public void Nonexistent_Key_Throws_RuntimeException() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "greeting", "Hello World" }
            }, Locale.getDefault()));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <LocalizedLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            text="{StaticResource<String> doesNotExist}"/>
        """));

        assertTrue(ex.getMessage().startsWith("Can't find resource for bundle"));
    }
}

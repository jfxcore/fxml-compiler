// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.extensions;

import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.jfxcore.markup.resource.ResourceContext;
import org.jfxcore.markup.resource.ResourceContextProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Orientation;
import javafx.scene.control.Label;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unused")
@ExtendWith(TestExtension.class)
public class DynamicResourceExtensionTest extends CompilerTestBase {

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

        private final IntegerProperty count = new SimpleIntegerProperty();
        public final IntegerProperty countProperty() { return count; }
        public final int getCount() { return count.get(); }
        public final void setCount(int value) { count.set(value); }

        private final BooleanProperty active = new SimpleBooleanProperty();
        public final BooleanProperty activeProperty() { return active; }
        public final boolean isActive() { return active.get(); }
        public final void setActive(boolean value) { active.set(value); }

        private final ObjectProperty<Orientation> orientation = new SimpleObjectProperty<>();
        public final ObjectProperty<Orientation> orientationProperty() { return orientation; }
        public final Orientation getOrientation() { return orientation.get(); }
        public final void setOrientation(Orientation value) { orientation.set(value); }

        @Override
        public ResourceContext getResourceContext() {
            return resourceContext;
        }
    }

    public static class NonStringTarget extends Label implements ResourceContextProvider {
        private final DoubleProperty amount = new SimpleDoubleProperty(1234.5);
        public final DoubleProperty amountProperty() { return amount; }
        public final double getAmount() { return amount.get(); }
        public final void setAmount(double value) { amount.set(value); }

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
                            text="{DynamicResource greeting}"/>
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
                            text="{DynamicResource message; formatArguments=World, $arg}">
                <fx:define>
                    <Double fx:id="arg">1234.5</Double>
                </fx:define>
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
                             charSequence="{DynamicResource message; formatArguments=World, $arg}"
                             objectValue="{DynamicResource message; formatArguments=World, $arg}">
                 <fx:define>
                    <Double fx:id="arg">1234.5</Double>
                </fx:define>
            </NonStringTarget>
        """);

        assertEquals("Hello World, amount = 1,234.5", root.getCharSequence().toString());
        assertEquals("Hello World, amount = 1,234.5", root.getObjectValue());
    }

    @Test
    public void String_Updates_When_Observable_Format_Argument_Changes() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "message", "Hello {0}, Amount = {1,number,#,##0.0}" }
            }, Locale.US));

        LocalizedLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <LocalizedLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            text="{DynamicResource message; formatArguments=World, ${amount}}"/>
        """);

        assertEquals("Hello World, Amount = 1,234.5", root.getText());
        root.setAmount(42.0);
        assertEquals("Hello World, Amount = 42.0", root.getText());
    }

    @Test
    public void Formatted_String_Updates_When_Observable_Format_Argument_Changes_For_NonString_Targets() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "message", "Hello {0}, Amount = {1,number,#,##0.0}" }
            }, Locale.US));

        NonStringTarget root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <NonStringTarget xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                             charSequence="{DynamicResource message; formatArguments=World, ${amount}}"
                             objectValue="{DynamicResource message; formatArguments=World, ${amount}}"/>
        """);

        assertEquals("Hello World, Amount = 1,234.5", root.getCharSequence().toString());
        assertEquals("Hello World, Amount = 1,234.5", root.getObjectValue());

        root.setAmount(42.0);

        assertEquals("Hello World, Amount = 42.0", root.getCharSequence().toString());
        assertEquals("Hello World, Amount = 42.0", root.getObjectValue());
    }

    @Test
    public void String_Updates_When_Formatting_Locale_Changes() {
        var locale = new SimpleObjectProperty<>(Locale.US);

        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "message", "Amount = {0,number,#,##0.0}" }
            }, Locale.US), locale);

        LocalizedLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <LocalizedLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            text="{DynamicResource message; formatArguments=$arg}">
                <fx:define>
                    <Double fx:id="arg">1234.5</Double>
                </fx:define>
            </LocalizedLabel>
        """);

        assertEquals("Amount = 1,234.5", root.getText());
        locale.set(Locale.GERMAN);
        assertEquals("Amount = 1.234,5", root.getText());
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
                            text="{DynamicResource doesNotExist}"/>
        """));

        assertTrue(ex.getMessage().startsWith("Can't find resource for bundle"));
    }

    @Test
    public void Int_Is_Assigned_Correctly_From_Int_Resource() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "count", 42 }
            }, Locale.getDefault()));

        LocalizedLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <LocalizedLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            count="{DynamicResource count}"/>
        """);

        assertEquals(42, root.getCount());
    }

    @Test
    public void Int_Is_Assigned_Correctly_From_String_Resource() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "count", "42" }
            }, Locale.getDefault()));

        LocalizedLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <LocalizedLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            count="{DynamicResource count}"/>
        """);

        assertEquals(42, root.getCount());
    }

    @Test
    public void Boolean_Is_Assigned_Correctly_From_Boolean_Resource() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "active", true }
            }, Locale.getDefault()));

        LocalizedLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <LocalizedLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            active="{DynamicResource active}"/>
        """);

        assertTrue(root.isActive());
    }

    @Test
    public void Boolean_Is_Assigned_Correctly_From_String_Resource() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "active", "true" }
            }, Locale.getDefault()));

        LocalizedLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <LocalizedLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            active="{DynamicResource active}"/>
        """);

        assertTrue(root.isActive());
    }

    @Test
    public void Enum_Is_Assigned_Correctly_From_String_Resource() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "orientation", "VERTICAL" }
            }, Locale.getDefault()));

        LocalizedLabel root = compileAndRun("""
            <?import javafx.geometry.Orientation?>
            <?import org.jfxcore.markup.resource.*?>
            <LocalizedLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            orientation="{DynamicResource orientation}"/>
        """);

        assertEquals(Orientation.VERTICAL, root.getOrientation());
    }

    @Test
    public void Object_Valued_Enum_Resource_Is_Assigned_Correctly() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "orientation", Orientation.HORIZONTAL }
            }, Locale.getDefault()));

        LocalizedLabel root = compileAndRun("""
            <?import javafx.geometry.Orientation?>
            <?import org.jfxcore.markup.resource.*?>
            <LocalizedLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            orientation="{DynamicResource orientation}"/>
        """);

        assertEquals(Orientation.HORIZONTAL, root.getOrientation());
    }

    @Test
    public void Invalid_NonString_Value_Throws_RuntimeException() {
        resourceContext = ResourceContext.ofResourceBundle(
            bundle(new Object[][] {
                { "count", "not-a-number" }
            }, Locale.getDefault()));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <LocalizedLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                            count="{DynamicResource count}"/>
        """));

        assertTrue(ex.getMessage().startsWith("Unexpected value for resource key 'count'"));
        assertTrue(ex.getMessage().contains("actual = java.lang.String"));
    }
}

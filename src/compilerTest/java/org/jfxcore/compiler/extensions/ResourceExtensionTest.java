// Copyright (c) 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.extensions;

import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.junit.jupiter.api.Test;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Label;
import java.net.URI;
import java.net.URL;
import java.util.Objects;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

public class ResourceExtensionTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class TestLabel extends Label {
        private final ObjectProperty<URL> url = new SimpleObjectProperty<>();
        public final ObjectProperty<URL> urlProperty() { return url; }
        public final URL getUrl() { return url.get(); }

        private final ObjectProperty<URI> uri = new SimpleObjectProperty<>();
        public final ObjectProperty<URI> uriProperty() { return uri; }
        public final URI getUri() { return uri.get(); }
    }

    @Test
    public void Resource_With_Relative_Location_Is_Evaluated_Correctly() throws Exception {
        TestLabel root = compileAndRun("""
            <?import org.jfxcore.markup.*?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="{Resource image.jpg}"
                       url="{Resource image.jpg}"
                       uri="{Resource image.jpg}"/>
        """);

        URL url = Objects.requireNonNull(root.getClass().getResource("image.jpg"));
        assertTrue(root.getText().endsWith("org/jfxcore/compiler/classes/image.jpg"));
        assertEquals(url, root.getUrl());
        assertEquals(url.toURI(), root.getUri());
    }

    @Test
    public void Resource_With_Root_Location_Is_Evaluated_Correctly() throws Exception {
        TestLabel root = compileAndRun("""
            <?import org.jfxcore.markup.*?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="{Resource /org/jfxcore/compiler/classes/image.jpg}"
                       url="{Resource /org/jfxcore/compiler/classes/image.jpg}"
                       uri="{Resource /org/jfxcore/compiler/classes/image.jpg}"/>
        """);

        URL url = Objects.requireNonNull(root.getClass().getResource("image.jpg"));
        assertTrue(root.getText().endsWith("org/jfxcore/compiler/classes/image.jpg"));
        assertEquals(url, root.getUrl());
        assertEquals(url.toURI(), root.getUri());
    }

    @Test
    public void Resource_With_Quoted_Path_Is_Evaluated_Correctly() throws Exception {
        TestLabel root = compileAndRun("""
            <?import org.jfxcore.markup.*?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="{Resource '/org/jfxcore/compiler/classes/image with   spaces.jpg'}"
                       url="{Resource '/org/jfxcore/compiler/classes/image with   spaces.jpg'}"
                       uri="{Resource '/org/jfxcore/compiler/classes/image with   spaces.jpg'}"/>
        """);

        URL url = Objects.requireNonNull(root.getClass().getResource("image with   spaces.jpg"));
        assertTrue(root.getText().endsWith("org/jfxcore/compiler/classes/image%20with%20%20%20spaces.jpg"));
        assertEquals(url, root.getUrl());
        assertEquals(url.toURI(), root.getUri());
    }

    @Test
    public void Resource_Extension_Works_In_ValueOf_Expression() {
        Label root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.markup.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <text>
                    <String fx:value="{Resource image.jpg}"/>
                </text>
            </Label>
        """);

        assertTrue(root.getText().endsWith("org/jfxcore/compiler/classes/image.jpg"));
    }

    @Test
    public void Resource_Can_Be_Added_To_String_Collection() {
        Label root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.markup.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <stylesheets>
                    <Resource>image.jpg</Resource>
                </stylesheets>
            </Label>
        """);

        assertTrue(root.getStylesheets().stream().anyMatch(s -> s.endsWith("org/jfxcore/compiler/classes/image.jpg")));
    }

    @Test
    public void Resource_Cannot_Be_Assigned_To_Incompatible_Property() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.markup.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   prefWidth="{Resource image.jpg}"/>
        """));

        assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("{Resource image.jpg}", ex);
    }

    @Test
    public void Unsuitable_Parameter_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.markup.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   text="{Resource ${foo}}"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo", ex);
    }

    @Test
    public void Nonexistent_Resource_Throws_RuntimeException() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.markup.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   text="{Resource foobarbaz.jpg}"/>
        """));

        assertTrue(ex.getMessage().startsWith("Resource not found"));
    }
}

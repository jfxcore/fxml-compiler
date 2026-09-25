// Copyright (c) 2025, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.extensions;

import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Label;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class ClassPathResourceExtensionTest extends CompilerTestBase {

    private static final URL CUSTOM_RESOURCE = Objects.requireNonNull(
        ClassPathResourceExtensionTest.class.getResource("/org/jfxcore/compiler/image with   spaces.jpg"));

    @SuppressWarnings("unused")
    public static class TestLabel extends Label {
        public final ResourceClassLoader resourceLoader = new ResourceClassLoader();
        public final ClassLoader nullClassLoader = null;

        private final ObjectProperty<URL> url = new SimpleObjectProperty<>();
        public final ObjectProperty<URL> urlProperty() { return url; }
        public final URL getUrl() { return url.get(); }

        private final ObjectProperty<URI> uri = new SimpleObjectProperty<>();
        public final ObjectProperty<URI> uriProperty() { return uri; }
        public final URI getUri() { return uri.get(); }
    }

    public static class ResourceClassLoader extends ClassLoader {
        private final List<String> requestedNames = new ArrayList<>();

        public ResourceClassLoader() {
            super(null);
        }

        @Override
        public URL getResource(String name) {
            requestedNames.add(name);
            return switch (name) {
                case "custom/image.jpg", "image.jpg" -> CUSTOM_RESOURCE;
                default -> null;
            };
        }
    }

    @Test
    public void Resource_With_Relative_Location_Is_Evaluated_Correctly() throws Exception {
        TestLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="{ClassPathResource image.jpg}"
                       url="{ClassPathResource image.jpg}"
                       uri="{ClassPathResource image.jpg}"/>
        """);

        URL url = Objects.requireNonNull(root.getClass().getResource("image.jpg"));
        assertTrue(root.getText().endsWith("org/jfxcore/compiler/image.jpg"));
        assertEquals(url, root.getUrl());
        assertEquals(url.toURI(), root.getUri());
    }

    @Test
    public void Embedded_Resource_Takes_Precedence_Over_Relative_Fallback() {
        TestLabel root = compileAndRun("""
            <?resource image.jpg:embedded?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="@image.jpg"/>
        """);

        assertTrue(root.getText().endsWith("$image.jpg"));
    }

    @Test
    public void Absolute_Resource_Name_Skips_Embedded_Lookup() {
        TestLabel root = compileAndRun("""
            <?resource image.jpg:embedded?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="@/org/jfxcore/compiler/image.jpg"/>
        """);

        assertTrue(root.getText().endsWith("org/jfxcore/compiler/image.jpg"));
        assertFalse(root.getText().endsWith("$image.jpg"));
    }

    @Test
    public void Prefix_Syntax_With_Relative_Location_Is_Evaluated_Correctly() throws Exception {
        TestLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <?prefix @ = ClassPathResource?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="@ image.jpg"
                       url="@ image.jpg"
                       uri="@ image.jpg"/>
        """);

        URL url = Objects.requireNonNull(root.getClass().getResource("image.jpg"));
        assertTrue(root.getText().endsWith("org/jfxcore/compiler/image.jpg"));
        assertEquals(url, root.getUrl());
        assertEquals(url.toURI(), root.getUri());
    }

    @Test
    public void Builtin_Prefix_Syntax_With_Relative_Location_Is_Evaluated_Without_Declaration() throws Exception {
        TestLabel root = compileAndRun("""
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="@image.jpg"
                       url="@ image.jpg"
                       uri="@image.jpg "/>
        """);

        URL url = Objects.requireNonNull(root.getClass().getResource("image.jpg"));
        assertTrue(root.getText().endsWith("org/jfxcore/compiler/image.jpg"));
        assertEquals(url, root.getUrl());
        assertEquals(url.toURI(), root.getUri());
    }

    @Test
    public void Resource_With_Root_Location_Is_Evaluated_Correctly() throws Exception {
        TestLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="{ClassPathResource /org/jfxcore/compiler/image.jpg}"
                       url="{ClassPathResource /org/jfxcore/compiler/image.jpg}"
                       uri="{ClassPathResource /org/jfxcore/compiler/image.jpg}"/>
        """);

        URL url = Objects.requireNonNull(root.getClass().getResource("image.jpg"));
        assertTrue(root.getText().endsWith("org/jfxcore/compiler/image.jpg"));
        assertEquals(url, root.getUrl());
        assertEquals(url.toURI(), root.getUri());
    }

    @Test
    public void Explicit_ClassLoader_Resolves_All_Supported_Targets() throws Exception {
        TestLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="{ClassPathResource custom/image.jpg; classLoader=$resourceLoader}"
                       url="{ClassPathResource custom/image.jpg; classLoader=$resourceLoader}"
                       uri="{ClassPathResource custom/image.jpg; classLoader=$resourceLoader}"/>
        """);

        assertEquals(CUSTOM_RESOURCE.toExternalForm(), root.getText());
        assertEquals(CUSTOM_RESOURCE, root.getUrl());
        assertEquals(CUSTOM_RESOURCE.toURI(), root.getUri());
        assertEquals(List.of("custom/image.jpg", "custom/image.jpg", "custom/image.jpg"),
            root.resourceLoader.requestedNames);
    }

    @Test
    public void Explicit_ClassLoader_Accepts_Leading_Slash() {
        TestLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       url="{ClassPathResource /custom/image.jpg; classLoader=$resourceLoader}"/>
        """);

        assertEquals(CUSTOM_RESOURCE, root.getUrl());
        assertEquals(List.of("custom/image.jpg"), root.resourceLoader.requestedNames);
    }

    @Test
    public void Explicit_ClassLoader_Takes_Precedence_Over_Embedded_And_Root_Resources() {
        TestLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <?resource image.jpg:embedded?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       url="{ClassPathResource image.jpg; classLoader=$resourceLoader}"/>
        """);

        assertNotNull(root.getClass().getResource("image.jpg"));
        assertEquals(CUSTOM_RESOURCE, root.getUrl());
        assertEquals(List.of("image.jpg"), root.resourceLoader.requestedNames);
    }

    @Test
    public void Explicit_ClassLoader_Does_Not_Fall_Back_To_Default_Lookup() {
        assertNotNull(ClassPathResourceExtensionTest.class.getResource("/org/jfxcore/compiler/bundle.properties"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <?resource bundle.properties:embedded?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       url="{ClassPathResource bundle.properties; classLoader=$resourceLoader}"/>
        """));

        assertEquals("Resource not found: bundle.properties", ex.getMessage());
    }

    @Test
    public void Null_ClassLoader_Resolves_Relative_And_Absolute_Root_Resources() {
        TestLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="{ClassPathResource image.jpg; classLoader=$nullClassLoader}"
                       url="{ClassPathResource /org/jfxcore/compiler/image.jpg; classLoader=$nullClassLoader}"/>
        """);

        URL expected = Objects.requireNonNull(root.getClass().getResource("image.jpg"));
        assertEquals(expected.toExternalForm(), root.getText());
        assertEquals(expected, root.getUrl());
    }

    @Test
    public void Null_ClassLoader_Preserves_Embedded_Resource_Precedence() {
        TestLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <?resource image.jpg:embedded?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       url="{ClassPathResource image.jpg; classLoader=$nullClassLoader}"/>
        """);

        assertNotNull(root.getUrl());
        assertTrue(root.getUrl().getPath().endsWith("$image.jpg"));
        assertNotEquals(root.getClass().getResource("image.jpg"), root.getUrl());
    }

    @Test
    public void Absolute_Resource_Uses_Root_Class_With_Different_Thread_Context_ClassLoader() throws Exception {
        Class<TestLabel> markupClass = compile("""
            <?import org.jfxcore.markup.resource.*?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       url="{ClassPathResource /org/jfxcore/compiler/image.jpg}"/>
        """);

        var constructor = markupClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        var contextLoader = new ResourceClassLoader();
        Thread thread = Thread.currentThread();
        ClassLoader previousLoader = thread.getContextClassLoader();

        try {
            thread.setContextClassLoader(contextLoader);
            TestLabel root = constructor.newInstance();

            URL expected = Objects.requireNonNull(markupClass.getResource("image.jpg"));
            assertEquals(expected, root.getUrl());
            assertTrue(contextLoader.requestedNames.isEmpty());
        } finally {
            thread.setContextClassLoader(previousLoader);
        }
    }

    @Test
    public void Thread_Context_ClassLoader_Can_Be_Passed_Explicitly() throws Exception {
        Class<TestLabel> markupClass = compile("""
            <?import org.jfxcore.markup.resource.*?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       url="{ClassPathResource /custom/image.jpg;
                             classLoader=$Thread.currentThread.contextClassLoader}"/>
        """);

        var constructor = markupClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        var contextLoader = new ResourceClassLoader();
        Thread thread = Thread.currentThread();
        ClassLoader previousLoader = thread.getContextClassLoader();

        try {
            thread.setContextClassLoader(contextLoader);
            TestLabel root = constructor.newInstance();

            assertEquals(CUSTOM_RESOURCE, root.getUrl());
            assertEquals(List.of("custom/image.jpg"), contextLoader.requestedNames);
            assertTrue(root.resourceLoader.requestedNames.isEmpty());
        } finally {
            thread.setContextClassLoader(previousLoader);
        }
    }

    @Test
    public void Resource_With_Quoted_Path_Is_Evaluated_Correctly() throws Exception {
        TestLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="{ClassPathResource '/org/jfxcore/compiler/image with   spaces.jpg'}"
                       url="{ClassPathResource '/org/jfxcore/compiler/image with   spaces.jpg'}"
                       uri="{ClassPathResource '/org/jfxcore/compiler/image with   spaces.jpg'}"/>
        """);

        URL url = Objects.requireNonNull(root.getClass().getResource("image with   spaces.jpg"));
        assertTrue(root.getText().endsWith("org/jfxcore/compiler/image%20with%20%20%20spaces.jpg"));
        assertEquals(url, root.getUrl());
        assertEquals(url.toURI(), root.getUri());
    }

    @Test
    public void Resource_Extension_Works_In_ValueOf_Expression() {
        Label root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.markup.resource.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <text>
                    <String fx:value="{ClassPathResource image.jpg}"/>
                </text>
            </Label>
        """);

        assertTrue(root.getText().endsWith("org/jfxcore/compiler/image.jpg"));
    }

    @Test
    public void Resource_Can_Be_Added_To_String_Collection() {
        Label root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.markup.resource.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <stylesheets>
                    <ClassPathResource>image.jpg</ClassPathResource>
                </stylesheets>
            </Label>
        """);

        assertTrue(root.getStylesheets().stream().anyMatch(s -> s.endsWith("org/jfxcore/compiler/image.jpg")));
    }

    @Test
    public void Multiple_Resources_Can_Be_Added_With_Attribute_Sequence_Syntax() {
        Label root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   stylesheets="@image.jpg, @'image with   spaces.jpg'"/>
        """);

        assertEquals(2, root.getStylesheets().size());
        assertTrue(root.getStylesheets().get(0).endsWith("org/jfxcore/compiler/image.jpg"));
        assertTrue(root.getStylesheets().get(1).endsWith(
            "org/jfxcore/compiler/image%20with%20%20%20spaces.jpg"));
    }

    @Test
    public void Literal_Then_Resource_Stylesheets_Are_Item_Local() {
        Label root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   stylesheets="plain.css, @image.jpg"/>
        """);

        assertEquals("plain.css", root.getStylesheets().get(0));
        assertTrue(root.getStylesheets().get(1).endsWith("org/jfxcore/compiler/image.jpg"));
    }

    @Test
    public void Resource_Then_Literal_Stylesheets_Are_Item_Local() {
        Label root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   stylesheets="@image.jpg, plain.css"/>
        """);

        assertTrue(root.getStylesheets().get(0).endsWith("org/jfxcore/compiler/image.jpg"));
        assertEquals("plain.css", root.getStylesheets().get(1));
    }

    @Test
    public void Resource_Cannot_Be_Assigned_To_Incompatible_Property() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.markup.resource.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   prefWidth="{ClassPathResource image.jpg}"/>
        """));

        assertEquals(ErrorCode.MARKUP_EXTENSION_NOT_APPLICABLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("{ClassPathResource image.jpg}", ex);
    }

    @Test
    public void Unsuitable_Parameter_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.markup.resource.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   text="{ClassPathResource ${foo}}"/>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("foo", ex);
    }

    @Test
    public void Nonexistent_Resource_Throws_RuntimeException() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <?import org.jfxcore.markup.resource.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   text="{ClassPathResource foobarbaz.jpg}"/>
        """));

        assertTrue(ex.getMessage().startsWith("Resource not found"));
    }
}


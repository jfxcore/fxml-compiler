// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Label;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.CRC32;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestExtension.class)
public class EmbeddedResourceTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class TestLabel extends Label {
        private final ObjectProperty<URL> url = new SimpleObjectProperty<>();
        private final ObjectProperty<URI> uri = new SimpleObjectProperty<>();
        private final ObjectProperty<CharSequence> charSequence = new SimpleObjectProperty<>();
        private final ObjectProperty<Object> object = new SimpleObjectProperty<>();

        public final ObjectProperty<URL> urlProperty() { return url; }
        public final URL getUrl() { return url.get(); }

        public final ObjectProperty<URI> uriProperty() { return uri; }
        public final URI getUri() { return uri.get(); }

        public final ObjectProperty<CharSequence> charSequenceProperty() { return charSequence; }
        public final CharSequence getCharSequence() { return charSequence.get(); }

        public final ObjectProperty<Object> objectProperty() { return object; }
        public final Object getObject() { return object.get(); }
    }

    @Test
    public void Unused_Declaration_Is_Materialized_Eagerly() throws Exception {
        Class<TestLabel> compiledClass = compile("""
            <?resource unused.txt:unused?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"/>
        """);

        URL resource = getMaterializedResource(compiledClass, "unused.txt");
        assertNotNull(resource);
        assertEquals("unused", read(resource));
    }

    @Test
    public void Every_Declaration_Is_Materialized_Exactly_Once() throws Exception {
        Class<TestLabel> compiledClass = compile("""
            <?resource first.txt:first?>
            <?resource second.txt:second?>
            <?resource third.txt:third?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"/>
        """);

        for (String name : new String[] {"first.txt", "second.txt", "third.txt"}) {
            URL resource = getMaterializedResource(compiledClass, name);
            assertNotNull(resource);
            assertEquals(name.substring(0, name.indexOf('.')), read(resource));
        }
    }

    @Test
    public void ClassPathResource_Resolves_All_Supported_Targets() throws Exception {
        TestLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <?resource greeting.txt text/plain;charset=windows-1252:
                Grüße
            ?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="{ClassPathResource greeting.txt}"
                       uri="{ClassPathResource greeting.txt}"
                       url="{ClassPathResource greeting.txt}"
                       charSequence="{ClassPathResource greeting.txt}"
                       object="{ClassPathResource greeting.txt}"/>
        """);

        assertEquals(root.getUrl().toExternalForm(), root.getText());
        assertEquals(root.getUrl().toURI(), root.getUri());
        assertEquals(root.getText(), root.getCharSequence());
        assertEquals(root.getText(), root.getObject());
        assertTrue(root.getUrl().getPath().endsWith("$greeting.txt"));

        try (InputStream stream = root.getUrl().openStream()) {
            assertArrayEquals("Grüße".getBytes(Charset.forName("windows-1252")), stream.readAllBytes());
        }
    }

    @Test
    public void Shorthand_ClassPathResource_Resolves_Embedded_Resource() throws Exception {
        TestLabel root = compileAndRun("""
            <?resource styles.css text/css:.root {}?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="@styles.css"/>
        """);

        assertTrue(root.getText().endsWith("$styles.css"));
        assertEquals(".root {}", read(URI.create(root.getText()).toURL()));
    }

    @Test
    public void Shorthand_ClassPathResource_Matches_Exact_Interior_Whitespace() throws Exception {
        TestLabel root = compileAndRun("""
            <?resource "my    styles . css":resource content?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="@    my    styles . css  "/>
        """);

        assertEquals("resource content", read(URI.create(root.getText()).toURL()));
    }

    @Test
    public void Shorthand_ClassPathResource_Rejects_Differently_Spaced_Embedded_Name() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> compileAndRun("""
            <?resource "my    styles . css":resource content?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="@my styles . css"/>
        """));

        assertTrue(exception.getMessage().startsWith("Resource not found"));
    }

    @Test
    public void Embedded_Resource_Wins_Over_An_Ordinary_Package_Resource() throws Exception {
        TestLabel root = compileAndRun("""
            <?resource image.jpg:embedded?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       url="@image.jpg"/>
        """);

        assertTrue(root.getUrl().getPath().endsWith("$image.jpg"));
        assertEquals("embedded", read(root.getUrl()));
    }

    @Test
    public void Resource_With_Spaces_Uses_Safe_Encoding() throws Exception {
        TestLabel root = compileAndRun("""
            <?import org.jfxcore.markup.resource.*?>
            <?resource "dark theme.xyz" text/css:.root {}?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="{ClassPathResource 'dark theme.xyz'}"/>
        """);

        assertTrue(root.getText().endsWith("$dark+theme.xyz"));
        assertEquals(".root {}", read(URI.create(root.getText()).toURL()));
    }

    @Test
    public void Embedded_Resource_Url_Keeps_The_Declaring_Package_As_Its_Base() throws Exception {
        TestLabel root = compileAndRun("""
            <?resource styles.css text/css:.root {}?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       url="@styles.css"/>
        """);

        assertTrue(root.getUrl().toURI().resolve("images/background.png").getPath().endsWith(
            "/org/jfxcore/compiler/images/background.png"));
    }

    @Test
    public void Duplicate_Resource_Declaration_Is_Rejected() {
        MarkupException exception = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?resource value.txt:42?>
            <?resource Value.txt:43?>
            <TestLabel xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"/>
        """));

        assertEquals(ErrorCode.DUPLICATE_RESOURCE_DECLARATION, exception.getDiagnostic().getCode());
        assertEquals("Resource 'Value.txt' conflicts with declaration at 2:16", exception.getMessage());
        assertCodeHighlight("Value.txt", exception);
    }

    private URL getMaterializedResource(Class<?> compiledClass, String logicalName) {
        String documentName = compiledClass.getSimpleName();
        var crc = new CRC32();
        crc.update(documentName.getBytes(StandardCharsets.UTF_8));
        crc.update(logicalName.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        String resourceName = documentName + "$" + Long.toHexString(crc.getValue()) + "$" + logicalName;
        return compiledClass.getResource(resourceName);
    }

    private String read(URL resource) throws Exception {
        try (InputStream stream = resource.openStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

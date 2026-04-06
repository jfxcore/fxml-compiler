// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.parse.EmbeddingContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * A compilation unit descriptor contains information about a FXML document.
 *
 * @param embeddingContext the context of the embedded FXML document in a Java file
 * @param markupClass the fully qualified name of the generated base class of the FXML document
 * @param sourceRoot the root directory of the FXML file
 * @param sourceFile the FXML file relative to the root directory; if the FXML document
 *                   is embedded in a Java file, this is the path to the Java file
 * @param sourceText the FXML markup text
 */
public record CompilationUnitDescriptor(@Nullable EmbeddingContext embeddingContext,
                                        QualifiedName markupClass,
                                        Path sourceRoot,
                                        Path sourceFile,
                                        String sourceText) {

    private static final String EXTENSION = ".fxmd";

    public CompilationUnitDescriptor {
        Objects.requireNonNull(markupClass, "markupClass");
        Objects.requireNonNull(sourceRoot, "sourceRoot");
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(sourceText, "sourceText");
    }

    @SuppressWarnings("unused")
    public static CompilationUnitDescriptor readFrom(Path file) throws IOException {
        if (!file.isAbsolute()) {
            throw new IllegalArgumentException("The specified file must be an absolute path.");
        }

        Properties props = new Properties();

        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        }

        String markupBaseFile = props.getProperty("markupClass");
        String sourceRoot = props.getProperty("sourceRoot");
        String sourceFile = props.getProperty("sourceFile");
        String sourceText = props.getProperty("sourceText");

        if (Stream.of(markupBaseFile, sourceRoot, sourceFile, sourceText)
                  .anyMatch(Objects::isNull)) {
            throw new IOException("Invalid compilation unit descriptor: " + file);
        }

        return new CompilationUnitDescriptor(
            EmbeddingContext.readFrom(props),
            QualifiedName.of(markupBaseFile),
            Paths.get(sourceRoot),
            Paths.get(sourceFile),
            sourceText);
    }

    public void writeTo(Path directory) throws IOException {
        Properties props = new Properties();
        props.setProperty("markupClass", markupClass.fullName());
        props.setProperty("sourceRoot", sourceRoot.toAbsolutePath().toString());
        props.setProperty("sourceFile", sourceFile.toString());
        props.setProperty("sourceText", sourceText);

        if (embeddingContext != null) {
            embeddingContext.writeTo(props);
        }

        Path outFile = getDescriptorPath(directory);
        Files.createDirectories(outFile.getParent());

        try (OutputStream out = Files.newOutputStream(outFile)) {
            props.store(out, "FXML compilation unit descriptor");
        }
    }

    public Path absoluteSourceFile() {
        return sourceRoot.resolve(sourceFile);
    }

    @SuppressWarnings("unused")
    public Path resolveMarkupFile(Path baseDir, String extension) {
        String[] parts = markupClass.parts();

        for (int i = 0; i < parts.length - 1; ++i) {
            baseDir = baseDir.resolve(parts[i]);
        }

        return baseDir.resolve(parts[parts.length - 1] + extension);
    }

    private Path getDescriptorPath(Path baseDir) {
        String[] parts = markupClass.parts();

        for (int i = 0; i < parts.length - 1; ++i) {
            baseDir = baseDir.resolve(parts[i]);
        }

        return baseDir.resolve(parts[parts.length - 1] + EXTENSION);
    }
}

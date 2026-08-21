// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.resource;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.FileUtil;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.CRC32;

public final class EmbeddedResource {

    private final byte[] content;
    private final String logicalName;
    private final Path declaringSource;
    private final SourceInfo nameSourceInfo;
    private final String logicalPath;

    public EmbeddedResource(byte[] content, String logicalName, Path declaringSource, SourceInfo nameSourceInfo) {
        this.logicalName = Objects.requireNonNull(logicalName);
        this.content = Objects.requireNonNull(content);
        this.declaringSource = Objects.requireNonNull(declaringSource);
        this.nameSourceInfo = Objects.requireNonNull(nameSourceInfo);
        this.logicalPath = deriveLogicalPath(declaringSource, logicalName);
    }

    public byte[] content() {
        return content;
    }

    public String logicalName() {
        return logicalName;
    }

    public Path declaringSource() {
        return declaringSource;
    }

    public SourceInfo nameSourceInfo() {
        return nameSourceInfo;
    }

    public String logicalPath() {
        return logicalPath;
    }

    private static String deriveLogicalPath(Path sourceFile, String logicalName) {
        var crc = new CRC32();
        String documentName = FileUtil.getFileNameWithoutExtension(sourceFile.getFileName().toString());
        crc.update(documentName.getBytes(StandardCharsets.UTF_8));
        crc.update(logicalName.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        String hash = Long.toHexString(crc.getValue());
        String resourceFileName = documentName + "$" + hash + "$" + logicalName;

        Path parent = sourceFile.getParent();
        if (parent == null) {
            return resourceFileName;
        }

        List<String> elements = new ArrayList<>();
        for (Path element : parent) {
            String value = element.toString();
            if (!value.isEmpty() && !value.equals(".")) {
                elements.add(value);
            }
        }

        String directory = String.join("/", elements);
        return directory.isEmpty() ? resourceFileName : directory + "/" + resourceFileName;
    }
}

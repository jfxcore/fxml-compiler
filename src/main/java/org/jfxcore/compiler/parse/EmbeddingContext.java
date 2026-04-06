// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.util.QualifiedName;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public record EmbeddingContext(List<String> imports, QualifiedName embeddingHost, Location sourceOffset) {

    public EmbeddingContext {
        Objects.requireNonNull(imports, "imports");
        Objects.requireNonNull(embeddingHost, "embeddingHost");
        Objects.requireNonNull(sourceOffset, "sourceOffset");
    }

    public static @Nullable EmbeddingContext readFrom(Properties props) {
        String embeddingHost = props.getProperty("embeddingHost");
        if (embeddingHost == null) {
            return null;
        }

        int size = Integer.parseInt(props.getProperty("imports.size", "0"));
        List<String> values = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            values.add(props.getProperty("imports." + i));
        }

        return new EmbeddingContext(
            List.copyOf(values),
            QualifiedName.of(embeddingHost),
            new Location(
                Integer.parseInt(props.getProperty("sourceOffset.line", "0")),
                Integer.parseInt(props.getProperty("sourceOffset.column", "0"))));
    }

    public void writeTo(Properties props) {
        props.setProperty("embeddingHost", embeddingHost.fullName());
        props.setProperty("sourceOffset.line", String.valueOf(sourceOffset.getLine()));
        props.setProperty("sourceOffset.column", String.valueOf(sourceOffset.getColumn()));

        if (!imports.isEmpty()) {
            props.setProperty("imports.size", String.valueOf(imports.size()));

            for (int i = 0; i < imports.size(); i++) {
                props.setProperty("imports." + i, imports.get(i));
            }
        }
    }
}

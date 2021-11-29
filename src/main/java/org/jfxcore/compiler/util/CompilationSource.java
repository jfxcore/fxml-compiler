// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public interface CompilationSource {

    String getSourceText();

    String[] getSourceLines(boolean includeNewlines);

    final class FileSystem implements CompilationSource {
        private final Path sourceFile;
        private final String sourceText;
        private final String[] sourceLines;
        private final String[] sourceLinesWithNewlines;

        public FileSystem(Path sourceFile) throws IOException {
            this.sourceFile = sourceFile;
            this.sourceText = Files.readString(sourceFile);
            this.sourceLines = StringHelper.splitLines(this.sourceText, false);
            this.sourceLinesWithNewlines = StringHelper.splitLines(this.sourceText, true);
        }

        public Path getSourceFile() {
            return sourceFile;
        }

        @Override
        public String getSourceText() {
            return sourceText;
        }

        @Override
        public String[] getSourceLines(boolean includeNewlines) {
            return includeNewlines ? sourceLinesWithNewlines : sourceLines;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FileSystem that = (FileSystem)o;
            return sourceFile.equals(that.sourceFile);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceFile);
        }
    }

    final class InMemory implements CompilationSource {
        private final String sourceText;
        private final String[] sourceLines;
        private final String[] sourceLinesWithNewlines;

        public InMemory(String sourceText) {
            this.sourceText = sourceText;
            this.sourceLines = StringHelper.splitLines(this.sourceText, false);
            this.sourceLinesWithNewlines = StringHelper.splitLines(this.sourceText, true);
        }

        @Override
        public String getSourceText() {
            return sourceText;
        }

        @Override
        public String[] getSourceLines(boolean includeNewlines) {
            return includeNewlines ? sourceLinesWithNewlines : sourceLines;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InMemory that = (InMemory)o;
            return sourceText.equals(that.sourceText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceText);
        }
    }

}

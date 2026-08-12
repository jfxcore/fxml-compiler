// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Objects;

/**
 * A decoded UTF-16 cursor over source-mapped text.
 */
final class SourceCursor {

    private final SourceMappedText source;
    private int offset;

    SourceCursor(SourceMappedText source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    SourceMappedText getSource() {
        return source;
    }

    String getText() {
        return source.getText();
    }

    int length() {
        return source.getText().length();
    }

    int getOffset() {
        return offset;
    }

    void setOffset(int offset) {
        if (offset < 0 || offset > length()) {
            throw new IndexOutOfBoundsException("Invalid cursor offset: " + offset);
        }

        this.offset = offset;
    }

    int checkpoint() {
        return offset;
    }

    void reset(int checkpoint) {
        setOffset(checkpoint);
    }

    boolean isAtEnd() {
        return offset == length();
    }

    char peek() {
        if (isAtEnd()) {
            throw new IndexOutOfBoundsException("Cursor is at end of input");
        }

        return getText().charAt(offset);
    }

    char peek(int lookahead) {
        int index = offset + lookahead;
        if (index < 0 || index >= length()) {
            throw new IndexOutOfBoundsException("Invalid cursor lookahead: " + lookahead);
        }

        return getText().charAt(index);
    }

    boolean startsWith(String value) {
        return getText().startsWith(value, offset);
    }

    void advance() {
        setOffset(offset + 1);
    }

    void advance(int count) {
        setOffset(offset + count);
    }

    SourceMappedText remaining() {
        return source.slice(offset, length());
    }

    SourceMappedText slice(int start, int end) {
        return source.slice(start, end);
    }

    SourceInfo sourceInfo(int start, int end) {
        return source.getSourceInfo(start, end);
    }
}

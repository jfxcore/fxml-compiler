// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.CompilationSource;
import java.util.Objects;

/**
 * Identifies a span of text in a source code document.
 */
public final class SourceInfo {

    public static SourceInfo none() {
        return new SourceInfo(-1, -1);
    }

    public static SourceInfo span(SourceInfo from, SourceInfo to) {
        return new SourceInfo(from.start.getLine(), from.start.getColumn(), to.end.getLine(), to.end.getColumn());
    }

    public static SourceInfo shrink(SourceInfo sourceInfo) {
        if (sourceInfo.start.equals(sourceInfo.end)) {
            throw new IllegalArgumentException();
        }

        SourceInfo start = new SourceInfo(sourceInfo.start.getLine(), sourceInfo.start.getColumn() + 1), end;

        if (sourceInfo.end.getColumn() == 0) {
            CompilationSource source = CompilationContext.getCurrent().getCompilationSource();
            end = new SourceInfo(
                sourceInfo.end.getLine() - 1,
                source.getSourceLines(false)[sourceInfo.end.getLine() - 1].length() - 1);
        } else {
            end = new SourceInfo(sourceInfo.end.getLine(), sourceInfo.end.getColumn() - 1);
        }

        return span(start, end);
    }

    public static SourceInfo offset(SourceInfo source, Location offset) {
        int sourceStartCol = source.start.getColumn();
        int sourceEndCol = source.end.getColumn();
        int sourceStartLine = source.start.getLine();
        int sourceEndLine = source.end.getLine();

        if (source.start.getLine() == 0 && offset.getColumn() > 0) {
            sourceStartCol += offset.getColumn();
            sourceEndCol += offset.getColumn();
        }

        if (offset.getLine() > 0) {
            sourceStartLine += offset.getLine();
            sourceEndLine += offset.getLine();
        }

        return new SourceInfo(sourceStartLine, sourceStartCol, sourceEndLine, sourceEndCol);
    }

    public static SourceInfo after(SourceInfo sourceInfo) {
        return new SourceInfo(sourceInfo.end.getLine(), sourceInfo.end.getColumn() + 1);
    }

    private final Location start;
    private final Location end;
    private final String[] sourceLines;
    private final String lineText;

    public SourceInfo(int line, int column) {
        start = end = new Location(line, column);

        if (line >= 0) {
            sourceLines = CompilationContext.getCurrent().getCompilationSource().getSourceLines(false);
            lineText = sourceLines.length > 0 ? sourceLines[line] : null;
        } else {
            sourceLines = null;
            lineText = null;
        }
    }

    public SourceInfo(int line, int column, int endLine, int endColumn) {
        start = new Location(line, column);
        end = new Location(endLine, endColumn);

        if (line >= 0) {
            sourceLines = CompilationContext.getCurrent().getCompilationSource().getSourceLines(false);
            lineText = sourceLines.length > 0 ? sourceLines[line] : null;
        } else {
            sourceLines = null;
            lineText = null;
        }
    }

    /**
     * Returns the span of text identified by this {@link SourceInfo}.
     */
    @Nullable
    public String getText() {
        if (start.getLine() < 0) {
            return null;
        }

        StringBuilder builder = new StringBuilder();

        for (int i = start.getLine(); i <= end.getLine(); ++i) {
            String line = sourceLines[i];

            if (i == start.getLine()) {
                if (i < end.getLine()) {
                    builder.append(line.substring(start.getColumn()));
                } else {
                    builder.append(line, start.getColumn(), end.getColumn() + 1);
                }
            } else if (i == end.getLine()) {
                builder.append(line, 0, end.getColumn() + 1);
            } else {
                builder.append(line);
            }
        }

        return builder.toString()
            .replace("\r\n", "\\r\\n")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }

    /**
     * Returns the entire source line that contains the text.
     * If the text spans multiple lines, returns the first line.
     */
    @Nullable
    public String getLineText() {
        return lineText;
    }

    /**
     * The location of the first character.
     */
    public Location getStart() {
        return start;
    }

    /**
     * The location of the last character.
     */
    public Location getEnd() {
        return end;
    }

    @Override
    public String toString() {
        if (start.equals(end)) {
            return start.getLine() + ":" + start.getColumn();
        }

        return start.getLine() + ":" + start.getColumn() + ".." + end.getLine() + ":" + end.getColumn();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof SourceInfo)) {
            return false;
        }

        SourceInfo that = (SourceInfo)o;
        return Objects.equals(start, that.start) && Objects.equals(end, that.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

}
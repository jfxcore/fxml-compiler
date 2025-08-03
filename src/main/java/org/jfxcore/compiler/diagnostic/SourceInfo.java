// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.CompilationSource;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Identifies a span of text in a source code document.
 */
public final class SourceInfo {

    public static SourceInfo none() {
        return new SourceInfo(-1, -1);
    }

    public static SourceInfo span(SourceInfo from, SourceInfo to) {
        Location start = from.start.compareTo(to.start) <= 0 ? from.start : to.start;
        Location end = from.end.compareTo(to.end) >= 0 ? from.end : to.end;
        return new SourceInfo(start, end);
    }

    public static SourceInfo span(Collection<? extends Node> nodes) {
        var list = nodes.stream().map(Node::getSourceInfo).collect(Collectors.toList());

        if (list.isEmpty()) {
            return none();
        }

        return span(list.get(0), list.get(list.size() - 1));
    }

    public static SourceInfo shrink(SourceInfo sourceInfo) {
        if (sourceInfo.start.equals(sourceInfo.end)) {
            throw new IllegalArgumentException();
        }

        SourceInfo start = new SourceInfo(sourceInfo.start.getLine(), sourceInfo.start.getColumn() + 1), end;

        if (sourceInfo.end.getColumn() == 0 && CompilationContext.isCurrent()) {
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

        if (source.start.getLine() == 0) {
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
        return new SourceInfo(sourceInfo.end.getLine(), sourceInfo.end.getColumn());
    }

    private final Location start;
    private final Location end;
    private final String[] sourceLines;
    private final String lineText;
    private SourceInfo trimmed;

    public SourceInfo(int line, int column) {
        start = end = new Location(line, column);

        if (line >= 0 && CompilationContext.isCurrent()) {
            sourceLines = CompilationContext.getCurrent().getCompilationSource().getSourceLines(false);
            lineText = sourceLines.length > 0 ? sourceLines[line] : null;
        } else {
            sourceLines = null;
            lineText = null;
        }
    }

    public SourceInfo(int line, int column, int endLine, int endColumn) {
        this(new Location(line, column), new Location(endLine, endColumn));
    }

    private SourceInfo(Location start, Location end) {
        this.start = start;
        this.end = end;

        if (start.getLine() >= 0 && CompilationContext.isCurrent()) {
            sourceLines = CompilationContext.getCurrent().getCompilationSource().getSourceLines(false);
            lineText = sourceLines.length > 0 ? sourceLines[start.getLine()] : null;
        } else {
            sourceLines = null;
            lineText = null;
        }
    }

    /**
     * Returns a {@code SourceInfo} without leading and trailing whitespace.
     */
    public SourceInfo getTrimmed() {
        if (trimmed != null) {
            return trimmed;
        }

        int startLine = start.getLine();
        int startColumn = start.getColumn();
        int endLine = end.getLine();
        int endColumn = end.getColumn();

        if (isSubstringBlank(sourceLines[startLine], start.getColumn(), sourceLines[startLine].length())) {
            startLine++;
            startColumn = 0;
        }

        for (int i = startLine; i < endLine; i++) {
            if (sourceLines[i].isBlank()) {
                startLine = i + 1;
            } else {
                break;
            }
        }

        if (endLine > startLine) {
            if (isSubstringBlank(sourceLines[endLine], 0, end.getColumn())) {
                endLine--;
                endColumn = sourceLines[endLine].length();
            }

            for (int i = endLine; i >= startLine; i--) {
                if (sourceLines[i].isBlank()) {
                    endLine = i - 1;
                    endColumn = sourceLines[i - 1].length();
                } else {
                    break;
                }
            }
        }

        for (int i = startColumn; i < sourceLines[startLine].length(); i++) {
            if (Character.isWhitespace(sourceLines[startLine].charAt(i))) {
                startColumn++;
            } else {
                break;
            }
        }

        for (int i = endColumn - 1; i >= 0; i--) {
            if (Character.isWhitespace(sourceLines[endLine].charAt(i))) {
                endColumn--;
            } else {
                break;
            }
        }

        trimmed = startLine != endLine || startColumn < endColumn
            ? new SourceInfo(startLine, startColumn, endLine, endColumn)
            : new SourceInfo(start.getLine(), start.getColumn());

        trimmed.trimmed = trimmed; // no need to compute the trimmed version again
        return trimmed;
    }

    private boolean isSubstringBlank(String str, int start, int end) {
        for (int i = start; i < end; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return false;
            }
        }

        return true;
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
                    builder.append(line, start.getColumn(), end.getColumn());
                }
            } else if (i == end.getLine()) {
                builder.append(line, 0, end.getColumn());
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
     * The location after the last character.
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

        if (!(o instanceof SourceInfo that)) {
            return false;
        }

        return Objects.equals(start, that.start) && Objects.equals(end, that.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

}

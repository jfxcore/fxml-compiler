// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.CompilationSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Identifies a span of text in a source code document.
 */
public final class SourceInfo {

    /**
     * A logical view of a source document. A view can use different text and locations than the
     * original compilation source, and project its locations back when a diagnostic is displayed.
     */
    public abstract static class Source {

        public final SourceInfo getSourceInfo(Location start, Location end) {
            return new SourceInfo(start, end, this);
        }

        public final SourceInfo getSourceInfo(int line, int column) {
            return getSourceInfo(new Location(line, column), new Location(line, column));
        }

        public final SourceInfo getSourceInfo(int line, int column, int endLine, int endColumn) {
            return getSourceInfo(new Location(line, column), new Location(endLine, endColumn));
        }

        @Nullable
        protected abstract String getText(Location start, Location end);

        @Nullable
        protected abstract String getLineText(int line);

        protected SourceInfo toOriginal(Location start, Location end) {
            return getSourceInfo(start, end);
        }
    }

    private static final class OriginalSource extends Source {
        private final String text;
        private final String[] lines;
        private final int[] lineStarts;

        private OriginalSource(CompilationSource source) {
            text = source.getSourceText();
            lines = source.getSourceLines(false);
            lineStarts = createLineIndex(text);
        }

        @Override
        protected String getText(Location start, Location end) {
            return text.substring(offsetOf(start), offsetOf(end));
        }

        @Override
        protected String getLineText(int line) {
            if (line < 0) {
                return null;
            }

            if (line < lines.length) {
                return lines[line];
            }

            // StringHelper.splitLines() does not retain the final empty line.
            return line == lines.length && line < lineStarts.length ? "" : null;
        }

        private int offsetOf(Location location) {
            int line = location.getLine();
            if (line < 0 || line >= lineStarts.length) {
                throw new IndexOutOfBoundsException("Invalid source line: " + line);
            }

            int offset = lineStarts[line] + location.getColumn();
            if (offset < lineStarts[line] || offset > text.length()) {
                throw new IndexOutOfBoundsException("Invalid source location: " + location);
            }

            return offset;
        }
    }

    public static Source createSource(CompilationSource source) {
        return new OriginalSource(Objects.requireNonNull(source, "source"));
    }

    public static SourceInfo none() {
        return new SourceInfo(new Location(-1, -1), new Location(-1, -1), null);
    }

    public static SourceInfo span(SourceInfo from, SourceInfo to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        if (from.source != to.source) {
            from = from.toOriginal();
            to = to.toOriginal();
        }

        Location start = from.start.compareTo(to.start) <= 0 ? from.start : to.start;
        Location end = from.end.compareTo(to.end) >= 0 ? from.end : to.end;
        Source source = from.source == to.source ? from.source : null;
        return new SourceInfo(start, end, source);
    }

    public static SourceInfo span(Collection<? extends Node> nodes) {
        var list = nodes.stream().map(Node::getSourceInfo).collect(Collectors.toList());

        if (list.isEmpty()) {
            return none();
        }

        return span(list.get(0), list.get(list.size() - 1));
    }

    public static SourceInfo subspan(SourceInfo sourceInfo, Location start, Location end) {
        SourceInfo.Source source = sourceInfo.getSource();
        return source != null
            ? source.getSourceInfo(start, end)
            : new SourceInfo(start.getLine(), start.getColumn(), end.getLine(), end.getColumn());
    }

    public static SourceInfo shrink(SourceInfo sourceInfo) {
        if (sourceInfo.start.equals(sourceInfo.end)) {
            throw new IllegalArgumentException();
        }

        Location startLoc = new Location(sourceInfo.start.getLine(), sourceInfo.start.getColumn() + 1);
        SourceInfo start = sourceInfo.withLocations(startLoc, startLoc);
        SourceInfo end;

        if (sourceInfo.end.getColumn() == 0) {
            String previousLine = sourceInfo.getLineText(sourceInfo.end.getLine() - 1);
            if (previousLine != null) {
                Location endLoc = new Location(sourceInfo.end.getLine() - 1, previousLine.length() - 1);
                end = sourceInfo.withLocations(endLoc, endLoc);
            } else {
                Location endLoc = new Location(sourceInfo.end.getLine(), -1);
                end = sourceInfo.withLocations(endLoc, endLoc);
            }
        } else {
            Location endLoc = new Location(sourceInfo.end.getLine(), sourceInfo.end.getColumn() - 1);
            end = sourceInfo.withLocations(endLoc, endLoc);
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

        return new SourceInfo(
            new Location(sourceStartLine, sourceStartCol),
            new Location(sourceEndLine, sourceEndCol),
            source.source);
    }

    public static SourceInfo after(SourceInfo sourceInfo) {
        return new SourceInfo(sourceInfo.end, sourceInfo.end, sourceInfo.source);
    }

    public static SourceInfo content(String text, int line, int column) {
        int first = -1;
        int last = -1;

        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                first = i;
                break;
            }
        }

        if (first < 0) {
            return none();
        }

        for (int i = text.length() - 1; i >= 0; i--) {
            if (!Character.isWhitespace(text.charAt(i))) {
                last = i;
                break;
            }
        }

        Location start = locationOf(text, line, column, first);
        Location end = locationOf(text, line, column, last + 1);

        return new SourceInfo(start, end, currentSource(start.getLine()));
    }

    private static Location locationOf(String text, int line, int column, int offset) {
        for (int i = 0; i < offset; i++) {
            char ch = text.charAt(i);
            if (isLineBreak(ch)) {
                if (ch == '\r' && i + 1 < offset && text.charAt(i + 1) == '\n') {
                    ++i;
                }

                line++;
                column = 0;
            } else {
                column++;
            }
        }

        return new Location(line, column);
    }

    private final Location start;
    private final Location end;
    private final Source source;
    private SourceInfo trimmed;

    public SourceInfo(int line, int column) {
        this(new Location(line, column), new Location(line, column), currentSource(line));
    }

    public SourceInfo(int line, int column, int endLine, int endColumn) {
        this(
            new Location(line, column),
            new Location(endLine, endColumn),
            currentSource(line));
    }

    private SourceInfo(Location start, Location end, @Nullable Source source) {
        this.start = Objects.requireNonNull(start, "start");
        this.end = Objects.requireNonNull(end, "end");
        this.source = source;
    }

    private static Source currentSource(int line) {
        return line >= 0 && CompilationContext.isCurrent()
            ? CompilationContext.getCurrent().getSourceInfoSource()
            : null;
    }

    private SourceInfo withLocations(Location start, Location end) {
        return new SourceInfo(start, end, source);
    }

    /**
     * Returns a {@code SourceInfo} without leading and trailing whitespace.
     */
    public SourceInfo getTrimmed() {
        if (trimmed != null) {
            return trimmed;
        }

        if (source == null || start.getLine() < 0) {
            return this;
        }

        int startLine = start.getLine();
        int startColumn = start.getColumn();
        int endLine = end.getLine();
        int endColumn = end.getColumn();
        String startText = getLineText(startLine);

        if (startText == null) {
            return this;
        }

        if (isSubstringBlank(startText, startColumn, startText.length())) {
            startLine++;
            startColumn = 0;
        }

        for (int i = startLine; i < endLine; i++) {
            String line = getLineText(i);
            if (line != null && line.isBlank()) {
                startLine = i + 1;
            } else {
                break;
            }
        }

        if (endLine > startLine) {
            String endText = getLineText(endLine);
            if (endText != null && isSubstringBlank(endText, 0, endColumn)) {
                endLine--;
                endText = getLineText(endLine);
                endColumn = endText != null ? endText.length() : 0;
            }

            for (int i = endLine; i >= startLine; i--) {
                String line = getLineText(i);
                if (line != null && line.isBlank()) {
                    endLine = i - 1;
                    String previousLine = getLineText(i - 1);
                    endColumn = previousLine != null ? previousLine.length() : 0;
                } else {
                    break;
                }
            }
        }

        startText = getLineText(startLine);
        String endText = getLineText(endLine);
        if (startText == null || endText == null || endLine < startLine) {
            trimmed = withLocations(start, start);
            trimmed.trimmed = trimmed;
            return trimmed;
        }

        for (int i = startColumn; i < startText.length(); i++) {
            if (Character.isWhitespace(startText.charAt(i))) {
                startColumn++;
            } else {
                break;
            }
        }

        for (int i = Math.min(endColumn, endText.length()) - 1; i >= 0; i--) {
            if (Character.isWhitespace(endText.charAt(i))) {
                endColumn--;
            } else {
                break;
            }
        }

        trimmed = startLine != endLine || startColumn < endColumn
            ? withLocations(new Location(startLine, startColumn), new Location(endLine, endColumn))
            : withLocations(start, start);

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
     * Returns the span of text identified by this {@link SourceInfo} in its logical source view.
     */
    @Nullable
    public String getText() {
        if (start.getLine() < 0 || source == null) {
            return null;
        }

        String text = source.getText(start, end);
        return text != null
            ? text.replace("\r\n", "\\r\\n").replace("\r", "\\r").replace("\n", "\\n")
            : null;
    }

    /**
     * Returns the entire logical source line that contains the text.
     * If the text spans multiple lines, returns the first line.
     */
    @Nullable
    public String getLineText() {
        return getLineText(start.getLine());
    }

    @Nullable
    public String getLineText(int line) {
        return source != null ? source.getLineText(line) : null;
    }

    /**
     * Returns the logical source view used by this span.
     */
    @Nullable
    public Source getSource() {
        return source;
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

    /**
     * Projects this logical source span onto the original compilation source.
     */
    public SourceInfo toOriginal() {
        return source != null ? source.toOriginal(start, end) : this;
    }

    public SourceInfo toOneBased() {
        return new SourceInfo(start.getLine() + 1, start.getColumn() + 1, end.getLine() + 1, end.getColumn() + 1);
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

    private static int[] createLineIndex(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);

        for (int i = 0; i < text.length(); ++i) {
            char ch = text.charAt(i);
            if (isLineBreak(ch)) {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    ++i;
                }

                starts.add(i + 1);
            }
        }

        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    private static boolean isLineBreak(char ch) {
        return ch == '\r' || ch == '\n' || ch == '\u000B' || ch == '\u000C'
            || ch == '\u0085' || ch == '\u2028' || ch == '\u2029';
    }
}

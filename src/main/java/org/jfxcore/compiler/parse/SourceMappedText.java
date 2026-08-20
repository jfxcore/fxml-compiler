// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.XmlEntityDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Source-aware input for parsers that operate on transformed text.
 * <p>
 * The {@link SourceInfo} instances produced by this class use locations in {@link #getText()}.
 * Their source view retains enough information to project those locations onto the original
 * compilation source when a diagnostic is rendered.
 */
final class SourceMappedText {

    private record Replacement(int parentStart, int parentEnd, int decodedStart, int decodedEnd) {}

    private static final class TextIndex {
        private final String text;
        private final Location origin;
        private final int[] lineStarts;
        private final String[] lines;

        private TextIndex(String text, Location origin) {
            this.text = text;
            this.origin = origin;
            this.lineStarts = createLineIndex(text);
            this.lines = createLines(text, lineStarts);
        }

        private Location locationOf(int offset) {
            if (offset < 0 || offset > text.length()) {
                throw new IndexOutOfBoundsException("Invalid text offset: " + offset);
            }

            int index = Arrays.binarySearch(lineStarts, offset);
            int lineIndex = index >= 0 ? index : -index - 2;

            // There is no distinct Location for the boundary between CR and LF: both boundaries
            // denote the start of the following logical line.
            if (offset > 0
                    && offset < text.length()
                    && text.charAt(offset - 1) == '\r' && text.charAt(offset) == '\n') {
                return new Location(origin.getLine() + lineIndex + 1, 0);
            }

            int column = offset - lineStarts[lineIndex];
            if (lineIndex == 0) {
                column += origin.getColumn();
            }

            return new Location(origin.getLine() + lineIndex, column);
        }

        private int offsetOf(Location location) {
            int lineIndex = location.getLine() - origin.getLine();
            if (lineIndex < 0 || lineIndex >= lineStarts.length) {
                throw new IndexOutOfBoundsException("Location is outside the source view: " + location);
            }

            int column = location.getColumn();
            if (lineIndex == 0) {
                column -= origin.getColumn();
            }

            int offset = lineStarts[lineIndex] + column;
            int lineEnd = lineStarts[lineIndex] + lines[lineIndex].length();
            if (column < 0 || offset < lineStarts[lineIndex] || offset > lineEnd) {
                throw new IndexOutOfBoundsException("Location is outside the source view: " + location);
            }

            return offset;
        }

        private String line(int localLine) {
            return localLine >= 0 && localLine < lines.length ? lines[localLine] : null;
        }
    }

    private static final class TransformedSource extends SourceInfo.Source {
        private final String text;
        private final TextIndex decodedIndex;
        private final TextIndex parentIndex;
        private final SourceInfo.Source parentSource;
        private final Location parentEnd;
        private final List<Replacement> replacements;

        private TransformedSource(
                String text,
                String parentText,
                Location origin,
                @Nullable SourceInfo.Source parentSource,
                List<Replacement> replacements) {
            this.text = text;
            this.decodedIndex = new TextIndex(text, origin);
            this.parentIndex = new TextIndex(parentText, origin);
            this.parentSource = parentSource;
            this.parentEnd = parentIndex.locationOf(parentText.length());
            this.replacements = List.copyOf(replacements);
        }

        @Override
        protected String getText(Location start, Location end) {
            return text.substring(decodedIndex.offsetOf(start), decodedIndex.offsetOf(end));
        }

        @Override
        protected String getLineText(int line) {
            int localLine = line - decodedIndex.origin.getLine();
            String value = decodedIndex.line(localLine);
            if (value == null) {
                return null;
            }

            if (localLine == 0) {
                String parentLine = parentSource != null
                    ? parentSource.getSourceInfo(decodedIndex.origin, decodedIndex.origin).getLineText()
                    : null;

                String prefix = parentLine != null
                    ? parentLine.substring(0, Math.min(decodedIndex.origin.getColumn(), parentLine.length()))
                    : " ".repeat(Math.max(0, decodedIndex.origin.getColumn()));

                value = prefix + value;
            }

            if (localLine == decodedIndex.lines.length - 1) {
                String parentLine = parentSource != null
                    ? parentSource.getSourceInfo(parentEnd, parentEnd).getLineText()
                    : null;

                if (parentLine != null && parentEnd.getColumn() <= parentLine.length()) {
                    value += parentLine.substring(parentEnd.getColumn());
                }
            }

            return value;
        }

        @Override
        protected SourceInfo toOriginal(Location start, Location end) {
            int decodedStart = decodedIndex.offsetOf(start);
            int decodedEnd = decodedIndex.offsetOf(end);
            int parentStart;
            int parentEnd;

            if (decodedStart == decodedEnd) {
                parentStart = parentEnd = mapStart(decodedStart);
            } else {
                parentStart = mapStart(decodedStart);
                parentEnd = mapEnd(decodedEnd);
            }

            Location projectedStart = parentIndex.locationOf(parentStart);
            Location projectedEnd = parentIndex.locationOf(parentEnd);

            if (parentSource == null) {
                return new SourceInfo(
                    projectedStart.getLine(), projectedStart.getColumn(),
                    projectedEnd.getLine(), projectedEnd.getColumn());
            }

            return parentSource.getSourceInfo(projectedStart, projectedEnd).toOriginal();
        }

        private int mapStart(int decodedOffset) {
            int delta = 0;

            for (Replacement replacement : replacements) {
                if (decodedOffset < replacement.decodedStart()) {
                    break;
                }

                if (replacement.decodedStart() == replacement.decodedEnd()
                        && decodedOffset == replacement.decodedStart()) {
                    return replacement.parentEnd();
                }

                if (decodedOffset < replacement.decodedEnd()) {
                    return replacement.parentStart();
                }

                delta = replacement.parentEnd() - replacement.decodedEnd();
            }

            return decodedOffset + delta;
        }

        private int mapEnd(int decodedOffset) {
            int delta = 0;

            for (Replacement replacement : replacements) {
                if (decodedOffset <= replacement.decodedStart()) {
                    break;
                }

                if (decodedOffset <= replacement.decodedEnd()) {
                    return replacement.parentEnd();
                }

                delta = replacement.parentEnd() - replacement.decodedEnd();
            }

            return decodedOffset + delta;
        }
    }

    private final String text;
    private final TextIndex textIndex;
    private final SourceInfo.Source source;

    static SourceMappedText identity(String text, Location origin) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(origin, "origin");
        SourceInfo.Source source = CompilationContext.isCurrent()
            ? CompilationContext.getCurrent().getSourceInfoSource()
            : null;

        return new SourceMappedText(text, origin, source);
    }

    static SourceMappedText identity(String text, SourceInfo sourceInfo) {
        Objects.requireNonNull(sourceInfo, "sourceInfo");
        return new SourceMappedText(text, sourceInfo.getStart(), sourceInfo.getSource());
    }

    static SourceMappedText decodedXml(String rawText, Location origin, XmlEntityDecoder.DecodeResult decodeResult) {
        Objects.requireNonNull(rawText, "rawText");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(decodeResult, "decodeResult");

        if (!decodeResult.isFor(rawText)) {
            throw new IllegalArgumentException("Decode result was produced from different source text");
        }

        List<Replacement> replacements = decodeResult.replacements().stream()
            .map(replacement -> new Replacement(
                replacement.rawStart(), replacement.rawEnd(),
                replacement.decodedStart(), replacement.decodedEnd()))
            .toList();

        SourceInfo.Source parentSource = CompilationContext.isCurrent()
            ? CompilationContext.getCurrent().getSourceInfoSource()
            : null;

        var source = new TransformedSource(decodeResult.text(), rawText, origin, parentSource, replacements);
        return new SourceMappedText(decodeResult.text(), origin, source);
    }

    private SourceMappedText(String text, Location origin, @Nullable SourceInfo.Source source) {
        this.text = Objects.requireNonNull(text, "text");
        this.textIndex = new TextIndex(text, Objects.requireNonNull(origin, "origin"));
        this.source = source;
    }

    String getText() {
        return text;
    }

    String getLineText(int line) {
        return textIndex.line(line - textIndex.origin.getLine());
    }

    int getLocalColumn(Location location) {
        return location.getLine() == textIndex.origin.getLine()
            ? location.getColumn() - textIndex.origin.getColumn()
            : location.getColumn();
    }

    SourceMappedText without(int offset) {
        return without(offset, offset + 1);
    }

    SourceMappedText without(int start, int end) {
        return replace(start, end, "");
    }

    SourceMappedText replace(int start, int end, String replacement) {
        Objects.requireNonNull(replacement, "replacement");
        validateRange(start, end);
        String transformed = text.substring(0, start) + replacement + text.substring(end);

        var transformedSource = new TransformedSource(
            transformed,
            text,
            textIndex.origin,
            source,
            List.of(new Replacement(start, end, start, start + replacement.length())));

        return new SourceMappedText(transformed, textIndex.origin, transformedSource);
    }

    SourceMappedText withoutAll(int... offsets) {
        if (offsets.length == 0) {
            return this;
        }

        int[] sorted = offsets.clone();
        Arrays.sort(sorted);
        var builder = new StringBuilder(text);
        var replacements = new ArrayList<Replacement>(sorted.length);
        int removed = 0;
        int previous = -1;

        for (int offset : sorted) {
            if (offset < 0 || offset >= text.length()) {
                throw new IndexOutOfBoundsException("Invalid removal offset: " + offset);
            }

            if (offset == previous) {
                continue;
            }

            int decodedOffset = offset - removed;
            builder.deleteCharAt(decodedOffset);
            replacements.add(new Replacement(offset, offset + 1, decodedOffset, decodedOffset));
            previous = offset;
            ++removed;
        }

        String transformed = builder.toString();
        var transformedSource = new TransformedSource(transformed, text, textIndex.origin, source, replacements);

        return new SourceMappedText(transformed, textIndex.origin, transformedSource);
    }

    SourceMappedText slice(int start, int end) {
        validateRange(start, end);
        String sliced = text.substring(start, end);
        Location origin = textIndex.locationOf(start);
        var slicedSource = new TransformedSource(sliced, sliced, origin, source, List.of());
        return new SourceMappedText(sliced, origin, slicedSource);
    }

    SourceInfo getSourceInfo(int decodedStart, int decodedEnd) {
        validateRange(decodedStart, decodedEnd);
        Location start = textIndex.locationOf(decodedStart);
        Location end = textIndex.locationOf(decodedEnd);

        return source != null
            ? source.getSourceInfo(start, end)
            : new SourceInfo(start.getLine(), start.getColumn(), end.getLine(), end.getColumn());
    }

    SourceInfo getEndOfInput() {
        return getSourceInfo(text.length(), text.length());
    }

    private void validateRange(int decodedStart, int decodedEnd) {
        if (decodedStart < 0 || decodedEnd < 0 || decodedStart > text.length() || decodedEnd > text.length()) {
            throw new IndexOutOfBoundsException(
                "Decoded range " + decodedStart + ".." + decodedEnd + " is outside 0.." + text.length());
        }

        if (decodedStart > decodedEnd) {
            throw new IllegalArgumentException("Range start must not exceed range end");
        }
    }

    private static int[] createLineIndex(String text) {
        var starts = new ArrayList<Integer>();
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

    private static String[] createLines(String text, int[] starts) {
        String[] lines = new String[starts.length];

        for (int i = 0; i < starts.length; ++i) {
            int end = i + 1 < starts.length ? starts[i + 1] : text.length();
            while (end > starts[i] && isLineBreak(text.charAt(end - 1))) {
                --end;
            }

            lines[i] = text.substring(starts[i], end);
        }

        return lines;
    }

    private static boolean isLineBreak(char ch) {
        return ch == '\r' || ch == '\n' || ch == '\u000B' || ch == '\u000C'
            || ch == '\u0085' || ch == '\u2028' || ch == '\u2029';
    }
}

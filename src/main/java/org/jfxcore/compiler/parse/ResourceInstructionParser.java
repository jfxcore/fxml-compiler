// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ResourceErrors;
import org.jfxcore.compiler.resource.EmbeddedResource;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ResourceInstructionParser {

    private static final Set<String> RESERVED_DEVICE_NAMES = Set.of(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private final SourceMappedText input;
    private final Path declaringSource;

    public ResourceInstructionParser(String data, Path declaringSource) {
        this(SourceMappedText.identity(data, new Location(0, 0)), declaringSource);
    }

    ResourceInstructionParser(SourceMappedText input, Path declaringSource) {
        this.input = normalizeXmlLineEndings(input);
        this.declaringSource = declaringSource;
    }

    public EmbeddedResource parse() {
        String text = input.getText();
        if (text.isEmpty()) {
            throw ResourceErrors.missingName(input.getEndOfInput());
        }

        if (!isXmlWhitespace(text.charAt(0))) {
            throw ResourceErrors.invalidDeclaration(input.getSourceInfo(0, 1));
        }

        int cursor = skipXmlWhitespace(text, 0);
        if (cursor == text.length()) {
            throw ResourceErrors.missingName(input.getEndOfInput());
        }

        int nameStart;
        int nameEnd;
        String logicalName;
        char first = text.charAt(cursor);

        if (first == '\'' || first == '"') {
            nameStart = ++cursor;
            while (cursor < text.length() && text.charAt(cursor) != first) {
                ++cursor;
            }

            if (cursor == text.length()) {
                throw ResourceErrors.invalidDeclaration(input.getSourceInfo(nameStart - 1, text.length()));
            }

            nameEnd = cursor;
            logicalName = text.substring(nameStart, nameEnd);
            ++cursor;

            if (cursor < text.length()
                    && text.charAt(cursor) != ':'
                    && !isXmlWhitespace(text.charAt(cursor))) {
                throw ResourceErrors.invalidDeclaration(input.getSourceInfo(cursor, cursor + 1));
            }
        } else {
            nameStart = cursor;
            while (cursor < text.length()
                    && text.charAt(cursor) != ':'
                    && !isXmlWhitespace(text.charAt(cursor))) {
                ++cursor;
            }

            nameEnd = cursor;
            if (nameStart == nameEnd) {
                throw ResourceErrors.missingName(input.getSourceInfo(cursor, cursor));
            }

            logicalName = text.substring(nameStart, nameEnd);
        }

        SourceInfo nameSourceInfo = input.getSourceInfo(nameStart, nameEnd);
        validateFilename(logicalName, nameSourceInfo);

        cursor = skipXmlWhitespace(text, cursor);
        int mediaStart = cursor;
        int colon = findDescriptorColon(text, cursor);
        if (colon < 0) {
            throw ResourceErrors.invalidDeclaration(input.getEndOfInput());
        }

        int mediaEnd = colon;
        while (mediaEnd > mediaStart && isXmlWhitespace(text.charAt(mediaEnd - 1))) {
            --mediaEnd;
        }

        Charset charset = mediaStart == mediaEnd
            ? StandardCharsets.UTF_8
            : parseMediaType(logicalName, input.slice(mediaStart, mediaEnd));

        SourceMappedText rawPayload = input.slice(colon + 1, text.length());
        SourceMappedText normalizedPayload = normalizePayload(rawPayload);
        byte[] encoded = encode(logicalName, normalizedPayload, charset);

        return new EmbeddedResource(
            encoded,
            logicalName,
            declaringSource,
            nameSourceInfo);
    }

    private static SourceMappedText normalizeXmlLineEndings(SourceMappedText source) {
        for (int i = source.getText().length() - 1; i >= 0; --i) {
            if (source.getText().charAt(i) != '\r') {
                continue;
            }

            int end = i + 1 < source.getText().length() && source.getText().charAt(i + 1) == '\n'
                ? i + 2
                : i + 1;

            source = source.replace(i, end, "\n");
        }

        return source;
    }

    private int findDescriptorColon(String text, int start) {
        char quote = 0;
        boolean quotedPair = false;

        for (int i = start; i < text.length(); ++i) {
            char ch = text.charAt(i);

            if (quote != 0) {
                if (quotedPair) {
                    quotedPair = false;
                } else if (ch == '\\') {
                    quotedPair = true;
                } else if (ch == quote) {
                    quote = 0;
                }
            } else if (ch == '\'' || ch == '"') {
                quote = ch;
            } else if (ch == ':') {
                return i;
            }
        }

        return -1;
    }

    private void validateFilename(String name, SourceInfo sourceInfo) {
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw ResourceErrors.invalidName(sourceInfo, name);
        }

        for (int i = 0; i < name.length(); ++i) {
            char ch = name.charAt(i);
            if (ch <= 0x1f || ch == 0x7f || "/\\:*?\"<>|".indexOf(ch) >= 0) {
                throw ResourceErrors.invalidName(sourceInfo, name);
            }
        }

        if (name.endsWith(" ") || name.endsWith(".")) {
            throw ResourceErrors.invalidName(sourceInfo, name);
        }

        int extension = name.indexOf('.');
        String stem = (extension >= 0 ? name.substring(0, extension) : name).toUpperCase(Locale.ROOT);
        if (RESERVED_DEVICE_NAMES.contains(stem)) {
            throw ResourceErrors.invalidName(sourceInfo, name);
        }
    }

    private Charset parseMediaType(String logicalName, SourceMappedText mediaSource) {
        return new MediaTypeScanner(logicalName, mediaSource).parse();
    }

    private SourceMappedText normalizePayload(SourceMappedText payload) {
        String text = payload.getText();
        int opening = 0;
        boolean removedOpeningLine = false;
        while (opening < text.length() && isHorizontalWhitespace(text.charAt(opening))) {
            ++opening;
        }

        if (opening < text.length() && text.charAt(opening) == '\n') {
            payload = payload.without(0, opening + 1);
            removedOpeningLine = true;
        }

        text = payload.getText();
        int lastLineBreak = text.lastIndexOf('\n');
        if (lastLineBreak >= 0 && isHorizontalWhitespace(text, lastLineBreak + 1, text.length())) {
            payload = payload.without(lastLineBreak, text.length());
        }

        text = payload.getText();
        if (!removedOpeningLine && text.indexOf('\n') < 0) {
            return payload;
        }

        List<Line> lines = splitLines(text);
        String commonIndent = null;

        for (Line line : lines) {
            if (isHorizontalWhitespace(text, line.start(), line.end())) {
                continue;
            }

            int indentEnd = line.start();
            while (indentEnd < line.end() && isHorizontalWhitespace(text.charAt(indentEnd))) {
                ++indentEnd;
            }

            String indent = text.substring(line.start(), indentEnd);
            commonIndent = commonIndent == null ? indent : commonPrefix(commonIndent, indent);
            if (commonIndent.isEmpty()) {
                break;
            }
        }

        if (commonIndent == null || commonIndent.isEmpty()) {
            return payload;
        }

        List<Range> removals = new ArrayList<>();
        for (Line line : lines) {
            int length;
            if (isHorizontalWhitespace(text, line.start(), line.end())) {
                length = 0;
                while (length < commonIndent.length()
                        && line.start() + length < line.end()
                        && text.charAt(line.start() + length) == commonIndent.charAt(length)) {
                    ++length;
                }
            } else {
                length = commonIndent.length();
            }

            if (length > 0) {
                removals.add(new Range(line.start(), line.start() + length));
            }
        }

        for (int i = removals.size() - 1; i >= 0; --i) {
            Range range = removals.get(i);
            payload = payload.without(range.start(), range.end());
        }

        return payload;
    }

    private List<Line> splitLines(String text) {
        List<Line> result = new ArrayList<>();
        int start = 0;

        for (int i = 0; i <= text.length(); ++i) {
            if (i == text.length() || text.charAt(i) == '\n') {
                result.add(new Line(start, i));
                start = i + 1;
            }
        }

        return result;
    }

    private String commonPrefix(String left, String right) {
        int length = Math.min(left.length(), right.length());
        int index = 0;
        while (index < length && left.charAt(index) == right.charAt(index)) {
            ++index;
        }

        return left.substring(0, index);
    }

    private byte[] encode(String logicalName, SourceMappedText payload, Charset charset) {
        CharsetEncoder encoder = charset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

        CharBuffer input = CharBuffer.wrap(payload.getText());
        long requestedCapacity = (long)Math.ceil(input.remaining() * (double)encoder.maxBytesPerChar());
        int capacity = (int)Math.min(Integer.MAX_VALUE, Math.max(1, requestedCapacity));
        ByteBuffer output = ByteBuffer.allocate(capacity);
        CoderResult result = encoder.encode(input, output, true);

        if (!result.isError()) {
            result = encoder.flush(output);
        }

        if (result.isError()) {
            int offset = input.position();
            int length = offset < payload.getText().length()
                ? Character.charCount(payload.getText().codePointAt(offset))
                : 0;

            SourceInfo sourceInfo = offset < payload.getText().length()
                ? payload.getSourceInfo(offset, Math.min(payload.getText().length(), offset + length))
                : payload.getSourceInfo(0, payload.getText().length());

            throw ResourceErrors.unrepresentableCharacter(sourceInfo, logicalName, charset.name());
        }

        output.flip();
        byte[] bytes = new byte[output.remaining()];
        output.get(bytes);
        return bytes;
    }

    private int skipXmlWhitespace(String text, int offset) {
        while (offset < text.length() && isXmlWhitespace(text.charAt(offset))) {
            ++offset;
        }

        return offset;
    }

    private boolean isXmlWhitespace(char ch) {
        return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r';
    }

    private boolean isHorizontalWhitespace(char ch) {
        return ch == ' ' || ch == '\t';
    }

    private boolean isHorizontalWhitespace(String text, int start, int end) {
        for (int i = start; i < end; ++i) {
            if (!isHorizontalWhitespace(text.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private record Line(int start, int end) {}

    private record Range(int start, int end) {}

    private final class MediaTypeScanner {
        private final String logicalName;
        private final SourceMappedText source;
        private final String text;
        private int offset;

        private MediaTypeScanner(String logicalName, SourceMappedText source) {
            this.logicalName = logicalName;
            this.source = source;
            this.text = source.getText();
        }

        private Charset parse() {
            String type = parseToken();
            if (type == null || type.equals("*") || !poll('/')) {
                throw invalidMediaType();
            }

            String subtype = parseToken();
            if (subtype == null || subtype.equals("*")) {
                throw invalidMediaType();
            }

            SourceInfo charsetSourceInfo = null;
            String charsetName = null;
            Set<String> parameterNames = new HashSet<>();

            while (offset < text.length()) {
                skipOws();
                if (!poll(';')) {
                    throw invalidMediaType();
                }

                skipOws();
                int parameterStart = offset;
                String name = parseToken();
                if (name == null) {
                    throw invalidMediaType();
                }

                skipOws();
                if (!poll('=')) {
                    throw invalidMediaType();
                }

                skipOws();
                String value = parseParameterValue();
                if (value == null) {
                    throw invalidMediaType();
                }

                if (!parameterNames.add(name.toLowerCase(Locale.ROOT))) {
                    throw ResourceErrors.duplicateMediaTypeParameter(
                        source.getSourceInfo(parameterStart, offset), logicalName, name);
                }

                if (name.equalsIgnoreCase("charset")) {
                    charsetName = value;
                    charsetSourceInfo = source.getSourceInfo(parameterStart, offset);
                }
            }

            Charset charset = StandardCharsets.UTF_8;
            if (charsetName != null) {
                try {
                    charset = Charset.forName(charsetName);
                } catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
                    throw ResourceErrors.unsupportedCharset(
                        charsetSourceInfo, logicalName, charsetName, ex);
                }
            }

            return charset;
        }

        private String parseToken() {
            int start = offset;
            while (offset < text.length() && isTokenCharacter(text.charAt(offset))) {
                ++offset;
            }

            return start == offset ? null : text.substring(start, offset);
        }

        private String parseParameterValue() {
            if (offset == text.length()) {
                return null;
            }

            char quote = text.charAt(offset);
            if (quote != '\'' && quote != '"') {
                return parseToken();
            }

            ++offset;
            StringBuilder value = new StringBuilder();
            while (offset < text.length()) {
                char ch = text.charAt(offset++);
                if (ch == quote) {
                    return value.toString();
                }

                if (ch == '\\') {
                    if (offset == text.length()) {
                        return null;
                    }

                    ch = text.charAt(offset++);
                }

                if (ch == 0x7f || ch < 0x20 && ch != '\t') {
                    return null;
                }

                value.append(ch);
            }

            return null;
        }

        private void skipOws() {
            while (offset < text.length() && isHorizontalWhitespace(text.charAt(offset))) {
                ++offset;
            }
        }

        private boolean poll(char expected) {
            if (offset < text.length() && text.charAt(offset) == expected) {
                ++offset;
                return true;
            }

            return false;
        }

        private boolean isTokenCharacter(char ch) {
            return ch >= 'a' && ch <= 'z'
                || ch >= 'A' && ch <= 'Z'
                || ch >= '0' && ch <= '9'
                || "!#$%&'*+-.^_`|~".indexOf(ch) >= 0;
        }

        private RuntimeException invalidMediaType() {
            int end = Math.min(text.length(), offset + 1);
            SourceInfo sourceInfo = source.getSourceInfo(Math.min(offset, text.length()), end);
            return ResourceErrors.invalidMediaType(sourceInfo, logicalName);
        }
    }
}

// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;

public class InlineTokenizer extends CurlyTokenizer<InlineToken> {

    private final SourceCursor cursor;
    private final int cursorStart;
    private final LazyTokenStream tokenStream;

    public InlineTokenizer(String text, Location sourceOffset) {
        this(SourceMappedText.identity(text, sourceOffset));
    }

    InlineTokenizer(SourceMappedText source) {
        super(InlineToken.class, source);
        this.cursor = null;
        this.cursorStart = 0;
        this.tokenStream = new LazyTokenStream(source);
    }

    InlineTokenizer(SourceCursor cursor) {
        super(InlineToken.class, cursor.remaining());
        this.cursor = cursor;
        this.cursorStart = cursor.getOffset();
        this.tokenStream = new LazyTokenStream(cursor.remaining());
    }

    void commit() {
        if (cursor == null) {
            throw new IllegalStateException("Tokenizer is not cursor-backed");
        }

        cursor.setOffset(cursorStart + tokenStream.nextDecodedOffset());
    }

    @Override
    public void mark() {
        tokenStream.mark();
    }

    @Override
    public void resetToMark() {
        tokenStream.resetToMark();
    }

    @Override
    public void forgetMark() {
        tokenStream.forgetMark();
    }

    @Override
    public InlineToken peek() {
        return tokenStream.peek();
    }

    @Override
    public InlineToken poll() {
        return tokenStream.poll();
    }

    @Override
    public InlineToken remove() {
        return tokenStream.remove();
    }

    @Override
    public InlineToken poll(CurlyTokenType type) {
        return tokenStream.poll(type);
    }

    @Override
    public InlineToken getLastRemoved() {
        return tokenStream.getLastRemoved();
    }

    @Override
    public boolean isEmpty() {
        return tokenStream.isEmpty();
    }

    @Override
    public int size() {
        return tokenStream.size();
    }

    @Override
    public InlineToken[] peekAhead(int count) {
        return tokenStream.peekAhead(count);
    }

    @Override
    public InlineToken peekSkipWS() {
        return tokenStream.peekSkipWS();
    }

    @Override
    public InlineToken peekSkipWS(CurlyTokenType expected) {
        return tokenStream.peekSkipWS(expected);
    }

    @Override
    public InlineToken peekNotNullSkipWS() {
        InlineToken token = tokenStream.peekSkipWS();
        if (token == null) {
            throw unexpectedEnd(getEndOfInputSourceInfo());
        }

        return token;
    }

    @Override
    public InlineToken removeSkipWS(CurlyTokenType expected) {
        return tokenStream.removeSkipWS(expected);
    }

    String getLiteralSourceText(InlineToken first, InlineToken last) {
        return tokenStream.getLiteralSourceText(first, last);
    }

    private final class LazyTokenStream {

        private record IgnoredRange(int start, int end) {}

        private final SourceMappedText source;
        private final String text;
        private final List<InlineToken> tokens = new ArrayList<>();
        private final List<IgnoredRange> ignoredRanges = new ArrayList<>();
        private final Deque<Integer> marks = new java.util.ArrayDeque<>();
        private int tokenIndex;
        private int rawOffset;
        private InlineToken pendingSemi;
        private InlineToken deferredToken;
        private InlineToken lastNormalizedToken;
        private InlineToken lastRemoved;
        private boolean finalEnd;

        private LazyTokenStream(SourceMappedText source) {
            this.source = source;
            this.text = source.getText();
        }

        private void mark() {
            marks.push(tokenIndex);
        }

        private void resetToMark() {
            if (marks.isEmpty()) {
                throw new IllegalStateException();
            }

            tokenIndex = marks.pop();
            lastRemoved = tokenIndex > 0 ? tokens.get(tokenIndex - 1) : null;
        }

        private void forgetMark() {
            if (marks.isEmpty()) {
                throw new IllegalStateException();
            }

            marks.pop();
        }

        private InlineToken peek() {
            return ensureFinalToken(tokenIndex) ? tokens.get(tokenIndex) : null;
        }

        private InlineToken poll() {
            InlineToken token = peek();
            if (token != null) {
                ++tokenIndex;
                lastRemoved = token;
            }

            return token;
        }

        private InlineToken remove() {
            InlineToken token = poll();
            if (token == null) {
                throw unexpectedEnd(source.getEndOfInput());
            }

            return token;
        }

        private InlineToken poll(CurlyTokenType type) {
            InlineToken token = peek();
            return token != null && token.getType() == type ? poll() : null;
        }

        private InlineToken getLastRemoved() {
            return lastRemoved;
        }

        private boolean isEmpty() {
            return peek() == null;
        }

        private int size() {
            while (ensureFinalToken(tokens.size())) {
                // Materialize only when callers explicitly ask for the complete size.
            }

            return tokens.size() - tokenIndex;
        }

        private InlineToken[] peekAhead(int count) {
            if (count < 0) {
                throw new IllegalArgumentException("count");
            }

            if (count == 0) {
                return new InlineToken[0];
            }

            if (!ensureFinalToken(tokenIndex + count - 1)) {
                return null;
            }

            InlineToken[] result = new InlineToken[count];
            for (int i = 0; i < count; ++i) {
                result[i] = tokens.get(tokenIndex + i);
            }

            return result;
        }

        private InlineToken peekSkipWS() {
            int index = tokenIndex;
            while (ensureFinalToken(index) && tokens.get(index).getType().isWhitespace()) {
                ++index;
            }

            return ensureFinalToken(index) ? tokens.get(index) : null;
        }

        private InlineToken peekSkipWS(CurlyTokenType expected) {
            InlineToken token = peekSkipWS();
            return token != null && token.getType() == expected ? token : null;
        }

        private InlineToken removeSkipWS(CurlyTokenType expected) {
            while (peek() != null && peek().getType().isWhitespace()) {
                poll();
            }

            return InlineTokenizer.this.remove(expected);
        }

        private int nextDecodedOffset() {
            InlineToken token = peek();
            return token != null ? token.getDecodedStart() : text.length();
        }

        private String getLiteralSourceText(InlineToken first, InlineToken last) {
            if (first.getDecodedStart() < 0 || first.getDecodedEnd() < 0
                    || last.getDecodedStart() < 0 || last.getDecodedEnd() < 0) {
                throw new IllegalArgumentException("Tokens must have decoded source ranges");
            }

            int start = first.getDecodedStart();
            int end = last.getDecodedEnd();
            if (start > end) {
                throw new IllegalArgumentException("First token must not follow last token");
            }

            StringBuilder builder = null;
            int segmentStart = start;

            for (IgnoredRange range : ignoredRanges) {
                if (range.end() <= start) {
                    continue;
                }

                if (range.start() >= end) {
                    break;
                }

                if (builder == null) {
                    builder = new StringBuilder(end - start);
                }

                int ignoredStart = Math.max(range.start(), start);
                int ignoredEnd = Math.min(range.end(), end);
                builder.append(text, segmentStart, ignoredStart);
                segmentStart = ignoredEnd;
            }

            if (builder == null) {
                return text.substring(start, end);
            }

            return builder.append(text, segmentStart, end).toString();
        }

        private boolean ensureFinalToken(int index) {
            while (tokens.size() <= index && !finalEnd) {
                InlineToken token = readFinalToken();
                if (token == null) {
                    finalEnd = true;
                } else {
                    tokens.add(token);
                }
            }

            return index < tokens.size();
        }

        private InlineToken readFinalToken() {
            if (deferredToken != null) {
                InlineToken result = deferredToken;
                deferredToken = null;
                lastNormalizedToken = result;
                return result;
            }

            while (true) {
                InlineToken token = readRawToken();
                if (token == null) {
                    pendingSemi = null; // trailing semis are insignificant
                    return null;
                }

                if (token.getType().getTokenClass() == CurlyTokenClass.SEMI) {
                    if (lastNormalizedToken != null && pendingSemi == null) {
                        pendingSemi = token;
                    }

                    continue;
                }

                if (pendingSemi != null) {
                    InlineToken semi = pendingSemi;
                    pendingSemi = null;
                    boolean remove = lastNormalizedToken == null
                        || lastNormalizedToken.getType() == CurlyTokenType.OPEN_CURLY
                        || semi.getType() == CurlyTokenType.NEWLINE
                            && (removeNewlineAfter(lastNormalizedToken.getType())
                                || removeNewlineBefore(token.getType()));

                    if (!remove) {
                        deferredToken = token;
                        lastNormalizedToken = semi;
                        return semi;
                    }
                }

                lastNormalizedToken = token;
                return token;
            }
        }

        private InlineToken readRawToken() {
            while (rawOffset < text.length()) {
                char ch = text.charAt(rawOffset);
                if (Character.isWhitespace(ch) && !isLineBreak(ch)) {
                    ++rawOffset;
                    continue;
                }

                Matcher matcher = CurlyTokenizer.TOKENIZER_PATTERN.matcher(text);
                matcher.region(rawOffset, text.length());
                if (!matcher.lookingAt()) {
                    int start = rawOffset++;
                    while (rawOffset < text.length()
                            && !Character.isWhitespace(text.charAt(rawOffset))) {
                        matcher.region(rawOffset, text.length());
                        if (matcher.lookingAt()) {
                            break;
                        }

                        ++rawOffset;
                    }

                    return newRawToken(text.substring(start, rawOffset), start, rawOffset);
                }

                String value = matcher.group();
                int start = matcher.start();
                rawOffset = matcher.end();

                if (isLineBreakToken(value)) {
                    return newNewlineToken(value, start, rawOffset);
                }

                if (value.isBlank()) {
                    continue;
                }

                if (value.startsWith("/*")) {
                    ignoredRanges.add(new IgnoredRange(start, rawOffset));
                    continue;
                }

                if (value.startsWith("//")) {
                    skipLineComment();
                    ignoredRanges.add(new IgnoredRange(start, rawOffset));
                    continue;
                }

                return newRawToken(value, start, rawOffset);
            }

            return null;
        }

        private void skipLineComment() {
            int lineEnd = rawOffset;
            while (lineEnd < text.length() && !isLineBreak(text.charAt(lineEnd))) {
                ++lineEnd;
            }

            int blockComment = text.indexOf("/*", rawOffset);
            if (blockComment >= 0 && blockComment < lineEnd) {
                Matcher matcher = CurlyTokenizer.TOKENIZER_PATTERN.matcher(text);
                matcher.region(blockComment, text.length());
                if (matcher.lookingAt() && matcher.group().startsWith("/*")) {
                    rawOffset = matcher.end();
                    while (rawOffset < text.length() && !isLineBreak(text.charAt(rawOffset))) {
                        ++rawOffset;
                    }

                    return;
                }
            }

            rawOffset = lineEnd;
        }

        private InlineToken newRawToken(String value, int start, int end) {
            SourceInfo sourceInfo = source.getSourceInfo(start, end);
            InlineToken token = InlineToken.parse(
                value, source.getLineText(sourceInfo.getStart().getLine()), sourceInfo);
            token.setDecodedRange(start, end);
            return token;
        }

        private InlineToken newNewlineToken(String value, int start, int end) {
            SourceInfo sourceInfo = source.getSourceInfo(start, end);
            InlineToken token = new InlineToken(
                CurlyTokenType.NEWLINE, value, source.getLineText(sourceInfo.getStart().getLine()), sourceInfo);
            token.setDecodedRange(start, end);
            return token;
        }

        private boolean isLineBreakToken(String value) {
            return value.equals("\r\n") || value.length() == 1 && isLineBreak(value.charAt(0));
        }
    }

    private boolean removeNewlineAfter(CurlyTokenType type) {
        return type == CurlyTokenType.OPEN_CURLY
            || type == CurlyTokenType.OPEN_BRACKET
            || type == CurlyTokenType.OPEN_PAREN
            || type == CurlyTokenType.DOT
            || type == CurlyTokenType.COLON
            || type == CurlyTokenType.COMMA
            || type == CurlyTokenType.EQUALS
            || type == CurlyTokenType.NOT
            || type == CurlyTokenType.BOOLIFY
            || isInfixOperator(type);
    }

    private boolean removeNewlineBefore(CurlyTokenType type) {
        return type == CurlyTokenType.DOT
            || type == CurlyTokenType.COLON
            || type == CurlyTokenType.COMMA
            || type == CurlyTokenType.EQUALS
            || isInfixOperator(type);
    }

    private boolean isInfixOperator(CurlyTokenType type) {
        return switch (type) {
            case PLUS, MINUS, STAR, SLASH,
                 OPEN_ANGLE, CLOSE_ANGLE, LESS_THAN_OR_EQUAL, GREATER_THAN_OR_EQUAL,
                 VALUE_EQUALITY, VALUE_INEQUALITY, IDENTITY_EQUALITY, IDENTITY_INEQUALITY,
                 LOGICAL_AND, LOGICAL_OR -> true;
            default -> false;
        };
    }

    private boolean isLineBreak(char ch) {
        return ch == '\r' || ch == '\n' || ch == '\u000B' || ch == '\u000C'
            || ch == '\u0085' || ch == '\u2028' || ch == '\u2029';
    }
}

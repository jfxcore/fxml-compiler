// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import java.util.ArrayDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tokenizer for curly-based markup that is used in markup extensions and stylesheets.
 */
public abstract class CurlyTokenizer<TToken extends CurlyToken> extends AbstractTokenizer<CurlyTokenType, TToken> {

    private static final Pattern TOKENIZER_PATTERN = Pattern.compile(
        "\"[^\"\\\\]*(\\\\(.|\\n)[^\"\\\\]*)*\"|'[^'\\\\]*(\\\\(.|\\n)[^'\\\\]*)*'|" + // quoted strings
            "/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/|" + // block comments
            "(?:[+-]?\\d*\\.?\\d+(?!\\.))?(?:-?[^\\W\\d][\\w-]*)|" + // number+identifier
            "[+-]?\\d*\\.?\\d+(?!\\.)|" + // numbers
            "@|;|,|\\.|:|\\*|%|=|\\$|#|\\R|//|/|->|" + // other tokens
            "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*|" + // Java identifiers
            "\\{|}|\\(|\\)|<|>|\\[|]|" + // braces/brackets/parens
            "==|!=|" + // ==/!= operator
            "!!|!", // not/boolify operator
        Pattern.DOTALL
    );

    private final Location sourceOffset;

    protected CurlyTokenizer(Class<TToken> tokenClass, String text, Location sourceOffset) {
        super(text, tokenClass);
        this.sourceOffset = sourceOffset;
        tokenize(text);
    }

    protected abstract TToken parseToken(String value, String line, SourceInfo sourceInfo);

    protected abstract TToken newToken(CurlyTokenType type, String value, String line, SourceInfo sourceInfo);

    public String getSourceText(SourceInfo sourceInfo) {
        StringBuilder builder = new StringBuilder();

        int fromLine = sourceInfo.getStart().getLine();
        int fromColumn = sourceInfo.getStart().getColumn();
        int endLine = sourceInfo.getEnd().getLine();
        int endColumn = sourceInfo.getEnd().getColumn();

        if (fromLine == endLine && fromColumn == endColumn) {
            return "";
        }

        builder.append(" ".repeat(fromColumn));

        builder.append(
            getLines().get(fromLine),
            fromColumn,
            fromLine == endLine ? endColumn : getLines().get(fromLine).length());

        builder.append('\n');

        for (int line = fromLine + 1; line < endLine; ++line) {
            builder.append(getLines().get(line)).append('\n');
        }

        if (endLine > fromLine) {
            builder.append(getLines().get(endLine), 0, endColumn).append('\n');
        }

        return builder.toString();
    }

    public TToken peekSemi() {
        TToken token = peek();
        if (token != null && token.getType().getTokenClass() == CurlyTokenClass.SEMI) {
            return token;
        }

        return null;
    }

    private void tokenize(String text) {
        ArrayDeque<TToken> tokens = new ArrayDeque<>();
        Matcher matcher = TOKENIZER_PATTERN.matcher(text);
        int lastPosition = 0;
        boolean eatLine = false;

        while (matcher.find()) {
            String value = matcher.group();
            int start = matcher.start();
            int end = matcher.end();

            if (eatLine && !isNewline(value)) {
                lastPosition = start + value.length();
                continue;
            } else {
                eatLine = false;
            }

            if (isNewline(value)) {
                SourceInfo sourceInfo = getSourceInfo(start, end);
                TToken newToken = newToken(
                    CurlyTokenType.NEWLINE,
                    value,
                    getLines().get(sourceInfo.getStart().getLine()),
                    SourceInfo.offset(sourceInfo, sourceOffset));

                tokens.add(newToken);
                lastPosition = start + value.length();
                continue;
            }

            if (value.isBlank() || value.startsWith("/*")) {
                lastPosition = start + value.length();
                continue;
            }

            if (value.startsWith("//")) {
                lastPosition = start + value.length();
                eatLine = true;
                continue;
            }

            String excess = text.substring(lastPosition, start);
            int firstNonWhitespace = -1;

            for (int i = 0; i < excess.length(); ++i) {
                if (!Character.isWhitespace(excess.charAt(i))) {
                    firstNonWhitespace = i;
                    break;
                }
            }

            if (firstNonWhitespace >= 0) {
                SourceInfo sourceInfo = getSourceInfo(lastPosition + firstNonWhitespace, end);
                throw ParserErrors.unexpectedToken(SourceInfo.offset(sourceInfo, sourceOffset));
            }

            SourceInfo sourceInfo = getSourceInfo(start, end);
            TToken newToken = parseToken(
                value,
                getLines().get(sourceInfo.getStart().getLine()),
                SourceInfo.offset(sourceInfo, sourceOffset));

            tokens.add(newToken);
            lastPosition = start + value.length();
        }

        tokens = preprocessSemis(tokens);

        if (!tokens.isEmpty() && tokens.getLast().getType().getTokenClass() == CurlyTokenClass.SEMI) {
            tokens.removeLast();
        }

        addAll(tokens);
    }

    // Removes SEMI tokens where we don't need them.
    private ArrayDeque<TToken> preprocessSemis(ArrayDeque<TToken> tokens) {
        ArrayDeque<TToken> newTokens = new ArrayDeque<>(tokens.size());
        TToken last = null;
        boolean newline;
        boolean semi = true;
        boolean empty = true;

        while (!tokens.isEmpty()) {
            TToken token = tokens.poll();

            if (token.getType().getTokenClass() == CurlyTokenClass.SEMI) {
                if (semi) {
                    continue;
                }

                semi = true;
                newline = token.getType() == CurlyTokenType.NEWLINE;
            } else {
                newline = false;
                semi = false;
                empty = false;
            }

            if (newline) {
                if (removeNewlineAfter(last.getType())) {
                    continue;
                }

                TToken next = tokens.peek();

                while (next != null && next.getType() == CurlyTokenType.NEWLINE) {
                    tokens.remove();
                    next = tokens.peek();
                }

                if (next != null && removeNewlineBefore(next.getType())) {
                    continue;
                }
            } else if (semi && last.getType() == CurlyTokenType.OPEN_CURLY) {
                continue;
            }

            newTokens.add(token);
            last = token;
        }

        if (empty) {
            throw ParserErrors.unexpectedEndOfFile(SourceInfo.none());
        }

        return newTokens;
    }

    private boolean removeNewlineAfter(CurlyTokenType type) {
        return type == CurlyTokenType.OPEN_CURLY
            || type == CurlyTokenType.OPEN_BRACKET
            || type == CurlyTokenType.OPEN_PAREN
            || type == CurlyTokenType.DOT
            || type == CurlyTokenType.COLON
            || type == CurlyTokenType.COMMA
            || type == CurlyTokenType.EQUALS;
    }

    private boolean removeNewlineBefore(CurlyTokenType type) {
        return type == CurlyTokenType.DOT
            || type == CurlyTokenType.COLON
            || type == CurlyTokenType.COMMA
            || type == CurlyTokenType.EQUALS;
    }

}

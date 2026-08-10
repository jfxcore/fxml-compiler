// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import java.util.ArrayDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tokenizer for curly-based inline markup. */
public abstract class CurlyTokenizer<TToken extends CurlyToken> extends AbstractTokenizer<CurlyTokenType, TToken> {

    /*
     * Greedy quantifiers implement maximal munch within each operator family. The alternatives start with
     * distinct characters, so recognition of a complete operator does not depend on alternation order.
     * Single ampersands and vertical bars are retained as UNKNOWN tokens for precise diagnostics.
     */
    private static final String OPERATOR_PATTERN =
        "!(?:={1,2}|!)?|={1,3}|[<>]=?|&{1,2}|[|]{1,2}";

    private static final Pattern TOKENIZER_PATTERN = Pattern.compile(
        "\"[^\"\\\\]*(\\\\(.|\\n)[^\"\\\\]*)*\"|'[^'\\\\]*(\\\\(.|\\n)[^'\\\\]*)*'|" + // quoted strings
        "/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/|" + // block comments
        "[+-]?\\d*\\.?\\d+(?:[dDfFlL])?(?!\\.)|" + // numbers
        OPERATOR_PATTERN + "|" + // operators and reserved operator characters
        "\\^|°|§|%|@|\\?|~|;|,|\\.|:|\\+|-(?:>)?|\\*|\\$|#|\\R|/{1,2}|" + // other tokens
        "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*|" + // Java identifiers
        "\\{|}|\\(|\\)|\\[|]", // braces/brackets/parens
        Pattern.DOTALL
    );

    private final SourceMappedText source;

    protected CurlyTokenizer(Class<TToken> tokenClass, String source, Location sourceOffset) {
        this(tokenClass, SourceMappedText.identity(source, sourceOffset));
    }

    CurlyTokenizer(Class<TToken> tokenClass, SourceMappedText source) {
        super(source.getText(), tokenClass);
        this.source = source;
        tokenize(source.getText());
    }

    protected abstract TToken parseToken(String value, String line, SourceInfo sourceInfo);

    protected abstract TToken newToken(CurlyTokenType type, String value, String line, SourceInfo sourceInfo);

    public String getSourceText(SourceInfo sourceInfo) {
        StringBuilder builder = new StringBuilder();

        int fromLine = sourceInfo.getStart().getLine();
        int fromColumn = sourceInfo.getStart().getColumn();
        int endLine = sourceInfo.getEnd().getLine();
        int endColumn = sourceInfo.getEnd().getColumn();
        int localFromColumn = source.getLocalColumn(sourceInfo.getStart());
        int localEndColumn = source.getLocalColumn(sourceInfo.getEnd());

        if (fromLine == endLine && fromColumn == endColumn) {
            return "";
        }

        builder.append(" ".repeat(fromColumn));

        builder.append(
            source.getLineText(fromLine),
            localFromColumn,
            fromLine == endLine ? localEndColumn : source.getLineText(fromLine).length());

        builder.append('\n');

        for (int line = fromLine + 1; line < endLine; ++line) {
            builder.append(source.getLineText(line)).append('\n');
        }

        if (endLine > fromLine) {
            builder.append(source.getLineText(endLine), 0, localEndColumn).append('\n');
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
                SourceInfo decodedSourceInfo = getSourceInfo(start, end);
                TToken newToken = newToken(
                    CurlyTokenType.NEWLINE,
                    value,
                    getLines().get(decodedSourceInfo.getStart().getLine()),
                    source.getSourceInfo(start, end));

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
                throw ParserErrors.unexpectedToken(
                    source.getSourceInfo(lastPosition + firstNonWhitespace, end));
            }

            SourceInfo decodedSourceInfo = getSourceInfo(start, end);
            TToken newToken = parseToken(
                value,
                getLines().get(decodedSourceInfo.getStart().getLine()),
                source.getSourceInfo(start, end));

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
            throw ParserErrors.unexpectedEndOfFile(getEndOfInputSourceInfo());
        }

        return newTokens;
    }

    @Override
    protected SourceInfo getEndOfInputSourceInfo() {
        return source.getEndOfInput();
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
}

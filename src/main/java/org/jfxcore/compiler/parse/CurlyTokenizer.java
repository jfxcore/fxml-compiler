// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tokenizer for curly-based markup that is used in markup extensions and stylesheets.
 */
public class CurlyTokenizer extends AbstractTokenizer<MeTokenType, MeToken> {

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

    private final String intrinsicPrefix;
    private final Location sourceOffset;

    public CurlyTokenizer(String text, String intrinsicPrefix, Location sourceOffset) {
        super(text, MeToken.class);
        this.intrinsicPrefix = intrinsicPrefix;
        this.sourceOffset = sourceOffset;
        tokenize(text);
    }

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

    public MeToken peekSemi() {
        MeToken token = peek();
        if (token != null && token.getType().getTokenClass() == MeTokenClass.SEMI) {
            return token;
        }

        return null;
    }

    private void tokenize(String text) {
        ArrayDeque<MeToken> tokens = new ArrayDeque<>();
        Matcher matcher = TOKENIZER_PATTERN.matcher(text);
        int lastPosition = 0;
        boolean eatLine = false;

        while (matcher.find()) {
            String value = matcher.group();
            int start = matcher.start();
            int end = matcher.end() - 1;

            if (eatLine && !isNewline(value)) {
                lastPosition = start + value.length();
                continue;
            } else {
                eatLine = false;
            }

            if (isNewline(value)) {
                SourceInfo sourceInfo = getSourceInfo(start, end);
                MeToken newToken = new MeToken(
                    MeTokenType.NEWLINE,
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
            MeToken newToken = MeToken.parse(
                intrinsicPrefix,
                value,
                getLines().get(sourceInfo.getStart().getLine()),
                SourceInfo.offset(sourceInfo, sourceOffset));

            tokens.add(newToken);
            lastPosition = start + value.length();
        }

        tokens = preprocessSemis(tokens);
        tokens = preprocessIntrinsicIdentifiers(tokens);

        if (!tokens.isEmpty() && tokens.getLast().getType().getTokenClass() == MeTokenClass.SEMI) {
            tokens.removeLast();
        }

        addAll(tokens);
    }

    // Concatenates prefixes and identifiers.
    private ArrayDeque<MeToken> preprocessIntrinsicIdentifiers(ArrayDeque<MeToken> tokens) {
        ArrayDeque<MeToken> newTokens = new ArrayDeque<>(tokens.size());
        List<MeToken> tempTokens = new ArrayList<>();

        while (!tokens.isEmpty()) {
            MeToken current = tokens.remove();
            tempTokens.add(current);

            while (intrinsicPrefix.startsWith(current.getValue()) && !intrinsicPrefix.equals(current.getValue())) {
                MeToken next = tokens.poll();
                if (next == null) {
                    break;
                }

                current = new MeToken(
                    MeTokenType.UNKNOWN,
                    current.getValue() + next.getValue(),
                    current.getLine(),
                    current.getSourceInfo());

                tempTokens.add(next);
            }

            if (intrinsicPrefix.equals(current.getValue()) && isIdentifierToken(tokens.peek())) {
                MeToken identifier = tokens.remove();
                MeToken newToken = new MeToken(
                    MeTokenType.IDENTIFIER,
                    intrinsicPrefix + identifier.getValue(),
                    current.getLine(),
                    SourceInfo.span(current.getSourceInfo(), identifier.getSourceInfo()));
                newTokens.add(newToken);
            } else {
                newTokens.addAll(tempTokens);
            }

            tempTokens.clear();
        }

        return newTokens;
    }

    private boolean isIdentifierToken(MeToken token) {
        if (token == null) {
            return false;
        }

        return token.getType() == MeTokenType.IDENTIFIER || token.getValue().equals("class");
    }

    // Removes SEMI tokens where we don't need them.
    private ArrayDeque<MeToken> preprocessSemis(ArrayDeque<MeToken> tokens) {
        ArrayDeque<MeToken> newTokens = new ArrayDeque<>(tokens.size());
        MeToken last = null;
        boolean newline;
        boolean semi = true;
        boolean empty = true;

        while (!tokens.isEmpty()) {
            MeToken token = tokens.poll();

            if (token.getType().getTokenClass() == MeTokenClass.SEMI) {
                if (semi) {
                    continue;
                }

                semi = true;
                newline = token.getType() == MeTokenType.NEWLINE;
            } else {
                newline = false;
                semi = false;
                empty = false;
            }

            if (newline) {
                if (removeNewlineAfter(last.getType())) {
                    continue;
                }

                MeToken next = tokens.peek();

                while (next != null && next.getType() == MeTokenType.NEWLINE) {
                    tokens.remove();
                    next = tokens.peek();
                }

                if (next != null && removeNewlineBefore(next.getType())) {
                    continue;
                }
            } else if (semi && last.getType() == MeTokenType.OPEN_CURLY) {
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

    private boolean removeNewlineAfter(MeTokenType type) {
        return type == MeTokenType.OPEN_CURLY
            || type == MeTokenType.OPEN_BRACKET
            || type == MeTokenType.OPEN_PAREN
            || type == MeTokenType.DOT
            || type == MeTokenType.COLON
            || type == MeTokenType.COMMA;
    }

    private boolean removeNewlineBefore(MeTokenType type) {
        return type == MeTokenType.DOT
            || type == MeTokenType.COLON
            || type == MeTokenType.COMMA;
    }

}

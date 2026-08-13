// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.regex.Pattern;

/**
 * Tokenizer for curly-based inline markup.
 */
public abstract class CurlyTokenizer<TToken extends CurlyToken> extends AbstractTokenizer<CurlyTokenType, TToken> {

    /*
     * Greedy quantifiers implement maximal munch within each operator family. The alternatives start with
     * distinct characters, so recognition of a complete operator does not depend on alternation order.
     * Single ampersands and vertical bars are retained as UNKNOWN tokens for precise diagnostics.
     */
    private static final String OPERATOR_PATTERN =
        "!(?:={1,2}|!)?|={1,3}|[<>]=?|&{1,2}|[|]{1,2}";

    static final Pattern TOKENIZER_PATTERN = Pattern.compile(
        "\"[^\"\\\\]*(\\\\(.|\\n)[^\"\\\\]*)*\"|'[^'\\\\]*(\\\\(.|\\n)[^'\\\\]*)*'|" + // quoted strings
        "/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/|" + // block comments
        "\\d*\\.?\\d+(?:[dDfFlL])?(?!\\.)|" + // unsigned numbers; signs are grammar tokens
        OPERATOR_PATTERN + "|" + // operators and reserved operator characters
        "\\^|°|§|%|@|\\?|~|;|,|\\.|:|\\+|-(?:>)?|\\*|\\$|#|\\R|/{1,2}|" + // other tokens
        "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*|" + // Java identifiers
        "\\{|}|\\(|\\)|\\[|]", // braces/brackets/parens
        Pattern.DOTALL
    );

    private final SourceMappedText source;

    CurlyTokenizer(Class<TToken> tokenClass, SourceMappedText source) {
        super(source.getText(), tokenClass);
        this.source = source;
    }

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

    @Override
    protected SourceInfo getEndOfInputSourceInfo() {
        return source.getEndOfInput();
    }

}

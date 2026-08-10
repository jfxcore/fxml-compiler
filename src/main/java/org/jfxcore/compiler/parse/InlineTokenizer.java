// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.SourceInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class InlineTokenizer extends CurlyTokenizer<InlineToken> {

    public InlineTokenizer(String text, Location sourceOffset) {
        super(InlineToken.class, text, sourceOffset);
        normalizeSigns();
        concatPrefixesAndIdentifiers();
    }

    InlineTokenizer(SourceMappedText source) {
        super(InlineToken.class, source);
        normalizeSigns();
        concatPrefixesAndIdentifiers();
    }

    @Override
    protected InlineToken parseToken(String value, String line, SourceInfo sourceInfo) {
        return InlineToken.parse(value, line, sourceInfo);
    }

    @Override
    protected InlineToken newToken(CurlyTokenType type, String value, String line, SourceInfo sourceInfo) {
        return new InlineToken(type, value, line, sourceInfo);
    }

    private void normalizeSigns() {
        Deque<InlineToken> newTokens = new ArrayDeque<>(size());

        while (!isEmpty()) {
            InlineToken token = remove();
            String value = token.getValue();

            boolean signedNumber = token.getType() == CurlyTokenType.NUMBER
                && value.length() > 1
                && (value.charAt(0) == '+' || value.charAt(0) == '-');

            if (!signedNumber) {
                newTokens.add(token);
                continue;
            }

            SourceInfo sourceInfo = token.getSourceInfo();
            Location start = sourceInfo.getStart();
            Location valueStart = new Location(start.getLine(), start.getColumn() + 1);
            SourceInfo signSourceInfo = SourceInfo.subspan(sourceInfo, start, valueStart);
            SourceInfo valueSourceInfo = SourceInfo.subspan(sourceInfo, valueStart, sourceInfo.getEnd());

            newTokens.add(new InlineToken(
                value.charAt(0) == '+' ? CurlyTokenType.PLUS : CurlyTokenType.MINUS,
                value.substring(0, 1), token.getLine(), signSourceInfo));

            newTokens.add(new InlineToken(
                CurlyTokenType.NUMBER, value.substring(1), token.getLine(), valueSourceInfo));
        }

        addAll(newTokens);
    }

    private void concatPrefixesAndIdentifiers() {
        Deque<InlineToken> newTokens = new ArrayDeque<>(size());
        List<InlineToken> tempTokens = new ArrayList<>(4);

        while (!isEmpty()) {
            InlineToken current = remove();
            tempTokens.add(current);

            if (tempTokens.size() == 4) {
                newTokens.add(tempTokens.remove(0));
            }

            if (tempTokens.size() == 3
                    && tempTokens.get(0).getType() == CurlyTokenType.IDENTIFIER
                    && !"$".equals(tempTokens.get(0).getValue())
                    && tempTokens.get(1).getType() == CurlyTokenType.COLON
                    && tempTokens.get(2).getType() == CurlyTokenType.IDENTIFIER
                    && areAdjacent(tempTokens.get(0), tempTokens.get(1))
                    && areAdjacent(tempTokens.get(1), tempTokens.get(2))) {
                InlineToken token = new InlineToken(
                    CurlyTokenType.IDENTIFIER,
                    tempTokens.get(0).getValue() + ":" + current.getValue(),
                    tempTokens.get(0).getLine(),
                    SourceInfo.span(tempTokens.get(0).getSourceInfo(), tempTokens.get(2).getSourceInfo()));

                newTokens.add(token);
                tempTokens.clear();
            }
        }

        newTokens.addAll(tempTokens);
        addAll(newTokens);
    }

    private boolean areAdjacent(InlineToken first, InlineToken second) {
        return first.getSourceInfo().getEnd().equals(second.getSourceInfo().getStart());
    }
}

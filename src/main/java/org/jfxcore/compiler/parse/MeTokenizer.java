// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.SourceInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class MeTokenizer extends CurlyTokenizer<MeToken> {

    public MeTokenizer(String text, Location sourceOffset) {
        super(MeToken.class, text, sourceOffset);
        concatPrefixesAndIdentifiers();
    }

    @Override
    protected MeToken parseToken(String value, String line, SourceInfo sourceInfo) {
        return MeToken.parse(value, line, sourceInfo);
    }

    @Override
    protected MeToken newToken(CurlyTokenType type, String value, String line, SourceInfo sourceInfo) {
        return new MeToken(type, value, line, sourceInfo);
    }

    private void concatPrefixesAndIdentifiers() {
        Deque<MeToken> newTokens = new ArrayDeque<>(size());
        List<MeToken> tempTokens = new ArrayList<>(4);

        while (!isEmpty()) {
            MeToken current = remove();
            tempTokens.add(current);

            if (tempTokens.size() == 4) {
                newTokens.add(tempTokens.remove(0));
            }

            if (tempTokens.size() == 3
                    && tempTokens.get(0).getType() == CurlyTokenType.IDENTIFIER
                    && tempTokens.get(1).getType() == CurlyTokenType.COLON
                    && tempTokens.get(2).getType() == CurlyTokenType.IDENTIFIER) {
                MeToken token = new MeToken(
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

}

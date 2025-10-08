// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TypeTokenizer extends AbstractTokenizer<TypeTokenType, TypeToken> {

    private static final Pattern TOKENIZER_PATTERN = Pattern.compile(
        "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*|\\.|<|>|\\(|\\)|\\[|]|,|\\?");

    private final Location sourceOffset;

    public TypeTokenizer(Location sourceOffset, String text, Class<TypeToken> typeTokenClass) {
        super(text, typeTokenClass);
        this.sourceOffset = sourceOffset;
        tokenize(text);
    }

    private void tokenize(String text) {
        Matcher matcher = TOKENIZER_PATTERN.matcher(text);
        List<TypeToken> tokens = new ArrayList<>();
        int lastPosition = 0;

        while (matcher.find()) {
            String token = matcher.group();
            int start = matcher.start();
            int end = matcher.end();

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
            tokens.add(new TypeToken(token, getLines().get(sourceInfo.getStart().getLine()),
                                     SourceInfo.offset(sourceInfo, sourceOffset)));
            lastPosition = end;
        }

        addAll(tokens);
    }
}

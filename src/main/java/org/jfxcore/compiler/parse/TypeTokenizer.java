// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TypeTokenizer extends AbstractTokenizer<TypeTokenType, TypeToken> {

    private static final Pattern TOKENIZER_PATTERN = Pattern.compile(
        "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*|\\.|<|>|\\(|\\)|\\[|]|,|\\?");

    private final LexerInput input;
    private final int inputOffset;
    private final int inputLength;

    public TypeTokenizer(Location sourceOffset, String text, Class<TypeToken> typeTokenClass) {
        this(LexerInput.identity(text, sourceOffset), 0, text, typeTokenClass);
    }

    TypeTokenizer(LexerInput input, int inputOffset, String text, Class<TypeToken> typeTokenClass) {
        super(text, typeTokenClass);
        this.input = input;
        this.inputOffset = inputOffset;
        this.inputLength = text.length();
        tokenize(text);
    }

    @Override
    protected MarkupException unexpectedEnd(SourceInfo sourceInfo) {
        return ParserErrors.unexpectedEndOfTypeDeclaration(sourceInfo);
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
                throw ParserErrors.unexpectedToken(input.sourceInfo(
                    inputOffset + lastPosition + firstNonWhitespace, inputOffset + end));
            }

            SourceInfo localSourceInfo = getSourceInfo(start, end);
            SourceInfo sourceInfo = input.sourceInfo(inputOffset + start, inputOffset + end);
            tokens.add(new TypeToken(
                token, getLines().get(localSourceInfo.getStart().getLine()), sourceInfo));
            lastPosition = end;
        }

        addAll(tokens);
    }

    @Override
    protected SourceInfo getEndOfInputSourceInfo() {
        return input.sourceInfo(inputOffset + inputLength, inputOffset + inputLength);
    }
}

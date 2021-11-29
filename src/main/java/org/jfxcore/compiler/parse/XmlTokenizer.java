// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XmlTokenizer extends AbstractTokenizer<XmlTokenType, XmlToken> {

    private static final Pattern TOKENIZER_PATTERN = Pattern.compile(
        "\"[^\"\\\\]*(\\\\(.|\\n)[^\"\\\\]*)*\"|'[^'\\\\]*(\\\\(.|\\n)[^'\\\\]*)*'|" + // quoted strings
        "<!\\[CDATA\\[|]]>|" + // CDATA section
        "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*|" + // Java identifiers
        "<\\?|\\?>|<!--|-->|\\s+|."
    );

    public XmlTokenizer(String text) {
        super(text, XmlToken.class);
        tokenize(text);
    }

    private void tokenize(String text) {
        List<XmlToken> tokens = new ArrayList<>();
        Matcher matcher = TOKENIZER_PATTERN.matcher(text);

        while (matcher.find()) {
            String value = matcher.group();
            int start = matcher.start();
            int end = matcher.end() - 1;
            SourceInfo sourceInfo = getSourceInfo(start, end);
            tokens.add(new XmlToken(value, getLines().get(sourceInfo.getStart().getLine()), sourceInfo));
        }

        addAll(tokens);
    }
}

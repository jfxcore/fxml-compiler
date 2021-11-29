// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.NameHelper;

public class XmlToken extends AbstractToken<XmlTokenType> {

    public XmlToken(String value, String line, SourceInfo sourceInfo) {
        super(parseTokenType(value), value, line, sourceInfo);
    }

    private static XmlTokenType parseTokenType(String value) {
        switch (value) {
            case "<": return XmlTokenType.OPEN_BRACKET;
            case ">": return XmlTokenType.CLOSE_BRACKET;
            case "<?": return XmlTokenType.OPEN_PROCESSING_INSTRUCTION;
            case "?>": return XmlTokenType.CLOSE_PROCESSING_INSTRUCTION;
            case ":": return XmlTokenType.COLON;
            case "=": return XmlTokenType.EQUALS;
            case "/": return XmlTokenType.SLASH;
            case "<!--": return XmlTokenType.COMMENT_START;
            case "-->": return XmlTokenType.COMMENT_END;
            case "<![CDATA[": return XmlTokenType.CDATA_START;
            case "]]>": return XmlTokenType.CDATA_END;
        }

        if (value.isBlank()) {
            return XmlTokenType.WHITESPACE;
        }

        if (value.length() > 1 && (value.startsWith("'") && value.endsWith("'")
                || value.startsWith("\"") && value.endsWith("\""))) {
            return XmlTokenType.QUOTED_STRING;
        }

        return NameHelper.isJavaIdentifier(value) ? XmlTokenType.IDENTIFIER : XmlTokenType.OTHER;
    }

}

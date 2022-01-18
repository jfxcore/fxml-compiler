// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import java.util.ArrayList;
import java.util.List;

public class TypeFormatter {

    private final String text;
    private final Location sourceOffset;

    public TypeFormatter(String text) {
        this.text = text;
        this.sourceOffset = new Location(0, 0);
    }

    public TypeFormatter(String text, Location sourceOffset) {
        this.text = text;
        this.sourceOffset = sourceOffset;
    }

    public String format() {
        List<String> result = new ArrayList<>();
        TypeTokenizer tokenizer = new TypeTokenizer(sourceOffset, text, TypeToken.class);

        do {
            result.add(parseType(tokenizer));
        } while (tokenizer.poll(TypeTokenType.COMMA) != null);

        if (!tokenizer.isEmpty()) {
            throw ParserErrors.unexpectedToken(tokenizer.peekNotNull());
        }

        return String.join(", ", result);
    }

    private String parseType(TypeTokenizer tokenizer) {
        if (tokenizer.peekNotNull().getType() == TypeTokenType.WILDCARD) {
            throw ParserErrors.unexpectedToken(tokenizer.peekNotNull());
        }

        String typeName = tokenizer.removeQualifiedIdentifier(false).getValue();
        List<String> arguments = new ArrayList<>();

        if (tokenizer.poll(TypeTokenType.OPEN_ANGLE) != null) {
            arguments.add(parseType(tokenizer));

            while (tokenizer.poll(TypeTokenType.COMMA) != null) {
                arguments.add(parseType(tokenizer));
            }

            tokenizer.remove(TypeTokenType.CLOSE_ANGLE);
        }

        StringBuilder array = new StringBuilder();

        while (tokenizer.poll(TypeTokenType.OPEN_BRACKET) != null) {
            tokenizer.remove(TypeTokenType.CLOSE_BRACKET);
            array.append("[]");
        }

        StringBuilder result = new StringBuilder(typeName);

        if (arguments.size() > 0) {
            result.append("<");

            for (int i = 0; i < arguments.size(); ++i) {
                result.append(arguments.get(i));

                if (i < arguments.size() - 1) {
                    result.append(", ");
                }
            }

            result.append(">");
        }

        return result.toString() + array;
    }

    private static class Foo<T> {}

}

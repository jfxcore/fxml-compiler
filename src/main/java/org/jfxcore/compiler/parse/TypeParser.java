// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.ArrayList;
import java.util.List;

public class TypeParser {

    public record MethodInfo(
            List<TypeInstance> typeWitnesses,
            String methodName,
            SourceInfo sourceInfo) {}

    private final String text;
    private final Location sourceOffset;
    private final Resolver resolver;

    public TypeParser(String text) {
        this.text = text;
        this.sourceOffset = new Location(0, 0);
        this.resolver = new Resolver(SourceInfo.none());
    }

    public TypeParser(String text, Location sourceOffset) {
        int line = sourceOffset.getLine();
        int column = sourceOffset.getColumn();

        this.text = text;
        this.sourceOffset = sourceOffset;
        this.resolver = new Resolver(new SourceInfo(line, column, line, column + text.length()));
    }

    public List<TypeInstance> parse() {
        return parseText(text, 0);
    }

    public MethodInfo parseMethod() {
        int start = text.length() - text.stripLeading().length();
        var sourceInfo = new SourceInfo(
            sourceOffset.getLine(), sourceOffset.getColumn() + start,
            sourceOffset.getLine(), sourceOffset.getColumn() + start + text.trim().length());

        int closingAngleIndex = text.lastIndexOf('>');
        if (closingAngleIndex <= 0) {
            return new MethodInfo(List.of(), text.trim(), sourceInfo);
        }

        if (text.charAt(start) != '<') {
            throw ParserErrors.invalidExpression(sourceInfo);
        }

        return new MethodInfo(
            parseText(text.substring(start + 1, closingAngleIndex), start + 1),
            text.substring(closingAngleIndex + 1).trim(),
            sourceInfo);
    }

    private List<TypeInstance> parseText(String text, int offset) {
        List<TypeInstance> result = new ArrayList<>();
        TypeTokenizer tokenizer = new TypeTokenizer(
            new Location(this.sourceOffset.getLine(), this.sourceOffset.getColumn() + offset),
            text, TypeToken.class);

        do {
            result.add(parseType(tokenizer));
        } while (tokenizer.poll(TypeTokenType.COMMA) != null);

        if (!tokenizer.isEmpty()) {
            throw ParserErrors.unexpectedToken(tokenizer.peekNotNull());
        }

        return result;
    }

    private TypeInstance parseType(TypeTokenizer tokenizer) {
        if (tokenizer.poll(TypeTokenType.WILDCARD) != null) {
            TypeInstance objectInst;
            TypeInstance.WildcardType wildcardType;

            if (tokenizer.peek(TypeTokenType.UPPER_BOUND) != null) {
                wildcardType = TypeInstance.WildcardType.UPPER;
                tokenizer.remove();
                objectInst = parseType(tokenizer);
            } else if (tokenizer.peek(TypeTokenType.LOWER_BOUND) != null) {
                wildcardType = TypeInstance.WildcardType.LOWER;
                tokenizer.remove();
                objectInst = parseType(tokenizer);
            } else {
                wildcardType = TypeInstance.WildcardType.ANY;
                objectInst = resolver.getTypeInstance(Classes.ObjectType());
            }

            return objectInst.withWildcard(wildcardType);
        }

        String typeName = tokenizer.removeQualifiedIdentifier(false).getValue();
        List<TypeInstance> arguments = new ArrayList<>();

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

        return resolver.getTypeInstance(
            resolver.resolveClassAgainstImports(typeName + array),
            arguments);
    }
}

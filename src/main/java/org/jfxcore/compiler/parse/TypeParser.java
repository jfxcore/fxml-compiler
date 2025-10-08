// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeInstance;
import org.jfxcore.compiler.util.TypeInvoker;
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
    private final TypeInvoker invoker;

    public TypeParser(String text) {
        this.text = text;
        this.sourceOffset = new Location(0, 0);
        this.resolver = new Resolver(SourceInfo.none());
        this.invoker = new TypeInvoker(SourceInfo.none());
    }

    public TypeParser(String text, Location sourceOffset) {
        int line = sourceOffset.getLine();
        int column = sourceOffset.getColumn();
        var sourceInfo = new SourceInfo(line, column, line, column + text.length());

        this.text = text;
        this.sourceOffset = sourceOffset;
        this.resolver = new Resolver(sourceInfo);
        this.invoker = new TypeInvoker(sourceInfo);
    }

    public List<TypeInstance> parse() {
        return parseText(text, 0);
    }

    public MethodInfo parseMethod() {
        int start = -1;
        int end = -1;

        for (int i = 0; i < text.length(); ++i) {
            if (!Character.isWhitespace(text.charAt(i))) {
                start = i;
                break;
            }
        }

        for (int i = text.length() - 1; i >= 0; --i) {
            if (!Character.isWhitespace(text.charAt(i))) {
                end = i;
                break;
            }
        }

        if (start < 0) {
            throw ParserErrors.expectedIdentifier(
                new SourceInfo(sourceOffset.getLine(), sourceOffset.getColumn()));
        }

        var sourceInfo = new SourceInfo(
            sourceOffset.getLine(), sourceOffset.getColumn() + start,
            sourceOffset.getLine(), sourceOffset.getColumn() + end + 1);

        int openingAngleIndex = text.indexOf('<');
        if (openingAngleIndex < 0) {
            String methodName = text.trim();

            if (!NameHelper.isJavaIdentifier(methodName)) {
                throw ParserErrors.expectedIdentifier(
                    new SourceInfo(sourceOffset.getLine(), sourceOffset.getColumn() + start));
            }

            return new MethodInfo(List.of(), methodName, sourceInfo);
        }

        String methodName = text.substring(start, openingAngleIndex).trim();

        if (!NameHelper.isJavaIdentifier(methodName)) {
            throw ParserErrors.expectedIdentifier(
                new SourceInfo(sourceOffset.getLine(), sourceOffset.getColumn() + start));
        }

        if (text.charAt(end) != '>') {
            throw ParserErrors.expectedToken(
                new SourceInfo(sourceOffset.getLine(), sourceOffset.getColumn() + end), ">");
        }

        List<TypeInstance> typeWitnesses = parseText(
            text.substring(openingAngleIndex + 1, end), openingAngleIndex + 1);

        return new MethodInfo(typeWitnesses, methodName, sourceInfo);
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
                objectInst = invoker.invokeType(Classes.ObjectType());
            }

            return objectInst.withWildcard(wildcardType);
        }

        String typeName = tokenizer.removeQualifiedIdentifier(false).getValue();
        List<TypeInstance> arguments = new ArrayList<>();

        if (tokenizer.poll(TypeTokenType.OPEN_ANGLE) != null) {
            do {
                arguments.add(parseType(tokenizer));
            } while (tokenizer.poll(TypeTokenType.COMMA) != null);

            tokenizer.remove(TypeTokenType.CLOSE_ANGLE);
        }

        StringBuilder array = new StringBuilder();

        while (tokenizer.poll(TypeTokenType.OPEN_BRACKET) != null) {
            tokenizer.remove(TypeTokenType.CLOSE_BRACKET);
            array.append("[]");
        }

        return invoker.invokeType(
            resolver.resolveClassAgainstImports(typeName + array),
            arguments);
    }
}

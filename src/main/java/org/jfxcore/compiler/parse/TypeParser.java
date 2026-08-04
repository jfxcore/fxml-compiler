// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.NameHelper;
import java.util.ArrayList;
import java.util.List;

import static org.jfxcore.compiler.type.KnownSymbols.*;

public class TypeParser {

    public record MethodInfo(
            List<TypeInstance> typeWitnesses,
            String methodName,
            SourceInfo sourceInfo) {}

    private final String text;
    private final LexerInput input;
    private final Resolver resolver;
    private final TypeInvoker invoker;

    public TypeParser(String text) {
        this.text = text;
        this.input = LexerInput.identity(text, new Location(0, 0));
        this.resolver = new Resolver(SourceInfo.none());
        this.invoker = new TypeInvoker(SourceInfo.none());
    }

    public TypeParser(String text, SourceInfo sourceInfo) {
        this(text, LexerInput.identity(text, sourceInfo));
    }

    private TypeParser(String text, LexerInput input) {
        var sourceInfo = input.sourceInfo(0, text.length());

        this.text = text;
        this.input = input;
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
            throw ParserErrors.expectedIdentifier(input.sourceInfo(0, 0));
        }

        var sourceInfo = input.sourceInfo(start, end + 1);

        int openingAngleIndex = text.indexOf('<');
        if (openingAngleIndex < 0) {
            String methodName = text.trim();

            if (!NameHelper.isJavaIdentifier(methodName)) {
                throw ParserErrors.expectedIdentifier(input.sourceInfo(start, start));
            }

            return new MethodInfo(List.of(), methodName, sourceInfo);
        }

        String methodName = text.substring(start, openingAngleIndex).trim();

        if (!NameHelper.isJavaIdentifier(methodName)) {
            throw ParserErrors.expectedIdentifier(input.sourceInfo(start, start));
        }

        if (text.charAt(end) != '>') {
            throw ParserErrors.expectedToken(input.sourceInfo(end, end), ">");
        }

        List<TypeInstance> typeWitnesses = parseText(
            text.substring(openingAngleIndex + 1, end), openingAngleIndex + 1);

        return new MethodInfo(typeWitnesses, methodName, sourceInfo);
    }

    private List<TypeInstance> parseText(String text, int offset) {
        List<TypeInstance> result = new ArrayList<>();
        TypeTokenizer tokenizer = new TypeTokenizer(input, offset, text, TypeToken.class);

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
                objectInst = invoker.invokeType(ObjectDecl());
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

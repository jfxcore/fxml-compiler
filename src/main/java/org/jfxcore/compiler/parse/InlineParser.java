// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsic;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.CompositeNode;
import org.jfxcore.compiler.ast.text.ContextSelectorNode;
import org.jfxcore.compiler.ast.text.BooleanNode;
import org.jfxcore.compiler.ast.text.FunctionNode;
import org.jfxcore.compiler.ast.text.ListNode;
import org.jfxcore.compiler.ast.text.NumberNode;
import org.jfxcore.compiler.ast.text.PathNode;
import org.jfxcore.compiler.ast.text.PathSegmentNode;
import org.jfxcore.compiler.ast.text.SubPathSegmentNode;
import org.jfxcore.compiler.ast.text.TextSegmentNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import java.util.ArrayList;
import java.util.List;

import static org.jfxcore.compiler.parse.CurlyTokenType.*;

public class InlineParser {

    public static final String ONCE_EXPR_PREFIX = "$";
    public static final String BIND_EXPR_PREFIX = "${";
    public static final String BIND_BIDIRECTIONAL_EXPR_PREFIX = "#{";

    private record SyntaxMapping(String compact, Intrinsic intrinsic, boolean closingCurly) {}

    private static final SyntaxMapping[] SYNTAX_MAPPING = new SyntaxMapping[] {
        new SyntaxMapping(BIND_BIDIRECTIONAL_EXPR_PREFIX, Intrinsics.BIND_BIDIRECTIONAL, true),
        new SyntaxMapping(BIND_EXPR_PREFIX, Intrinsics.BIND, true),
        new SyntaxMapping(ONCE_EXPR_PREFIX, Intrinsics.ONCE, false),
    };

    private final String source;
    private final String intrinsicPrefix;
    private final Location sourceOffset;

    public InlineParser(String source, @Nullable String intrinsicPrefix) {
        this.source = source;
        this.intrinsicPrefix = intrinsicPrefix;
        this.sourceOffset = new Location(0, 0);
    }

    public InlineParser(String source, @Nullable String intrinsicPrefix, Location sourceOffset) {
        this.source = source;
        this.intrinsicPrefix = intrinsicPrefix;
        this.sourceOffset = sourceOffset;
    }

    public ObjectNode parseObject() {
        InlineTokenizer tokenizer = new InlineTokenizer(source, sourceOffset);
        ObjectNode result = parseObjectExpression(tokenizer, tryGetSyntaxMapping(tokenizer));
        if (!tokenizer.isEmpty()) {
            throw ParserErrors.unexpectedToken(tokenizer.peekNotNull());
        }

        return result;
    }

    public ValueNode parsePath() {
        InlineTokenizer tokenizer = new InlineTokenizer(source, sourceOffset);
        PathNode pathNode = parsePath(tokenizer, true, true, false);
        return !tokenizer.isEmpty() && tokenizer.peekNotNull().getType() == OPEN_PAREN ?
            parseFunctionExpression(tokenizer, pathNode) : pathNode;
    }

    private ValueNode parseExpression(InlineTokenizer tokenizer, boolean eager) {
        List<ValueNode> list = new ArrayList<>();
        List<ValueNode> values = new ArrayList<>();
        CurlyTokenClass nextTokenClass;

        do {
            values.add(parseSingleExpression(tokenizer));

            if (tokenizer.isEmpty()) {
                break;
            }

            if (tokenizer.peekNotNull().getType() == COMMA) {
                if (eager) {
                    break;
                }

                tokenizer.remove(COMMA);
                list.add(compositeNode(values));
                values.clear();
            }

            nextTokenClass = tokenizer.peekNotNull().getType().getTokenClass();
        } while (nextTokenClass != CurlyTokenClass.SEMI && nextTokenClass != CurlyTokenClass.DELIMITER);

        if (!values.isEmpty()) {
            list.add(compositeNode(values));
        }

        if (list.size() == 1) {
            return list.get(0);
        }

        return listNode(list);
    }

    private SyntaxMapping tryGetSyntaxMapping(InlineTokenizer tokenizer) {
        outerLoop:
        for (SyntaxMapping mapping : SYNTAX_MAPPING) {
            String value = tokenizer.peekNotNull().getValue();
            if (value.isEmpty() || value.charAt(0) != mapping.compact().charAt(0)) {
                continue;
            }

            InlineToken[] tokens = tokenizer.peekAhead(mapping.compact().length());
            if (tokens != null) {
                for (int i = 0; i < tokens.length; ++i) {
                    if (tokens[i].getValue().length() > 1 ||
                            tokens[i].getValue().charAt(0) != mapping.compact().charAt(i)) {
                        continue outerLoop;
                    }
                }

                return mapping;
            }
        }

        return null;
    }

    private ValueNode parseSingleExpression(InlineTokenizer tokenizer) {
        SyntaxMapping mapping = tryGetSyntaxMapping(tokenizer);
        if (mapping != null) {
            return parseObjectExpression(tokenizer, mapping);
        }

        return switch (tokenizer.peekNotNull().getType()) {
            case NUMBER -> {
                InlineToken number = tokenizer.remove(NUMBER);
                yield new NumberNode(number.getValue(), number.getSourceInfo());
            }

            case BOOLEAN -> {
                InlineToken bool = tokenizer.remove(BOOLEAN);
                yield new BooleanNode(bool.getValue(), bool.getSourceInfo());
            }

            case STRING -> {
                InlineToken string = tokenizer.remove(STRING);
                yield TextNode.createRawUnresolved(string.getValue(), string.getSourceInfo());
            }

            case IDENTIFIER, OPEN_ANGLE -> {
                PathNode path = parsePath(tokenizer, true, true, false);
                InlineToken token = tokenizer.peek();
                yield token != null && token.getType() == OPEN_PAREN ? parseFunctionExpression(tokenizer, path) : path;
            }

            case OPEN_CURLY -> parseObjectExpression(tokenizer, null);

            default -> {
                if (tokenizer.containsAhead(COLON, COLON)) {
                    PathNode path = parsePath(tokenizer, true, true, false);
                    yield tokenizer.peek(OPEN_PAREN) != null ? parseFunctionExpression(tokenizer, path) : path;
                }

                InlineToken token = tokenizer.remove();
                if (token.getType().getTokenClass() == CurlyTokenClass.DELIMITER) {
                    throw ParserErrors.unexpectedToken(token);
                }

                yield new TextNode(token.getValue(), token.getSourceInfo());
            }
        };
    }

    private ObjectNode parseObjectExpression(InlineTokenizer tokenizer, SyntaxMapping mapping) {
        SourceInfo sourceStart, sourceEnd;
        TextNode name;
        String cleanName;

        if (mapping != null) {
            sourceStart = tokenizer.remove().getSourceInfo();
            cleanName = mapping.intrinsic().getName();

            for (int i = 0; i < mapping.compact().length() - 1; ++i) {
                tokenizer.remove();
            }

            sourceEnd = new SourceInfo(
                sourceStart.getStart().getLine(),
                sourceStart.getStart().getColumn() + mapping.compact().length());

            name = new TextNode(
                intrinsicPrefix != null
                    ? intrinsicPrefix + ":" + mapping.intrinsic().getName()
                    : mapping.intrinsic().getName(),
                SourceInfo.span(sourceStart, sourceEnd));
        } else {
            sourceStart = sourceEnd = tokenizer.remove(OPEN_CURLY).getSourceInfo();
            name = parseIdentifier(tokenizer);
            cleanName = cleanIdentifier(name.getText(), name.getSourceInfo());
        }

        List<ValueNode> children = new ArrayList<>();
        List<PropertyNode> properties = new ArrayList<>();

        if (mapping == null) {
            eatSemis(tokenizer);
        }

        try {
            while (tokenizer.peek(CLOSE_CURLY) == null) {
                if (mapping != null) {
                    if (tokenizer.isEmpty()) {
                        break;
                    }

                    CurlyTokenClass tokenClass = tokenizer.peekNotNull().getType().getTokenClass();
                    if (!mapping.closingCurly() && tokenClass == CurlyTokenClass.DELIMITER) {
                        break;
                    }
                }

                tokenizer.mark();
                ValueNode key = parseExpression(tokenizer, mapping != null && !mapping.closingCurly());

                if (tokenizer.poll(EQUALS) != null) {
                    tokenizer.resetToMark();
                    PropertyNode propertyNode = parsePropertyExpression(tokenizer);
                    sourceEnd = propertyNode.getSourceInfo();
                    properties.add(propertyNode);
                } else {
                    tokenizer.forgetMark();
                    sourceEnd = key.getSourceInfo();
                    children.add(key);
                }

                if (mapping != null && !mapping.closingCurly() && !tokenizer.isEmpty()) {
                    var tokenClass = tokenizer.peekNotNull().getType().getTokenClass();
                    if (tokenClass == CurlyTokenClass.DELIMITER || tokenClass == CurlyTokenClass.SEMI) {
                        break;
                    }
                }

                eatSemis(tokenizer);
            }
        } catch (MarkupException ex) {
            if (ex.getDiagnostic().getCode() == ErrorCode.UNEXPECTED_END_OF_FILE) {
                throw ParserErrors.expectedToken(ex.getSourceInfo(), CLOSE_CURLY.getSymbol());
            }

            throw ex;
        }

        if (mapping == null || mapping.closingCurly()) {
            sourceEnd = tokenizer.remove(CLOSE_CURLY).getSourceInfo();
        }

        return new ObjectNode(
            new TypeNode(cleanName, name.getText(), !cleanName.equals(name.getText()), name.getSourceInfo()),
            properties, children, true, SourceInfo.span(sourceStart, sourceEnd));
    }

    private PropertyNode parsePropertyExpression(InlineTokenizer tokenizer) {
        TextNode propertyName = parseIdentifier(tokenizer);
        String cleanName = cleanIdentifier(propertyName.getText(), propertyName.getSourceInfo());
        tokenizer.remove(EQUALS);
        ValueNode value = parseExpression(tokenizer, false);

        return new PropertyNode(
            cleanName.split("\\."),
            propertyName.getText(),
            value,
            !propertyName.getText().equals(cleanName),
            false,
            SourceInfo.span(propertyName.getSourceInfo(), value.getSourceInfo()));
    }

    private FunctionNode parseFunctionExpression(InlineTokenizer tokenizer, PathNode functionName) {
        tokenizer.remove(OPEN_PAREN);
        ValueNode arguments = parseExpression(tokenizer, false);
        InlineToken lastToken = tokenizer.removeSkipWS(CLOSE_PAREN);

        return new FunctionNode(
            functionName,
            arguments instanceof ListNode listNode ? listNode.getValues() : List.of(arguments),
            SourceInfo.span(functionName.getSourceInfo(), lastToken.getSourceInfo()));
    }

    private TextNode parseIdentifier(InlineTokenizer tokenizer) {
        var text = new StringBuilder();
        SourceInfo start = tokenizer.peekNotNull().getSourceInfo(), end;
        InlineToken identifier = null;

        do {
            if (identifier != null && isIntrinsicIdentifier(identifier.getValue(), identifier.getSourceInfo())) {
                throw ParserErrors.unexpectedToken(tokenizer.peekNotNull());
            }

            if (!text.isEmpty()) {
                text.append(tokenizer.remove(DOT).getValue());
            }

            identifier = tokenizer.remove(IDENTIFIER);
            end = identifier.getSourceInfo();
            text.append(identifier.getValue());
        } while (tokenizer.peek(DOT) != null);

        return new TextNode(text.toString(), SourceInfo.span(start, end));
    }

    private PathNode parsePath(InlineTokenizer tokenizer,
                               boolean allowContextSelector,
                               boolean allowTypeWitnesses,
                               boolean allowTypeArguments) {
        var segments = new ArrayList<PathSegmentNode>();
        SourceInfo startSourceInfo = tokenizer.peekNotNull().getSourceInfo();
        ContextSelectorNode bindingContextSelector = null;

        if (allowContextSelector) {
            bindingContextSelector = tryParseContextSelector(tokenizer);
        }

        do {
            boolean colonSelector = false;

            if (tokenizer.poll(COLON) != null) {
                tokenizer.remove(COLON);
                colonSelector = true;
            } else if (!segments.isEmpty()) {
                tokenizer.remove(DOT);
            }

            if (tokenizer.peek(OPEN_PAREN) != null) {
                SourceInfo start = tokenizer.remove(OPEN_PAREN).getSourceInfo();
                PathNode path = parsePath(tokenizer, false, false, false);
                SourceInfo end = tokenizer.remove(CLOSE_PAREN).getSourceInfo();
                segments.add(new SubPathSegmentNode(colonSelector, path.getSegments(), SourceInfo.span(start, end)));
            } else {
                SourceInfo start = tokenizer.peekNotNull().getSourceInfo();
                PathInfo typeWitnesses = allowTypeWitnesses ? parseAngleBracketPath(tokenizer) : null;
                InlineToken identifier = tokenizer.remove(IDENTIFIER);

                segments.add(new TextSegmentNode(
                    colonSelector,
                    new TextNode(identifier.getValue(), identifier.getSourceInfo()),
                    typeWitnesses != null ? typeWitnesses.paths() : List.of(),
                    SourceInfo.span(start, identifier.getSourceInfo())));
            }
        } while (tokenizer.peek(DOT) != null || tokenizer.containsAhead(COLON, COLON));

        PathInfo typeArguments = allowTypeArguments ? parseAngleBracketPath(tokenizer) : null;

        return new PathNode(
            bindingContextSelector,
            segments,
            typeArguments != null ? typeArguments.paths() : List.of(),
            typeArguments != null
                ? SourceInfo.span(startSourceInfo, typeArguments.sourceInfo())
                : SourceInfo.span(startSourceInfo, segments.get(segments.size() - 1).getSourceInfo()));
    }

    private record PathInfo(List<PathNode> paths, SourceInfo sourceInfo) {}

    private PathInfo parseAngleBracketPath(InlineTokenizer tokenizer) {
        if (tokenizer.peek(OPEN_ANGLE) != null) {
            SourceInfo start = tokenizer.remove(OPEN_ANGLE).getSourceInfo();
            var result = new ArrayList<PathNode>();

            do {
                result.add(parsePath(tokenizer, false, false, true));
            } while (tokenizer.poll(COMMA) != null);

            SourceInfo end = tokenizer.remove(CLOSE_ANGLE).getSourceInfo();
            return new PathInfo(result, SourceInfo.span(start, end));
        }

        return null;
    }

    private ContextSelectorNode tryParseContextSelector(InlineTokenizer tokenizer) {
        ContextSelectorNode result = null;
        tokenizer.mark();

        try {
            InlineToken contextName = tokenizer.poll(IDENTIFIER);
            if (contextName == null) {
                return null;
            }

            if (tokenizer.poll(SLASH) != null) {
                return result = new ContextSelectorNode(
                    new TextNode(contextName.getValue(), contextName.getSourceInfo()),
                    null, null, contextName.getSourceInfo());
            }

            if (tokenizer.poll(OPEN_BRACKET) == null) {
                return null;
            }

            TextNode typeName = null;
            NumberNode depth = null;

            if (tokenizer.peek(IDENTIFIER) != null) {
                typeName = parseIdentifier(tokenizer);
            } else if (tokenizer.peek(NUMBER) != null) {
                var token = tokenizer.remove(NUMBER);
                depth = new NumberNode(token.getValue(), token.getSourceInfo());
            } else {
                return null;
            }

            if (depth != null) {
                if (!tokenizer.containsAhead(CLOSE_BRACKET, SLASH)) {
                    return null;
                }

                var token = tokenizer.remove(CLOSE_BRACKET);
                tokenizer.remove(SLASH);

                return result = new ContextSelectorNode(
                    new TextNode(contextName.getValue(), contextName.getSourceInfo()),
                    null,
                    depth,
                    SourceInfo.span(contextName.getSourceInfo(), token.getSourceInfo()));
            }

            if (tokenizer.containsAhead(CLOSE_BRACKET, SLASH)) {
                var token = tokenizer.remove(CLOSE_BRACKET);
                tokenizer.remove(SLASH);

                return result = new ContextSelectorNode(
                    new TextNode(contextName.getValue(), contextName.getSourceInfo()),
                    typeName,
                    null,
                    SourceInfo.span(contextName.getSourceInfo(), token.getSourceInfo()));
            }

            if (!tokenizer.containsAhead(COLON, NUMBER, CLOSE_BRACKET, SLASH)) {
                return null;
            }

            tokenizer.remove(COLON);
            var token = tokenizer.remove(NUMBER);
            depth = new NumberNode(token.getValue(), token.getSourceInfo());
            token = tokenizer.remove(CLOSE_BRACKET);
            tokenizer.remove(SLASH);

            var sourceInfo = SourceInfo.span(contextName.getSourceInfo(), token.getSourceInfo());

            return result = new ContextSelectorNode(
                new TextNode(contextName.getValue(), contextName.getSourceInfo()), typeName, depth, sourceInfo);
        } finally {
            if (result != null) {
                tokenizer.forgetMark();
            } else {
                tokenizer.resetToMark();
            }
        }
    }

    private void eatSemis(InlineTokenizer tokenizer) {
        while (tokenizer.peekSemi() != null) {
            tokenizer.remove();
        }
    }

    private boolean isIntrinsicIdentifier(String identifier, SourceInfo sourceInfo) {
        return !cleanIdentifier(identifier, sourceInfo).equals(identifier);
    }

    private String cleanIdentifier(String identifier, SourceInfo sourceInfo) {
        int index = identifier.indexOf(":");

        if (index >= 0) {
            String namespace = identifier.substring(0, index).trim();

            if (!namespace.equals(intrinsicPrefix)) {
                throw ParserErrors.unknownNamespace(sourceInfo, identifier.split(":")[0]);
            }
        }

        return index >= 0 ? identifier.substring(index + 1) : identifier;
    }

    private ValueNode listNode(List<? extends ValueNode> values) {
        if (values.size() == 1) {
            return values.get(0);
        }

        return new ListNode(
            values, SourceInfo.span(values.get(0).getSourceInfo(), values.get(values.size() - 1).getSourceInfo()));
    }

    private ValueNode compositeNode(List<? extends ValueNode> values) {
        if (values.size() == 1) {
            return values.get(0);
        }

        return new CompositeNode(
            values, SourceInfo.span(values.get(0).getSourceInfo(), values.get(values.size() - 1).getSourceInfo()));
    }
}

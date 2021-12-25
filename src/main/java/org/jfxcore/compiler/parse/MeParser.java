// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
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
import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;

import java.util.ArrayList;
import java.util.List;

import static org.jfxcore.compiler.parse.CurlyTokenType.*;

public class MeParser {

    private final String source;
    private final String intrinsicPrefix;
    private final Location sourceOffset;

    public MeParser(String source, @Nullable String intrinsicPrefix) {
        this.source = source;
        this.intrinsicPrefix = intrinsicPrefix;
        this.sourceOffset = new Location(0, 0);
    }

    public MeParser(String source, @Nullable String intrinsicPrefix, Location sourceOffset) {
        this.source = source;
        this.intrinsicPrefix = intrinsicPrefix;
        this.sourceOffset = sourceOffset;
    }

    public ValueNode parsePath() {
        MeTokenizer tokenizer = new MeTokenizer(source.trim(), sourceOffset);
        PathNode pathNode = parsePath(tokenizer, true);
        return tokenizer.size() > 0 && tokenizer.peekNotNull().getType() == OPEN_PAREN ?
            parseFunctionExpression(tokenizer, pathNode) : pathNode;
    }

    public ObjectNode tryParseObject() {
        String trimmed = source.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }

        MeTokenizer tokenizer = new MeTokenizer(source, sourceOffset);
        return parseObjectExpression(tokenizer);
    }

    private ValueNode parseExpression(MeTokenizer tokenizer) {
        List<ValueNode> list = new ArrayList<>();
        List<ValueNode> values = new ArrayList<>();
        CurlyTokenClass nextTokenClass;

        do {
            values.add(parseSingleExpression(tokenizer));

            if (tokenizer.peekNotNull().getType() == COMMA) {
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

    private ValueNode parseSingleExpression(MeTokenizer tokenizer) {
        return switch (tokenizer.peekNotNull().getType()) {
            case NUMBER -> {
                MeToken number = tokenizer.remove(NUMBER);
                yield new NumberNode(number.getValue(), number.getSourceInfo());
            }

            case BOOLEAN -> {
                MeToken bool = tokenizer.remove(BOOLEAN);
                yield new BooleanNode(bool.getValue(), bool.getSourceInfo());
            }

            case STRING -> {
                MeToken string = tokenizer.remove(STRING);
                yield TextNode.createRawUnresolved(string.getValue(), string.getSourceInfo());
            }

            case IDENTIFIER -> {
                PathNode path = parsePath(tokenizer, true);
                yield tokenizer.peekNotNull().getType() == OPEN_PAREN ? parseFunctionExpression(tokenizer, path) : path;
            }

            case OPEN_CURLY -> parseObjectExpression(tokenizer);

            default -> {
                if (tokenizer.containsAhead(COLON, COLON)) {
                    PathNode path = parsePath(tokenizer, true);
                    yield tokenizer.peekNotNull().getType() == OPEN_PAREN ? parseFunctionExpression(tokenizer, path) : path;
                }

                MeToken token = tokenizer.remove();
                if (token.getType().getTokenClass() == CurlyTokenClass.DELIMITER) {
                    throw ParserErrors.unexpectedToken(token);
                }

                yield new TextNode(token.getValue(), token.getSourceInfo());
            }
        };
    }

    private ObjectNode parseObjectExpression(MeTokenizer tokenizer) {
        MeToken openCurly = tokenizer.remove(OPEN_CURLY);
        TextNode name = parseIdentifier(tokenizer);
        String cleanName = cleanIdentifier(name.getText(), name.getSourceInfo());
        List<ValueNode> children = new ArrayList<>();
        List<PropertyNode> properties = new ArrayList<>();
        eatSemis(tokenizer);

        while (tokenizer.peek(CLOSE_CURLY) == null) {
            tokenizer.mark();
            ValueNode key = parseExpression(tokenizer);

            if (tokenizer.poll(EQUALS) != null) {
                tokenizer.resetToMark();
                properties.add(parsePropertyExpression(tokenizer));
            } else {
                tokenizer.forgetMark();
                children.add(key);
            }

            eatSemis(tokenizer);
        }

        MeToken closeCurly = tokenizer.remove(CLOSE_CURLY);

        return new ObjectNode(
            new TypeNode(cleanName, name.getText(), !cleanName.equals(name.getText()), name.getSourceInfo()),
            properties, children, SourceInfo.span(openCurly.getSourceInfo(), closeCurly.getSourceInfo()));
    }

    private PropertyNode parsePropertyExpression(MeTokenizer tokenizer) {
        TextNode propertyName = parseIdentifier(tokenizer);
        tokenizer.remove(EQUALS);
        ValueNode value = parseExpression(tokenizer);

        return new PropertyNode(
            propertyName.getText().split("\\."),
            propertyName.getText(),
            value,
            false,
            SourceInfo.span(propertyName.getSourceInfo(), value.getSourceInfo()));
    }

    private FunctionNode parseFunctionExpression(MeTokenizer tokenizer, PathNode functionName) {
        tokenizer.remove(OPEN_PAREN);
        ValueNode arguments = parseExpression(tokenizer);
        MeToken lastToken = tokenizer.remove(CLOSE_PAREN);

        return new FunctionNode(
            functionName,
            arguments instanceof ListNode listNode ? listNode.getValues() : List.of(arguments),
            SourceInfo.span(functionName.getSourceInfo(), lastToken.getSourceInfo()));
    }

    private TextNode parseIdentifier(MeTokenizer tokenizer) {
        var text = new StringBuilder();
        SourceInfo start = tokenizer.peekNotNull().getSourceInfo(), end;
        MeToken identifier = null;

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

    private PathNode parsePath(MeTokenizer tokenizer, boolean allowContextSelector) {
        var segments = new ArrayList<PathSegmentNode>();
        SourceInfo start = tokenizer.peekNotNull().getSourceInfo(), end;
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

            if (tokenizer.poll(OPEN_PAREN) != null) {
                PathNode path = parsePath(tokenizer, false);
                segments.add(new SubPathSegmentNode(colonSelector, path.getSegments(), path.getSourceInfo()));
                end = tokenizer.remove(CLOSE_PAREN).getSourceInfo();
            } else {
                var identifier = tokenizer.remove(IDENTIFIER);
                end = identifier.getSourceInfo();
                segments.add(new TextSegmentNode(
                    colonSelector, new TextNode(identifier.getValue(), identifier.getSourceInfo())));
            }
        } while (tokenizer.peek(DOT) != null || tokenizer.containsAhead(COLON, COLON));

        return new PathNode(bindingContextSelector, segments, SourceInfo.span(start, end));
    }

    private ContextSelectorNode tryParseContextSelector(MeTokenizer tokenizer) {
        ContextSelectorNode result = null;
        tokenizer.mark();

        try {
            MeToken contextName = tokenizer.poll(IDENTIFIER);
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

    private void eatSemis(MeTokenizer tokenizer) {
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

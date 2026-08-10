// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.AttachedSegmentNode;
import org.jfxcore.compiler.ast.text.BinaryOperator;
import org.jfxcore.compiler.ast.text.BinaryOperatorNode;
import org.jfxcore.compiler.ast.text.CompositeNode;
import org.jfxcore.compiler.ast.text.ContextSelector;
import org.jfxcore.compiler.ast.text.ContextSelectorNode;
import org.jfxcore.compiler.ast.text.InvocationNode;
import org.jfxcore.compiler.ast.text.ListNode;
import org.jfxcore.compiler.ast.text.LiteralKeywordNode;
import org.jfxcore.compiler.ast.text.NumberNode;
import org.jfxcore.compiler.ast.text.ParenthesizedNode;
import org.jfxcore.compiler.ast.text.PathNode;
import org.jfxcore.compiler.ast.text.PathSegmentNode;
import org.jfxcore.compiler.ast.text.SelectedMemberNode;
import org.jfxcore.compiler.ast.text.StringLiteralNode;
import org.jfxcore.compiler.ast.text.TextSegmentNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.ast.text.UnaryOperator;
import org.jfxcore.compiler.ast.text.UnaryOperatorNode;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import static org.jfxcore.compiler.parse.CurlyTokenType.*;

public class InlineParser {

    public static final String EVALUATE_EXPR_PREFIX = "$";
    public static final String OBSERVE_EXPR_PREFIX = "${";
    public static final String PUSH_EXPR_PREFIX = ">{";
    public static final String SYNCHRONIZE_EXPR_PREFIX = "#{";

    private record SyntaxMapping(String compact, String name, boolean intrinsic, boolean closingCurly) {}

    private enum ParseMode {
        VALUE,
        EXPRESSION
    }

    private record InvocationInfo(
            List<ValueNode> arguments,
            SourceInfo openParenSourceInfo,
            SourceInfo closeParenSourceInfo) {}

    private static final SyntaxMapping[] SYNTAX_MAPPING = new SyntaxMapping[] {
        new SyntaxMapping(SYNCHRONIZE_EXPR_PREFIX, Intrinsics.SYNCHRONIZE.getName(), true, true),
        new SyntaxMapping(OBSERVE_EXPR_PREFIX, Intrinsics.OBSERVE.getName(), true, true),
        new SyntaxMapping(PUSH_EXPR_PREFIX, Intrinsics.PUSH.getName(), true, true),
        new SyntaxMapping(EVALUATE_EXPR_PREFIX, Intrinsics.EVALUATE.getName(), true, false),
    };

    private final SourceMappedText input;
    private final String intrinsicPrefix;
    private final Map<Character, String> prefixMappings;

    public InlineParser(String source, @Nullable String intrinsicPrefix) {
        this(source, intrinsicPrefix, new Location(0, 0), Map.of());
    }

    public InlineParser(String source, @Nullable String intrinsicPrefix, Map<Character, String> prefixMappings) {
        this(source, intrinsicPrefix, new Location(0, 0), prefixMappings);
    }

    public InlineParser(String source,
                        @Nullable String intrinsicPrefix,
                        Location sourceOffset,
                        Map<Character, String> prefixMappings) {
        this(SourceMappedText.identity(source, sourceOffset), intrinsicPrefix, prefixMappings);
    }

    InlineParser(SourceMappedText input,
                 @Nullable String intrinsicPrefix,
                 Map<Character, String> prefixMappings) {
        this.input = input;
        this.intrinsicPrefix = intrinsicPrefix;
        this.prefixMappings = Map.copyOf(prefixMappings);
    }

    public ObjectNode parseObject() {
        InlineTokenizer tokenizer = new InlineTokenizer(input);
        ObjectNode result = parseObjectExpression(tokenizer, tryGetSyntaxMapping(tokenizer));
        if (!tokenizer.isEmpty()) {
            throw ParserErrors.unexpectedToken(tokenizer.peekNotNull());
        }

        return result;
    }

    public ValueNode parsePath() {
        InlineTokenizer tokenizer = new InlineTokenizer(input);
        return parseSelectedMemberSuffixes(
            tokenizer, parsePathOrInvocation(tokenizer, ParseMode.EXPRESSION), ParseMode.EXPRESSION);
    }

    public ValueNode parseExpression() {
        InlineTokenizer tokenizer = new InlineTokenizer(input);
        ValueNode result = parseExpression(tokenizer);
        if (!tokenizer.isEmpty()) {
            throw ParserErrors.unexpectedToken(tokenizer.peekNotNull());
        }

        return result;
    }

    private ValueNode parseValueSequence(InlineTokenizer tokenizer, boolean eager, ParseMode mode) {
        List<ValueNode> list = new ArrayList<>();
        List<ValueNode> values = new ArrayList<>();
        CurlyTokenClass nextTokenClass;

        do {
            values.add(parseSingleValue(tokenizer, mode));

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

        InlineToken token = tokenizer.peek();
        if (token != null && token.getValue().length() == 1) {
            String mappedType = prefixMappings.get(token.getValue().charAt(0));
            if (mappedType != null) {
                return new SyntaxMapping(token.getValue(), mappedType, false, false);
            }
        }

        return null;
    }

    private ValueNode parseSingleValue(InlineTokenizer tokenizer, ParseMode mode) {
        SyntaxMapping mapping = tryGetSyntaxMapping(tokenizer);
        if (mapping != null) {
            return parseObjectExpression(tokenizer, mapping);
        }

        CurlyTokenType tokenType = tokenizer.peekNotNull().getType();
        if (mode == ParseMode.EXPRESSION && isExpressionToken(tokenType)) {
            return parseExpression(tokenizer);
        }

        return switch (tokenType) {
            case NUMBER -> {
                InlineToken number = tokenizer.remove(NUMBER);
                yield new NumberNode(number.getValue(), number.getSourceInfo());
            }

            case PLUS, MINUS -> parseSignedLiteral(tokenizer);

            case STRING -> {
                InlineToken string = tokenizer.remove(STRING);
                yield new StringLiteralNode(
                    string.getValue(), string.getLexeme(), string.getSourceInfo());
            }

            case IDENTIFIER, COLON -> parsePathPrimary(tokenizer, ParseMode.VALUE);

            case KEYWORD -> throw ParserErrors.unexpectedToken(tokenizer.peekNotNull());

            case OPEN_CURLY -> parseObjectExpression(tokenizer, null);

            default -> {
                if (tokenizer.containsAhead(COLON, COLON)) {
                    PathNode path = parsePath(tokenizer, true, true, false);
                    yield tokenizer.peek(OPEN_PAREN) != null
                        ? parseInvocationExpression(tokenizer, path, ParseMode.VALUE)
                        : path;
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
            InlineToken firstToken = tokenizer.remove();
            InlineToken lastToken = firstToken;
            sourceStart = firstToken.getSourceInfo();
            cleanName = mapping.name();

            for (int i = 0; i < mapping.compact().length() - 1; ++i) {
                lastToken = tokenizer.remove();
            }

            sourceEnd = lastToken.getSourceInfo();

            name = new TextNode(
                intrinsicPrefix != null && mapping.intrinsic()
                    ? intrinsicPrefix + ":" + mapping.name()
                    : mapping.name(),
                SourceInfo.span(sourceStart, sourceEnd));
        } else {
            sourceStart = sourceEnd = tokenizer.remove(OPEN_CURLY).getSourceInfo();
            name = parseIdentifier(tokenizer);
            cleanName = cleanIdentifier(name.getText(), name.getSourceInfo());
        }

        List<ValueNode> children = new ArrayList<>();
        List<PropertyNode> properties = new ArrayList<>();
        boolean bindingExpression = isBindingExpression(mapping, cleanName, name.getText());

        if (mapping == null) {
            if (tokenizer.peek(OPEN_ANGLE) != null) {
                PropertyNode typeArgs = parseTypeArguments(tokenizer);
                name = new TextNode(name.getText(), SourceInfo.span(name.getSourceInfo(), typeArgs.getSourceInfo()));
                properties.add(typeArgs);
            }

            eatSemis(tokenizer);
        } else if (tokenizer.peek(OPEN_ANGLE) != null) {
            throw ParserErrors.expectedIdentifier(tokenizer.peekNotNull().getSourceInfo());
        } else if (!mapping.closingCurly()) {
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
                ValueNode key = parseValueSequence(
                    tokenizer,
                    mapping != null && !mapping.closingCurly(),
                    bindingExpression ? ParseMode.EXPRESSION : ParseMode.VALUE);

                if (tokenizer.poll(EQUALS) != null) {
                    tokenizer.resetToMark();
                    PropertyNode propertyNode = parsePropertyExpression(tokenizer, bindingExpression);
                    sourceEnd = propertyNode.getSourceInfo();
                    properties.add(propertyNode);
                } else {
                    tokenizer.forgetMark();
                    sourceEnd = key.getSourceInfo();
                    children.add(key);
                }

                if (mapping != null && !mapping.closingCurly() && !tokenizer.isEmpty()) {
                    var tokenClass = tokenizer.peekNotNull().getType().getTokenClass();
                    if (tokenClass == CurlyTokenClass.DELIMITER
                            || tokenClass == CurlyTokenClass.SEMI && mapping.intrinsic()) {
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
            new TypeNode(
                cleanName,
                name.getText(),
                mapping != null ? mapping.intrinsic() : !cleanName.equals(name.getText()),
                name.getSourceInfo()),
            properties, children, true, SourceInfo.span(sourceStart, sourceEnd));
    }

    private PropertyNode parsePropertyExpression(InlineTokenizer tokenizer, boolean bindingExpression) {
        TextNode propertyName = parseIdentifier(tokenizer);
        String cleanName = cleanIdentifier(propertyName.getText(), propertyName.getSourceInfo());
        tokenizer.remove(EQUALS);
        ValueNode value = parseValueSequence(
            tokenizer,
            false,
            bindingExpression && "source".equals(cleanName)
                ? ParseMode.EXPRESSION : ParseMode.VALUE);

        return new PropertyNode(
            cleanName.split("\\."),
            propertyName.getText(),
            value,
            !propertyName.getText().equals(cleanName),
            false,
            SourceInfo.span(propertyName.getSourceInfo(), value.getSourceInfo()));
    }

    private InvocationNode parseInvocationExpression(InlineTokenizer tokenizer, ValueNode target, ParseMode mode) {
        if (!isCallableTarget(target)) {
            throw ParserErrors.unexpectedToken(tokenizer.peekNotNull());
        }

        InvocationInfo invocation = mode == ParseMode.EXPRESSION
            ? parseExpressionInvocationArguments(tokenizer)
            : parseValueInvocationArguments(tokenizer);

        return new InvocationNode(
            target,
            invocation.arguments(),
            invocation.openParenSourceInfo(),
            invocation.closeParenSourceInfo(),
            SourceInfo.span(target.getSourceInfo(), invocation.closeParenSourceInfo()));
    }

    private boolean isCallableTarget(ValueNode target) {
        if (target instanceof SelectedMemberNode) {
            return true;
        }

        if (!(target instanceof PathNode path)) {
            return false;
        }

        List<PathSegmentNode> segments = path.getSegments();
        return !segments.isEmpty()
            && segments.get(segments.size() - 1) instanceof TextSegmentNode;
    }

    private InvocationInfo parseExpressionInvocationArguments(InlineTokenizer tokenizer) {
        SourceInfo openParenSourceInfo = tokenizer.remove(OPEN_PAREN).getSourceInfo();
        List<ValueNode> arguments = new ArrayList<>();

        if (tokenizer.peekSkipWS(CLOSE_PAREN) == null) {
            while (true) {
                SyntaxMapping mapping = tryGetSyntaxMapping(tokenizer);
                ValueNode argument = mapping != null || tokenizer.peek(OPEN_CURLY) != null
                    ? parseObjectExpression(tokenizer, mapping)
                    : parseExpression(tokenizer);
                arguments.add(argument);

                if (tokenizer.peekSkipWS(CLOSE_PAREN) != null) {
                    break;
                }

                tokenizer.removeSkipWS(COMMA);
                if (tokenizer.peekSkipWS(CLOSE_PAREN) != null) {
                    throw ParserErrors.unexpectedToken(tokenizer.peekNotNullSkipWS());
                }
            }
        }

        SourceInfo closeParenSourceInfo = tokenizer.removeSkipWS(CLOSE_PAREN).getSourceInfo();
        return new InvocationInfo(arguments, openParenSourceInfo, closeParenSourceInfo);
    }

    private InvocationInfo parseValueInvocationArguments(InlineTokenizer tokenizer) {
        SourceInfo openParenSourceInfo = tokenizer.remove(OPEN_PAREN).getSourceInfo();
        ValueNode arguments = tokenizer.peekSkipWS(CLOSE_PAREN) == null
            ? parseValueSequence(tokenizer, false, ParseMode.VALUE)
            : null;

        SourceInfo closeParenSourceInfo = tokenizer.removeSkipWS(CLOSE_PAREN).getSourceInfo();

        return new InvocationInfo(
            arguments instanceof ListNode listNode
                ? listNode.getValues()
                : arguments != null
                    ? List.of(arguments)
                    : List.of(),
            openParenSourceInfo,
            closeParenSourceInfo);
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
                               boolean allowPostfixTypeArguments,
                               boolean allowTypeArguments) {
        var segments = new ArrayList<PathSegmentNode>();
        SourceInfo startSourceInfo = tokenizer.peekNotNull().getSourceInfo();
        ContextSelectorNode bindingContextSelector = null;

        if (allowContextSelector && tokenizer.containsAhead(COLON, COLON)) {
            SourceInfo selectorSourceInfo = parseObservableSelector(tokenizer);
            segments.add(parsePathSegment(
                tokenizer, true, selectorSourceInfo, allowPostfixTypeArguments));
        } else if (allowContextSelector && tokenizer.peek(COLON) != null) {
            bindingContextSelector = parseContextSelector(tokenizer);
        } else {
            segments.add(parsePathSegment(tokenizer, false, null, allowPostfixTypeArguments));
        }

        while (tokenizer.containsAhead(COLON, COLON) || tokenizer.peek(DOT) != null) {
            boolean observableSelector;
            SourceInfo selectorSourceInfo;
            InlineToken firstColon = tokenizer.poll(COLON);

            if (firstColon != null) {
                InlineToken secondColon = tokenizer.remove(COLON);
                observableSelector = true;
                selectorSourceInfo = SourceInfo.span(firstColon.getSourceInfo(), secondColon.getSourceInfo());
            } else {
                observableSelector = false;
                selectorSourceInfo = tokenizer.remove(DOT).getSourceInfo();
            }

            segments.add(parsePathSegment(
                tokenizer, observableSelector, selectorSourceInfo, allowPostfixTypeArguments));
        }

        PathInfo typeArguments = allowTypeArguments ? parseAngleBracketPath(tokenizer) : null;

        SourceInfo endSourceInfo = typeArguments != null
            ? typeArguments.sourceInfo()
            : !segments.isEmpty()
                ? segments.get(segments.size() - 1).getSourceInfo()
                : bindingContextSelector.getSourceInfo();

        return new PathNode(
            bindingContextSelector,
            segments,
            typeArguments != null ? typeArguments.paths() : List.of(),
            SourceInfo.span(startSourceInfo, endSourceInfo));
    }

    private SourceInfo parseObservableSelector(InlineTokenizer tokenizer) {
        InlineToken firstColon = tokenizer.remove(COLON);
        InlineToken secondColon = tokenizer.remove(COLON);
        return SourceInfo.span(firstColon.getSourceInfo(), secondColon.getSourceInfo());
    }

    private PathSegmentNode parsePathSegment(
            InlineTokenizer tokenizer,
            boolean observableSelector,
            @Nullable SourceInfo selectorSourceInfo,
            boolean allowPostfixTypeArguments) {
        if (tokenizer.peek(OPEN_PAREN) != null) {
            return parseAttachedSegment(tokenizer, observableSelector, selectorSourceInfo);
        }

        InlineToken identifier = tokenizer.remove(IDENTIFIER);
        PathInfo typeArguments = allowPostfixTypeArguments
            ? tryParsePostfixTypeArguments(tokenizer) : null;

        return new TextSegmentNode(
            observableSelector,
            new TextNode(identifier.getValue(), identifier.getSourceInfo()),
            typeArguments != null ? typeArguments.paths() : List.of(),
            selectorSourceInfo,
            typeArguments != null ? typeArguments.sourceInfo() : null,
            typeArguments != null
                ? SourceInfo.span(identifier.getSourceInfo(), typeArguments.sourceInfo())
                : identifier.getSourceInfo());
    }

    private AttachedSegmentNode parseAttachedSegment(
            InlineTokenizer tokenizer,
            boolean observableSelector,
            SourceInfo selectorSourceInfo) {
        SourceInfo openParenSourceInfo = tokenizer.remove(OPEN_PAREN).getSourceInfo();
        List<InlineToken> identifiers = new ArrayList<>();
        List<InlineToken> separators = new ArrayList<>();
        identifiers.add(tokenizer.remove(IDENTIFIER));

        while (tokenizer.containsAhead(DOT, IDENTIFIER)) {
            separators.add(tokenizer.remove(DOT));
            identifiers.add(tokenizer.remove(IDENTIFIER));
        }

        if (identifiers.size() < 2) {
            throw ParserErrors.expectedToken(tokenizer.peekNotNull().getSourceInfo(), DOT.getSymbol());
        }

        SourceInfo closeParenSourceInfo = tokenizer.remove(CLOSE_PAREN).getSourceInfo();
        InlineToken propertyName = identifiers.get(identifiers.size() - 1);
        InlineToken declaringTypeEnd = identifiers.get(identifiers.size() - 2);
        String declaringTypeName = identifiers.stream()
            .limit(identifiers.size() - 1L)
            .map(InlineToken::getValue)
            .collect(java.util.stream.Collectors.joining("."));

        return new AttachedSegmentNode(
            observableSelector,
            new TextNode(
                declaringTypeName,
                SourceInfo.span(identifiers.get(0).getSourceInfo(), declaringTypeEnd.getSourceInfo())),
            new TextNode(propertyName.getValue(), propertyName.getSourceInfo()),
            selectorSourceInfo,
            openParenSourceInfo,
            separators.get(separators.size() - 1).getSourceInfo(),
            closeParenSourceInfo,
            SourceInfo.span(openParenSourceInfo, closeParenSourceInfo));
    }

    private ValueNode parsePathPrimary(InlineTokenizer tokenizer, ParseMode mode) {
        return parseSelectedMemberSuffixes(
            tokenizer, parsePathOrInvocation(tokenizer, mode), mode);
    }

    private ValueNode parsePathOrInvocation(InlineTokenizer tokenizer, ParseMode mode) {
        PathNode path = parsePath(tokenizer, true, true, false);
        return tokenizer.peek(OPEN_PAREN) != null
            ? parseInvocationExpression(tokenizer, path, mode)
            : path;
    }

    private ValueNode parseSelectedMemberSuffixes(InlineTokenizer tokenizer, ValueNode receiver, ParseMode mode) {
        while (tokenizer.peek(DOT) != null || tokenizer.containsAhead(COLON, COLON)) {
            boolean observableSelector;
            SourceInfo selectorSourceInfo;
            InlineToken firstColon = tokenizer.poll(COLON);

            if (firstColon != null) {
                InlineToken secondColon = tokenizer.remove(COLON);
                observableSelector = true;
                selectorSourceInfo = SourceInfo.span(firstColon.getSourceInfo(), secondColon.getSourceInfo());
            } else {
                observableSelector = false;
                selectorSourceInfo = tokenizer.remove(DOT).getSourceInfo();
            }

            InlineToken identifier = tokenizer.remove(IDENTIFIER);
            PathInfo typeArguments = tryParsePostfixTypeArguments(tokenizer);
            SourceInfo memberSourceInfo = typeArguments != null
                ? SourceInfo.span(identifier.getSourceInfo(), typeArguments.sourceInfo())
                : identifier.getSourceInfo();

            TextSegmentNode member = new TextSegmentNode(
                observableSelector,
                new TextNode(identifier.getValue(), identifier.getSourceInfo()),
                typeArguments != null ? typeArguments.paths() : List.of(),
                selectorSourceInfo,
                typeArguments != null ? typeArguments.sourceInfo() : null,
                memberSourceInfo);

            receiver = new SelectedMemberNode(
                receiver, member, SourceInfo.span(receiver.getSourceInfo(), memberSourceInfo));

            if (tokenizer.peek(OPEN_PAREN) != null) {
                receiver = parseInvocationExpression(tokenizer, receiver, mode);
            }
        }

        return receiver;
    }

    private record PathInfo(List<PathNode> paths, SourceInfo sourceInfo) {}

    private PathInfo parseAngleBracketPath(InlineTokenizer tokenizer) {
        if (tokenizer.peek(OPEN_ANGLE) != null) {
            SourceInfo start = tokenizer.remove(OPEN_ANGLE).getSourceInfo();
            var result = new ArrayList<PathNode>();

            do {
                result.add(parseTypePath(tokenizer));
            } while (tokenizer.poll(COMMA) != null);

            SourceInfo end = tokenizer.remove(CLOSE_ANGLE).getSourceInfo();
            return new PathInfo(result, SourceInfo.span(start, end));
        }

        return null;
    }

    /**
     * Tentatively parses an expression-segment type list. A complete list wins over
     * relational syntax only when the following token can legally follow a postfix expression.
     */
    private PathInfo tryParsePostfixTypeArguments(InlineTokenizer tokenizer) {
        if (tokenizer.peek(OPEN_ANGLE) == null) {
            return null;
        }

        InlineToken[] candidateTokens = tokenizer.peekAhead(tokenizer.size());
        tokenizer.mark();

        try {
            PathInfo result = parseAngleBracketPath(tokenizer);
            if (isPostfixFollower(tokenizer)) {
                tokenizer.forgetMark();
                return result;
            }

            tokenizer.resetToMark();
            return null;
        } catch (MarkupException ex) {
            if (hasCompletedMalformedPostfixTypeArguments(candidateTokens)) {
                tokenizer.forgetMark();
                throw ex;
            }

            tokenizer.resetToMark();
            return null;
        }
    }

    private boolean hasCompletedMalformedPostfixTypeArguments(InlineToken[] tokens) {
        if (tokens == null || tokens.length == 0) {
            return false;
        }

        int depth = 0;

        for (int i = 0; i < tokens.length; ++i) {
            switch (tokens[i].getType()) {
                case OPEN_ANGLE -> ++depth;
                case CLOSE_ANGLE -> {
                    if (--depth == 0) {
                        InlineToken follower = i + 1 < tokens.length ? tokens[i + 1] : null;
                        return isPostfixFollower(follower, i + 2 < tokens.length ? tokens[i + 2] : null);
                    }

                    if (depth < 0) {
                        return false;
                    }
                }

                case IDENTIFIER, DOT, COMMA -> {
                    if (depth == 0) {
                        return false;
                    }
                }

                case KEYWORD -> {
                    if (depth == 0 || !isPrimitiveTypeKeyword(tokens[i].getValue())) {
                        return false;
                    }
                }

                default -> {
                    return false;
                }
            }
        }

        return false;
    }

    private boolean isPostfixFollower(InlineTokenizer tokenizer) {
        InlineToken first = tokenizer.peek();
        InlineToken[] tokens = tokenizer.peekAhead(2);
        InlineToken second = tokens != null ? tokens[1] : null;
        return isPostfixFollower(first, second);
    }

    private boolean isPostfixFollower(@Nullable InlineToken first, @Nullable InlineToken second) {
        if (first == null) {
            return true;
        }

        return switch (first.getType()) {
            case OPEN_PAREN, DOT,
                 CLOSE_PAREN, CLOSE_CURLY, CLOSE_BRACKET, COMMA, SEMICOLON, NEWLINE,
                 PLUS, MINUS, STAR, SLASH,
                 OPEN_ANGLE, CLOSE_ANGLE, LESS_THAN_OR_EQUAL, GREATER_THAN_OR_EQUAL,
                 VALUE_EQUALITY, VALUE_INEQUALITY, IDENTITY_EQUALITY, IDENTITY_INEQUALITY,
                 LOGICAL_AND, LOGICAL_OR -> true;
            case COLON -> second != null && second.getType() == COLON;
            default -> false;
        };
    }

    private PathNode parseTypePath(InlineTokenizer tokenizer) {
        InlineToken token = tokenizer.peek(KEYWORD);
        if (token == null || !isPrimitiveTypeKeyword(token.getValue())) {
            return parsePath(tokenizer, false, false, true);
        }

        token = tokenizer.remove(KEYWORD);
        TextNode name = new TextNode(token.getValue(), token.getSourceInfo());
        TextSegmentNode segment = new TextSegmentNode(false, name, List.of(), null, token.getSourceInfo());
        return new PathNode(null, List.of(segment), List.of(), token.getSourceInfo());
    }

    private boolean isPrimitiveTypeKeyword(String value) {
        return switch (value) {
            case "boolean", "byte", "char", "short", "int", "long", "float", "double", "void" -> true;
            default -> false;
        };
    }

    private PropertyNode parseTypeArguments(InlineTokenizer tokenizer) {
        int scope = 1;
        var builder = new StringBuilder();
        SourceInfo start = tokenizer.remove(OPEN_ANGLE).getSourceInfo();
        SourceInfo end = start;
        InlineToken lastToken = null;

        while (!tokenizer.isEmpty() && scope > 0) {
            if (tokenizer.peek(OPEN_ANGLE) != null) {
                scope++;
            } else if (tokenizer.peek(CLOSE_ANGLE) != null) {
                scope--;
            }

            InlineToken token = tokenizer.remove();
            end = token.getSourceInfo();

            if (scope > 0) {
                if (lastToken != null && lastToken.getType() == IDENTIFIER && token.getType() == IDENTIFIER) {
                    builder.append(' ');
                }

                builder.append(token.getValue());
                lastToken = token;
            }
        }

        String intrinsicName = Intrinsics.TYPE_ARGUMENTS.getName();

        return new PropertyNode(
            new String[] { intrinsicName },
            intrinsicPrefix != null ? intrinsicPrefix + ":" + intrinsicName : intrinsicName,
            new TextNode(builder.toString(), SourceInfo.span(start, end)),
            true, false, SourceInfo.span(start, end));
    }

    private ContextSelectorNode parseContextSelector(InlineTokenizer tokenizer) {
        SourceInfo colonSourceInfo = tokenizer.remove(COLON).getSourceInfo();
        InlineToken selectorToken = tokenizer.remove(IDENTIFIER);
        ContextSelector selector = ContextSelector.tryParse(selectorToken.getValue());

        if (selector == null) {
            throw ParserErrors.unexpectedToken(selectorToken);
        }

        TextNode searchType = null;
        NumberNode level = null;
        SourceInfo openParenSourceInfo = null;
        SourceInfo commaSourceInfo = null;
        SourceInfo closeParenSourceInfo = null;

        if (tokenizer.peek(OPEN_PAREN) != null) {
            if (selector != ContextSelector.PARENT) {
                throw ParserErrors.unexpectedToken(tokenizer.peekNotNull());
            }

            openParenSourceInfo = tokenizer.remove(OPEN_PAREN).getSourceInfo();

            if (tokenizer.peek(PLUS) != null
                    || tokenizer.peek(MINUS) != null
                    || tokenizer.peek(NUMBER) != null) {
                level = parseSignedInteger(tokenizer);
            } else {
                searchType = parseIdentifier(tokenizer);

                InlineToken comma = tokenizer.poll(COMMA);
                if (comma != null) {
                    commaSourceInfo = comma.getSourceInfo();
                    level = parseSignedInteger(tokenizer);
                }
            }

            closeParenSourceInfo = tokenizer.remove(CLOSE_PAREN).getSourceInfo();
        }

        SourceInfo sourceInfo = SourceInfo.span(
            colonSourceInfo,
            closeParenSourceInfo != null ? closeParenSourceInfo : selectorToken.getSourceInfo());

        return new ContextSelectorNode(
            selector,
            searchType,
            level,
            colonSourceInfo,
            selectorToken.getSourceInfo(),
            openParenSourceInfo,
            commaSourceInfo,
            closeParenSourceInfo,
            sourceInfo);
    }

    private NumberNode parseSignedInteger(InlineTokenizer tokenizer) {
        InlineToken sign = tokenizer.poll(PLUS);
        if (sign == null) {
            sign = tokenizer.poll(MINUS);
        }

        InlineToken number = tokenizer.remove(NUMBER);
        if (!number.getValue().matches("[0-9]+")) {
            throw ParserErrors.unexpectedToken(number);
        }

        return sign != null
            ? new NumberNode(
                sign.getValue() + number.getValue(),
                SourceInfo.span(sign.getSourceInfo(), number.getSourceInfo()))
            : new NumberNode(number.getValue(), number.getSourceInfo());
    }

    private void eatSemis(InlineTokenizer tokenizer) {
        while (tokenizer.peekSemi() != null) {
            tokenizer.remove();
        }
    }

    private ValueNode parseExpression(InlineTokenizer tokenizer) {
        ValueNode left = parseLogicalAndExpression(tokenizer);

        while (tokenizer.peek(LOGICAL_OR) != null) {
            InlineToken operator = tokenizer.remove();
            ValueNode right = parseLogicalAndExpression(tokenizer);
            left = createBinaryOperator(operator, left, right);
        }

        return left;
    }

    private ValueNode parseLogicalAndExpression(InlineTokenizer tokenizer) {
        ValueNode left = parseEqualityExpression(tokenizer);

        while (tokenizer.peek(LOGICAL_AND) != null) {
            InlineToken operator = tokenizer.remove();
            ValueNode right = parseEqualityExpression(tokenizer);
            left = createBinaryOperator(operator, left, right);
        }

        return left;
    }

    private ValueNode parseEqualityExpression(InlineTokenizer tokenizer) {
        ValueNode left = parseRelationalExpression(tokenizer);

        while (tokenizer.peek(VALUE_EQUALITY) != null
                || tokenizer.peek(VALUE_INEQUALITY) != null
                || tokenizer.peek(IDENTITY_EQUALITY) != null
                || tokenizer.peek(IDENTITY_INEQUALITY) != null) {
            InlineToken operator = tokenizer.remove();
            ValueNode right = parseRelationalExpression(tokenizer);
            left = createBinaryOperator(operator, left, right);
        }

        return left;
    }

    private ValueNode parseRelationalExpression(InlineTokenizer tokenizer) {
        ValueNode left = parseAdditiveExpression(tokenizer);

        while (tokenizer.peek(OPEN_ANGLE) != null
                || tokenizer.peek(LESS_THAN_OR_EQUAL) != null
                || tokenizer.peek(CLOSE_ANGLE) != null
                || tokenizer.peek(GREATER_THAN_OR_EQUAL) != null) {
            InlineToken operator = tokenizer.remove();
            ValueNode right = parseAdditiveExpression(tokenizer);
            left = createBinaryOperator(operator, left, right);
        }

        return left;
    }

    private ValueNode parseAdditiveExpression(InlineTokenizer tokenizer) {
        ValueNode left = parseMultiplicativeExpression(tokenizer);

        while (tokenizer.peek(PLUS) != null || tokenizer.peek(MINUS) != null) {
            InlineToken operator = tokenizer.remove();
            ValueNode right = parseMultiplicativeExpression(tokenizer);
            left = createBinaryOperator(operator, left, right);
        }

        return left;
    }

    private ValueNode parseMultiplicativeExpression(InlineTokenizer tokenizer) {
        ValueNode left = parseUnaryExpression(tokenizer);

        while (tokenizer.peek(STAR) != null || tokenizer.peek(SLASH) != null) {
            InlineToken operator = tokenizer.remove();
            ValueNode right = parseUnaryExpression(tokenizer);
            left = createBinaryOperator(operator, left, right);
        }

        return left;
    }

    private ValueNode parseUnaryExpression(InlineTokenizer tokenizer) {
        InlineToken operator = switch (tokenizer.peekNotNull().getType()) {
            case PLUS, MINUS, NOT, BOOLIFY -> tokenizer.remove();
            default -> null;
        };

        if (operator == null) {
            return parsePostfixExpression(tokenizer);
        }

        ValueNode operand = parseUnaryExpression(tokenizer);
        UnaryOperator unaryOperator = switch (operator.getType()) {
            case PLUS -> UnaryOperator.PLUS;
            case MINUS -> UnaryOperator.MINUS;
            case NOT -> UnaryOperator.NOT;
            case BOOLIFY -> UnaryOperator.BOOLIFY;
            default -> throw new IllegalArgumentException(operator.getType().toString());
        };

        return new UnaryOperatorNode(
            unaryOperator,
            operand,
            operator.getSourceInfo(),
            SourceInfo.span(operator.getSourceInfo(), operand.getSourceInfo()));
    }

    private ValueNode parsePostfixExpression(InlineTokenizer tokenizer) {
        return parseSelectedMemberSuffixes(
            tokenizer, parsePrimaryExpression(tokenizer), ParseMode.EXPRESSION);
    }

    private ValueNode parsePrimaryExpression(InlineTokenizer tokenizer) {
        InlineToken token = tokenizer.peekNotNull();

        if (token.getType() == STRING) {
            tokenizer.remove();
            return new StringLiteralNode(token.getValue(), token.getLexeme(), token.getSourceInfo());
        }

        if (token.getType() == NUMBER) {
            tokenizer.remove();
            return new NumberNode(token.getValue(), token.getSourceInfo());
        }

        if (token.getType() == IDENTIFIER || token.getType() == COLON) {
            if (token.getType() == COLON) {
                return parsePathOrInvocation(tokenizer, ParseMode.EXPRESSION);
            }

            LiteralKeywordNode literal = LiteralKeywordNode.tryCreate(token.getValue(), token.getSourceInfo());
            if (literal != null) {
                tokenizer.remove();
                return literal;
            }

            return parsePathOrInvocation(tokenizer, ParseMode.EXPRESSION);
        }

        if (token.getType() == OPEN_PAREN) {
            SourceInfo start = tokenizer.remove().getSourceInfo();
            ValueNode operand = parseExpression(tokenizer);
            SourceInfo end = tokenizer.remove(CLOSE_PAREN).getSourceInfo();
            return new ParenthesizedNode(operand, start, end, SourceInfo.span(start, end));
        }

        throw ParserErrors.unexpectedToken(token);
    }

    private BinaryOperatorNode createBinaryOperator(
            InlineToken operator, ValueNode left, ValueNode right) {
        BinaryOperator binaryOperator = switch (operator.getType()) {
            case PLUS -> BinaryOperator.ADD;
            case MINUS -> BinaryOperator.SUBTRACT;
            case STAR -> BinaryOperator.MULTIPLY;
            case SLASH -> BinaryOperator.DIVIDE;
            case OPEN_ANGLE -> BinaryOperator.LESS_THAN;
            case LESS_THAN_OR_EQUAL -> BinaryOperator.LESS_THAN_OR_EQUAL;
            case CLOSE_ANGLE -> BinaryOperator.GREATER_THAN;
            case GREATER_THAN_OR_EQUAL -> BinaryOperator.GREATER_THAN_OR_EQUAL;
            case VALUE_EQUALITY -> BinaryOperator.VALUE_EQUAL;
            case VALUE_INEQUALITY -> BinaryOperator.VALUE_NOT_EQUAL;
            case IDENTITY_EQUALITY -> BinaryOperator.IDENTITY_EQUAL;
            case IDENTITY_INEQUALITY -> BinaryOperator.IDENTITY_NOT_EQUAL;
            case LOGICAL_AND -> BinaryOperator.LOGICAL_AND;
            case LOGICAL_OR -> BinaryOperator.LOGICAL_OR;
            default -> throw new IllegalArgumentException(operator.getType().toString());
        };

        return new BinaryOperatorNode(
            binaryOperator,
            left,
            right,
            operator.getSourceInfo(),
            SourceInfo.span(left.getSourceInfo(), right.getSourceInfo()));
    }

    private boolean isExpressionToken(CurlyTokenType type) {
        return switch (type) {
            case STRING, NUMBER, IDENTIFIER, KEYWORD, OPEN_PAREN,
                 PLUS, MINUS, STAR, SLASH, NOT, BOOLIFY,
                 OPEN_ANGLE, CLOSE_ANGLE, LESS_THAN_OR_EQUAL, GREATER_THAN_OR_EQUAL,
                 VALUE_EQUALITY, VALUE_INEQUALITY, IDENTITY_EQUALITY, IDENTITY_INEQUALITY,
                 LOGICAL_AND, LOGICAL_OR, COLON -> true;
            default -> false;
        };
    }

    private ValueNode parseSignedLiteral(InlineTokenizer tokenizer) {
        InlineToken sign = tokenizer.remove();
        InlineToken next = tokenizer.peek();

        if (next != null && next.getType() == NUMBER) {
            tokenizer.remove();
            return new NumberNode(
                sign.getValue() + next.getValue(), SourceInfo.span(sign.getSourceInfo(), next.getSourceInfo()));
        }

        if (sign.getType() == MINUS && next != null && next.getType() == IDENTIFIER) {
            tokenizer.remove();
            InlineToken identifier = new InlineToken(
                IDENTIFIER, sign.getValue() + next.getValue(), sign.getLine(),
                SourceInfo.span(sign.getSourceInfo(), next.getSourceInfo()));
            tokenizer.addFirst(identifier);
            return parsePath(tokenizer, true, true, false);
        }

        return new TextNode(sign.getValue(), sign.getSourceInfo());
    }

    private boolean isBindingExpression(SyntaxMapping mapping, String cleanName, String markupName) {
        boolean intrinsic = mapping != null ? mapping.intrinsic() : !cleanName.equals(markupName);
        return intrinsic && (Intrinsics.EVALUATE.getName().equals(cleanName)
            || Intrinsics.OBSERVE.getName().equals(cleanName)
            || Intrinsics.PUSH.getName().equals(cleanName)
            || Intrinsics.SYNCHRONIZE.getName().equals(cleanName));
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

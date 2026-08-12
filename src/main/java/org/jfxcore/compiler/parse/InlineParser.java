// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AttributeValueNode;
import org.jfxcore.compiler.ast.ContentSelectionNode;
import org.jfxcore.compiler.ast.IdentifierNode;
import org.jfxcore.compiler.ast.InlineArgumentSequenceNode;
import org.jfxcore.compiler.ast.LiteralValueNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.SyntaxNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsic;
import org.jfxcore.compiler.ast.intrinsic.IntrinsicProperty;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.AttachedSegmentNode;
import org.jfxcore.compiler.ast.text.BinaryOperator;
import org.jfxcore.compiler.ast.text.BinaryOperatorNode;
import org.jfxcore.compiler.ast.text.ContextSelector;
import org.jfxcore.compiler.ast.text.ContextSelectorNode;
import org.jfxcore.compiler.ast.text.InvocationNode;
import org.jfxcore.compiler.ast.text.LiteralKeywordNode;
import org.jfxcore.compiler.ast.text.NumberNode;
import org.jfxcore.compiler.ast.text.ParenthesizedNode;
import org.jfxcore.compiler.ast.text.PathNode;
import org.jfxcore.compiler.ast.text.PathSegmentNode;
import org.jfxcore.compiler.ast.text.SelectedMemberNode;
import org.jfxcore.compiler.ast.text.StringLiteralNode;
import org.jfxcore.compiler.ast.text.TextSegmentNode;
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
import org.jfxcore.compiler.util.StringHelper;

import static org.jfxcore.compiler.parse.CurlyTokenType.*;

public class InlineParser {

    public static final String EVALUATE_EXPR_PREFIX = "$";
    public static final String OBSERVE_EXPR_PREFIX = "${";
    public static final String PUSH_EXPR_PREFIX = ">{";
    public static final String SYNCHRONIZE_EXPR_PREFIX = "#{";

    private record SyntaxMapping(String compact, String name, boolean intrinsic, boolean closingCurly) {}

    private enum ParseMode {
        VALUE,
        EXPRESSION,
        PATH_REFERENCE
    }

    private record InvocationInfo(
            List<Node> arguments,
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

    public ObjectNode parseObjectStrict() {
        InlineTokenizer tokenizer = new InlineTokenizer(input);
        ObjectNode result = parseObjectExpression(tokenizer, tryGetSyntaxMapping(tokenizer));
        if (!tokenizer.isEmpty()) {
            throw ParserErrors.unexpectedToken(tokenizer.peekNotNull());
        }

        return result;
    }

    public SyntaxNode parsePathReferenceStrict() {
        InlineTokenizer tokenizer = new InlineTokenizer(input);
        SyntaxNode result = parseSelectedMemberSuffixes(tokenizer, parsePathOrInvocation(tokenizer));

        if (!tokenizer.isEmpty()) {
            throw ParserErrors.unexpectedToken(tokenizer.peekNotNull());
        }

        if (!(result instanceof PathNode) && !(result instanceof SelectedMemberNode)) {
            throw ParserErrors.invalidExpression(result.getSourceInfo());
        }

        return result;
    }

    public SyntaxNode parseExpressionStrict() {
        InlineTokenizer tokenizer = new InlineTokenizer(input);
        SyntaxNode result = parseContentSelectionOrExpression(tokenizer);
        if (!tokenizer.isEmpty()) {
            throw ParserErrors.unexpectedToken(tokenizer.peekNotNull());
        }

        return result;
    }

    /**
     * Parses a complete XML attribute through the shared value-language grammar.
     */
    AttributeValueNode parseAttribute(AttributeMode mode) {
        return switch (mode) {
            case EXPRESSION -> AttributeValueNode.syntax(
                parseExpressionStrict(), input.getSourceInfo(0, input.getText().length()));

            case PATH_REFERENCE -> AttributeValueNode.syntax(
                parsePathReferenceStrict(), input.getSourceInfo(0, input.getText().length()));

            case GENERIC -> parseGenericAttribute();
        };
    }

    private AttributeValueNode parseGenericAttribute() {
        SourceCursor cursor = new SourceCursor(input);
        List<Node> items = new ArrayList<>();
        List<Integer> escapeOffsets = new ArrayList<>();
        boolean hasActiveItem = false;

        while (true) {
            skipHorizontalLayout(cursor);
            int itemStart = cursor.getOffset();

            if (!cursor.isAtEnd()
                    && cursor.peek() == '\\'
                    && activePrefixLength(cursor.getText(), itemStart + 1) > 0) {
                int itemEnd = findOuterComma(cursor.getText(), itemStart);
                SourceMappedText itemSource = input.slice(itemStart, itemEnd).without(0);
                items.add(createScalarLiteral(itemSource));
                escapeOffsets.add(itemStart);
                cursor.setOffset(itemEnd);
            } else if (activePrefixLength(cursor.getText(), itemStart) > 0) {
                hasActiveItem = true;
                items.add(parseObjectItem(cursor));

                if (!cursor.isAtEnd() && cursor.peek() != ',') {
                    throw ParserErrors.unexpectedToken(
                        input.getSourceInfo(cursor.getOffset(), Math.min(cursor.length(), cursor.getOffset() + 1)));
                }
            } else {
                int itemEnd = findOuterComma(cursor.getText(), itemStart);
                items.add(createScalarLiteral(input.slice(itemStart, itemEnd)));
                cursor.setOffset(itemEnd);
            }

            if (cursor.isAtEnd()) {
                break;
            }

            cursor.advance(); // outer comma
            consumeSeparatorLayout(cursor);

            // A trailing comma contributes a final empty member.
            if (cursor.isAtEnd()) {
                items.add(createScalarLiteral(input.slice(cursor.length(), cursor.length())));
                break;
            }
        }

        SourceMappedText logicalInput = escapeOffsets.isEmpty()
            ? input
            : input.withoutAll(escapeOffsets.stream().mapToInt(Integer::intValue).toArray());

        SourceInfo sourceInfo = logicalInput.getSourceInfo(0, logicalInput.getText().length());
        if (!hasActiveItem) {
            return AttributeValueNode.literal(createLiteral(logicalInput), sourceInfo);
        }

        return AttributeValueNode.sequence(items, sourceInfo);
    }

    private ObjectNode parseObjectItem(SourceCursor cursor) {
        InlineTokenizer tokenizer = new InlineTokenizer(cursor);
        ObjectNode result = parseObjectExpression(tokenizer, tryGetSyntaxMapping(tokenizer));
        tokenizer.commit();
        return result;
    }

    private int activePrefixLength(String text, int offset) {
        if (offset < 0 || offset >= text.length()) {
            return 0;
        }

        for (String prefix : new String[] {
                OBSERVE_EXPR_PREFIX, SYNCHRONIZE_EXPR_PREFIX,
                PUSH_EXPR_PREFIX, EVALUATE_EXPR_PREFIX, "{" }) {
            if (text.startsWith(prefix, offset)) {
                return prefix.length();
            }
        }

        return prefixMappings.containsKey(text.charAt(offset)) ? 1 : 0;
    }

    private int findOuterComma(String text, int start) {
        int comma = text.indexOf(',', start);
        return comma >= 0 ? comma : text.length();
    }

    private void skipHorizontalLayout(SourceCursor cursor) {
        while (!cursor.isAtEnd() && isHorizontalWhitespace(cursor.peek())) {
            cursor.advance();
        }
    }

    private void consumeSeparatorLayout(SourceCursor cursor) {
        int checkpoint = cursor.checkpoint();
        skipHorizontalLayout(cursor);

        if (cursor.isAtEnd() || !isLineBreak(cursor.peek())) {
            cursor.reset(checkpoint);
            return;
        }

        consumeLineBreak(cursor);

        while (!cursor.isAtEnd()) {
            int beforeLayout = cursor.checkpoint();
            skipHorizontalLayout(cursor);

            if (!cursor.isAtEnd() && isLineBreak(cursor.peek())) {
                consumeLineBreak(cursor);
            } else {
                cursor.reset(beforeLayout);
                break;
            }
        }
    }

    private void consumeLineBreak(SourceCursor cursor) {
        char ch = cursor.peek();
        cursor.advance();

        if (ch == '\r' && !cursor.isAtEnd() && cursor.peek() == '\n') {
            cursor.advance();
        }
    }

    private boolean isHorizontalWhitespace(char ch) {
        return ch == '\t' || ch == ' ' || ch == '\u00A0' || ch == '\u1680'
            || ch >= '\u2000' && ch <= '\u200A' || ch == '\u202F'
            || ch == '\u205F' || ch == '\u3000';
    }

    private boolean isLineBreak(char ch) {
        return ch == '\r' || ch == '\n' || ch == '\u000B' || ch == '\u000C'
            || ch == '\u0085' || ch == '\u2028' || ch == '\u2029';
    }

    private LiteralValueNode createLiteral(SourceMappedText source) {
        String text = source.getText();
        List<StringHelper.OffsetPart> split = StringHelper.splitListWithOffsets(text);
        List<LiteralValueNode> parts = split.size() > 1
            ? split.stream()
                .map(part -> new LiteralValueNode(
                    part.text(), source.getSourceInfo(part.start(), part.end())))
                .toList()
            : List.of();

        return new LiteralValueNode(text, parts, source.getSourceInfo(0, text.length()));
    }

    private LiteralValueNode createScalarLiteral(SourceMappedText source) {
        return new LiteralValueNode(source.getText(), source.getSourceInfo(0, source.getText().length()));
    }

    private Node parseValueSequence(InlineTokenizer tokenizer, boolean eager, ParseMode mode) {
        if (mode == ParseMode.EXPRESSION) {
            return parseContentSelectionOrExpression(tokenizer);
        }

        if (mode == ParseMode.PATH_REFERENCE) {
            return parseSelectedMemberSuffixes(tokenizer, parsePathOrInvocation(tokenizer));
        }

        List<Node> values = new ArrayList<>();
        values.add(parseInlineValue(tokenizer));

        while (!tokenizer.isEmpty() && tokenizer.peekNotNull().getType() == COMMA && !eager) {
            tokenizer.remove(COMMA);
            values.add(parseInlineValue(tokenizer));
        }

        if (values.size() == 1) {
            return values.get(0);
        }

        var sourceInfo = SourceInfo.span(values.get(0).getSourceInfo(), values.get(values.size() - 1).getSourceInfo());
        return new InlineArgumentSequenceNode(values, sourceInfo);
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

    private Node parseInlineValue(InlineTokenizer tokenizer) {
        SyntaxMapping mapping = tryGetSyntaxMapping(tokenizer);
        if (mapping != null) {
            return parseObjectExpression(tokenizer, mapping);
        }

        CurlyTokenType tokenType = tokenizer.peekNotNull().getType();
        if (tokenType == OPEN_CURLY) {
            return parseObjectExpression(tokenizer, null);
        }

        if (tokenType == STRING) {
            InlineToken string = tokenizer.remove(STRING);
            return new LiteralValueNode(string.getValue(), string.getSourceInfo());
        }

        List<String> fragments = new ArrayList<>();
        List<CurlyTokenType> delimiters = new ArrayList<>();
        SourceInfo start = tokenizer.peekNotNull().getSourceInfo();
        SourceInfo end = start;

        while (!tokenizer.isEmpty()) {
            InlineToken token = tokenizer.peekNotNull();
            CurlyTokenType type = token.getType();
            boolean atTopLevel = delimiters.isEmpty();

            if (atTopLevel && (type == COMMA || type == EQUALS
                    || type.getTokenClass() == CurlyTokenClass.SEMI
                    || type == CLOSE_CURLY || type == CLOSE_PAREN)) {
                break;
            }

            if (atTopLevel && !fragments.isEmpty()
                    && (type == OPEN_CURLY || tryGetSyntaxMapping(tokenizer) != null)) {
                throw ParserErrors.unexpectedToken(token);
            }

            if (isClosingDelimiter(type) && !delimiters.isEmpty()) {
                CurlyTokenType expected = delimiters.get(delimiters.size() - 1);
                if (type != expected) {
                    throw ParserErrors.expectedToken(token.getSourceInfo(), expected.getSymbol());
                }

                delimiters.remove(delimiters.size() - 1);
            }

            tokenizer.remove();
            end = token.getSourceInfo();

            if (type != NEWLINE) {
                fragments.add(type == STRING ? token.getLexeme() : token.getValue());
            }

            switch (type) {
                case OPEN_PAREN -> delimiters.add(CLOSE_PAREN);
                case OPEN_BRACKET -> delimiters.add(CLOSE_BRACKET);
                case OPEN_CURLY -> delimiters.add(CLOSE_CURLY);
                case OPEN_ANGLE -> delimiters.add(CLOSE_ANGLE);
                default -> {}
            }
        }

        if (fragments.isEmpty()) {
            throw ParserErrors.unexpectedToken(tokenizer.peekNotNull());
        }

        if (!delimiters.isEmpty()) {
            CurlyTokenType expected = delimiters.get(delimiters.size() - 1);
            throw ParserErrors.expectedToken(input.getEndOfInput(), expected.getSymbol());
        }

        return new LiteralValueNode(StringHelper.concatValues(fragments), SourceInfo.span(start, end));
    }

    private boolean isClosingDelimiter(CurlyTokenType type) {
        return type == CLOSE_PAREN || type == CLOSE_BRACKET
            || type == CLOSE_CURLY || type == CLOSE_ANGLE;
    }

    private ObjectNode parseObjectExpression(InlineTokenizer tokenizer, SyntaxMapping mapping) {
        SourceInfo sourceStart, sourceEnd;
        IdentifierNode name;
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

            name = new IdentifierNode(
                intrinsicPrefix != null && mapping.intrinsic()
                    ? intrinsicPrefix + ":" + mapping.name()
                    : mapping.name(),
                SourceInfo.span(sourceStart, sourceEnd));
        } else {
            sourceStart = sourceEnd = tokenizer.remove(OPEN_CURLY).getSourceInfo();
            name = parseIdentifier(tokenizer);
            cleanName = cleanIdentifier(name.getName(), name.getSourceInfo());
        }

        List<Node> children = new ArrayList<>();
        List<PropertyNode> properties = new ArrayList<>();
        Intrinsic intrinsic = findIntrinsic(mapping, cleanName, name.getName());

        if (mapping == null) {
            if (tokenizer.peek(OPEN_ANGLE) != null) {
                PropertyNode typeArgs = parseTypeArguments(tokenizer);
                name = new IdentifierNode(name.getName(), SourceInfo.span(name.getSourceInfo(), typeArgs.getSourceInfo()));
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
                IntrinsicProperty defaultProperty = intrinsic != null ? intrinsic.getDefaultProperty() : null;

                Node key = parseValueSequence(
                    tokenizer, mapping != null && !mapping.closingCurly(),
                    parseMode(defaultProperty));

                if (tokenizer.poll(EQUALS) != null) {
                    tokenizer.resetToMark();
                    PropertyNode propertyNode = parsePropertyExpression(tokenizer, intrinsic);
                    sourceEnd = propertyNode.getSourceInfo();
                    properties.add(propertyNode);
                } else {
                    tokenizer.forgetMark();
                    sourceEnd = key.getSourceInfo();
                    children.add(key);
                }

                if (mapping != null && !mapping.closingCurly() && !tokenizer.isEmpty()) {
                    CurlyTokenClass tokenClass = tokenizer.peekNotNull().getType().getTokenClass();
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
                name.getName(),
                mapping != null ? mapping.intrinsic() : !cleanName.equals(name.getName()),
                name.getSourceInfo()),
            properties, children, true, SourceInfo.span(sourceStart, sourceEnd));
    }

    private PropertyNode parsePropertyExpression(InlineTokenizer tokenizer, @Nullable Intrinsic intrinsic) {
        IdentifierNode propertyName = parseIdentifier(tokenizer);
        String cleanName = cleanIdentifier(propertyName.getName(), propertyName.getSourceInfo());
        tokenizer.remove(EQUALS);

        IntrinsicProperty intrinsicProperty = intrinsic != null ? intrinsic.findProperty(cleanName) : null;
        Node value = parseValueSequence(tokenizer, false, parseMode(intrinsicProperty));

        return new PropertyNode(
            cleanName.split("\\."),
            propertyName.getName(),
            value,
            !propertyName.getName().equals(cleanName),
            false,
            SourceInfo.span(propertyName.getSourceInfo(), value.getSourceInfo()));
    }

    private InvocationNode parseInvocationExpression(InlineTokenizer tokenizer, SyntaxNode target) {
        if (!isCallableTarget(target)) {
            throw ParserErrors.unexpectedToken(tokenizer.peekNotNull());
        }

        InvocationInfo invocation = parseInvocationArguments(tokenizer);

        return new InvocationNode(
            target,
            invocation.arguments(),
            invocation.openParenSourceInfo(),
            invocation.closeParenSourceInfo(),
            SourceInfo.span(target.getSourceInfo(), invocation.closeParenSourceInfo()));
    }

    private boolean isCallableTarget(SyntaxNode target) {
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

    private InvocationInfo parseInvocationArguments(InlineTokenizer tokenizer) {
        SourceInfo openParenSourceInfo = tokenizer.remove(OPEN_PAREN).getSourceInfo();
        List<Node> arguments = new ArrayList<>();

        if (tokenizer.peekSkipWS(CLOSE_PAREN) == null) {
            while (true) {
                SyntaxMapping mapping = tryGetSyntaxMapping(tokenizer);
                Node argument = mapping != null || tokenizer.peek(OPEN_CURLY) != null
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

    private IdentifierNode parseIdentifier(InlineTokenizer tokenizer) {
        var text = new StringBuilder();
        IdentifierNode identifier = parseIdentifierComponent(tokenizer);
        SourceInfo start = identifier.getSourceInfo(), end = start;
        text.append(identifier.getName());

        while (tokenizer.peek(DOT) != null) {
            if (isIntrinsicIdentifier(identifier.getName(), identifier.getSourceInfo())) {
                throw ParserErrors.unexpectedToken(tokenizer.peekNotNull());
            }

            text.append(tokenizer.remove(DOT).getValue());
            identifier = parseIdentifierComponent(tokenizer);
            end = identifier.getSourceInfo();
            text.append(identifier.getName());
        }

        return new IdentifierNode(text.toString(), SourceInfo.span(start, end));
    }

    private IdentifierNode parseIdentifierComponent(InlineTokenizer tokenizer) {
        InlineToken first = tokenizer.remove(IDENTIFIER);
        InlineToken[] following = tokenizer.peekAhead(2);

        if (following != null && following[0].getType() == COLON
                && following[1].getType() == IDENTIFIER
                && first.getDecodedEnd() == following[0].getDecodedStart()
                && following[0].getDecodedEnd() == following[1].getDecodedStart()) {
            tokenizer.remove(COLON);
            InlineToken localName = tokenizer.remove(IDENTIFIER);

            return new IdentifierNode(
                first.getValue() + ":" + localName.getValue(),
                SourceInfo.span(first.getSourceInfo(), localName.getSourceInfo()));
        }

        return new IdentifierNode(first.getValue(), first.getSourceInfo());
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
            segments.add(parsePathSegment(tokenizer, true, selectorSourceInfo, allowPostfixTypeArguments));
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
        PathInfo typeArguments = allowPostfixTypeArguments ? tryParsePostfixTypeArguments(tokenizer) : null;

        return new TextSegmentNode(
            observableSelector,
            new IdentifierNode(identifier.getValue(), identifier.getSourceInfo()),
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
            new IdentifierNode(
                declaringTypeName,
                SourceInfo.span(identifiers.get(0).getSourceInfo(), declaringTypeEnd.getSourceInfo())),
            new IdentifierNode(propertyName.getValue(), propertyName.getSourceInfo()),
            selectorSourceInfo,
            openParenSourceInfo,
            separators.get(separators.size() - 1).getSourceInfo(),
            closeParenSourceInfo,
            SourceInfo.span(openParenSourceInfo, closeParenSourceInfo));
    }

    private SyntaxNode parsePathOrInvocation(InlineTokenizer tokenizer) {
        PathNode path = parsePath(tokenizer, true, true, false);
        return tokenizer.peek(OPEN_PAREN) != null
            ? parseInvocationExpression(tokenizer, path)
            : path;
    }

    private SyntaxNode parseSelectedMemberSuffixes(InlineTokenizer tokenizer, SyntaxNode receiver) {
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
                new IdentifierNode(identifier.getValue(), identifier.getSourceInfo()),
                typeArguments != null ? typeArguments.paths() : List.of(),
                selectorSourceInfo,
                typeArguments != null ? typeArguments.sourceInfo() : null,
                memberSourceInfo);

            receiver = new SelectedMemberNode(
                receiver, member, SourceInfo.span(receiver.getSourceInfo(), memberSourceInfo));

            if (tokenizer.peek(OPEN_PAREN) != null) {
                receiver = parseInvocationExpression(tokenizer, receiver);
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
            tokenizer.resetToMark();
            if (hasCompletedMalformedPostfixTypeArguments(tokenizer)) {
                throw ex;
            }

            return null;
        }
    }

    private boolean hasCompletedMalformedPostfixTypeArguments(InlineTokenizer tokenizer) {
        int depth = 0;

        for (int i = 0; ; ++i) {
            InlineToken[] tokens = tokenizer.peekAhead(i + 1);
            if (tokens == null) {
                return false;
            }

            InlineToken token = tokens[i];
            switch (token.getType()) {
                case OPEN_ANGLE -> ++depth;
                case CLOSE_ANGLE -> {
                    if (--depth == 0) {
                        InlineToken[] followers = tokenizer.peekAhead(i + 3);
                        InlineToken first = followers != null ? followers[i + 1] : null;
                        InlineToken second = followers != null ? followers[i + 2] : null;
                        if (first == null) {
                            InlineToken[] oneFollower = tokenizer.peekAhead(i + 2);
                            first = oneFollower != null ? oneFollower[i + 1] : null;
                        }

                        return isPostfixFollower(first, second);
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
                    if (depth == 0 || !isPrimitiveTypeKeyword(token.getValue())) {
                        return false;
                    }
                }

                default -> {
                    return false;
                }
            }
        }

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
        IdentifierNode name = new IdentifierNode(token.getValue(), token.getSourceInfo());
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
            new LiteralValueNode(builder.toString(), SourceInfo.span(start, end)),
            true, false, SourceInfo.span(start, end));
    }

    private ContextSelectorNode parseContextSelector(InlineTokenizer tokenizer) {
        SourceInfo colonSourceInfo = tokenizer.remove(COLON).getSourceInfo();
        InlineToken selectorToken = tokenizer.remove(IDENTIFIER);
        ContextSelector selector = ContextSelector.tryParse(selectorToken.getValue());

        if (selector == null) {
            throw ParserErrors.unexpectedToken(selectorToken);
        }

        IdentifierNode searchType = null;
        NumberNode level = null;
        SourceInfo openAngleSourceInfo = null;
        SourceInfo closeAngleSourceInfo = null;
        SourceInfo openParenSourceInfo = null;
        SourceInfo closeParenSourceInfo = null;

        if (selector == ContextSelector.PARENT && tokenizer.peek(OPEN_ANGLE) != null) {
            openAngleSourceInfo = tokenizer.remove(OPEN_ANGLE).getSourceInfo();
            searchType = parseIdentifier(tokenizer);
            closeAngleSourceInfo = tokenizer.remove(CLOSE_ANGLE).getSourceInfo();
        }

        if (tokenizer.peek(OPEN_PAREN) != null) {
            if (selector != ContextSelector.PARENT) {
                throw ParserErrors.unexpectedToken(tokenizer.peekNotNull());
            }

            openParenSourceInfo = tokenizer.remove(OPEN_PAREN).getSourceInfo();
            level = parseSignedInteger(tokenizer);
            closeParenSourceInfo = tokenizer.remove(CLOSE_PAREN).getSourceInfo();
        }

        SourceInfo sourceInfo = SourceInfo.span(
            colonSourceInfo,
            closeParenSourceInfo != null
                ? closeParenSourceInfo
                : closeAngleSourceInfo != null
                    ? closeAngleSourceInfo
                    : selectorToken.getSourceInfo());

        return new ContextSelectorNode(
            selector,
            searchType,
            level,
            colonSourceInfo,
            selectorToken.getSourceInfo(),
            openAngleSourceInfo,
            closeAngleSourceInfo,
            openParenSourceInfo,
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

    private SyntaxNode parseContentSelectionOrExpression(InlineTokenizer tokenizer) {
        InlineToken[] prefix = tokenizer.peekAhead(2);
        if (prefix != null && prefix[0].getType() == DOT && prefix[1].getType() == DOT) {
            SourceInfo start = tokenizer.remove(DOT).getSourceInfo();
            tokenizer.remove(DOT);

            if (tokenizer.peek(DOT) != null) {
                throw ParserErrors.invalidExpression(SourceInfo.span(start, expressionTail(tokenizer)));
            }

            SyntaxNode value = parseExpression(tokenizer);
            return new ContentSelectionNode(value, SourceInfo.span(start, value.getSourceInfo()));
        }

        SyntaxNode result = parseExpression(tokenizer);
        InlineToken trailing = tokenizer.peek(NUMBER);
        if (trailing != null && trailing.getLexeme().startsWith(".")) {
            throw ParserErrors.unexpectedExpression(
                SourceInfo.span(result.getSourceInfo(), trailing.getSourceInfo()));
        }

        return result;
    }

    private SourceInfo expressionTail(InlineTokenizer tokenizer) {
        InlineToken end = tokenizer.peekNotNull();

        for (int count = 2; ; ++count) {
            InlineToken[] tokens = tokenizer.peekAhead(count);
            if (tokens == null) {
                break;
            }

            InlineToken candidate = tokens[tokens.length - 1];
            if (candidate.getType().getTokenClass() == CurlyTokenClass.DELIMITER
                    || candidate.getType().getTokenClass() == CurlyTokenClass.SEMI) {
                break;
            }

            end = candidate;
        }

        return end.getSourceInfo();
    }

    private SyntaxNode parseExpression(InlineTokenizer tokenizer) {
        SyntaxNode left = parseLogicalAndExpression(tokenizer);

        while (tokenizer.peek(LOGICAL_OR) != null) {
            InlineToken operator = tokenizer.remove();
            SyntaxNode right = parseLogicalAndExpression(tokenizer);
            left = createBinaryOperator(operator, left, right);
        }

        return left;
    }

    private SyntaxNode parseLogicalAndExpression(InlineTokenizer tokenizer) {
        SyntaxNode left = parseEqualityExpression(tokenizer);

        while (tokenizer.peek(LOGICAL_AND) != null) {
            InlineToken operator = tokenizer.remove();
            SyntaxNode right = parseEqualityExpression(tokenizer);
            left = createBinaryOperator(operator, left, right);
        }

        return left;
    }

    private SyntaxNode parseEqualityExpression(InlineTokenizer tokenizer) {
        SyntaxNode left = parseRelationalExpression(tokenizer);

        while (tokenizer.peek(VALUE_EQUALITY) != null
                || tokenizer.peek(VALUE_INEQUALITY) != null
                || tokenizer.peek(IDENTITY_EQUALITY) != null
                || tokenizer.peek(IDENTITY_INEQUALITY) != null) {
            InlineToken operator = tokenizer.remove();
            SyntaxNode right = parseRelationalExpression(tokenizer);
            left = createBinaryOperator(operator, left, right);
        }

        return left;
    }

    private SyntaxNode parseRelationalExpression(InlineTokenizer tokenizer) {
        SyntaxNode left = parseAdditiveExpression(tokenizer);

        while (tokenizer.peek(OPEN_ANGLE) != null
                || tokenizer.peek(LESS_THAN_OR_EQUAL) != null
                || tokenizer.peek(CLOSE_ANGLE) != null
                || tokenizer.peek(GREATER_THAN_OR_EQUAL) != null) {
            InlineToken operator = tokenizer.remove();
            SyntaxNode right = parseAdditiveExpression(tokenizer);
            left = createBinaryOperator(operator, left, right);
        }

        return left;
    }

    private SyntaxNode parseAdditiveExpression(InlineTokenizer tokenizer) {
        SyntaxNode left = parseMultiplicativeExpression(tokenizer);

        while (tokenizer.peek(PLUS) != null || tokenizer.peek(MINUS) != null) {
            InlineToken operator = tokenizer.remove();
            SyntaxNode right = parseMultiplicativeExpression(tokenizer);
            left = createBinaryOperator(operator, left, right);
        }

        return left;
    }

    private SyntaxNode parseMultiplicativeExpression(InlineTokenizer tokenizer) {
        SyntaxNode left = parseUnaryExpression(tokenizer);

        while (tokenizer.peek(STAR) != null || tokenizer.peek(SLASH) != null) {
            InlineToken operator = tokenizer.remove();
            SyntaxNode right = parseUnaryExpression(tokenizer);
            left = createBinaryOperator(operator, left, right);
        }

        return left;
    }

    private SyntaxNode parseUnaryExpression(InlineTokenizer tokenizer) {
        InlineToken operator = switch (tokenizer.peekNotNull().getType()) {
            case PLUS, MINUS, NOT, BOOLIFY -> tokenizer.remove();
            default -> null;
        };

        if (operator == null) {
            return parsePostfixExpression(tokenizer);
        }

        SyntaxNode operand = parseUnaryExpression(tokenizer);

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

    private SyntaxNode parsePostfixExpression(InlineTokenizer tokenizer) {
        return parseSelectedMemberSuffixes(tokenizer, parsePrimaryExpression(tokenizer));
    }

    private SyntaxNode parsePrimaryExpression(InlineTokenizer tokenizer) {
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
                return parsePathOrInvocation(tokenizer);
            }

            LiteralKeywordNode literal = LiteralKeywordNode.tryCreate(token.getValue(), token.getSourceInfo());
            if (literal != null) {
                tokenizer.remove();
                return literal;
            }

            return parsePathOrInvocation(tokenizer);
        }

        if (token.getType() == OPEN_PAREN) {
            SourceInfo start = tokenizer.remove().getSourceInfo();
            SyntaxNode operand = parseExpression(tokenizer);
            SourceInfo end = tokenizer.remove(CLOSE_PAREN).getSourceInfo();
            return new ParenthesizedNode(operand, start, end, SourceInfo.span(start, end));
        }

        throw ParserErrors.unexpectedToken(token);
    }

    private BinaryOperatorNode createBinaryOperator(InlineToken operator, SyntaxNode left, SyntaxNode right) {
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

    private @Nullable Intrinsic findIntrinsic(
            @Nullable SyntaxMapping mapping, String cleanName, String markupName) {
        boolean intrinsic = mapping != null ? mapping.intrinsic() : !cleanName.equals(markupName);
        return intrinsic ? Intrinsics.find(cleanName) : null;
    }

    private ParseMode parseMode(@Nullable IntrinsicProperty property) {
        if (property == null) {
            return ParseMode.VALUE;
        }

        return switch (property.getSyntax()) {
            case GENERIC -> ParseMode.VALUE;
            case EXPRESSION -> ParseMode.EXPRESSION;
            case PATH_REFERENCE -> ParseMode.PATH_REFERENCE;
        };
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
}

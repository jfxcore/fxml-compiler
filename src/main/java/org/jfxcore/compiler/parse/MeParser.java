// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.text.BooleanNode;
import org.jfxcore.compiler.ast.text.CompositeNode;
import org.jfxcore.compiler.ast.text.FunctionNode;
import org.jfxcore.compiler.ast.text.ListNode;
import org.jfxcore.compiler.ast.text.NumberNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jfxcore.compiler.parse.MeTokenType.*;

public class MeParser {

    private final String source;
    private final String intrinsicPrefix;
    private final Location sourceOffset;

    public MeParser(String source, String intrinsicPrefix) {
        this.source = source;
        this.intrinsicPrefix = intrinsicPrefix + ":";
        this.sourceOffset = new Location(0, 0);
    }

    public MeParser(String source, String intrinsicPrefix, Location sourceOffset) {
        this.source = source;
        this.intrinsicPrefix = intrinsicPrefix + ":";
        this.sourceOffset = sourceOffset;
    }

    public ObjectNode tryParseObject() {
        String trimmed = source.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }

        CurlyTokenizer tokenizer = new CurlyTokenizer(source, intrinsicPrefix, sourceOffset);
        TokenInfo tokenInfo = getNextToken(tokenizer, false);

        if (tokenInfo.type != NodeType.OBJECT) {
            tokenizer = new CurlyTokenizer(source, intrinsicPrefix, sourceOffset);
            throw ParserErrors.invalidExpression(
                SourceInfo.span(tokenizer.getFirst().getSourceInfo(), tokenizer.getLast().getSourceInfo()));
        }

        ObjectNode root = parseObjectExpression(tokenInfo.token, tokenizer);
        if (!tokenizer.isEmpty()) {
            return null;
        }

        return root;
    }

    private ObjectNode parseObjectExpression(MeToken identifier, CurlyTokenizer tokenizer) {
        SourceInfo sourceInfo = identifier.getSourceInfo();
        boolean intrinsic = identifier.getValue().startsWith(intrinsicPrefix);
        String name = cleanIdentifier(identifier.getValue());

        TypeNode typeNode = new TypeNode(name, intrinsic ? intrinsicPrefix + name : name, intrinsic, sourceInfo);
        List<PropertyNode> properties = new ArrayList<>();
        List<ValueNode> children = new ArrayList<>();

        loop: while (!tokenizer.isEmpty()) {
            TokenInfo tokenInfo;

            switch ((tokenInfo = getNextToken(tokenizer, false)).type) {
                case VALUE: children.add(parseValueExpression(tokenizer, true, false)); break;
                case FUNCTION: children.add(parseFunctionExpression(tokenInfo.token, tokenizer)); break;
                case PROPERTY: properties.add(parsePropertyExpression(tokenInfo.token, tokenizer)); break;
                case OBJECT:
                    ValueNode node = parseObjectExpression(tokenInfo.token, tokenizer);

                    if (tokenizer.poll(COMMA) != null) {
                        List<ValueNode> values = new ArrayList<>();
                        values.add(node);
                        node = parseValueExpression(tokenizer, true, false);
                        if (node instanceof ListNode) {
                            values.addAll(((ListNode)node).getValues());
                        } else {
                            values.add(node);
                        }

                        children.add(new ListNode(values,
                            SourceInfo.span(values.get(0).getSourceInfo(), values.get(values.size() - 1).getSourceInfo())));
                    } else {
                        children.add(node);
                    }
                    break;

                default: if (!eatSemicolons(tokenizer)) break loop;
            }
        }

        tokenizer.remove(CLOSE_CURLY);

        return new ObjectNode(
            typeNode, properties, children, SourceInfo.span(sourceInfo, tokenizer.getLastRemoved().getSourceInfo()));
    }

    private PropertyNode parsePropertyExpression(MeToken identifier, CurlyTokenizer tokenizer) {
        SourceInfo sourceInfo = identifier.getSourceInfo();
        boolean intrinsic = identifier.getValue().startsWith(intrinsicPrefix);
        String name = cleanIdentifier(identifier.getValue());
        List<ValueNode> children = new ArrayList<>();
        TokenInfo tokenInfo;

        switch ((tokenInfo = getNextToken(tokenizer, false)).type) {
            case VALUE: children.add(parseValueExpression(tokenizer, false, false)); break;
            case OBJECT: children.add(parseObjectExpression(tokenInfo.token, tokenizer)); break;
            case FUNCTION: children.add(parseFunctionExpression(tokenInfo.token, tokenizer)); break;
            default: throw ParserErrors.unexpectedToken(tokenizer.peekNotNull());
        }

        return new PropertyNode(
            name.split("\\."),
            intrinsic ? intrinsicPrefix + name : name,
            children,
            intrinsic,
            SourceInfo.span(sourceInfo, tokenizer.getLastRemoved().getSourceInfo()));
    }

    private ValueNode parseValueExpression(
        CurlyTokenizer tokenizer, boolean allowObjectExpression, boolean isFunctionExpression) {
        List<ValueNode> result = new ArrayList<>();
        List<ValueNode> current = new ArrayList<>();
        TokenInfo tokenInfo;

        loop: while (!tokenizer.isEmpty()) {
            switch ((tokenInfo = getNextToken(tokenizer, true)).type) {
                case VALUE:
                    if (isFunctionExpression && tokenizer.peek(COMMA) != null) {
                        break loop;
                    }

                    MeToken token = tokenizer.remove();

                    if (token.getType() == COMMA) {
                        if (current.size() == 1) {
                            result.add(current.get(0));
                        } else if (current.size() > 1) {
                            SourceInfo start = current.get(0).getSourceInfo();
                            SourceInfo end = current.get(current.size() - 1).getSourceInfo();
                            result.add(new CompositeNode(current, SourceInfo.span(start, end)));
                        }

                        current.clear();
                    } else {
                        if (token.getType() == IDENTIFIER && token.getValue().startsWith(intrinsicPrefix)) {
                            current.add(
                                new ObjectNode(
                                    new TypeNode(
                                        cleanIdentifier(token.getValue()),
                                        token.getValue(),
                                        true,
                                        tokenInfo.token.getSourceInfo()),
                                    Collections.emptyList(),
                                    Collections.emptyList(),
                                    tokenInfo.token.getSourceInfo()));
                        } else {
                            if (token.getType() == NUMBER) {
                                current.add(new NumberNode(token.getValue(), token.getSourceInfo()));
                            } else if (token.getType() == BOOLEAN) {
                                current.add(new BooleanNode(token.getValue(), token.getSourceInfo()));
                            } else if (token.getType() == STRING) {
                                current.add(TextNode.createRawUnresolved(token.getValue(), token.getSourceInfo()));
                            } else {
                                current.add(new TextNode(token.getValue(), token.getSourceInfo()));
                            }
                        }
                    }
                    break;

                case FUNCTION:
                    current.add(parseFunctionExpression(tokenInfo.token, tokenizer));
                    break;

                case OBJECT:
                    if (allowObjectExpression) {
                        current.add(parseObjectExpression(tokenInfo.token, tokenizer));
                        break;
                    }

                    throw ParserErrors.unexpectedToken(tokenizer.getLastRemoved());

                case PROPERTY:
                    throw ParserErrors.unexpectedToken(tokenizer.getLastRemoved());

                case OTHER:
                    if (isFunctionExpression && tokenizer.peek(CLOSE_PAREN) != null
                            || !isFunctionExpression && tokenizer.peek(CLOSE_CURLY) != null) {
                        break loop;
                    }

                    if (tokenizer.peekSemi() != null && !isFunctionExpression) {
                        tokenizer.remove();
                        break loop;
                    }

                    throw ParserErrors.unexpectedToken(tokenInfo.token);

                default:
                    throw ParserErrors.unexpectedToken(tokenInfo.token);
            }
        }

        if (current.size() == 1) {
            result.add(current.get(0));
        } else if (current.size() > 1) {
            SourceInfo start = current.get(0).getSourceInfo();
            SourceInfo end = current.get(current.size() - 1).getSourceInfo();
            result.add(new CompositeNode(current, SourceInfo.span(start, end)));
        }

        if (result.size() == 0) {
            throw ParserErrors.unexpectedToken(tokenizer.getLastRemoved());
        } else if (result.size() == 1) {
            return result.get(0);
        }

        return new ListNode(
            result, SourceInfo.span(result.get(0).getSourceInfo(), result.get(result.size() - 1).getSourceInfo()));
    }

    private FunctionNode parseFunctionExpression(MeToken identifier, CurlyTokenizer tokenizer) {
        List<ValueNode> args = new ArrayList<>();

        do {
            TokenInfo tokenInfo;

            switch ((tokenInfo = getNextToken(tokenizer, false)).type) {
                case VALUE: args.add(parseValueExpression(tokenizer, false, true)); break;
                case OBJECT: args.add(parseObjectExpression(tokenInfo.token, tokenizer)); break;
                case FUNCTION: args.add(parseFunctionExpression(tokenInfo.token, tokenizer)); break;
            }
        } while (tokenizer.poll(COMMA) != null);

        tokenizer.remove(CLOSE_PAREN);

        return new FunctionNode(
            new TextNode(identifier.getValue(), identifier.getSourceInfo()),
            args, SourceInfo.span(identifier.getSourceInfo(), tokenizer.getLastRemoved().getSourceInfo()));
    }

    private String cleanIdentifier(String identifier) {
        return identifier.startsWith(intrinsicPrefix) ? identifier.substring(intrinsicPrefix.length()) : identifier;
    }

    private boolean eatSemicolons(CurlyTokenizer tokenizer) {
        boolean result = false;

        while (tokenizer.peekSemi() != null) {
            tokenizer.remove();
            result = true;
        }

        return result;
    }

    private TokenInfo getNextToken(CurlyTokenizer tokenizer, boolean allowDoubleColon) {
        MeToken token;
        if ((token = tokenizer.poll(OPEN_CURLY)) != null) {
            MeToken identifier = tokenizer.pollQualifiedIdentifier(false);
            if (identifier != null) {
                return new TokenInfo(NodeType.OBJECT, identifier);
            }

            tokenizer.addFirst(token);
        }

        MeToken identifier = tokenizer.pollQualifiedIdentifier(false);

        if (identifier == null && allowDoubleColon) {
            identifier = concatDoubleColons(null, tokenizer);
        }

        if (identifier != null) {
            if (tokenizer.poll(EQUALS) != null) {
                return new TokenInfo(NodeType.PROPERTY, identifier);
            }

            if (tokenizer.poll(OPEN_PAREN) != null) {
                return new TokenInfo(NodeType.FUNCTION, identifier);
            }

            if (allowDoubleColon) {
                token = null;

                do {
                    if (token != null) {
                        identifier = new MeToken(
                            IDENTIFIER,
                            identifier.getValue() + token.getValue(),
                            identifier.getLine(),
                            SourceInfo.span(identifier.getSourceInfo(), token.getSourceInfo()));
                    }

                    MeToken newIdentifier = concatDoubleColons(identifier, tokenizer);
                    if (newIdentifier != null) {
                        identifier = newIdentifier;
                        token = tokenizer.pollQualifiedIdentifier(false);
                    } else {
                        token = null;
                    }
                } while (token != null);
            }

            tokenizer.addFirst(identifier);
        }

        MeTokenClass tokenClass = tokenizer.peekNotNull().getType().getTokenClass();

        if (tokenClass != MeTokenClass.DELIMITER && tokenClass != MeTokenClass.SEMI) {
            return new TokenInfo(NodeType.VALUE, tokenizer.peekNotNull());
        }

        return new TokenInfo(NodeType.OTHER, tokenizer.peekNotNull());
    }

    private MeToken concatDoubleColons(MeToken identifier, CurlyTokenizer tokenizer) {
        MeToken[] nextTokens = tokenizer.peekAhead(2);

        if (nextTokens != null && nextTokens[0].getType() == COLON && nextTokens[1].getType() == COLON) {
            tokenizer.remove();
            tokenizer.remove();

            MeToken first = identifier != null ? identifier : nextTokens[0];

            return new MeToken(
                IDENTIFIER,
                (identifier != null ? identifier.getValue() : "") + "::",
                first.getLine(),
                SourceInfo.span(first.getSourceInfo(), nextTokens[1].getSourceInfo()));
        }

        return null;
    }

    private static class TokenInfo {
        final NodeType type;
        final MeToken token;

        public TokenInfo(NodeType type, MeToken token) {
            this.type = type;
            this.token = token;
        }
    }

    private enum NodeType {
        OTHER,
        OBJECT,
        PROPERTY,
        FUNCTION,
        VALUE
    }

}

// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.BindingNode;
import org.jfxcore.compiler.ast.ContextNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.NodeDataKey;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.TemplateContentNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.expression.BindingContextNode;
import org.jfxcore.compiler.ast.expression.BindingContextSelector;
import org.jfxcore.compiler.ast.expression.ExpressionNode;
import org.jfxcore.compiler.ast.expression.FunctionExpressionNode;
import org.jfxcore.compiler.ast.expression.Operator;
import org.jfxcore.compiler.ast.expression.PathExpressionNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.CompositeNode;
import org.jfxcore.compiler.ast.text.ContextSelectorNode;
import org.jfxcore.compiler.ast.text.FunctionNode;
import org.jfxcore.compiler.ast.text.ListNode;
import org.jfxcore.compiler.ast.text.NumberNode;
import org.jfxcore.compiler.ast.text.PathNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.List;

public class BindingTransform implements Transform {

    private final boolean allowContextSelector;

    public BindingTransform(boolean allowContextSelector) {
        this.allowContextSelector = allowContextSelector;
    }

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof ObjectNode objectNode)) {
            return node;
        }

        if (context.getParent() instanceof PropertyNode propertyNode && propertyNode.isIntrinsic(Intrinsics.CONTEXT)) {
            return node;
        }

        BindingMode bindingMode = getBindingMode(objectNode);
        if (bindingMode == null) {
            return node;
        }

        ValueNode sourceNode = (ValueNode)objectNode.getProperty("source").getSingleValue(context);
        PropertyNode inverseMethod = objectNode.findProperty("inverseMethod");
        ValueNode inverseMethodNode = inverseMethod != null ?
            inverseMethod.getSingleValue(context).as(ValueNode.class) : null;

        ExpressionNode pathExpression = tryParseExpression(
            context, sourceNode, !bindingMode.isReverse(), inverseMethodNode);

        if (pathExpression == null) {
            throw ParserErrors.invalidExpression(sourceNode.getSourceInfo());
        }

        PropertyNode converter = objectNode.findProperty("converter");
        PathNode converterPath = converter != null ? converter.getSingleValue(context).as(PathNode.class) : null;

        PropertyNode format = objectNode.findProperty("format");
        PathNode formatPath = format != null ? format.getSingleValue(context).as(PathNode.class) : null;

        return BindingNode.newInstance(
            bindingMode, pathExpression,
            converterPath != null ? parsePathNode(context, Operator.IDENTITY, converterPath) : null,
            formatPath != null ? parsePathNode(context, Operator.IDENTITY, formatPath) : null,
            context.getParent() instanceof ListNode,
            node.getSourceInfo());
    }

    private BindingMode getBindingMode(ObjectNode node) {
        if (node.isIntrinsic(Intrinsics.EVALUATE)) {
            return getBindingMode(node, BindingMode.ONCE, BindingMode.CONTENT);
        } else if (node.isIntrinsic(Intrinsics.OBSERVE)) {
            return getBindingMode(node, BindingMode.UNIDIRECTIONAL, BindingMode.UNIDIRECTIONAL_CONTENT);
        } else if (node.isIntrinsic(Intrinsics.PUSH)) {
            return getBindingMode(node, BindingMode.REVERSE, BindingMode.REVERSE_CONTENT);
        } else if (node.isIntrinsic(Intrinsics.SYNCHRONIZE)) {
            return getBindingMode(node, BindingMode.BIDIRECTIONAL, BindingMode.BIDIRECTIONAL_CONTENT);
        }

        return null;
    }

    private BindingMode getBindingMode(ObjectNode node, BindingMode defaultMode, BindingMode contentMode) {
        return node.getNodeData(NodeDataKey.CONTENT_EXPRESSION) == Boolean.TRUE ? contentMode : defaultMode;
    }

    private ExpressionNode tryParseExpression(
            TransformContext context,
            ValueNode value,
            boolean allowOperator,
            @Nullable ValueNode inverseMethodNode) {
        if (value instanceof CompositeNode compositeNode) {
            return parseCompositeNode(context, compositeNode, allowOperator);
        }

        if (value instanceof PathNode pathNode) {
            return parsePathNode(context, Operator.IDENTITY, pathNode);
        }

        if (value instanceof FunctionNode functionNode) {
            return parseFunctionNode(context, Operator.IDENTITY, functionNode, inverseMethodNode);
        }

        return null;
    }

    private PathExpressionNode parsePathNode(TransformContext context, Operator operator, PathNode pathNode) {
        return new PathExpressionNode(
            operator,
            parseBindingContext(context, pathNode),
            pathNode.getSegments(),
            pathNode.getSourceInfo());
    }

    private FunctionExpressionNode parseFunctionNode(
            TransformContext context,
            Operator operator,
            FunctionNode functionNode,
            @Nullable ValueNode inverseMethodNode) {
        ExpressionNode expression = tryParseExpression(context, inverseMethodNode, true, null);
        if (expression != null && !(expression instanceof PathExpressionNode)) {
            throw GeneralErrors.expressionNotApplicable(expression.getSourceInfo(), false);
        }

        return new FunctionExpressionNode(
            context.getMarkupClass(),
            parsePathNode(context, operator, functionNode.getPath()),
            functionNode.getArguments().stream().map(arg -> parseFunctionArgumentNode(context, arg)).toList(),
            (PathExpressionNode)expression,
            functionNode.getSourceInfo());
    }

    private Node parseFunctionArgumentNode(TransformContext context, ValueNode value) {
        if (value instanceof NumberNode || value instanceof ObjectNode) {
            return value;
        }

        ExpressionNode expression = tryParseExpression(context, value, true, null);
        if (expression != null) {
            return expression;
        }

        if (value instanceof TextNode) {
            return value;
        }

        throw ParserErrors.unexpectedExpression(value.getSourceInfo());
    }

    private ExpressionNode parseCompositeNode(TransformContext context, CompositeNode node, boolean allowOperator) {
        List<ValueNode> values = node.getValues();
        Operator operator;

        if (values.get(0) instanceof TextNode textNode) {
            if (!allowOperator) {
                throw ParserErrors.unexpectedToken(textNode.getSourceInfo());
            }

            operator = switch (textNode.getText()) {
                case "!" -> Operator.NOT;
                case "!!" -> Operator.BOOLIFY;
                default -> {
                    // A malformed context selector like parent[Button] will end up here as a CompositeNode
                    // instead of a ContextSelectorNode. We generate a better error message than the message
                    // that is generated in the general case below.
                    if (values.size() >= 4
                            && "parent".equals(textNode.getText())
                            && values.get(1) instanceof TextNode t1 && "[".equals(t1.getText())
                            && values.get(3) instanceof TextNode t2 && "]".equals(t2.getText())) {
                        throw ParserErrors.unexpectedToken(values.get(2).getSourceInfo());
                    }

                    throw ParserErrors.unexpectedExpression(node.getSourceInfo());
                }
            };
        } else {
            throw ParserErrors.unexpectedExpression(node.getSourceInfo());
        }

        if (values.size() > 2) {
            throw ParserErrors.unexpectedExpression(values.get(2).getSourceInfo());
        }

        if (values.get(1) instanceof PathNode pathNode) {
            return parsePathNode(context, operator, pathNode);
        } else if (values.get(1) instanceof FunctionNode functionNode) {
            return parseFunctionNode(context, operator, functionNode, null);
        } else {
            throw ParserErrors.unexpectedExpression(values.get(1).getSourceInfo());
        }
    }

    private BindingContextNode parseBindingContext(TransformContext context, PathNode pathNode) {
        ContextSelectorNode contextSelectorNode = pathNode.getContextSelector();
        if (contextSelectorNode == null && allowContextSelector) {
            List<Node> parents = context.getParents().stream()
                .filter(node -> node instanceof ObjectNode)
                .toList();

            for (int i = parents.size() - 1; i >= 0; --i) {
                for (PropertyNode propertyNode : ((ObjectNode)parents.get(i)).getProperties()) {
                    if (propertyNode.isIntrinsic(Intrinsics.CONTEXT)
                            && propertyNode.getSingleValue(context) instanceof ContextNode contextNode) {
                        return new BindingContextNode(
                            BindingContextSelector.CONTEXT,
                            contextNode.getType().getTypeInstance(),
                            contextNode.getValueType(),
                            contextNode.getObservableType(),
                            contextNode.getField(),
                            parents.size() - i - 1,
                            pathNode.getSourceInfo());
                    }
                }
            }
        }

        BindingContextSelector bindingContextSelector = contextSelectorNode != null
            ? parseBindingContextSelector(contextSelectorNode.getSelector())
            : BindingContextSelector.ROOT;

        if (contextSelectorNode != null && bindingContextSelector != BindingContextSelector.PARENT) {
            if (contextSelectorNode.getLevel() != null) {
                throw ParserErrors.unexpectedExpression(contextSelectorNode.getLevel().getSourceInfo());
            }

            if (contextSelectorNode.getSearchType() != null) {
                throw ParserErrors.unexpectedExpression(contextSelectorNode.getSearchType().getSourceInfo());
            }
        }

        return switch (bindingContextSelector) {
            case STATIC, CONTEXT -> throw new IllegalArgumentException();

            case ROOT -> {
                List<Node> parents = context.getParents().stream()
                    .filter(node -> node instanceof ObjectNode)
                    .toList();

                for (int i = parents.size() - 1; i >= 0; --i) {
                    TypeInstance type = TypeHelper.getTypeInstance(parents.get(i));
                    if (type.subtypeOf(context.getCodeBehindOrMarkupClass())) {
                        yield new BindingContextNode(
                            BindingContextSelector.ROOT, type,
                            parents.size() - i - 1, pathNode.getSourceInfo());
                    }
                }

                throw ParserErrors.invalidExpression(pathNode.getSourceInfo());
            }

            case SELF -> {
                List<Node> parents = context.getParents().stream()
                    .filter(node -> node instanceof ObjectNode)
                    .toList();

                yield new BindingContextNode(
                    bindingContextSelector,
                    TypeHelper.getTypeInstance(parents.get(parents.size() - 1)),
                    0,
                    contextSelectorNode.getSourceInfo());
            }

            case PARENT -> {
                List<Node> parents = context.getParents().stream()
                    .filter(node -> node instanceof ObjectNode)
                    .toList();

                Integer level = null;
                TypeDeclaration searchType = null;

                if (contextSelectorNode.getLevel() != null) {
                    level = parseParentLevel(contextSelectorNode.getLevel());
                }

                if (contextSelectorNode.getSearchType() != null) {
                    var resolver = new Resolver(contextSelectorNode.getSearchType().getSourceInfo());
                    searchType = resolver.resolveClassAgainstImports(contextSelectorNode.getSearchType().getText());
                }

                ParentInfo parentInfo = findParent(parents, searchType, level, contextSelectorNode.getSourceInfo());

                yield new BindingContextNode(
                    bindingContextSelector,
                    parentInfo.type(),
                    parents.size() - parentInfo.parentStackIndex() - 1,
                    contextSelectorNode.getSourceInfo());
            }

            case TEMPLATED_ITEM -> {
                TemplateContentNode templateContentNode = context.tryFindParent(TemplateContentNode.class);
                if (templateContentNode != null) {
                    yield new BindingContextNode(
                        BindingContextSelector.TEMPLATED_ITEM,
                        templateContentNode.getItemType(),
                        0,
                        contextSelectorNode.getSourceInfo());
                }

                throw BindingSourceErrors.bindingContextNotApplicable(contextSelectorNode.getSourceInfo());
            }
        };
    }

    private BindingContextSelector parseBindingContextSelector(TextNode value) {
        try {
            return BindingContextSelector.parse(value.getText());
        } catch (IllegalArgumentException ignored) {
            throw ParserErrors.unexpectedExpression(value.getSourceInfo());
        }
    }

    private Integer parseParentLevel(NumberNode value) {
        try {
            return Integer.parseInt(value.getText());
        } catch (NumberFormatException ex) {
            throw ParserErrors.unexpectedToken(value.getSourceInfo());
        }
    }

    private record ParentInfo(TypeInstance type, int parentStackIndex) {}

    private ParentInfo findParent(
            List<Node> parents,
            @Nullable TypeDeclaration searchType,
            @Nullable Integer level,
            SourceInfo sourceInfo) {
        int parentIndex = -1;
        TypeInstance parentType = null;

        if (level != null && (level < 0 || level > parents.size() - 2)) {
            throw BindingSourceErrors.parentIndexOutOfBounds(sourceInfo);
        }

        if (searchType == null) {
            parentIndex = parents.size() - (level != null ? level : 0) - 2;
            parentType = TypeHelper.getTypeInstance(parents.get(parentIndex));
        } else {
            for (int i = parents.size() - 2, match = 0; i >= 0; --i) {
                parentType = TypeHelper.getTypeInstance(parents.get(i));

                if (parentType.subtypeOf(searchType)) {
                    if (level != null) {
                        if (match++ == level) {
                            parentIndex = i;
                            break;
                        }
                    } else {
                        parentIndex = i;
                        break;
                    }
                }
            }

            if (parentIndex == -1) {
                throw BindingSourceErrors.parentTypeNotFound(sourceInfo, searchType.name());
            }
        }

        return new ParentInfo(parentType, parentIndex);
    }
}

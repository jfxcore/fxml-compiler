// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import javassist.CtClass;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.BindingNode;
import org.jfxcore.compiler.ast.Node;
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
import org.jfxcore.compiler.ast.text.BooleanNode;
import org.jfxcore.compiler.ast.text.CompositeNode;
import org.jfxcore.compiler.ast.text.ContextSelectorNode;
import org.jfxcore.compiler.ast.text.FunctionNode;
import org.jfxcore.compiler.ast.text.NumberNode;
import org.jfxcore.compiler.ast.text.PathNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.List;

public class BindingTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof ObjectNode objectNode)) {
            return node;
        }

        BindingMode bindingMode = getBindingMode(objectNode);

        if (bindingMode == null) {
            return node;
        }

        ValueNode pathNode = (ValueNode)objectNode.getProperty("path").getSingleValue(context);
        PropertyNode inverseMethod = objectNode.findProperty("inverseMethod");
        ValueNode inverseMethodNode = inverseMethod != null ?
            inverseMethod.getSingleValue(context).as(ValueNode.class) : null;

        ExpressionNode pathExpression = tryParseExpression(context, pathNode, inverseMethodNode);
        if (pathExpression == null) {
            throw ParserErrors.invalidExpression(pathNode.getSourceInfo());
        }

        return new BindingNode(pathExpression, bindingMode, node.getSourceInfo());
    }

    private BindingMode getBindingMode(ObjectNode node) {
        if (node.isIntrinsic(Intrinsics.ONCE)) {
            return BindingMode.ONCE;
        } else if (node.isIntrinsic(Intrinsics.CONTENT)) {
            return BindingMode.CONTENT;
        } else if (node.isIntrinsic(Intrinsics.BIND)) {
            return BindingMode.UNIDIRECTIONAL;
        } else if (node.isIntrinsic(Intrinsics.BIND_CONTENT)) {
            return BindingMode.UNIDIRECTIONAL_CONTENT;
        } else if (node.isIntrinsic(Intrinsics.BIND_BIDIRECTIONAL)) {
            return BindingMode.BIDIRECTIONAL;
        } else if (node.isIntrinsic(Intrinsics.BIND_CONTENT_BIDIRECTIONAL)) {
            return BindingMode.BIDIRECTIONAL_CONTENT;
        }

        return null;
    }

    private ExpressionNode tryParseExpression(
            TransformContext context,
            ValueNode value,
            @Nullable ValueNode inverseMethodNode) {
        if (value instanceof CompositeNode compositeNode) {
            return parseCompositeNode(context, compositeNode);
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
        ExpressionNode expression = tryParseExpression(context, inverseMethodNode, null);
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
        if (value instanceof BooleanNode || value instanceof NumberNode || value instanceof ObjectNode) {
            return value;
        }

        ExpressionNode expression = tryParseExpression(context, value, null);
        if (expression != null) {
            return expression;
        }

        if (value instanceof TextNode) {
            return value;
        }

        throw ParserErrors.unexpectedExpression(value.getSourceInfo());
    }

    private ExpressionNode parseCompositeNode(TransformContext context, CompositeNode node) {
        Operator operator;

        if (node.getValues().get(0) instanceof TextNode textNode) {
            operator = switch (textNode.getText()) {
                case "!" -> Operator.NOT;
                case "!!" -> Operator.BOOLIFY;
                default -> throw ParserErrors.unexpectedToken(textNode.getSourceInfo());
            };
        } else {
            throw ParserErrors.unexpectedExpression(node.getSourceInfo());
        }

        if (node.getValues().size() > 2) {
            throw ParserErrors.unexpectedExpression(node.getValues().get(2).getSourceInfo());
        }

        if (node.getValues().get(1) instanceof PathNode pathNode) {
            return parsePathNode(context, operator, pathNode);
        } else if (node.getValues().get(1) instanceof FunctionNode functionNode) {
            return parseFunctionNode(context, operator, functionNode, null);
        } else {
            throw ParserErrors.unexpectedExpression(node.getValues().get(1).getSourceInfo());
        }
    }

    private BindingContextNode parseBindingContext(TransformContext context, PathNode pathNode) {
        ContextSelectorNode contextSelectorNode = pathNode.getContextSelector();
        if (contextSelectorNode == null) {
            List<Node> parents = context.getParents().stream()
                .filter(node -> node instanceof ObjectNode)
                .toList();

            for (int i = parents.size() - 1; i >= 0; --i) {
                TypeInstance type = TypeHelper.getTypeInstance(parents.get(i));
                if (type.subtypeOf(context.getBindingContextClass())) {
                    return new BindingContextNode(
                        BindingContextSelector.DEFAULT, type, i, parents.size() - i - 1, pathNode.getSourceInfo());
                }
            }

            throw ParserErrors.invalidExpression(pathNode.getSourceInfo());
        }

        BindingContextSelector bindingContextSelector = parseBindingContextSelector(contextSelectorNode.getSelector());
        if (bindingContextSelector != BindingContextSelector.PARENT) {
            if (contextSelectorNode.getLevel() != null) {
                throw ParserErrors.unexpectedExpression(contextSelectorNode.getLevel().getSourceInfo());
            }

            if (contextSelectorNode.getSearchType() != null) {
                throw ParserErrors.unexpectedExpression(contextSelectorNode.getSearchType().getSourceInfo());
            }
        }

        return switch (bindingContextSelector) {
            case STATIC, DEFAULT -> throw new IllegalArgumentException();

            case SELF -> {
                List<Node> parents = context.getParents().stream()
                    .filter(node -> node instanceof ObjectNode)
                    .toList();

                yield new BindingContextNode(
                    bindingContextSelector,
                    TypeHelper.getTypeInstance(parents.get(parents.size() - 1)),
                    parents.size() - 1,
                    0,
                    contextSelectorNode.getSourceInfo());
            }

            case PARENT -> {
                List<Node> parents = context.getParents().stream()
                    .filter(node -> node instanceof ObjectNode)
                    .toList();

                Integer level = null;
                CtClass searchType = null;

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
                    parentInfo.parentStackIndex(),
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
            @Nullable CtClass searchType,
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
                throw BindingSourceErrors.parentTypeNotFound(sourceInfo, searchType.getName());
            }
        }

        return new ParentInfo(parentType, parentIndex);
    }

}

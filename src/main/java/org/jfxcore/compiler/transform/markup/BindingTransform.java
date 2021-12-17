// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.BindingNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.TemplateContentNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.expression.BindingContextNode;
import org.jfxcore.compiler.ast.expression.ExpressionNode;
import org.jfxcore.compiler.ast.expression.FunctionExpressionNode;
import org.jfxcore.compiler.ast.expression.Operator;
import org.jfxcore.compiler.ast.expression.PathExpressionNode;
import org.jfxcore.compiler.ast.expression.BindingContextSelector;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.BooleanNode;
import org.jfxcore.compiler.ast.text.CompositeNode;
import org.jfxcore.compiler.ast.text.FunctionNode;
import org.jfxcore.compiler.ast.text.ListNode;
import org.jfxcore.compiler.ast.text.NumberNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BindingTransform implements Transform {

    private static final Pattern VALIDATE_PATH_PATTERN = Pattern.compile(
        "(?:(?:::\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)|" +
        "(?:\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)|" +
        "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)+");

    private final static String PARENT_CONTEXT_NAME = "parent";

    @Override
    public Set<Class<? extends Transform>> getDependsOn() {
        return Set.of(DefaultPropertyTransform.class);
    }

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof ObjectNode objectNode)) {
            return node;
        }

        BindingMode bindingMode = getBindingMode(context, objectNode);

        if (bindingMode == null) {
            return node;
        }

        ValueNode pathNode = (ValueNode)objectNode.getProperty("path").getSingleValue(context);
        PropertyNode inverseMethodNode = objectNode.findProperty("inverseMethod");
        BindingContextNode bindingSourceNode = createBindingContextNode(context, pathNode.getSourceInfo());

        ExpressionNode pathExpression = parsePath(
            context, pathNode, inverseMethodNode, Operator.IDENTITY, bindingSourceNode, pathNode.getSourceInfo());

        return new BindingNode(pathExpression, bindingMode, node.getSourceInfo());
    }

    private BindingMode getBindingMode(TransformContext context, ObjectNode node) {
        if (node.isIntrinsic(Intrinsics.ONCE)) {
            return isContent(context, node) ? BindingMode.CONTENT : BindingMode.ONCE;
        } else if (node.isIntrinsic(Intrinsics.BIND)) {
            return isContent(context, node) ? BindingMode.UNIDIRECTIONAL_CONTENT : BindingMode.UNIDIRECTIONAL;
        } else if (node.isIntrinsic(Intrinsics.BIND_BIDIRECTIONAL)) {
            return isContent(context, node) ? BindingMode.BIDIRECTIONAL_CONTENT : BindingMode.BIDIRECTIONAL;
        }

        return null;
    }

    private boolean isContent(TransformContext context, ObjectNode node) {
        PropertyNode contentProperty = node.findProperty("content");
        if (contentProperty == null) {
            return false;
        }

        Node valueNode = contentProperty.getSingleValue(context);

        if (valueNode instanceof BooleanNode booleanNode) {
            return Boolean.parseBoolean(booleanNode.getText());
        }

        if (valueNode instanceof TextNode textNode) {
            throw PropertyAssignmentErrors.cannotCoercePropertyValue(
                contentProperty.getSourceInfo(),
                node.getType().getMarkupName(),
                "content",
                textNode.getText());
        }

        throw PropertyAssignmentErrors.propertyMustContainText(
            contentProperty.getSourceInfo(), node.getType().getMarkupName(), "content");
    }

    private ExpressionNode parsePath(
            TransformContext context,
            ValueNode pathNode,
            @Nullable PropertyNode inverseMethodNode,
            Operator operator,
            BindingContextNode bindingSourceNode,
            SourceInfo sourceInfo) {
        if (pathNode instanceof FunctionNode functionNode) {
            TextNode funcName = functionNode.getName();

            TextNode inverseMethodText = inverseMethodNode != null ?
                (TextNode)inverseMethodNode.getSingleValue(context) : null;

            PathExpressionNode inverseMethodExpression = null;

            if (inverseMethodText != null) {
                ExpressionNode pathExpression = parsePath(
                    context, inverseMethodText, null, Operator.IDENTITY,
                    bindingSourceNode, inverseMethodText.getSourceInfo());

                if (!(pathExpression instanceof PathExpressionNode)) {
                    throw GeneralErrors.expressionNotApplicable(pathExpression.getSourceInfo(), false);
                }

                inverseMethodExpression = (PathExpressionNode)pathExpression;
            }

            return new FunctionExpressionNode(
                new PathExpressionNode(operator, bindingSourceNode, funcName.getText(), funcName.getSourceInfo()),
                functionNode.getArguments().stream().map(arg -> parseFunctionArgument(context, arg)).collect(Collectors.toList()),
                inverseMethodExpression,
                sourceInfo);
        }

        if (pathNode instanceof CompositeNode compositeNode) {
            Deque<ValueNode> values = new ArrayDeque<>(compositeNode.getValues());

            if (!values.isEmpty()) {
                operator = parseOperator(values);
            }

            if (!values.isEmpty()) {
                bindingSourceNode = parseBindingContext(context, values);
            }

            if (values.size() != 1) {
                throw BindingSourceErrors.invalidBindingExpression(sourceInfo);
            }

            return parsePath(context, values.poll(), inverseMethodNode, operator, bindingSourceNode, sourceInfo);
        }

        if (pathNode instanceof TextNode textNode && !textNode.isRawText()) {
            if (!isValidPath(textNode.getText())) {
                throw ParserErrors.expectedIdentifier(textNode.getSourceInfo());
            }

            return new PathExpressionNode(operator, bindingSourceNode, textNode.getText(), sourceInfo);
        }

        throw BindingSourceErrors.invalidBindingExpression(sourceInfo);
    }

    private boolean isValidPath(String text) {
        Matcher matcher = VALIDATE_PATH_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return false;
        }

        return !text.startsWith(".") && matcher.start() == 0  && matcher.end() == text.length();
    }

    private Operator parseOperator(Deque<ValueNode> values) {
        ValueNode node = values.peek();
        if (!(node instanceof TextNode)) {
            return null;
        }

        switch (((TextNode)node).getText()) {
            case "!": values.poll(); return Operator.NOT;
            case "!!": values.poll(); return Operator.BOOLIFY;
            default: return Operator.IDENTITY;
        }
    }

    private BindingContextNode parseBindingContext(TransformContext context, Deque<ValueNode> values) {
        boolean hasBindingContext = false;

        for (ValueNode node : new ArrayList<>(values)) {
            if (node.typeEquals(TextNode.class) && ((TextNode)node).getText().equals("/")) {
                hasBindingContext = true;
                break;
            }
        }

        ValueNode first = values.peek();

        if (first == null) {
            throw GeneralErrors.internalError();
        }

        if (!(first instanceof TextNode) || !hasBindingContext) {
            return createBindingContextNode(context, first.getSourceInfo());
        }

        if (PARENT_CONTEXT_NAME.equals(((TextNode)first).getText())) {
            values.remove();
            ValueNode node = values.peek();

            if (node instanceof TextNode && ((TextNode)node).getText().equals("/")) {
                values.remove();

                return new BindingContextNode(
                    BindingContextSelector.PARENT, findParentClass(context, 1, node.getSourceInfo()), 1,
                    SourceInfo.span(first.getSourceInfo(), node.getSourceInfo()));
            }

            pollOpenBracket(values);
            node = values.peek();

            if (node instanceof NumberNode) {
                int level = parseParentLevel(values);
                return new BindingContextNode(
                    BindingContextSelector.PARENT, findParentClass(context, level, node.getSourceInfo()), level,
                    SourceInfo.span(first.getSourceInfo(), pollCloseBracket(values).getSourceInfo()));
            }

            if (node instanceof TextNode textNode) {
                values.remove();
                Resolver resolver = new Resolver(textNode.getSourceInfo());
                TypeInstance searchType = resolver.getTypeInstance(
                    resolver.resolveClassAgainstImports(textNode.getText()));

                ValueNode next = values.element();
                if (!(next instanceof TextNode nextTextNode)) {
                    throw ParserErrors.unexpectedToken(next.getSourceInfo());
                }

                if (nextTextNode.getText().equals(":")) {
                    values.remove();

                    int level = parseParentLevel(values);
                    return new BindingContextNode(
                        BindingContextSelector.PARENT, searchType, searchType, level,
                        SourceInfo.span(first.getSourceInfo(), pollCloseBracket(values).getSourceInfo()));
                }

                return new BindingContextNode(
                    BindingContextSelector.PARENT, searchType, searchType, 1,
                    SourceInfo.span(first.getSourceInfo(), pollCloseBracket(values).getSourceInfo()));
            }
        }

        throw BindingSourceErrors.invalidBindingExpression(first.getSourceInfo());
    }

    private BindingContextNode createBindingContextNode(TransformContext context, SourceInfo sourceInfo) {
        TemplateContentNode templateContentNode = context.tryFindParent(TemplateContentNode.class);
        if (templateContentNode != null) {
            return new BindingContextNode(
                BindingContextSelector.TEMPLATED_ITEM,
                templateContentNode.getItemType(),
                0,
                sourceInfo);
        }

        return new BindingContextNode(
            BindingContextSelector.DEFAULT, context.getBindingContextClass(), null, sourceInfo);
    }

    private TypeInstance findParentClass(TransformContext context, int level, SourceInfo sourceInfo) {
        List<Node> parents = context.getParents();
        int currentLevel = 0;

        for (int i = parents.size() - 1; i >= 0; --i) {
            Node parent = parents.get(i);

            if (parent instanceof ObjectNode p && !p.getType().isIntrinsic()) {
                if (currentLevel++ == level) {
                    return (i == 1) ? context.getBindingContextClass() : TypeHelper.getTypeInstance(p);
                }
            }

            if (parent instanceof TemplateContentNode) {
                break;
            }
        }

        throw BindingSourceErrors.parentIndexOutOfBounds(sourceInfo);
    }

    private int parseParentLevel(Deque<ValueNode> values) {
        NumberNode value = (NumberNode)values.remove();

        try {
            int level = Integer.parseInt(value.getText());
            if (level < 0) {
                throw BindingSourceErrors.parentIndexOutOfBounds(value.getSourceInfo());
            }

            return level;
        } catch (NumberFormatException ex) {
            throw ParserErrors.unexpectedToken(value.getSourceInfo());
        }
    }

    private void pollOpenBracket(Deque<ValueNode> values) {
        ValueNode node = values.remove();
        if (!(node instanceof TextNode textNode) || !textNode.getText().equals("[")) {
            throw ParserErrors.expectedToken(node.getSourceInfo(), "[");
        }
    }

    private ValueNode pollCloseBracket(Deque<ValueNode> values) {
        ValueNode node = values.remove();
        if (!(node instanceof TextNode textNode1) || !textNode1.getText().equals("]")) {
            throw ParserErrors.expectedToken(node.getSourceInfo(), "]");
        }

        node = values.remove();
        if (!(node instanceof TextNode textNode2) || !textNode2.getText().equals("/")) {
            throw ParserErrors.expectedToken(node.getSourceInfo(), "/");
        }

        return node;
    }

    private Node parseFunctionArgument(TransformContext context, ValueNode node) {
        if (node instanceof BooleanNode || node instanceof NumberNode || node instanceof ObjectNode) {
            return node;
        }

        if (node instanceof ListNode listNode) {
            Deque<ValueNode> values = new ArrayDeque<>(listNode.getValues());
            BindingContextNode bindingSourceNode = parseBindingContext(context, values);

            return new PathExpressionNode(
                Operator.IDENTITY,
                bindingSourceNode,
                values.stream().map(n -> ((TextNode)n).getText()).collect(Collectors.joining()),
                node.getSourceInfo());
        }

        if (node instanceof TextNode textNode && textNode.isRawText()) {
            return node;
        }

        BindingContextNode bindingSourceNode = new BindingContextNode(
            BindingContextSelector.DEFAULT, context.getBindingContextClass(), 0, node.getSourceInfo());

        return parsePath(context, node, null, Operator.IDENTITY, bindingSourceNode, node.getSourceInfo());
    }

}

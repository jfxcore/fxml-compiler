// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.BindingNode;
import org.jfxcore.compiler.ast.ContextNode;
import org.jfxcore.compiler.ast.LiteralValueNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.NodeDataKey;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.expression.AnalyzedExpressionNode;
import org.jfxcore.compiler.ast.expression.BindingContextNode;
import org.jfxcore.compiler.ast.expression.BindingContextSelector;
import org.jfxcore.compiler.ast.expression.ArithmeticExpressionNode;
import org.jfxcore.compiler.ast.expression.CompiledExpressionNode;
import org.jfxcore.compiler.ast.expression.ComparisonExpressionNode;
import org.jfxcore.compiler.ast.expression.ExpressionNode;
import org.jfxcore.compiler.ast.expression.ExternalExpressionNode;
import org.jfxcore.compiler.ast.expression.GroupExpressionNode;
import org.jfxcore.compiler.ast.expression.InvocationExpressionNode;
import org.jfxcore.compiler.ast.expression.LiteralExpressionNode;
import org.jfxcore.compiler.ast.expression.LogicalExpressionNode;
import org.jfxcore.compiler.ast.expression.MemberAccessExpressionNode;
import org.jfxcore.compiler.ast.expression.BindingOperator;
import org.jfxcore.compiler.ast.expression.PathExpressionNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.BinaryOperatorNode;
import org.jfxcore.compiler.ast.text.ContextSelector;
import org.jfxcore.compiler.ast.text.ContextSelectorNode;
import org.jfxcore.compiler.ast.text.InvocationNode;
import org.jfxcore.compiler.ast.text.LiteralKeywordNode;
import org.jfxcore.compiler.ast.text.NumberNode;
import org.jfxcore.compiler.ast.text.ParenthesizedNode;
import org.jfxcore.compiler.ast.text.PathNode;
import org.jfxcore.compiler.ast.text.SelectedMemberNode;
import org.jfxcore.compiler.ast.text.StringLiteralNode;
import org.jfxcore.compiler.ast.text.UnaryOperator;
import org.jfxcore.compiler.ast.text.UnaryOperatorNode;
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
import org.jfxcore.compiler.util.NumberUtil;
import java.util.List;

public class BindingTransform implements Transform {

    private final boolean allowContextLookup;

    public BindingTransform(boolean allowContextLookup) {
        this.allowContextLookup = allowContextLookup;
    }

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof ObjectNode objectNode)) {
            return node;
        }

        PropertyNode semanticProperty = context.tryFindSemanticOwner(PropertyNode.class);
        if (semanticProperty != null && semanticProperty.isIntrinsic(Intrinsics.CONTEXT)) {
            return node;
        }

        BindingMode bindingMode = getBindingMode(objectNode);
        if (bindingMode == null) {
            return node;
        }

        Node sourceNode = objectNode.getProperty("source").getSingleValue(context);
        PropertyNode inverseMethod = objectNode.findProperty("inverseMethod");
        Node inverseMethodNode = inverseMethod != null ? inverseMethod.getSingleValue(context) : null;

        ExpressionNode pathExpression = tryParseExpression(
            context, sourceNode, bindingMode, !bindingMode.isReverse(), inverseMethodNode);

        if (pathExpression == null) {
            throw ParserErrors.invalidExpression(sourceNode.getSourceInfo());
        }

        PropertyNode converter = objectNode.findProperty("converter");
        PathNode converterPath = converter != null ? converter.getSingleValue(context).as(PathNode.class) : null;

        PropertyNode format = objectNode.findProperty("format");
        PathNode formatPath = format != null ? format.getSingleValue(context).as(PathNode.class) : null;

        return BindingNode.newInstance(
            bindingMode, pathExpression,
            converterPath != null ? parsePathNode(context, BindingOperator.IDENTITY, converterPath) : null,
            formatPath != null ? parsePathNode(context, BindingOperator.IDENTITY, formatPath) : null,
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
            @Nullable Node value,
            BindingMode bindingMode,
            boolean allowOperator,
            @Nullable Node inverseMethodNode) {
        if (value == null) {
            return null;
        }

        value = unwrapGroups(value);

        if (value instanceof PathNode pathNode) {
            return parsePathNode(context, BindingOperator.IDENTITY, pathNode);
        }

        if (value instanceof InvocationNode node) {
            return parseInvocationNode(context, BindingOperator.IDENTITY, node, bindingMode, inverseMethodNode);
        }

        if (value instanceof SelectedMemberNode node) {
            return parseSelectedMemberNode(context, BindingOperator.IDENTITY, node, bindingMode);
        }

        ExpressionNode directBooleanExpression = tryParseDirectBooleanExpression(
            context, value, bindingMode, allowOperator, inverseMethodNode);

        if (directBooleanExpression != null) {
            return directBooleanExpression;
        }

        if (value instanceof BinaryOperatorNode
                || value instanceof UnaryOperatorNode
                || value instanceof ParenthesizedNode
                || value instanceof NumberNode
                || value instanceof LiteralKeywordNode
                || value instanceof StringLiteralNode) {
            return parseCompiledExpression(context, value, bindingMode);
        }

        return null;
    }

    private PathExpressionNode parsePathNode(TransformContext context, BindingOperator operator, PathNode pathNode) {
        return new PathExpressionNode(
            operator,
            parseBindingContext(context, pathNode),
            pathNode.getSegments(),
            pathNode.getSourceInfo());
    }

    private InvocationExpressionNode parseInvocationNode(
            TransformContext context,
            BindingOperator operator,
            InvocationNode invocationNode,
            BindingMode bindingMode,
            @Nullable Node inverseMethodNode) {
        ExpressionNode inverseExpression = tryParseExpression(
            context, inverseMethodNode, BindingMode.ONCE, true, null);

        if (inverseExpression != null && !(inverseExpression instanceof PathExpressionNode)) {
            throw GeneralErrors.expressionNotApplicable(inverseExpression.getSourceInfo(), false);
        }

        List<Node> arguments = invocationNode.getArguments().stream()
            .map(argument -> parseFunctionArgumentNode(context, argument, bindingMode))
            .toList();

        if (invocationNode.getTarget() instanceof PathNode path) {
            return new InvocationExpressionNode(
                context.getMarkupClass(),
                operator,
                parsePathNode(context, operator, path),
                arguments,
                (PathExpressionNode)inverseExpression,
                invocationNode.getSourceInfo());
        }

        SelectedMemberNode selected = (SelectedMemberNode)invocationNode.getTarget();
        ExpressionNode receiver = tryParseExpression(context, selected.getReceiver(), bindingMode, true, null);
        if (receiver == null) {
            throw ParserErrors.unexpectedExpression(selected.getReceiver().getSourceInfo());
        }

        return new InvocationExpressionNode(
            context.getMarkupClass(),
            operator,
            receiver,
            selected.getMember(),
            arguments,
            (PathExpressionNode)inverseExpression,
            invocationNode.getSourceInfo());
    }

    private MemberAccessExpressionNode parseSelectedMemberNode(
            TransformContext context,
            BindingOperator operator,
            SelectedMemberNode selected,
            BindingMode bindingMode) {
        ExpressionNode receiver = tryParseExpression(context, selected.getReceiver(), bindingMode, true, null);
        if (receiver == null) {
            throw ParserErrors.unexpectedExpression(selected.getReceiver().getSourceInfo());
        }

        return new MemberAccessExpressionNode(
            operator, receiver, List.of(selected.getMember()), selected.getSourceInfo());
    }

    private Node parseFunctionArgumentNode(TransformContext context, Node value, BindingMode bindingMode) {
        if (value instanceof LiteralKeywordNode literal) {
            return parseLiteralKeywordNode(literal);
        }

        if (value instanceof NumberNode number) {
            return parseNumberNode(number);
        }

        if (value instanceof StringLiteralNode text) {
            return LiteralExpressionNode.ofString(text.getText(), text.getSourceInfo());
        }

        if (value instanceof ObjectNode object) {
            LiteralExpressionNode literal = tryParseLiteralIntrinsic(object);
            return literal != null ? literal : object;
        }

        ExpressionNode expression = tryParseExpression(context, value, bindingMode, true, null);
        if (expression != null) {
            return expression;
        }

        if (value instanceof LiteralValueNode literal) {
            return LiteralExpressionNode.ofString(literal.getText(), literal.getSourceInfo());
        }

        throw ParserErrors.unexpectedExpression(value.getSourceInfo());
    }

    private @Nullable ExpressionNode tryParseDirectBooleanExpression(
            TransformContext context,
            Node value,
            BindingMode bindingMode,
            boolean allowOperator,
            @Nullable Node inverseMethodNode) {
        if (!allowOperator) {
            return null;
        }

        Node unwrapped = unwrapGroups(value);

        if (!(unwrapped instanceof UnaryOperatorNode unary)
                || unary.getOperator() != UnaryOperator.NOT && unary.getOperator() != UnaryOperator.BOOLIFY) {
            return null;
        }

        Node operand = unwrapGroups(unary.getOperand());

        BindingOperator operator = unary.getOperator() == UnaryOperator.NOT
            ? BindingOperator.NOT
            : BindingOperator.BOOLIFY;

        if (operand instanceof PathNode path) {
            return parsePathNode(context, operator, path);
        }

        if (operand instanceof InvocationNode invocation) {
            return parseInvocationNode(context, operator, invocation, bindingMode, inverseMethodNode);
        }

        if (operand instanceof SelectedMemberNode selected) {
            return parseSelectedMemberNode(context, operator, selected, bindingMode);
        }

        return null;
    }

    private Node unwrapGroups(Node value) {
        while (value instanceof ParenthesizedNode parenthesized) {
            value = parenthesized.getOperand();
        }

        return value;
    }

    private CompiledExpressionNode parseCompiledExpression(
            TransformContext context, Node value, BindingMode bindingMode) {
        AnalyzedExpressionNode root = parseCompiledNode(context, value, bindingMode);
        boolean arithmetic = isPureArithmeticSyntax(value);
        var expression = new CompiledExpressionNode(
            context.getMarkupClass(),
            arithmetic ? "arithmetic expression" : "expression",
            root, value.getSourceInfo());

        if (bindingMode.isReverse()) {
            throw ParserErrors.unexpectedToken(expression.getFirstOperatorSourceInfo());
        }

        if (bindingMode.isBidirectional()) {
            throw BindingSourceErrors.expressionNotInvertible(value.getSourceInfo());
        }

        if (bindingMode.isContent()) {
            throw GeneralErrors.expressionNotApplicable(value.getSourceInfo(), false);
        }

        return expression;
    }

    private boolean isPureArithmeticSyntax(Node value) {
        if (value instanceof BinaryOperatorNode binary) {
            return switch (binary.getOperator()) {
                case ADD, SUBTRACT, MULTIPLY, DIVIDE ->
                    isPureArithmeticSyntax(binary.getLeft())
                    && isPureArithmeticSyntax(binary.getRight());
                default -> false;
            };
        }

        if (value instanceof UnaryOperatorNode unary) {
            return switch (unary.getOperator()) {
                case PLUS, MINUS -> isPureArithmeticSyntax(unary.getOperand());
                default -> false;
            };
        }

        return !(value instanceof ParenthesizedNode parenthesized)
            || isPureArithmeticSyntax(parenthesized.getOperand());
    }

    private AnalyzedExpressionNode parseCompiledNode(
            TransformContext context, Node value, BindingMode bindingMode) {
        if (value instanceof BinaryOperatorNode binary) {
            AnalyzedExpressionNode left = parseCompiledNode(context, binary.getLeft(), bindingMode);
            AnalyzedExpressionNode right = parseCompiledNode(context, binary.getRight(), bindingMode);

            return switch (binary.getOperator()) {
                case ADD, SUBTRACT, MULTIPLY, DIVIDE -> new ArithmeticExpressionNode(
                    binary.getOperator(), left, right,
                    binary.getOperatorSourceInfo(), binary.getSourceInfo());

                case LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL,
                     VALUE_EQUAL, VALUE_NOT_EQUAL, IDENTITY_EQUAL, IDENTITY_NOT_EQUAL ->
                    new ComparisonExpressionNode(
                        binary.getOperator(), left, right,
                        binary.getOperatorSourceInfo(), binary.getSourceInfo());

                case LOGICAL_AND, LOGICAL_OR -> new LogicalExpressionNode(
                    binary.getOperator(), left, right,
                    binary.getOperatorSourceInfo(), binary.getSourceInfo());
            };
        }

        if (value instanceof UnaryOperatorNode unary) {
            AnalyzedExpressionNode operand = parseCompiledNode(context, unary.getOperand(), bindingMode);

            return switch (unary.getOperator()) {
                case PLUS, MINUS -> new ArithmeticExpressionNode(
                    unary.getOperator(), operand,
                    unary.getOperatorSourceInfo(), unary.getSourceInfo());

                case NOT, BOOLIFY -> new LogicalExpressionNode(
                    unary.getOperator(), operand,
                    unary.getOperatorSourceInfo(), unary.getSourceInfo());
            };
        }

        if (value instanceof ParenthesizedNode parenthesized) {
            return new GroupExpressionNode(
                parseCompiledNode(context, parenthesized.getOperand(), bindingMode),
                parenthesized.getSourceInfo());
        }

        if (value instanceof NumberNode number) {
            return parseNumberNode(number);
        }

        if (value instanceof LiteralKeywordNode literal) {
            return parseLiteralKeywordNode(literal);
        }

        if (value instanceof StringLiteralNode text) {
            return LiteralExpressionNode.ofString(text.getText(), text.getSourceInfo());
        }

        if (value instanceof PathNode path) {
            return new ExternalExpressionNode(
                parsePathNode(context, BindingOperator.IDENTITY, path), path.getSourceInfo());
        }

        if (value instanceof InvocationNode invocation) {
            return new ExternalExpressionNode(
                parseInvocationNode(context, BindingOperator.IDENTITY, invocation, bindingMode, null),
                invocation.getSourceInfo());
        }

        if (value instanceof SelectedMemberNode selected) {
            return new ExternalExpressionNode(
                parseSelectedMemberNode(context, BindingOperator.IDENTITY, selected, bindingMode),
                selected.getSourceInfo());
        }

        if (value instanceof ObjectNode object) {
            return new ExternalExpressionNode(object, object.getSourceInfo());
        }

        if (value instanceof LiteralValueNode literal) {
            return LiteralExpressionNode.ofString(literal.getText(), literal.getSourceInfo());
        }

        throw ParserErrors.unexpectedExpression(value.getSourceInfo());
    }

    private LiteralExpressionNode parseNumberNode(NumberNode node) {
        try {
            return LiteralExpressionNode.ofNumber(NumberUtil.parse(node.getText()), node.getSourceInfo());
        } catch (IllegalArgumentException ex) {
            throw ParserErrors.unexpectedExpression(node.getSourceInfo());
        }
    }

    private LiteralExpressionNode parseLiteralKeywordNode(LiteralKeywordNode node) {
        return switch (node.getKind()) {
            case TRUE -> LiteralExpressionNode.ofBoolean(true, node.getSourceInfo());
            case FALSE -> LiteralExpressionNode.ofBoolean(false, node.getSourceInfo());
            case NULL -> LiteralExpressionNode.ofNull(node.getSourceInfo());
        };
    }

    private @Nullable LiteralExpressionNode tryParseLiteralIntrinsic(ObjectNode node) {
        if (node.isIntrinsic(Intrinsics.TRUE)) {
            return LiteralExpressionNode.ofBoolean(true, node.getSourceInfo());
        }

        if (node.isIntrinsic(Intrinsics.FALSE)) {
            return LiteralExpressionNode.ofBoolean(false, node.getSourceInfo());
        }

        if (node.isIntrinsic(Intrinsics.NULL)) {
            return LiteralExpressionNode.ofNull(node.getSourceInfo());
        }

        return null;
    }

    private BindingContextNode parseBindingContext(TransformContext context, PathNode pathNode) {
        ContextSelectorNode contextSelectorNode = pathNode.getContextSelector();

        if (contextSelectorNode == null) {
            return parseDefaultBindingContext(context, pathNode.getSourceInfo(), false);
        }

        if (contextSelectorNode.getSelector() != ContextSelector.PARENT) {
            if (contextSelectorNode.getLevel() != null) {
                throw ParserErrors.unexpectedExpression(contextSelectorNode.getLevel().getSourceInfo());
            }

            if (contextSelectorNode.getSearchType() != null) {
                throw ParserErrors.unexpectedExpression(contextSelectorNode.getSearchType().getSourceInfo());
            }
        }

        return switch (contextSelectorNode.getSelector()) {
            case CONTEXT -> parseDefaultBindingContext(
                context, contextSelectorNode.getSourceInfo(), true);

            case ROOT -> createRootBindingContext(
                context, contextSelectorNode.getSourceInfo(), true);

            case ELEMENT -> {
                List<Node> parents = getObjectParents(context);

                yield new BindingContextNode(
                    BindingContextSelector.ELEMENT,
                    TypeHelper.getTypeInstance(parents.get(parents.size() - 1)),
                    0,
                    true,
                    contextSelectorNode.getSourceInfo());
            }

            case PARENT -> {
                List<Node> parents = getObjectParents(context);

                Integer level = null;
                TypeDeclaration searchType = null;

                if (contextSelectorNode.getLevel() != null) {
                    level = parseParentLevel(contextSelectorNode.getLevel());
                }

                if (contextSelectorNode.getSearchType() != null) {
                    var resolver = new Resolver(contextSelectorNode.getSearchType().getSourceInfo());
                    searchType = resolver.resolveClassAgainstImports(contextSelectorNode.getSearchType().getName());
                }

                ParentInfo parentInfo = findParent(parents, searchType, level, contextSelectorNode.getSourceInfo());

                yield new BindingContextNode(
                    BindingContextSelector.PARENT,
                    parentInfo.type(),
                    parents.size() - parentInfo.parentStackIndex() - 1,
                    true,
                    contextSelectorNode.getSourceInfo());
            }
        };
    }

    private BindingContextNode parseDefaultBindingContext(
            TransformContext context, SourceInfo sourceInfo, boolean explicitReceiver) {
        List<Node> parents = getObjectParents(context);

        if (allowContextLookup) {
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
                            explicitReceiver,
                            sourceInfo);
                    }
                }
            }
        }

        return createRootBindingContext(context, sourceInfo, explicitReceiver);
    }

    private BindingContextNode createRootBindingContext(
            TransformContext context, SourceInfo sourceInfo, boolean explicitReceiver) {
        List<Node> parents = getObjectParents(context);

        for (int i = parents.size() - 1; i >= 0; --i) {
            TypeInstance type = TypeHelper.getTypeInstance(parents.get(i));
            if (type.subtypeOf(context.getCodeBehindOrMarkupClass())) {
                return new BindingContextNode(
                    BindingContextSelector.ROOT,
                    type,
                    parents.size() - i - 1,
                    explicitReceiver,
                    sourceInfo);
            }
        }

        throw ParserErrors.invalidExpression(sourceInfo);
    }

    private List<Node> getObjectParents(TransformContext context) {
        return context.getParents().stream()
            .filter(node -> node instanceof ObjectNode)
            .toList();
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
        int effectiveLevel = level != null ? level : 1;

        if (effectiveLevel < 0
                || ((level != null || searchType == null) && effectiveLevel > parents.size() - 1)) {
            throw BindingSourceErrors.parentIndexOutOfBounds(sourceInfo);
        }

        if (searchType == null) {
            parentIndex = parents.size() - effectiveLevel - 1;
            parentType = TypeHelper.getTypeInstance(parents.get(parentIndex));
        } else {
            if (effectiveLevel == 0) {
                parentType = TypeHelper.getTypeInstance(parents.get(parents.size() - 1));
                if (parentType.subtypeOf(searchType)) {
                    parentIndex = parents.size() - 1;
                }
            }

            for (int i = parents.size() - 2, match = 1; parentIndex == -1 && i >= 0; --i) {
                parentType = TypeHelper.getTypeInstance(parents.get(i));

                if (parentType.subtypeOf(searchType)) {
                    if (match++ == effectiveLevel) {
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

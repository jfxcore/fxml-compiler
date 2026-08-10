// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-plan analysis state for one mutable compiled-expression tree.
 */
public final class ExpressionAnalysisContext {

    public static final class Input {
        private final Node expression;
        private TypeDeclaration parameterType;
        private int localIndex = -1;

        private Input(Node expression, TypeInstance sourceType) {
            this.expression = expression;
            this.parameterType = sourceType.declaration();
        }

        public Node expression() {
            return expression;
        }

        public TypeDeclaration parameterType() {
            return parameterType;
        }

        public int localIndex() {
            if (localIndex < 0) {
                throw new IllegalStateException("Expression input slots have not been allocated");
            }

            return localIndex;
        }
    }

    private final BindingMode bindingMode;
    private final TypeInstance invokingType;
    private final Map<AnalyzedExpressionNode, TypeInstance> types = new IdentityHashMap<>();
    private final Map<AnalyzedExpressionNode, AnalyzedExpressionNode> aliases = new IdentityHashMap<>();
    private final Map<AnalyzedExpressionNode, Input> inputsByNode = new IdentityHashMap<>();
    private final Map<AnalyzedExpressionNode, Object> analysisData = new IdentityHashMap<>();
    private final List<Input> inputs = new ArrayList<>();

    private int parameterSlots = -1;

    public ExpressionAnalysisContext(BindingMode bindingMode, TypeInstance invokingType) {
        this.bindingMode = bindingMode;
        this.invokingType = invokingType;
    }

    public TypeInstance analyze(AnalyzedExpressionNode node) {
        TypeInstance type = types.get(node);
        if (type == null) {
            type = node.analyze(this);
            types.put(node, type);
        }

        return type;
    }

    public TypeInstance getType(AnalyzedExpressionNode node) {
        TypeInstance type = types.get(node);
        if (type == null) {
            throw new IllegalStateException("Expression node has not been analyzed");
        }

        return type;
    }

    public void alias(AnalyzedExpressionNode node, AnalyzedExpressionNode target) {
        aliases.put(node, canonical(target));
    }

    public TypeInstance addInput(AnalyzedExpressionNode node, Node expression) {
        if (parameterSlots >= 0) {
            throw new IllegalStateException("Expression input slots have already been allocated");
        }

        TypeInstance sourceType = resolveInputType(expression);
        Input input = new Input(expression, sourceType);
        inputsByNode.put(node, input);
        inputs.add(input);
        return sourceType;
    }

    public Input getInput(AnalyzedExpressionNode node) {
        Input input = inputsByNode.get(canonical(node));
        if (input == null) {
            throw new IllegalStateException("Expression node is not an external input");
        }

        return input;
    }

    public TypeDeclaration requireNumeric(AnalyzedExpressionNode node, SourceInfo sourceInfo, String operator) {
        AnalyzedExpressionNode canonicalNode = canonical(node);
        TypeInstance sourceType = getType(node);
        TypeDeclaration primitive = TypeHelper.getExactNumericPrimitive(sourceType);

        if (primitive == null) {
            throw GeneralErrors.invalidOperand(sourceInfo, operator, sourceType.javaName());
        }

        Input input = inputsByNode.get(canonicalNode);
        if (input != null) {
            input.parameterType = primitive;
        }

        return primitive;
    }

    public void putAnalysisData(AnalyzedExpressionNode node, Object value) {
        analysisData.put(node, value);
    }

    public <T> T getAnalysisData(AnalyzedExpressionNode node, Class<T> type) {
        return type.cast(analysisData.get(node));
    }

    public void allocateInputSlots() {
        int slots = 0;
        for (Input input : inputs) {
            input.localIndex = slots;
            slots += input.parameterType.slots();
        }

        parameterSlots = slots;
    }

    public int getParameterSlots() {
        if (parameterSlots < 0) {
            throw new IllegalStateException("Expression input slots have not been allocated");
        }

        return parameterSlots;
    }

    public List<Input> getInputs() {
        return List.copyOf(inputs);
    }

    private TypeInstance resolveInputType(Node expression) {
        if (expression instanceof ExpressionNode expressionNode) {
            return expressionNode.toEmitter(bindingMode, invokingType, null).getValueType();
        }

        if (expression instanceof ValueNode) {
            return TypeHelper.getTypeInstance(expression);
        }

        throw ParserErrors.unexpectedExpression(expression.getSourceInfo());
    }

    private AnalyzedExpressionNode canonical(AnalyzedExpressionNode node) {
        AnalyzedExpressionNode target = aliases.get(node);
        while (target != null) {
            node = target;
            target = aliases.get(node);
        }

        return node;
    }
}

// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ValueSourceKind;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.emit.EmitCompiledExpressionNode;
import org.jfxcore.compiler.ast.emit.EmitMethodArgumentNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.util.CompiledExpressionEmitterFactory;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.NameHelper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Owns analysis and one ordered-input helper for a complete compiled-expression island. */
public final class CompiledExpressionNode extends AbstractNode implements ExpressionNode {

    private static final String EVAL_HELPER_NAME = "eval";

    private record AnalysisKey(
        BindingMode bindingMode,
        TypeInstance invokingType) {}

    private static final class Plan {
        private final BindingEmitterInfo emitter;

        private Plan(BindingEmitterInfo emitter) {
            this.emitter = emitter;
        }
    }

    private final TypeDeclaration invocationContext;
    private final String sourceName;

    private AnalyzedExpressionNode root;

    private transient Map<AnalysisKey, Plan> plans;

    public CompiledExpressionNode(
            TypeDeclaration invocationContext,
            String sourceName,
            AnalyzedExpressionNode root,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.invocationContext = checkNotNull(invocationContext);
        this.sourceName = checkNotNull(sourceName);
        this.root = checkNotNull(root);
    }

    public AnalyzedExpressionNode getRoot() {
        return root;
    }

    public SourceInfo getFirstOperatorSourceInfo() {
        SourceInfo sourceInfo = root.getFirstOperatorSourceInfo();
        return sourceInfo != null ? sourceInfo : getSourceInfo();
    }

    @Override
    public int getBindingDistance() {
        return root.getBindingDistance();
    }

    @Override
    public BindingEmitterInfo toEmitter(
            BindingMode bindingMode,
            TypeInstance invokingType,
            @Nullable TypeInstance targetType) {
        if (bindingMode.isContent() || bindingMode.isReverse()) {
            throw GeneralErrors.expressionNotApplicable(getSourceInfo(), false);
        }

        if (bindingMode.isBidirectional()) {
            throw BindingSourceErrors.expressionNotInvertible(getSourceInfo());
        }

        if (plans == null) {
            plans = new HashMap<>();
        }

        var key = new AnalysisKey(bindingMode, invokingType);
        Plan plan = plans.get(key);
        if (plan == null) {
            plan = createPlan(bindingMode, invokingType);
            plans.put(key, plan);
        }

        return plan.emitter;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        if (plans != null && !plans.isEmpty()) {
            throw new IllegalStateException("Compiled expressions cannot be mutated after analysis");
        }

        root = (AnalyzedExpressionNode)root.accept(visitor);
    }

    @Override
    public CompiledExpressionNode deepClone() {
        return new CompiledExpressionNode(invocationContext, sourceName, root.deepClone(), getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof CompiledExpressionNode that
            && invocationContext.equals(that.invocationContext)
            && sourceName.equals(that.sourceName)
            && root.equals(that.root);
    }

    @Override
    public int hashCode() {
        return Objects.hash(invocationContext, sourceName, root);
    }

    private Plan createPlan(BindingMode bindingMode, TypeInstance invokingType) {
        var context = new ExpressionAnalysisContext(bindingMode, invokingType);
        TypeInstance resultType = context.analyze(root);
        context.allocateInputSlots();

        if (context.getParameterSlots() > 255) {
            throw GeneralErrors.expressionTooComplex(getSourceInfo());
        }

        TypeDeclaration[] parameterTypes = context.getInputs().stream()
            .map(ExpressionAnalysisContext.Input::parameterType)
            .toArray(TypeDeclaration[]::new);

        String uniqueHelperName = NameHelper.getUniqueName(EVAL_HELPER_NAME, this);

        List<EmitMethodArgumentNode> arguments = new CompiledExpressionEmitterFactory(invokingType)
            .createArguments(context.getInputs(), bindingMode.isObservable(), uniqueHelperName);

        boolean observable = bindingMode.isObservable()
            && arguments.stream().anyMatch(EmitMethodArgumentNode::isObservable);

        ValueEmitterNode body = root.toEmitter(context);

        ValueEmitterNode value = new EmitCompiledExpressionNode(
            invocationContext, uniqueHelperName, resultType, parameterTypes,
            body, arguments, observable, getSourceInfo());

        TypeInstance emittedType = TypeHelper.getTypeInstance(value);

        BindingEmitterInfo emitter = new BindingEmitterInfo(
            value, resultType, observable ? emittedType : null,
            observable ? ValueSourceKind.get(emittedType.declaration()) : ValueSourceKind.NONE,
            ObservableDependencyKind.get(emittedType.declaration()),
            invocationContext, sourceName, true, false, getSourceInfo());

        return new Plan(emitter);
    }
}

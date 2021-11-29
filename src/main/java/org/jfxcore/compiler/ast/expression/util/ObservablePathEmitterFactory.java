// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.emit.EmitObservablePathNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.expression.Operator;
import org.jfxcore.compiler.ast.expression.PathExpressionNode;
import org.jfxcore.compiler.ast.expression.path.ResolvedPath;
import org.jfxcore.compiler.ast.expression.path.Segment;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.util.ObservableKind;
import org.jfxcore.compiler.util.TypeHelper;

public class ObservablePathEmitterFactory implements ObservableEmitterFactory {

    private final PathExpressionNode pathExpression;

    public ObservablePathEmitterFactory(PathExpressionNode pathExpression) {
        this.pathExpression = pathExpression;
    }

    @Override
    public BindingEmitterInfo newInstance() {
        return newInstance(false);
    }

    @Override
    public BindingEmitterInfo newInstance(boolean bidirectional) {
        SourceInfo sourceInfo = pathExpression.getSourceInfo();
        ResolvedPath path = pathExpression.resolvePath(true);
        Segment lastSegment = path.get(path.size() - 1);

        if (bidirectional && path.getObservableKind() != ObservableKind.FX_PROPERTY) {
            MarkupException ex;

            if (lastSegment.getDeclaringClass() == null) {
                ex = BindingSourceErrors.invalidBidirectionalBindingSource(
                    sourceInfo, lastSegment.getValueTypeInstance().jvmType(), false);
            } else {
                ex = BindingSourceErrors.invalidBidirectionalBindingSource(
                    sourceInfo, lastSegment.getDeclaringClass(), lastSegment.getDisplayName());
            }

            ex.getProperties().put("sourceType", path.getValueTypeInstance());
            throw ex;
        }

        if (path.isInvariant()) {
            return null;
        }

        ValueEmitterNode value = new EmitObservablePathNode(path, bidirectional, sourceInfo);
        Operator operator = pathExpression.getOperator();

        if (bidirectional && !operator.isInvertible(path.getValueTypeInstance())) {
            throw BindingSourceErrors.expressionNotInvertible(value.getSourceInfo());
        }

        value = operator.toEmitter(value, bidirectional ? BindingMode.BIDIRECTIONAL : BindingMode.UNIDIRECTIONAL);

        return new BindingEmitterInfo(
            value,
            operator.evaluateType(path.getValueTypeInstance()),
            TypeHelper.getTypeInstance(value),
            lastSegment.getDeclaringClass(),
            lastSegment.getDisplayName(),
            pathExpression.getSourceInfo());
    }

}

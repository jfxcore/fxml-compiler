// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ValueSourceKind;
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
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;

import static org.jfxcore.compiler.type.KnownSymbols.*;

public class ObservablePathEmitterFactory implements ObservableEmitterFactory {

    private final PathExpressionNode pathExpression;

    public ObservablePathEmitterFactory(PathExpressionNode pathExpression) {
        this.pathExpression = pathExpression;
    }

    @Override
    public @Nullable BindingEmitterInfo newInstance() {
        return newInstance(false);
    }

    @Override
    public @Nullable BindingEmitterInfo newInstance(boolean bidirectional) {
        return newInstance(bidirectional, false);
    }

    public @Nullable BindingEmitterInfo newInstance(boolean bidirectional, boolean allowDirectContentSource) {
        SourceInfo sourceInfo = pathExpression.getSourceInfo();
        ResolvedPath path = pathExpression.resolvePath(true);
        Segment lastSegment = path.get(path.size() - 1);

        if (bidirectional && path.getValueSourceKind() != ValueSourceKind.WRITABLE) {
            MarkupException ex;

            if (lastSegment.getDeclaringType() == null) {
                ex = BindingSourceErrors.invalidBidirectionalBindingSource(
                    sourceInfo, lastSegment.getValueTypeInstance(), false);
            } else {
                ex = BindingSourceErrors.invalidBidirectionalBindingSource(
                    sourceInfo, lastSegment.getDeclaringType(), lastSegment.getDisplayName());
            }

            ex.getProperties().put("sourceType", path.getValueTypeInstance());
            throw ex;
        }

        if (path.isInvariant() || !allowDirectContentSource && isDirectContentSource(path)) {
            return null;
        }

        var emitPathNode = new EmitObservablePathNode(path, bidirectional, sourceInfo);
        ValueEmitterNode value = emitPathNode;
        Operator operator = pathExpression.getOperator();

        if (bidirectional && !operator.isInvertible(path.getValueTypeInstance())) {
            throw BindingSourceErrors.expressionNotInvertible(value.getSourceInfo());
        }

        value = operator.toEmitter(value, bidirectional ? BindingMode.BIDIRECTIONAL : BindingMode.UNIDIRECTIONAL);
        boolean exposesValueSource = exposesValueSource(path);

        return new BindingEmitterInfo(
            value,
            operator.evaluateType(path.getValueTypeInstance()),
            exposesValueSource ? TypeHelper.getTypeInstance(value) : null,
            exposesValueSource ? ValueSourceKind.get(TypeHelper.getTypeDeclaration(value)) : ValueSourceKind.NONE,
            path.getObservableDependencyKind(),
            lastSegment.getDeclaringType(),
            lastSegment.getDisplayName(),
            false,
            emitPathNode.isCompiledPath(),
            pathExpression.getSourceInfo());
    }

    private boolean exposesValueSource(ResolvedPath path) {
        if (path.getValueSourceKind() != ValueSourceKind.NONE) {
            return true;
        }

        TypeInstance valueType = path.getValueTypeInstance();
        return ObservableDependencyKind.get(valueType.declaration()) != ObservableDependencyKind.CONTENT
            && !valueType.subtypeOf(CollectionDecl())
            && !valueType.subtypeOf(MapDecl());
    }

    private boolean isDirectContentSource(ResolvedPath path) {
        return path.fold().getGroups().length == 1
            && path.getValueSourceKind() == ValueSourceKind.NONE
            && ObservableDependencyKind.get(path.getValueTypeInstance().declaration()) == ObservableDependencyKind.CONTENT;
    }
}

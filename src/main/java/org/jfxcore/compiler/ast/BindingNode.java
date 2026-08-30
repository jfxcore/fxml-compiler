// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.expression.ExpressionNode;
import org.jfxcore.compiler.ast.expression.ExpressionResolution;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.Objects;

import static org.jfxcore.compiler.type.KnownSymbols.*;

public class BindingNode extends AbstractNode {

    private final BindingMode mode;
    private ExpressionNode path;
    private ExpressionNode converter;
    private ExpressionNode format;

    public static BindingNode newInstance(BindingMode mode,
                                          ExpressionNode path,
                                          @Nullable ExpressionNode converter,
                                          @Nullable ExpressionNode format,
                                          SourceInfo sourceInfo) {
        return new BindingNode(mode, path, converter, format, sourceInfo);
    }

    private BindingNode(BindingMode mode,
                       ExpressionNode path,
                       @Nullable ExpressionNode converter,
                       @Nullable ExpressionNode format,
                       SourceInfo sourceInfo) {
        super(sourceInfo);
        this.mode = checkNotNull(mode);
        this.path = checkNotNull(path);
        this.converter = converter;
        this.format = format;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        path = (ExpressionNode)path.accept(visitor);

        if (converter != null) {
            converter = (ExpressionNode)converter.accept(visitor);
        }

        if (format != null) {
            format = (ExpressionNode)format.accept(visitor);
        }
    }

    public BindingMode getMode() {
        return mode;
    }

    public ExpressionNode getPath() {
        return path;
    }

    public @Nullable ExpressionNode getConverter() {
        return converter;
    }

    public @Nullable ExpressionNode getFormat() {
        return format;
    }

    public BindingEmitterInfo toPathEmitter(TypeInstance invokingType, @Nullable TypeInstance targetType) {
        return path.resolve(mode, invokingType, targetType).toEmitter();
    }

    public ExpressionResolution resolvePath(TypeInstance invokingType, @Nullable TypeInstance targetType) {
        return path.resolve(mode, invokingType, targetType);
    }

    public @Nullable BindingEmitterInfo toConverterEmitter(TypeInstance invokingType) {
        return converter != null
            ? converter.resolve(BindingMode.ONCE, invokingType, TypeInstance.of(StringConverterDecl())).toEmitter()
            : null;
    }

    public @Nullable BindingEmitterInfo toFormatEmitter(TypeInstance invokingType) {
        return format != null
            ? format.resolve(BindingMode.ONCE, invokingType, TypeInstance.of(FormatDecl())).toEmitter()
            : null;
    }

    /**
     * Gets the smallest binding distance within the binding expression,
     * where binding to the current element == 0, binding to the first parent == 1, etc.
     */
    public int getBindingDistance() {
        return path.getBindingDistance();
    }

    @Override
    public BindingNode deepClone() {
        return new BindingNode(
            mode,
            path.deepClone(),
            converter != null ? converter.deepClone() : null,
            format != null ? format.deepClone() : null,
            getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BindingNode other && equalsNode(other);
    }

    boolean equalsNode(BindingNode other) {
        return mode == other.mode
            && path.equals(other.path)
            && Objects.equals(format, other.format)
            && Objects.equals(converter, other.converter);
    }
}

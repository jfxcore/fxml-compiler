// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.Visitor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FunctionNode extends TextNode {

    private PathNode path;
    private final List<ValueNode> arguments;

    public FunctionNode(PathNode path, Collection<? extends ValueNode> arguments, SourceInfo sourceInfo) {
        super(format(path, arguments), sourceInfo);
        this.path = path;
        this.arguments = new ArrayList<>(checkNotNull(arguments));
    }

    private FunctionNode(PathNode path, Collection<? extends ValueNode> arguments, TypeNode type, SourceInfo sourceInfo) {
        super(format(path, arguments), false, type, sourceInfo);
        this.path = path;
        this.arguments = new ArrayList<>(checkNotNull(arguments));
    }

    public PathNode getPath() {
        return path;
    }

    public List<ValueNode> getArguments() {
        return arguments;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        path = (PathNode) path.accept(visitor);
        acceptChildren(arguments, visitor);
    }

    @Override
    public FunctionNode deepClone() {
        return new FunctionNode(path.deepClone(), deepClone(arguments), getType(), getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FunctionNode that = (FunctionNode) o;
        return Objects.equals(path, that.path)
            && Objects.equals(arguments, that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), path, arguments);
    }

    private static String format(TextNode name, Collection<? extends ValueNode> arguments) {
        return name.getText() + "(" + arguments.stream().map(node -> {
            if (node instanceof TextNode) {
                return ((TextNode)node).getText();
            }

            return node.getType().getMarkupName();
        }).collect(Collectors.joining(",")) + ")";
    }

}

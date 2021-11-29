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
import java.util.stream.Collectors;

public class FunctionNode extends TextNode {

    private TextNode name;
    private final List<ValueNode> arguments;

    public FunctionNode(TextNode name, Collection<? extends ValueNode> arguments, SourceInfo sourceInfo) {
        super(format(name, arguments), sourceInfo);
        this.name = name;
        this.arguments = new ArrayList<>(checkNotNull(arguments));
    }

    private FunctionNode(TextNode name, Collection<? extends ValueNode> arguments, TypeNode type, SourceInfo sourceInfo) {
        super(format(name, arguments), false, type, sourceInfo);
        this.name = name;
        this.arguments = new ArrayList<>(checkNotNull(arguments));
    }

    public TextNode getName() {
        return name;
    }

    public List<ValueNode> getArguments() {
        return arguments;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        name = (TextNode)name.accept(visitor);
        acceptChildren(arguments, visitor);
    }

    @Override
    public FunctionNode deepClone() {
        return new FunctionNode(name.deepClone(), deepClone(arguments), getType(), getSourceInfo());
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

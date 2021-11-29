// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.codebehind;

import javassist.Modifier;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.ast.ValueNode;

public class AddCodeFieldNode extends PropertyNode implements JavaEmitterNode {

    private final int modifier;

    public AddCodeFieldNode(String name, ValueNode value, int modifier, SourceInfo sourceInfo) {
        super(new String[] {name}, name, value, false, sourceInfo);
        this.modifier = modifier;
    }

    public int getModifier() {
        return modifier;
    }

    @Override
    public void emit(JavaEmitContext context) {
        boolean isPrivate = Modifier.isPrivate(modifier);
        boolean isProtected = Modifier.isProtected(modifier);

        context.getOutput().append(
            String.format(
                "\t%s%s@javafx.fxml.FXML %s %s;\r\n",
                isPrivate ? "private " : "",
                isProtected ? "protected " : "",
                ((TextNode)getValues().get(0)).getText(),
                getName()));
    }

    @Override
    public AddCodeFieldNode deepClone() {
        return new AddCodeFieldNode(getName(), (ValueNode)getValues().get(0), modifier, getSourceInfo());
    }

}

// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.codebehind;

import javassist.Modifier;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.LiteralValueNode;
import org.jfxcore.compiler.ast.PropertyNode;

public class AddCodeFieldNode extends PropertyNode implements JavaEmitterNode {

    private final int modifier;

    public AddCodeFieldNode(String name, LiteralValueNode value, int modifier, SourceInfo sourceInfo) {
        super(new String[] {name}, name, value, false, false, sourceInfo);
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
                "\t%s%s%s %s;\r\n",
                isPrivate ? "private " : "",
                isProtected ? "protected " : "",
                ((LiteralValueNode)getValues().get(0)).getText(),
                getName()));
    }

    @Override
    public AddCodeFieldNode deepClone() {
        return new AddCodeFieldNode(
            getName(), (LiteralValueNode)getValues().get(0), modifier, getSourceInfo()).copy(this);
    }
}

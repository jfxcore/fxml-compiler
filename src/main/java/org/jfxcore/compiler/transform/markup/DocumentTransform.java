// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.emit.EmitInitializeRootNode;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.util.Bytecode;
import java.lang.reflect.Modifier;

import static org.jfxcore.compiler.type.Types.*;

public class DocumentTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!node.typeEquals(DocumentNode.class)) {
            return node;
        }

        DocumentNode documentNode = (DocumentNode)node;

        return new EmitInitializeRootNode(documentNode.getRoot(), false, documentNode.getSourceInfo()) {
            @Override
            public void emit(BytecodeEmitContext context) {
                super.emit(context);
                emitStaticInitializer(context);
            }

            private void emitStaticInitializer(BytecodeEmitContext context) {
                TypeDeclaration clazz = context.getMarkupClass();

                MethodDeclaration method = clazz.createMethod("<clinit>", voidDecl())
                                                       .setModifiers(Modifier.PUBLIC | Modifier.STATIC);

                Bytecode code = new Bytecode(clazz, 0);

                for (TypeDeclaration nestedClass : context.getNestedClasses()) {
                    code.invoke(nestedClass.requireDeclaredMethod("forceInit"));
                }

                code.vreturn();

                method.setCode(code);
            }
        };
    }
}

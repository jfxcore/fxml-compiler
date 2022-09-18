// Copyright (c) 2021, 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.emit.EmitInitializeRootNode;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.Bytecode;
import java.lang.reflect.Modifier;

import static org.jfxcore.compiler.util.Descriptors.function;

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
                unchecked(() -> {
                    CtClass clazz = context.getMarkupClass();

                    CtMethod method = new CtMethod(CtClass.voidType, MethodInfo.nameClinit, new CtClass[0], clazz);
                    method.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
                    clazz.addMethod(method);

                    Bytecode code = new Bytecode(clazz, 0);

                    for (CtClass nestedClass : context.getNestedClasses()) {
                        code.invokestatic(nestedClass, "forceInit", function(CtClass.voidType));
                    }

                    code.vreturn();

                    method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
                    method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
                });
            }
        };
    }

}

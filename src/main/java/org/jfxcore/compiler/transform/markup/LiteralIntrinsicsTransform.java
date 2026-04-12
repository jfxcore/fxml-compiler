// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.expression.FunctionExpressionNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import java.util.List;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * Transforms the {@code fx:Null}, {@code fx:True}, {@code fx:False}, and {@code fx:Type} intrinsics into their values.
 */
public class LiteralIntrinsicsTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!node.typeEquals(ObjectNode.class) || !((ObjectNode)node).isIntrinsic(
                Intrinsics.NULL, Intrinsics.TRUE, Intrinsics.FALSE, Intrinsics.TYPE)){
            return node;
        }

        ObjectNode objectNode = (ObjectNode)node;
        Node parentNode = context.getParent();
        if (!(parentNode instanceof PropertyNode) && !(parentNode instanceof FunctionExpressionNode)) {
            throw GeneralErrors.unexpectedIntrinsic(node.getSourceInfo(), objectNode.getType().getMarkupName());
        }

        if (objectNode.isIntrinsic(Intrinsics.NULL)) {
            return new EmitLiteralNode(TypeInstance.nullType(), null, node.getSourceInfo());
        }

        if (objectNode.isIntrinsic(Intrinsics.TRUE)) {
            return new EmitLiteralNode(TypeInstance.booleanType(), true, node.getSourceInfo());
        }

        if (objectNode.isIntrinsic(Intrinsics.FALSE)) {
            return new EmitLiteralNode(TypeInstance.booleanType(), false, node.getSourceInfo());
        }

        if (objectNode.isIntrinsic(Intrinsics.TYPE)) {
            return transformTypeIntrinsic(context, objectNode);
        }

        return node;
    }

    private Node transformTypeIntrinsic(TransformContext context, ObjectNode node) {
        PropertyNode propertyNode = node.getProperty("name");
        SourceInfo sourceInfo = propertyNode.getTrimmedTextSourceInfo(context);
        Resolver resolver = new Resolver(sourceInfo);
        TypeInvoker invoker = new TypeInvoker(sourceInfo);
        TypeDeclaration clazz = resolver.resolveClassAgainstImports(propertyNode.getTrimmedTextNotEmpty(context));
        TypeInstance typeInstance = invoker.invokeType(clazz);

        return new EmitLiteralNode(
            invoker.invokeType(ClassDecl(), List.of(typeInstance)),
            clazz.name(),
            node.getSourceInfo());
    }
}

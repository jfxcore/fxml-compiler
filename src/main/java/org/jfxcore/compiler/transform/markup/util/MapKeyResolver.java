// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup.util;

import org.jfxcore.compiler.ast.LiteralValueNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.emit.EmitClassConstantNode;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.emit.ReferenceableNode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.NameHelper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import static org.jfxcore.compiler.diagnostic.errors.GeneralErrors.*;
import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * Plans key synthesis for element-notation map items independently of value conversion.
 */
final class MapKeyResolver {

    sealed interface Result {
        record Applicable(MapKeyPlan plan) implements Result {
            public Applicable {
                Objects.requireNonNull(plan);
            }
        }

        record Invalid(MarkupException diagnostic) implements Result {
            public Invalid {
                Objects.requireNonNull(diagnostic);
            }
        }
    }

    static final class MapKeyPlan {
        private final Supplier<? extends ValueNode> lowerer;
        private ValueNode lowered;

        private MapKeyPlan(Supplier<? extends ValueNode> lowerer) {
            this.lowerer = Objects.requireNonNull(lowerer);
        }

        ValueNode lower() {
            if (lowered == null) {
                lowered = Objects.requireNonNull(lowerer.get());
            }

            return lowered;
        }
    }

    private MapKeyResolver() {}

    static Result resolve(
            TransformContext context,
            Node item,
            TypeInstance keyType,
            TargetValueResolver.TargetContext target) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(item);
        Objects.requireNonNull(keyType);
        Objects.requireNonNull(target);

        if (item instanceof LiteralValueNode
                || !(item instanceof EmitLiteralNode
                    || item instanceof EmitObjectNode
                    || item instanceof EmitClassConstantNode)) {
            return new Result.Invalid(cannotAddItemIncompatibleValue(
                item.getSourceInfo(), target.invokingType().declaration(),
                Objects.requireNonNull(target.targetName()), item.getSourceInfo().getText()));
        }

        if (!keyType.equals(StringDecl()) && !keyType.equals(ObjectDecl())) {
            return new Result.Invalid(unsupportedMapKeyType(
                item.getSourceInfo(), Objects.requireNonNull(target.targetProperty())));
        }

        if (item instanceof ReferenceableNode referenceable && referenceable.getId() != null) {
            String id = referenceable.getId();
            return new Result.Applicable(new MapKeyPlan(() -> new EmitLiteralNode(
                TypeInstance.StringType(), id, item.getSourceInfo())));
        }

        if (keyType.equals(StringDecl())) {
            StringBuilder builder = new StringBuilder();
            for (Node parent : context.getParents()) {
                if (parent instanceof ValueNode value) {
                    builder.append(value.getType().getMarkupName());
                }
            }

            return new Result.Applicable(new MapKeyPlan(() -> new EmitLiteralNode(
                TypeInstance.StringType(),
                NameHelper.getUniqueName(
                    UUID.nameUUIDFromBytes(builder.toString().getBytes(StandardCharsets.UTF_8)).toString(),
                    item),
                item.getSourceInfo())));
        }

        TypeInstance itemValueType = TypeHelper.getTypeInstance(item);

        if (Core.TemplateDecl() != null && itemValueType.subtypeOf(Core.TemplateDecl())) {
            TypeInstance templateItemType = new Resolver(item.getSourceInfo()).tryFindArgument(itemValueType, Core.TemplateDecl());
            TypeInstance classType = new TypeInvoker(item.getSourceInfo()).invokeType(ClassDecl(), List.of(templateItemType));

            return new Result.Applicable(
                new MapKeyPlan(() -> new EmitLiteralNode(classType, templateItemType.name(), item.getSourceInfo())));
        }

        return new Result.Applicable(new MapKeyPlan(() -> EmitObjectNode
            .constructor(
                TypeInstance.ObjectType(),
                ObjectDecl().requireDeclaredConstructor(),
                List.of(),
                item.getSourceInfo())
            .create()));
    }
}

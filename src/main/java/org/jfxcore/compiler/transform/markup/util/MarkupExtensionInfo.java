// Copyright (c) 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup.util;

import javassist.CtClass;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import org.jfxcore.compiler.util.TypeInvoker;
import java.util.ArrayList;
import java.util.List;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.ExceptionHelper.*;

public sealed interface MarkupExtensionInfo {

    record Supplier(TypeInstance returnType,
                    List<TypeInstance> providedTypes,
                    CtClass markupExtensionInterface) implements MarkupExtensionInfo {
        Supplier(TypeInstance returnType, CtClass markupExtensionClass) {
            this(returnType, List.of(returnType), markupExtensionClass);
        }
    }

    record PropertyConsumer(TypeInstance propertyType,
                            CtClass markupExtensionInterface,
                            boolean readOnly) implements MarkupExtensionInfo {}

    static MarkupExtensionInfo of(Node node) {
        if (!Markup.isAvailable()) {
            return null;
        }

        TypeInstance type = TypeHelper.getTypeInstance(node);
        var resolver = new Resolver(node.getSourceInfo());
        var invoker = new TypeInvoker(node.getSourceInfo());

        if (type.subtypeOf(Markup.MarkupExtension.SupplierType())) {
            var applyMethod = resolver.tryResolveMethod(type.jvmType(), method ->
                unchecked(node.getSourceInfo(), () ->
                    method.getName().equals("get")
                    && method.getParameterTypes().length == 1
                    && TypeHelper.equals(method.getParameterTypes()[0], Markup.MarkupContextType())));

            var returnType = invoker.invokeReturnType(applyMethod, List.of(type));

            var returnTypeAnnotation = resolver.tryResolveMethodAnnotation(
                applyMethod, Markup.MarkupExtensionReturnTypeAnnotationName, false);

            List<TypeInstance> providedTypes = new ArrayList<>();

            if (returnTypeAnnotation != null) {
                for (String typeName : TypeHelper.getAnnotationClassArray(returnTypeAnnotation, "value")) {
                    CtClass supportedType = resolver.resolveClass(typeName);
                    if (unchecked(SourceInfo.none(), () -> supportedType.subtypeOf(returnType.jvmType()))) {
                        providedTypes.add(invoker.invokeType(supportedType));
                    }
                }
            }

            if (providedTypes.isEmpty()) {
                providedTypes.add(returnType);
            }

            return new Supplier(returnType, providedTypes, Markup.MarkupExtension.SupplierType());
        }

        if (type.subtypeOf(Markup.MarkupExtension.BooleanSupplierType())) {
            return new Supplier(TypeInstance.booleanType(), Markup.MarkupExtension.BooleanSupplierType());
        }

        if (type.subtypeOf(Markup.MarkupExtension.IntSupplierType())) {
            return new Supplier(TypeInstance.intType(), Markup.MarkupExtension.IntSupplierType());
        }

        if (type.subtypeOf(Markup.MarkupExtension.LongSupplierType())) {
            return new Supplier(TypeInstance.longType(), Markup.MarkupExtension.LongSupplierType());
        }

        if (type.subtypeOf(Markup.MarkupExtension.FloatSupplierType())) {
            return new Supplier(TypeInstance.floatType(), Markup.MarkupExtension.FloatSupplierType());
        }

        if (type.subtypeOf(Markup.MarkupExtension.DoubleSupplierType())) {
            return new Supplier(TypeInstance.doubleType(), Markup.MarkupExtension.DoubleSupplierType());
        }

        CtClass propertyType = type.subtypeOf(Markup.MarkupExtension.PropertyConsumerType())
            ? PropertyType()
            : type.subtypeOf(Markup.MarkupExtension.ReadOnlyPropertyConsumerType())
                ? ReadOnlyPropertyType()
                : null;

        if (propertyType != null) {
            var consumeMethod = resolver.tryResolveMethod(type.jvmType(), method ->
                unchecked(node.getSourceInfo(), () ->
                    method.getName().equals("accept")
                    && method.getParameterTypes().length == 2
                    && TypeHelper.equals(method.getParameterTypes()[0], propertyType)
                    && TypeHelper.equals(method.getParameterTypes()[1], Markup.MarkupContextType())));

            var paramType = invoker.invokeParameterTypes(consumeMethod, List.of(type))[0];

            if (propertyType == PropertyType()) {
                return new PropertyConsumer(paramType, Markup.MarkupExtension.PropertyConsumerType(), false);
            }

            if (propertyType == ReadOnlyPropertyType()) {
                return new PropertyConsumer(paramType, Markup.MarkupExtension.ReadOnlyPropertyConsumerType(), true);
            }
        }

        return null;
    }
}

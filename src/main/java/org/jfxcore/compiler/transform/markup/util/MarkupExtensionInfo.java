// Copyright (c) 2025, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup.util;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import java.util.ArrayList;
import java.util.List;

import static org.jfxcore.compiler.type.Types.*;

public sealed interface MarkupExtensionInfo {

    record Supplier(TypeInstance returnType,
                    List<TypeInstance> providedTypes,
                    TypeDeclaration markupExtensionInterface) implements MarkupExtensionInfo {
        Supplier(TypeInstance returnType, TypeDeclaration markupExtensionClass) {
            this(returnType, List.of(returnType), markupExtensionClass);
        }
    }

    record PropertyConsumer(TypeInstance propertyType,
                            TypeDeclaration markupExtensionInterface,
                            boolean readOnly) implements MarkupExtensionInfo {}

    static MarkupExtensionInfo of(Node node) {
        if (!Markup.isAvailable()) {
            return null;
        }

        TypeInstance type = TypeHelper.getTypeInstance(node);
        var resolver = new Resolver(node.getSourceInfo());
        var invoker = new TypeInvoker(node.getSourceInfo());

        if (type.subtypeOf(Markup.MarkupExtension.SupplierDecl())) {
            var applyMethod = resolver.tryResolveMethod(type.declaration(), method ->
                method.name().equals("get")
                && method.parameters().size() == 1
                && method.parameters().get(0).type().equals(Markup.MarkupContextDecl()));

            if (applyMethod == null) {
                throw SymbolResolutionErrors.memberNotFound(node.getSourceInfo(), type.declaration(), "get");
            }

            var returnType = invoker.invokeReturnType(applyMethod, List.of(type));
            var returnTypeAnnotation = applyMethod.annotation(Markup.MarkupExtensionReturnTypeAnnotationName).orElse(null);
            List<TypeInstance> providedTypes = new ArrayList<>();

            if (returnTypeAnnotation != null) {
                for (String typeName : returnTypeAnnotation.getClassArray("value")) {
                    TypeDeclaration supportedType = resolver.resolveClass(typeName);
                    if (supportedType.subtypeOf(returnType.declaration())) {
                        providedTypes.add(invoker.invokeType(supportedType));
                    }
                }
            }

            // If the following conditions are met, we consider the supplier extension to return the bottom type:
            //   1. The MarkupExtension.Supplier interface is implemented with a generic type parameter.
            //   2. The extension type is used in raw form such that the generic type parameter of the
            //      MarkupExtension.Supplier interface is erased.
            //   3. The 'get' method returns java.lang.Object
            //
            // In this special case, the markup extension is considered to be applicable to any type.
            if (providedTypes.isEmpty()) {
                if (returnType.equals(TypeInstance.ObjectType()) && isRawSupplier(type)) {
                    providedTypes.add(TypeInstance.bottomType());
                } else {
                    providedTypes.add(returnType);
                }
            }

            return new Supplier(returnType, providedTypes, Markup.MarkupExtension.SupplierDecl());
        }

        if (type.subtypeOf(Markup.MarkupExtension.BooleanSupplierDecl())) {
            return new Supplier(TypeInstance.booleanType(), Markup.MarkupExtension.BooleanSupplierDecl());
        }

        if (type.subtypeOf(Markup.MarkupExtension.IntSupplierDecl())) {
            return new Supplier(TypeInstance.intType(), Markup.MarkupExtension.IntSupplierDecl());
        }

        if (type.subtypeOf(Markup.MarkupExtension.LongSupplierDecl())) {
            return new Supplier(TypeInstance.longType(), Markup.MarkupExtension.LongSupplierDecl());
        }

        if (type.subtypeOf(Markup.MarkupExtension.FloatSupplierDecl())) {
            return new Supplier(TypeInstance.floatType(), Markup.MarkupExtension.FloatSupplierDecl());
        }

        if (type.subtypeOf(Markup.MarkupExtension.DoubleSupplierDecl())) {
            return new Supplier(TypeInstance.doubleType(), Markup.MarkupExtension.DoubleSupplierDecl());
        }

        TypeDeclaration propertyType = type.subtypeOf(Markup.MarkupExtension.PropertyConsumerDecl())
            ? PropertyDecl()
            : type.subtypeOf(Markup.MarkupExtension.ReadOnlyPropertyConsumerDecl())
                ? ReadOnlyPropertyDecl()
                : null;

        if (propertyType != null) {
            var consumeMethod = resolver.tryResolveMethod(type.declaration(), method ->
                method.name().equals("accept")
                && method.parameters().size() == 2
                && method.parameters().get(0).type().equals(propertyType)
                && method.parameters().get(1).type().equals(Markup.MarkupContextDecl()));

            var paramType = invoker.invokeParameterTypes(consumeMethod, List.of(type))[0];

            if (propertyType == PropertyDecl()) {
                return new PropertyConsumer(paramType, Markup.MarkupExtension.PropertyConsumerDecl(), false);
            }

            if (propertyType == ReadOnlyPropertyDecl()) {
                return new PropertyConsumer(paramType, Markup.MarkupExtension.ReadOnlyPropertyConsumerDecl(), true);
            }
        }

        return null;
    }

    private static boolean isRawSupplier(TypeInstance type) {
        TypeInstance supplierType = getSupplierType(type);
        return supplierType != null && supplierType.isRaw();
    }

    private static TypeInstance getSupplierType(TypeInstance type) {
        if (type.equals(Markup.MarkupExtension.SupplierDecl())) {
            return type;
        }

        for (TypeInstance superType : type.superTypes()) {
            TypeInstance supplierType = getSupplierType(superType);
            if (supplierType != null) {
                return supplierType;
            }
        }

        return null;
    }
}

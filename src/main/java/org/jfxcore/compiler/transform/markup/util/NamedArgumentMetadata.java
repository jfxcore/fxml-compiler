// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.AnnotationDeclaration;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * Resolves complete {@code @NamedArg} metadata in an instantiated generic context.
 * */
final class NamedArgumentMetadata {

    record Parameter(String name, @Nullable String defaultValue, TypeInstance type) {
        Parameter {
            Objects.requireNonNull(name);
            Objects.requireNonNull(type);
        }

        boolean isOptional() {
            return defaultValue != null;
        }
    }

    private NamedArgumentMetadata() {}

    static List<Parameter> get(TypeInstance type, ConstructorDeclaration constructor, SourceInfo sourceInfo) {
        TypeInstance[] parameterTypes = new TypeInvoker(sourceInfo).invokeParameterTypes(constructor, List.of(type));
        List<Parameter> result = new ArrayList<>(parameterTypes.length);

        for (int i = 0; i < parameterTypes.length; ++i) {
            AnnotationDeclaration namedArg = constructor.parameters().get(i).annotations().stream()
                .filter(annotation -> annotation.typeName().equals(NamedArgAnnotationName))
                .findFirst()
                .orElse(null);

            if (namedArg == null) {
                return List.of();
            }

            String name = namedArg.getString("value");
            if (name != null) {
                result.add(new Parameter(name, namedArg.getString("defaultValue"), parameterTypes[i]));
            }
        }

        return List.copyOf(result);
    }
}

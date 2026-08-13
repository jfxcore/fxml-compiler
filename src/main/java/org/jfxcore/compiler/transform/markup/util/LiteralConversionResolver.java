// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup.util;

import javafx.scene.paint.Color;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.emit.EmitClassConstantNode;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.parse.TypeParser;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * Discovers a literal-to-target conversion without constructing an emitter node.
 */
final class LiteralConversionResolver {

    record TargetDescriptor(
            TypeInstance targetType,
            List<TypeInstance> declaringTypes,
            @Nullable String backingField,
            SourceInfo sourceInfo) {

        TargetDescriptor {
            Objects.requireNonNull(targetType);
            declaringTypes = List.copyOf(declaringTypes);
            Objects.requireNonNull(sourceInfo);
        }
    }

    sealed interface Result {
        record Applicable(LiteralConversionPlan plan) implements Result {
            public Applicable {
                Objects.requireNonNull(plan);
            }
        }

        record NotApplicable(@Nullable MarkupException diagnostic) implements Result {}
    }

    /**
     * A deferred literal conversion. The emitter is materialized at most once.
     */
    static final class LiteralConversionPlan {
        private final Supplier<? extends ValueEmitterNode> lowerer;
        private ValueEmitterNode lowered;

        private LiteralConversionPlan(Supplier<? extends ValueEmitterNode> lowerer) {
            this.lowerer = Objects.requireNonNull(lowerer);
        }

        ValueEmitterNode lower() {
            if (lowered == null) {
                lowered = Objects.requireNonNull(lowerer.get());
            }

            return lowered;
        }
    }

    private static final Map<Color, Field> COLOR_FIELDS = createColorFields();

    private LiteralConversionResolver() {}

    static Result resolve(String value, TargetDescriptor target) {
        try {
            LiteralConversionPlan plan = find(value, target);
            return plan != null ? new Result.Applicable(plan) : new Result.NotApplicable(null);
        } catch (MarkupException ex) {
            return new Result.NotApplicable(ex);
        }
    }

    private static @Nullable LiteralConversionPlan find(String value, TargetDescriptor target) {
        TypeInstance targetType = target.targetType();
        TypeInstance boxedTargetType = targetType.boxed();
        SourceInfo sourceInfo = target.sourceInfo();
        String backingField = target.backingField();
        String trimmedValue = value.trim();

        switch (targetType.name()) {
        case "boolean":
        case BooleanName:
            if (trimmedValue.equals("true")) {
                return new LiteralConversionPlan(() ->
                    new EmitLiteralNode(backingField, TypeInstance.booleanType(), true, sourceInfo));
            } else if (trimmedValue.equals("false")) {
                return new LiteralConversionPlan(() ->
                    new EmitLiteralNode(backingField, TypeInstance.booleanType(), false, sourceInfo));
            }

            break;
        case "char":
        case CharacterName:
            if (trimmedValue.length() == 1) {
                return new LiteralConversionPlan(() ->
                    new EmitLiteralNode(
                        backingField, TypeInstance.charType(), trimmedValue.charAt(0), sourceInfo));
            }

            break;
        case "byte":
        case ByteName:
            try {
                byte converted = Byte.parseByte(trimmedValue);
                return new LiteralConversionPlan(() ->
                    new EmitLiteralNode(backingField, TypeInstance.byteType(), converted, sourceInfo));
            } catch (NumberFormatException ex) {
                break;
            }
        case "short":
        case ShortName:
            try {
                short converted = Short.parseShort(trimmedValue);
                return new LiteralConversionPlan(() ->
                    new EmitLiteralNode(backingField, TypeInstance.shortType(), converted, sourceInfo));
            } catch (NumberFormatException ex) {
                break;
            }
        case "int":
        case IntegerName:
            try {
                int converted = Integer.parseInt(trimmedValue);
                return new LiteralConversionPlan(() ->
                    new EmitLiteralNode(backingField, TypeInstance.intType(), converted, sourceInfo));
            } catch (NumberFormatException ex) {
                break;
            }
        case "long":
        case LongName:
            try {
                long converted = Long.parseLong(trimmedValue);
                return new LiteralConversionPlan(() ->
                    new EmitLiteralNode(backingField, TypeInstance.longType(), converted, sourceInfo));
            } catch (NumberFormatException ex) {
                break;
            }
        case "float":
        case FloatName:
            try {
                float converted = Float.parseFloat(trimmedValue);
                return new LiteralConversionPlan(() ->
                    new EmitLiteralNode(backingField, TypeInstance.floatType(), converted, sourceInfo));
            } catch (NumberFormatException ex) {
                break;
            }
        case "double":
        case DoubleName:
            try {
                double converted = Double.parseDouble(trimmedValue);
                return new LiteralConversionPlan(() ->
                    new EmitLiteralNode(backingField, TypeInstance.doubleType(), converted, sourceInfo));
            } catch (NumberFormatException ex) {
                break;
            }
        }

        if (targetType.subtypeOf(ClassDecl())) {
            List<TypeInstance> types = new TypeParser(value, sourceInfo).parse();

            if (types.size() > 1 || !types.get(0).arguments().isEmpty() && !types.get(0).isRaw()) {
                throw ParserErrors.invalidExpression(sourceInfo);
            }

            TypeInstance sourceType = new TypeInvoker(sourceInfo).invokeType(ClassDecl(), types);

            if (!targetType.isAssignableFrom(sourceType)) {
                throw BindingSourceErrors.cannotConvertSourceType(
                    sourceInfo, sourceType.javaName(), targetType.javaName());
            }

            return new LiteralConversionPlan(() -> new EmitLiteralNode(
                backingField, targetType, types.get(0).declaration().javaName(), sourceInfo));
        }

        if (targetType.declaration().isEnum()) {
            return targetType.declaration().field(trimmedValue)
                .map(field -> new LiteralConversionPlan(() ->
                    new EmitLiteralNode(backingField, targetType, field, sourceInfo)))
                .orElseThrow(() -> SymbolResolutionErrors.memberNotFound(sourceInfo, targetType.declaration(), value));
        }

        TypeInstance colorType = TypeInstance.of(ColorDecl());
        if (colorType.subtypeOf(targetType)) {
            try {
                Color color = Color.valueOf(trimmedValue);
                Field colorField = COLOR_FIELDS.get(color);

                if (colorField != null) {
                    return new LiteralConversionPlan(() -> new EmitClassConstantNode(
                        backingField, colorType, ColorDecl(), colorField.getName(), sourceInfo));
                }

                MethodDeclaration valueOfMethod = new Resolver(sourceInfo).tryResolveMethod(
                    ColorDecl(), method -> "valueOf".equals(method.name()));

                return new LiteralConversionPlan(() -> EmitObjectNode
                    .valueOf(colorType, valueOfMethod, sourceInfo)
                    .textValue(trimmedValue)
                    .create());
            } catch (NullPointerException | IllegalArgumentException ex) {
                if (boxedTargetType.subtypeOf(colorType)) {
                    return null;
                }
            }
        }

        for (TypeInstance declaringType : target.declaringTypes()) {
            // Always use the boxed type to support static fields on primitive wrapper classes.
            TypeDeclaration boxedDeclaringType = declaringType.boxed().declaration();
            FieldDeclaration field = new Resolver(sourceInfo).tryResolveField(
                boxedDeclaringType, trimmedValue);

            if (field != null) {
                TypeInstance fieldType = new TypeInvoker(sourceInfo).invokeFieldType(
                    field, List.of(declaringType));
                if (targetType.isAssignableFrom(fieldType)) {
                    return new LiteralConversionPlan(() -> new EmitClassConstantNode(
                        backingField, targetType, boxedDeclaringType, field.name(), sourceInfo));
                }
            }
        }

        if (TypeInstance.StringType().subtypeOf(targetType)) {
            return new LiteralConversionPlan(() ->
                new EmitLiteralNode(backingField, TypeInstance.StringType(), value, sourceInfo));
        }

        return null;
    }

    private static Map<Color, Field> createColorFields() {
        Map<Color, Field> fields = new HashMap<>();

        for (Field field : Color.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    || !Modifier.isPublic(field.getModifiers())
                    || !Modifier.isFinal(field.getModifiers())
                    || !field.getType().equals(Color.class)) {
                continue;
            }

            try {
                fields.put((Color)field.get(null), field);
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }

        return Map.copyOf(fields);
    }
}

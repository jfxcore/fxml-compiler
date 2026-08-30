// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AttributeValueNode;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.BindingNode;
import org.jfxcore.compiler.ast.LiteralValueNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.emit.EmitApplyMarkupExtensionNode;
import org.jfxcore.compiler.ast.emit.EmitArrayNode;
import org.jfxcore.compiler.ast.emit.EmitterNode;
import org.jfxcore.compiler.ast.emit.EmitPropertyAdderNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingTypeInfo;
import org.jfxcore.compiler.ast.expression.ExpressionResolution;
import org.jfxcore.compiler.ast.expression.TargetTypeNotApplicableException;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.DiagnosticInfo;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.PropertyInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static org.jfxcore.compiler.type.TypeInstance.AssignmentContext.*;
import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * Resolves a syntax value into a target-specific semantic plan.
 */
public final class TargetValueResolver {

    public enum TargetKind {
        PROPERTY,
        COLLECTION_ITEM,
        ARRAY_COMPONENT,
        CONSTRUCTOR_PARAMETER,
        OBJECT
    }

    /**
     * Stores a parsed value in the two forms the resolver may need: one complete value for direct
     * use and a list of items for an array, collection, map, or constructor. It also remembers
     * which uses are allowed by the original syntax.
     */
    public static final class ValueInput {

        private enum MapPopulationPolicy {
            NONE,
            REJECT_KEYLESS_ITEMS,
            DERIVE_KEYS_FROM_ELEMENTS
        }

        private final @Nullable Node directValue;
        private final List<Node> structuralItems;
        private final SourceInfo sourceInfo;
        private final boolean allowsConstructedValueFallback;
        private final MapPopulationPolicy mapPopulationPolicy;

        private ValueInput(
                @Nullable Node directValue,
                List<? extends Node> structuralItems,
                SourceInfo sourceInfo,
                boolean allowsConstructedValueFallback,
                MapPopulationPolicy mapPopulationPolicy) {
            this.directValue = directValue;
            this.structuralItems = List.copyOf(structuralItems);
            this.sourceInfo = Objects.requireNonNull(sourceInfo);
            this.allowsConstructedValueFallback = allowsConstructedValueFallback;
            this.mapPopulationPolicy = Objects.requireNonNull(mapPopulationPolicy);
        }

        public @Nullable Node directValue() {
            return directValue;
        }

        public List<Node> structuralItems() {
            return structuralItems;
        }

        public SourceInfo sourceInfo() {
            return sourceInfo;
        }

        public static ValueInput of(Node node) {
            Objects.requireNonNull(node);

            if (node instanceof AttributeValueNode attribute) {
                return of(attribute);
            }

            if (node instanceof LiteralValueNode literal) {
                return new ValueInput(
                    literal,
                    literal.hasCoercionParts()
                        ? List.copyOf(literal.getCoercionParts()) : List.of(literal),
                    literal.getSourceInfo(), true, MapPopulationPolicy.NONE);
            }

            return new ValueInput(
                node, List.of(node), node.getSourceInfo(), true, MapPopulationPolicy.NONE);
        }

        public static ValueInput of(AttributeValueNode attribute) {
            Objects.requireNonNull(attribute);

            return switch (attribute.getForm()) {
                case LITERAL -> {
                    LiteralValueNode literal = attribute.getLiteral();
                    yield new ValueInput(
                        literal,
                        literal.hasCoercionParts() ? List.copyOf(literal.getCoercionParts()) : List.of(literal),
                        attribute.getSourceInfo(), true, MapPopulationPolicy.NONE);
                }

                case SYNTAX -> new ValueInput(
                    attribute.getSyntax(),
                    List.of(attribute.getSyntax()), attribute.getSourceInfo(),
                    true, MapPopulationPolicy.NONE);

                case SEQUENCE -> {
                    List<Node> items = attribute.getItems();

                    yield new ValueInput(
                        items.size() == 1 ? items.get(0) : null, items, attribute.getSourceInfo(),
                        true, MapPopulationPolicy.REJECT_KEYLESS_ITEMS);
                }
            };
        }

        public static ValueInput structural(List<? extends Node> items, SourceInfo sourceInfo) {
            return new ValueInput(null, items, sourceInfo, true, MapPopulationPolicy.NONE);
        }

        /**
         * Creates input from values inside a property element. A single value is tried as-is first.
         * If that does not work, its items may be used to fill an array, collection, or map, or to
         * call a constructor.
         */
        public static ValueInput propertyContent(             List<? extends Node> values, SourceInfo sourceInfo) {
            Objects.requireNonNull(values);
            List<Node> items = new ArrayList<>();

            for (Node value : values) {
                if (value instanceof LiteralValueNode literal && literal.hasCoercionParts()) {
                    items.addAll(literal.getCoercionParts());
                } else {
                    items.add(value);
                }
            }

            Node directValue = values.size() == 1 ? values.get(0) : null;
            boolean allowsConstructedValueFallback = directValue instanceof LiteralValueNode;

            return new ValueInput(
                directValue, items, sourceInfo, allowsConstructedValueFallback,
                MapPopulationPolicy.DERIVE_KEYS_FROM_ELEMENTS);
        }
    }

    /**
     * Stores the extra information needed to create an object after its constructor is chosen.
     */
    public record ConstructionSite(
            @Nullable String backingField,
            List<Node> children,
            SourceInfo sourceInfo) {

        public ConstructionSite {
            children = List.copyOf(children);
            Objects.requireNonNull(sourceInfo);
        }

        public static ConstructionSite empty(SourceInfo sourceInfo) {
            return new ConstructionSite(null, List.of(), sourceInfo);
        }
    }

    /**
     * Describes where a value will go, such as a property, constructor parameter, collection item,
     * array element, or object. It gives every destination a name and type for error messages.
     */
    public record TargetSubject(
            TargetKind kind,
            String displayName,
            TypeInstance type,
            @Nullable String name,
            @Nullable PropertyInfo property,
            SourceInfo sourceInfo) {

        public TargetSubject {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(displayName);
            Objects.requireNonNull(type);
            Objects.requireNonNull(sourceInfo);
        }
    }

    public record TargetContext(
            TargetSubject subject,
            TypeInstance invokingType,
            int parentsUnderInitialization,
            boolean currentObjectUnderInitialization,
            ConstructionSite constructionSite) {

        public TargetContext {
            Objects.requireNonNull(subject);
            Objects.requireNonNull(invokingType);
            Objects.requireNonNull(constructionSite);
        }

        public TypeInstance type() {
            return subject.type();
        }

        public @Nullable String targetName() {
            return subject.name();
        }

        public @Nullable PropertyInfo targetProperty() {
            return subject.property();
        }

        public boolean isDirectProperty() {
            return subject.kind() == TargetKind.PROPERTY;
        }

        public static TargetContext property(
                PropertyInfo property,
                TypeInstance invokingType,
                int parentsUnderInitialization,
                SourceInfo sourceInfo) {
            return new TargetContext(
                new TargetSubject(
                    TargetKind.PROPERTY, NameHelper.formatPropertyName(property), property.getType(),
                    property.getName(), property, sourceInfo),
                invokingType, parentsUnderInitialization, false, ConstructionSite.empty(sourceInfo));
        }

        public static TargetContext collectionItem(
                TypeInstance collectionType,
                TypeInstance itemType,
                TypeInstance invokingType,
                int parentsUnderInitialization,
                SourceInfo sourceInfo) {
            return new TargetContext(
                new TargetSubject(
                    TargetKind.COLLECTION_ITEM, collectionType.simpleName(), itemType,
                    null, null, sourceInfo),
                invokingType, parentsUnderInitialization, false, ConstructionSite.empty(sourceInfo));
        }

        public static TargetContext constructorParameter(
                TypeInstance constructedType,
                String parameterName,
                TypeInstance parameterType,
                TypeInstance invokingType,
                int parentsUnderInitialization,
                SourceInfo sourceInfo) {
            return constructorParameter(
                constructedType, parameterName, parameterType, invokingType,
                parentsUnderInitialization, true, sourceInfo);
        }

        static TargetContext constructorParameter(
                TypeInstance constructedType,
                String parameterName,
                TypeInstance parameterType,
                TypeInstance invokingType,
                int parentsUnderInitialization,
                boolean currentObjectUnderInitialization,
                SourceInfo sourceInfo) {
            return new TargetContext(
                new TargetSubject(
                    TargetKind.CONSTRUCTOR_PARAMETER,
                    constructedType.simpleName() + "." + parameterName,
                    parameterType, parameterName, null, sourceInfo),
                invokingType, parentsUnderInitialization, currentObjectUnderInitialization,
                ConstructionSite.empty(sourceInfo));
        }

        public static TargetContext object(
                TypeInstance type,
                TypeInstance invokingType,
                int parentsUnderInitialization,
                ConstructionSite constructionSite) {
            return new TargetContext(
                new TargetSubject(
                    TargetKind.OBJECT, type.simpleName(), type, null, null,
                    constructionSite.sourceInfo()),
                invokingType, parentsUnderInitialization, true, constructionSite);
        }

        public TargetContext arrayComponent(TypeInstance componentType, SourceInfo sourceInfo) {
            return new TargetContext(
                new TargetSubject(
                    TargetKind.ARRAY_COMPONENT, subject.displayName(), componentType,
                    subject.name(), subject.property(), sourceInfo),
                invokingType, parentsUnderInitialization, currentObjectUnderInitialization,
                ConstructionSite.empty(sourceInfo));
        }

        public TargetContext collectionItem(TypeInstance itemType, SourceInfo sourceInfo) {
            return new TargetContext(
                new TargetSubject(
                    TargetKind.COLLECTION_ITEM, subject.displayName(), itemType,
                    subject.name(), subject.property(), sourceInfo),
                invokingType, parentsUnderInitialization, currentObjectUnderInitialization,
                ConstructionSite.empty(sourceInfo));
        }
    }

    public enum PlanKind {
        VALUE,
        PROPERTY
    }

    public enum ConversionKind {
        IDENTITY,
        STRICT,
        LOOSE,
        LITERAL,
        STRUCTURAL,
        IMPLICIT_CONSTRUCTION,
        PROPERTY_CONSUMER
    }

    public sealed interface Lowered {
        record Value(ValueNode node) implements Lowered {
            public Value {
                Objects.requireNonNull(node);
            }
        }

        record Property(EmitterNode node) implements Lowered {
            public Property {
                Objects.requireNonNull(node);
            }
        }
    }

    /**
     * The conversion chosen for a value. Calling {@link #lower()} creates the output node and saves
     * it for later calls. No other conversions are tried at this point.
     */
    public static final class ValuePlan {
        private final PlanKind kind;
        private final TargetContext target;
        private final List<TypeInstance> sourceTypes;
        private final ConversionKind conversionKind;
        private final Supplier<? extends Lowered> lowerer;
        private Lowered lowered;

        private ValuePlan(
                PlanKind kind,
                TargetContext target,
                List<TypeInstance> sourceTypes,
                ConversionKind conversionKind,
                Supplier<? extends Lowered> lowerer) {
            this.kind = Objects.requireNonNull(kind);
            this.target = Objects.requireNonNull(target);
            this.sourceTypes = List.copyOf(sourceTypes);
            this.conversionKind = Objects.requireNonNull(conversionKind);
            this.lowerer = Objects.requireNonNull(lowerer);
        }

        public PlanKind kind() {
            return kind;
        }

        public TargetContext target() {
            return target;
        }

        /**
         * Types provided by the input. Literal and multi-item conversions leave this list empty.
         */
        public List<TypeInstance> sourceTypes() {
            return sourceTypes;
        }

        public ConversionKind conversionKind() {
            return conversionKind;
        }

        public Lowered lower() {
            if (lowered == null) {
                lowered = Objects.requireNonNull(
                    lowerer.get(), "An applicable value plan must always lower successfully");
            }

            return lowered;
        }

        public ValueNode lowerValue() {
            if (kind != PlanKind.VALUE) {
                throw new IllegalStateException("A property-action plan cannot be used as a value");
            }

            return ((Lowered.Value)lower()).node();
        }
    }

    public enum FailureKind {
        TYPE_MISMATCH,
        BINDING_TYPE_MISMATCH,
        TARGET_TYPE_MISMATCH,
        TARGET_CONSTRAINT,
        ROLE_MISMATCH,
        CONSTRUCTION_FAILURE,
        NO_CONVERSION
    }

    /**
     * Stores why a conversion failed so callers can try other choices and report a useful error.
     */
    public record CandidateFailure(
            FailureKind kind,
            SourceInfo sourceInfo,
            TargetContext target,
            List<TypeInstance> sourceTypes,
            @Nullable MarkupException diagnostic) {

        public CandidateFailure {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(sourceInfo);
            Objects.requireNonNull(target);
            sourceTypes = List.copyOf(sourceTypes);
        }

        public @Nullable TypeInstance valueType() {
            return sourceTypes.isEmpty() ? null : sourceTypes.get(0);
        }
    }

    public sealed interface ResolutionResult {
        record Applicable(ValuePlan plan) implements ResolutionResult {
            public Applicable {
                Objects.requireNonNull(plan);
            }
        }

        record NotApplicable(CandidateFailure failure) implements ResolutionResult {
            public NotApplicable {
                Objects.requireNonNull(failure);
            }
        }

        record Invalid(MarkupException diagnostic) implements ResolutionResult {
            public Invalid {
                Objects.requireNonNull(diagnostic);
            }
        }
    }

    private TargetValueResolver() {}

    public static ResolutionResult resolve(TransformContext transformContext, Node node, TargetContext target) {
        Objects.requireNonNull(transformContext);
        Objects.requireNonNull(node);
        Objects.requireNonNull(target);

        try {
            return resolveImpl(transformContext, node, target);
        } catch (TargetTypeNotApplicableException ex) {
            return notApplicable(FailureKind.TARGET_TYPE_MISMATCH, node, target, List.of(), ex);
        } catch (MarkupException ex) {
            return new ResolutionResult.Invalid(ex);
        }
    }

    /**
     * Tries the input as a value first. If that does not work and the syntax allows it, tries its
     * items as an array, collection, map, or constructor arguments.
     */
    public static ResolutionResult resolveSequence(
            TransformContext transformContext,
            ValueInput input,
            TargetContext target) {
        Objects.requireNonNull(transformContext);
        Objects.requireNonNull(input);
        Objects.requireNonNull(target);

        CandidateFailure directFailure = null;
        if (input.directValue() != null) {
            ResolutionResult direct = resolve(transformContext, input.directValue(), target);
            if (direct instanceof ResolutionResult.Applicable applicable) {
                if (!isReadOnlyValueAssignment(target, applicable.plan())) {
                    return direct;
                }

                directFailure = new CandidateFailure(
                    FailureKind.TARGET_CONSTRAINT,
                    input.directValue().getSourceInfo(),
                    target,
                    applicable.plan().sourceTypes(),
                    PropertyAssignmentErrors.cannotModifyReadOnlyProperty(
                        target.subject().sourceInfo(),
                        Objects.requireNonNull(target.targetProperty())));
            } else if (direct instanceof ResolutionResult.Invalid) {
                return direct;
            } else {
                directFailure = ((ResolutionResult.NotApplicable)direct).failure();
            }
        }

        List<Node> items = input.structuralItems();

        if (target.type().isArray()) {
            if (!input.allowsConstructedValueFallback) {
                return structuralFallbackInapplicable(input, target, directFailure);
            }

            if (target.type().dimensions() != 1) {
                return structuralFallbackInapplicable(input, target, directFailure);
            }

            if (target.isDirectProperty() && target.targetProperty().isReadOnly()) {
                return new ResolutionResult.NotApplicable(
                    directFailure != null
                        ? directFailure
                        : new CandidateFailure(
                            FailureKind.TARGET_CONSTRAINT, input.sourceInfo(), target, List.of(),
                            PropertyAssignmentErrors.cannotModifyReadOnlyProperty(
                                target.subject().sourceInfo(), target.targetProperty())));
            }

            TypeInstance componentType = target.type().componentType();
            List<ValuePlan> itemPlans = new ArrayList<>(items.size());

            for (Node item : items) {
                ResolutionResult itemResult = resolve(
                    transformContext, item, target.arrayComponent(componentType, item.getSourceInfo()));

                if (itemResult instanceof ResolutionResult.Invalid invalid) {
                    return invalid;
                }

                if (itemResult instanceof ResolutionResult.NotApplicable notApplicable) {
                    return new ResolutionResult.NotApplicable(
                        preferDirectDiagnostic(directFailure)
                            ? directFailure : notApplicable.failure());
                }

                itemPlans.add(((ResolutionResult.Applicable)itemResult).plan());
            }

            return applicableValue(
                target, List.of(), ConversionKind.STRUCTURAL,
                () -> new EmitArrayNode(
                    target.type(), itemPlans.stream().map(ValuePlan::lowerValue).toList()));
        }

        boolean collection = target.type().subtypeOf(CollectionDecl());
        boolean map = target.type().subtypeOf(MapDecl());

        if (input.allowsConstructedValueFallback
                && (collection || map)
                && (!target.isDirectProperty() || !target.targetProperty().isReadOnly())) {
            ResolutionResult construction = resolveImplicitConstruction(
                transformContext, input, target, directFailure);

            if (construction instanceof ResolutionResult.Applicable
                    || construction instanceof ResolutionResult.Invalid) {
                return construction;
            }
        }

        if (map && target.isDirectProperty()) {
            if (preferDirectDiagnostic(directFailure)) {
                return new ResolutionResult.NotApplicable(directFailure);
            }

            return switch (input.mapPopulationPolicy) {
                case DERIVE_KEYS_FROM_ELEMENTS -> resolveMapPopulation(transformContext, input, target);

                case REJECT_KEYLESS_ITEMS -> new ResolutionResult.Invalid(
                    GeneralErrors.cannotPopulateMapWithoutKeys(
                        input.sourceInfo(), Objects.requireNonNull(target.targetProperty())));

                case NONE -> structuralFallbackInapplicable(input, target, directFailure);
            };
        }

        if (collection && target.isDirectProperty()) {
            PropertyInfo property = Objects.requireNonNull(target.targetProperty());
            List<TypeInstance> typeArguments = TypeHelper.getTypeArguments(target.type(), CollectionDecl());
            TypeInstance itemType = typeArguments.isEmpty() ? TypeInstance.ObjectType() : typeArguments.get(0);
            List<ValuePlan> itemPlans = new ArrayList<>(items.size());

            for (Node item : items) {
                ResolutionResult itemResult = resolve(
                    transformContext, item, target.collectionItem(itemType, item.getSourceInfo()));

                if (itemResult instanceof ResolutionResult.Invalid invalid) {
                    return invalid;
                }

                if (itemResult instanceof ResolutionResult.NotApplicable notApplicable) {
                    if (preferDirectDiagnostic(directFailure)) {
                        return new ResolutionResult.NotApplicable(directFailure);
                    }

                    return collectionItemFailure(item, itemType, target, notApplicable.failure());
                }

                itemPlans.add(((ResolutionResult.Applicable)itemResult).plan());
            }

            return applicableProperty(
                target, List.of(), ConversionKind.STRUCTURAL,
                () -> new EmitPropertyAdderNode(
                    property,
                    List.of(),
                    itemPlans.stream().map(ValuePlan::lowerValue).toList(),
                    itemType,
                    input.sourceInfo()));
        }

        if (collection || map) {
            return new ResolutionResult.NotApplicable(
                directFailure != null
                    ? directFailure
                    : new CandidateFailure(
                        FailureKind.NO_CONVERSION, input.sourceInfo(), target, List.of(), null));
        }

        if (target.isDirectProperty() && target.targetProperty().isReadOnly()) {
            return new ResolutionResult.NotApplicable(
                directFailure != null
                    ? directFailure
                    : new CandidateFailure(
                        FailureKind.TARGET_CONSTRAINT,
                        input.sourceInfo(), target, List.of(),
                        PropertyAssignmentErrors.cannotModifyReadOnlyProperty(
                            input.sourceInfo(), target.targetProperty())));
        }

        if (!input.allowsConstructedValueFallback) {
            return structuralFallbackInapplicable(input, target, directFailure);
        }

        return resolveImplicitConstruction(transformContext, input, target, directFailure);
    }

    private static ResolutionResult resolveMapPopulation(
            TransformContext transformContext,
            ValueInput input,
            TargetContext target) {
        PropertyInfo property = Objects.requireNonNull(target.targetProperty());
        List<TypeInstance> typeArguments = TypeHelper.getTypeArguments(target.type(), MapDecl());
        TypeInstance keyType = typeArguments.isEmpty() ? TypeInstance.ObjectType() : typeArguments.get(0);
        TypeInstance itemType = typeArguments.isEmpty() ? TypeInstance.ObjectType() : typeArguments.get(1);
        List<ValuePlan> itemPlans = new ArrayList<>(input.structuralItems().size());
        List<MapKeyResolver.MapKeyPlan> keyPlans = new ArrayList<>(input.structuralItems().size());

        for (Node item : input.structuralItems()) {
            ResolutionResult itemResult = resolve(
                transformContext, item, target.collectionItem(itemType, item.getSourceInfo()));

            if (itemResult instanceof ResolutionResult.Invalid invalid) {
                return invalid;
            }

            if (itemResult instanceof ResolutionResult.NotApplicable notApplicable) {
                return collectionItemFailure(
                    item, itemType, target, notApplicable.failure());
            }

            MapKeyResolver.Result keyResult = MapKeyResolver.resolve(
                transformContext, item, keyType, target);

            if (keyResult instanceof MapKeyResolver.Result.Invalid invalid) {
                return new ResolutionResult.Invalid(invalid.diagnostic());
            }

            itemPlans.add(((ResolutionResult.Applicable)itemResult).plan());
            keyPlans.add(((MapKeyResolver.Result.Applicable)keyResult).plan());
        }

        return applicableProperty(
            target, List.of(), ConversionKind.STRUCTURAL,
            () -> new EmitPropertyAdderNode(
                property,
                keyPlans.stream().map(MapKeyResolver.MapKeyPlan::lower).toList(),
                itemPlans.stream().map(ValuePlan::lowerValue).toList(),
                itemType,
                input.sourceInfo()));
    }

    private static ResolutionResult collectionItemFailure(
            Node item,
            TypeInstance itemType,
            TargetContext target,
            CandidateFailure failure) {
        if (failure.diagnostic() != null) {
            return new ResolutionResult.Invalid(failure.diagnostic());
        }

        TypeInstance valueType = failure.valueType();
        if (valueType == null && item instanceof ValueNode) {
            valueType = TypeHelper.getTypeInstance(item);
        }

        if (target.targetProperty() != null && valueType != null) {
            return new ResolutionResult.Invalid(
                GeneralErrors.cannotAddItemIncompatibleType(
                    item.getSourceInfo(), target.targetProperty(), valueType, itemType));
        }

        return new ResolutionResult.NotApplicable(failure);
    }

    private static ResolutionResult structuralFallbackInapplicable(
            ValueInput input,
            TargetContext target,
            @Nullable CandidateFailure directFailure) {
        return new ResolutionResult.NotApplicable(
            directFailure != null
                ? directFailure
                : new CandidateFailure(
                    FailureKind.NO_CONVERSION, input.sourceInfo(), target, List.of(), null));
    }

    private static ResolutionResult resolveImplicitConstruction(
            TransformContext transformContext,
            ValueInput input,
            TargetContext target,
            @Nullable CandidateFailure directFailure) {

        ImplicitConstructorResolver.Result constructorResult = ImplicitConstructorResolver.resolve(
            transformContext, input, target);

        if (constructorResult instanceof ImplicitConstructorResolver.Result.Applicable applicable) {
            return applicableValue(
                target, List.of(), ConversionKind.IMPLICIT_CONSTRUCTION,
                applicable.plan()::lower);
        }

        if (constructorResult instanceof ImplicitConstructorResolver.Result.Invalid invalid) {
            return new ResolutionResult.Invalid(invalid.diagnostic());
        }

        if (constructorResult instanceof ImplicitConstructorResolver.Result.NotApplicable notApplicable) {
            MarkupException diagnostic = notApplicable.failure().diagnostic();

            return new ResolutionResult.NotApplicable(
                directFailure != null && (diagnostic == null || preferDirectDiagnostic(directFailure))
                    ? directFailure
                    : new CandidateFailure(
                        FailureKind.CONSTRUCTION_FAILURE,
                        diagnostic != null ? diagnostic.getSourceInfo() : input.sourceInfo(),
                        target, List.of(), diagnostic));
        }

        throw new AssertionError();
    }

    private static boolean isReadOnlyValueAssignment(TargetContext target, ValuePlan plan) {
        return target.isDirectProperty()
            && target.targetProperty().isReadOnly()
            && plan.kind() == PlanKind.VALUE;
    }

    private static boolean preferDirectDiagnostic(@Nullable CandidateFailure failure) {
        if (failure == null || failure.diagnostic() == null) {
            return false;
        }

        return switch (failure.kind()) {
            case BINDING_TYPE_MISMATCH, TARGET_TYPE_MISMATCH, TARGET_CONSTRAINT -> true;
            default -> false;
        };
    }

    private static ResolutionResult resolveImpl(
            TransformContext transformContext, Node node, TargetContext target) {
        if (node instanceof LiteralValueNode literal) {
            return resolveLiteral(literal, target);
        }

        if (node instanceof BindingNode binding) {
            return resolveBinding(transformContext, binding, target);
        }

        MarkupExtensionInfo.PropertyConsumer consumer = MarkupExtensionInfo.of(
            node, MarkupExtensionInfo.PropertyConsumer.class);

        if (target.isDirectProperty() && consumer != null
                && target.targetProperty() != null
                && target.targetProperty().isObservable()
                && consumer.propertyType().isAssignableFrom(target.targetProperty().getObservableType())) {
            return applicableProperty(
                target, List.of(consumer.propertyType()), ConversionKind.PROPERTY_CONSUMER,
                () -> new EmitApplyMarkupExtensionNode(
                    constructExtension(transformContext, node),
                    consumer.markupExtensionInterface(), target.targetName(),
                    target.type(), TypeInstance.voidType(), target.targetProperty()));
        }

        MarkupExtensionInfo.Supplier supplier = MarkupExtensionInfo.of(node, MarkupExtensionInfo.Supplier.class);
        if (supplier != null) {
            boolean applicable = supplier.providedTypes().stream().anyMatch(target.type()::isAssignableFrom);
            if (!applicable) {
                MarkupException diagnostic = target.targetProperty() != null
                    ? PropertyAssignmentErrors.markupExtensionNotApplicable(
                        node.getSourceInfo(), target.targetProperty(), TypeHelper.getTypeDeclaration(node),
                        supplier.providedTypes().toArray(TypeInstance[]::new))
                    : null;

                return notApplicable(FailureKind.TYPE_MISMATCH, node, target, supplier.providedTypes(), diagnostic);
            }

            return applicableValue(
                target, supplier.providedTypes(), conversionKind(target.type(), supplier.providedTypes()),
                () -> new EmitApplyMarkupExtensionNode.Supplier(
                    constructExtension(transformContext, node),
                    supplier.markupExtensionInterface(), target.targetName(),
                    target.type(), supplier.returnType(), target.targetProperty()));
        }

        if (consumer != null) {
            MarkupException diagnostic = target.isDirectProperty() && target.targetProperty() != null
                ? PropertyAssignmentErrors.markupExtensionNotApplicable(
                    node.getSourceInfo(), target.targetProperty(), TypeHelper.getTypeDeclaration(node),
                    new TypeInstance[] {consumer.propertyType()})
                : ObjectInitializationErrors.invalidMarkupExtensionUsage(node.getSourceInfo());

            return target.isDirectProperty()
                ? notApplicable(
                    FailureKind.ROLE_MISMATCH, node, target,
                    List.of(TypeHelper.getTypeInstance(node)), diagnostic)
                : new ResolutionResult.Invalid(diagnostic);
        }

        if (node instanceof ValueNode value) {
            TypeInstance valueType = TypeHelper.getTypeInstance(value);
            if (target.type().isAssignableFrom(valueType)) {
                return applicableValue(
                    target, List.of(valueType), conversionKind(target.type(), List.of(valueType)),
                    () -> value);
            }

            MarkupException diagnostic = target.isDirectProperty() && target.targetProperty() != null
                ? PropertyAssignmentErrors.incompatiblePropertyType(
                    node.getSourceInfo(), target.targetProperty(), valueType)
                : null;

            return notApplicable(FailureKind.TYPE_MISMATCH, node, target, List.of(valueType), diagnostic);
        }

        return notApplicable(FailureKind.NO_CONVERSION, node, target, List.of(), null);
    }

    private static ResolutionResult resolveLiteral(LiteralValueNode literal, TargetContext target) {
        List<TypeInstance> declaringTypes = target.type().equals(target.invokingType())
            ? List.of(target.type())
            : List.of(target.type(), target.invokingType());

        LiteralConversionResolver.Result result = LiteralConversionResolver.resolve(
            literal.getText(),
            new LiteralConversionResolver.TargetDescriptor(
                target.type(), declaringTypes,
                target.constructionSite().backingField(),
                target.subject().kind() == TargetKind.OBJECT
                    ? target.constructionSite().sourceInfo() : literal.getSourceInfo()));

        if (result instanceof LiteralConversionResolver.Result.NotApplicable notApplicable) {
            return notApplicable(FailureKind.NO_CONVERSION, literal, target, List.of(), notApplicable.diagnostic());
        }

        if (result instanceof LiteralConversionResolver.Result.Applicable applicable) {
            return applicableValue(target, List.of(), ConversionKind.LITERAL, applicable.plan()::lower);
        }

        throw new AssertionError();
    }

    private static ResolutionResult resolveBinding(
            TransformContext transformContext, BindingNode binding, TargetContext target) {
        if (target.isDirectProperty()) {
            PropertyInfo property = Objects.requireNonNull(target.targetProperty());
            BindingMode mode = binding.getMode();

            try {
                BindingEmitterFactory.checkPreconditions(transformContext, property, binding, target.subject().sourceInfo());
            } catch (MarkupException ex) {
                if (ex.getDiagnostic().getCode()
                        == org.jfxcore.compiler.diagnostic.ErrorCode.CANNOT_MODIFY_READONLY_PROPERTY
                        && (mode == BindingMode.ONCE || mode == BindingMode.UNIDIRECTIONAL)) {
                    return notApplicable(FailureKind.TARGET_CONSTRAINT, binding, target, List.of(), ex);
                }

                return new ResolutionResult.Invalid(ex);
            }

            if (mode != BindingMode.ONCE && mode != BindingMode.UNIDIRECTIONAL) {
                EmitterNode emitter = BindingEmitterFactory.createBindingEmitter(
                    transformContext, binding, property, target.subject().sourceInfo());

                return applicableProperty(target, List.of(), ConversionKind.PROPERTY_CONSUMER, () -> emitter);
            }

            ExpressionResolution expression = binding.resolvePath(target.invokingType(), target.type());
            BindingTypeInfo source = expression.getTypeInfo();
            boolean applicable = target.type().isAssignableFrom(source.emittedType())
                || source.valueSourceType() != null
                    && target.type().isAssignableFrom(source.valueType());

            if (!applicable) {
                MarkupException diagnostic = BindingSourceErrors.cannotConvertSourceType(
                    source.sourceInfo(), source.valueType().javaName(), target.type().javaName());

                return notApplicable(
                    FailureKind.BINDING_TYPE_MISMATCH, binding, target,
                    List.of(source.emittedType()), diagnostic);
            }

            EmitterNode emitter = BindingEmitterFactory.createBindingEmitter(
                transformContext, binding, property, target.subject().sourceInfo(), expression);

            return applicableProperty(
                target, List.of(source.emittedType()),
                conversionKind(target.type(), List.of(source.emittedType())), () -> emitter);
        }

        validateNestedBinding(transformContext, binding, target);
        ExpressionResolution expression = binding.resolvePath(target.invokingType(), target.type());
        BindingTypeInfo source = expression.getTypeInfo();

        if (!target.type().isAssignableFrom(source.emittedType())) {
            return notApplicable(
                FailureKind.TYPE_MISMATCH, binding, target,
                List.of(source.emittedType()), null);
        }

        return applicableValue(
            target, List.of(source.emittedType()),
            conversionKind(target.type(), List.of(source.emittedType())), () -> {
                ValueEmitterNode value = expression.toEmitter().getValue();
                ValueEmitterFactory.adjustParentIndex(
                    value, target.parentsUnderInitialization() + 1);
                return value;
            });
    }

    private static ValueEmitterNode constructExtension(TransformContext transformContext, Node node) {
        if (node instanceof ValueEmitterNode value) {
            return value;
        }

        if (!(node instanceof ObjectNode object)) {
            throw GeneralErrors.expressionNotApplicable(node.getSourceInfo(), false);
        }

        ObjectNode copy = object.deepClone();
        ValueEmitterNode value = null;
        MarkupException literalDiagnostic = null;

        if (copy.getChildren().size() == 1 && copy.getChildren().get(0) instanceof LiteralValueNode literal) {
            PropertyNode idProperty = copy.findIntrinsicProperty(Intrinsics.ID);
            String backingField = readId(copy, idProperty);

            List<PropertyNode> properties = copy.getProperties().stream()
                .filter(property -> property != idProperty)
                .toList();

            List<Node> children = new PropertyAssignmentSorter(copy, properties)
                .sort()
                .stream()
                .map(property -> (Node)property)
                .toList();

            ConstructionSite site = new ConstructionSite(backingField, children, copy.getSourceInfo());

            TargetContext objectTarget = TargetContext.object(
                TypeHelper.getTypeInstance(copy), TypeHelper.getTypeInstance(copy),
                ValueEmitterFactory.getParentsUnderInitializationCount(transformContext), site);

            ResolutionResult literalResult = resolveSequence(transformContext, ValueInput.of(literal), objectTarget);

            if (literalResult instanceof ResolutionResult.Applicable applicable) {
                value = (ValueEmitterNode)applicable.plan().lowerValue();
            } else if (literalResult instanceof ResolutionResult.Invalid invalid) {
                throw invalid.diagnostic();
            } else if (literalResult instanceof ResolutionResult.NotApplicable notApplicable) {
                literalDiagnostic = notApplicable.failure().diagnostic();
            } else {
                throw new AssertionError();
            }
        }

        List<DiagnosticInfo> diagnostics = new ArrayList<>();
        if (value == null) {
            value = ValueEmitterFactory.newObjectWithNamedParams(transformContext, copy, diagnostics);
        }

        if (value == null) {
            value = ValueEmitterFactory.newDefaultObject(copy);
        }

        if (value != null) {
            return value;
        }

        if (literalDiagnostic != null) {
            throw literalDiagnostic;
        }

        Diagnostic[] causes = diagnostics.stream()
            .map(DiagnosticInfo::getDiagnostic)
            .toArray(Diagnostic[]::new);

        SourceInfo diagnosticSource = diagnostics.isEmpty()
            ? node.getSourceInfo() : diagnostics.get(0).getSourceInfo();

        throw causes.length == 0
            ? ObjectInitializationErrors.constructorNotFound(diagnosticSource, TypeHelper.getTypeDeclaration(node))
            : ObjectInitializationErrors.constructorNotFound(diagnosticSource, TypeHelper.getTypeDeclaration(node), causes);
    }

    private static @Nullable String readId(ObjectNode node, @Nullable PropertyNode idProperty) {
        if (idProperty == null) {
            return null;
        }

        Node idNode = idProperty.getValues().size() == 1 ? idProperty.getValues().get(0) : null;
        if (idNode instanceof AttributeValueNode attribute && attribute.getForm() == AttributeValueNode.Form.LITERAL) {
            idNode = attribute.getLiteral();
        }

        if (!(idNode instanceof LiteralValueNode literal)) {
            throw PropertyAssignmentErrors.propertyMustContainText(
                idProperty.getSourceInfo(), TypeHelper.getTypeDeclaration(node), Intrinsics.ID.getName());
        }

        return literal.getText();
    }

    private static void validateNestedBinding(
            TransformContext transformContext, BindingNode binding, TargetContext target) {
        if (binding.getMode() != BindingMode.ONCE && binding.getMode() != BindingMode.UNIDIRECTIONAL) {
            throw GeneralErrors.expressionNotApplicable(binding.getSourceInfo(), true);
        }

        if ((target.parentsUnderInitialization() > 0
                || target.currentObjectUnderInitialization())
                && binding.getBindingDistance() <= target.parentsUnderInitialization()) {
            if (target.targetProperty() != null) {
                throw PropertyAssignmentErrors.cannotReferenceNodeUnderInitialization(
                    transformContext, target.targetProperty(),
                    binding.getBindingDistance(), binding.getSourceInfo());
            }

            if (target.subject().kind() == TargetKind.CONSTRUCTOR_PARAMETER) {
                throw PropertyAssignmentErrors.cannotReferenceNodeUnderInitialization(
                    transformContext,
                    target.subject().displayName(),
                    Objects.requireNonNull(target.targetName()),
                    binding.getBindingDistance(),
                    binding.getSourceInfo());
            }

            throw GeneralErrors.expressionNotApplicable(binding.getSourceInfo(), true);
        }
    }

    private static ConversionKind conversionKind(TypeInstance targetType, List<TypeInstance> sourceTypes) {
        if (sourceTypes.stream().anyMatch(targetType::equals)) {
            return ConversionKind.IDENTITY;
        }

        if (sourceTypes.stream().anyMatch(type -> targetType.isAssignableFrom(type, STRICT))) {
            return ConversionKind.STRICT;
        }

        return ConversionKind.LOOSE;
    }

    private static ResolutionResult applicableValue(
            TargetContext target,
            List<TypeInstance> sourceTypes,
            ConversionKind conversionKind,
            Supplier<? extends ValueNode> lowerer) {
        return new ResolutionResult.Applicable(new ValuePlan(
            PlanKind.VALUE, target, sourceTypes, conversionKind,
            () -> new Lowered.Value(lowerer.get())));
    }

    private static ResolutionResult applicableProperty(
            TargetContext target,
            List<TypeInstance> sourceTypes,
            ConversionKind conversionKind,
            Supplier<? extends EmitterNode> lowerer) {
        return new ResolutionResult.Applicable(new ValuePlan(
            PlanKind.PROPERTY, target, sourceTypes, conversionKind,
            () -> new Lowered.Property(lowerer.get())));
    }

    private static ResolutionResult notApplicable(
            FailureKind kind,
            Node node,
            TargetContext target,
            List<TypeInstance> sourceTypes,
            @Nullable MarkupException diagnostic) {
        return new ResolutionResult.NotApplicable(new CandidateFailure(
            kind, node.getSourceInfo(), target, sourceTypes, diagnostic));
    }
}

// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.type.AccessModifier;
import org.jfxcore.compiler.type.BehaviorDeclaration;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.ApplicableInvocationCandidate;
import org.jfxcore.compiler.util.ArgumentConversion;
import org.jfxcore.compiler.util.ConversionCategory;
import org.jfxcore.compiler.util.InvocationCandidate;
import org.jfxcore.compiler.util.MethodFinder;
import org.jfxcore.compiler.util.NameHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Selects one implicit constructor from retained argument plans and candidate failures.
 */
public final class ImplicitConstructorResolver {

    public record CandidateResult(
            ConstructorDeclaration constructor,
            List<NamedArgumentMetadata.Parameter> parameters,
            List<TargetValueResolver.ValuePlan> argumentPlans,
            int matchedPrefix,
            @Nullable TargetValueResolver.CandidateFailure firstFailure,
            boolean arityEligible) {

        public CandidateResult {
            Objects.requireNonNull(constructor);
            parameters = List.copyOf(parameters);
            argumentPlans = List.copyOf(argumentPlans);
        }

        public boolean applicable() {
            return arityEligible
                && firstFailure == null
                && argumentPlans.size() == parameters.size();
        }
    }

    public record NoMatch(
            List<CandidateResult> candidates,
            @Nullable MarkupException diagnostic) {

        public NoMatch {
            candidates = List.copyOf(candidates);
        }
    }

    public sealed interface Result {
        record Applicable(ConstructorPlan plan) implements Result {
            public Applicable {
                Objects.requireNonNull(plan);
            }
        }

        record NotApplicable(NoMatch failure) implements Result {
            public NotApplicable {
                Objects.requireNonNull(failure);
            }
        }

        record Invalid(MarkupException diagnostic) implements Result {
            public Invalid {
                Objects.requireNonNull(diagnostic);
            }
        }
    }

    /**
     * The selected constructor and its already-resolved, immutable argument plans.
     */
    public static final class ConstructorPlan {
        private final TypeInstance targetType;
        private final ConstructorDeclaration constructor;
        private final List<TargetValueResolver.ValuePlan> arguments;
        private final TargetValueResolver.ConstructionSite constructionSite;
        private final SourceInfo sourceInfo;
        private ValueEmitterNode lowered;

        private ConstructorPlan(
                TypeInstance targetType,
                ConstructorDeclaration constructor,
                List<TargetValueResolver.ValuePlan> arguments,
                TargetValueResolver.ConstructionSite constructionSite,
                SourceInfo sourceInfo) {
            this.targetType = Objects.requireNonNull(targetType);
            this.constructor = Objects.requireNonNull(constructor);
            this.arguments = List.copyOf(arguments);
            this.constructionSite = Objects.requireNonNull(constructionSite);
            this.sourceInfo = Objects.requireNonNull(sourceInfo);
        }

        public ConstructorDeclaration constructor() {
            return constructor;
        }

        public List<TargetValueResolver.ValuePlan> arguments() {
            return arguments;
        }

        public synchronized ValueEmitterNode lower() {
            if (lowered == null) {
                List<ValueNode> values = arguments.stream()
                    .map(TargetValueResolver.ValuePlan::lowerValue)
                    .toList();

                lowered = EmitObjectNode
                    .constructor(targetType, constructor, values, sourceInfo)
                    .backingField(constructionSite.backingField())
                    .children(constructionSite.children())
                    .create();
            }

            return lowered;
        }
    }

    private ImplicitConstructorResolver() {}

    public static Result resolve(
            TransformContext transformContext,
            TargetValueResolver.ValueInput input,
            TargetValueResolver.TargetContext target) {
        if (target.type().declaration().isPrimitiveBox()) {
            return new Result.NotApplicable(new NoMatch(List.of(), null));
        }

        List<Node> arguments = input.structuralItems();
        SourceInfo sourceInfo = input.sourceInfo();
        List<CandidateResult> candidates = new ArrayList<>();

        for (ConstructorDeclaration constructor : target.type().declaration().constructors()) {
            if (constructor.accessModifier() != AccessModifier.PUBLIC) {
                continue;
            }

            List<NamedArgumentMetadata.Parameter> parameters = NamedArgumentMetadata.get(
                target.type(), constructor, sourceInfo);

            if (parameters.isEmpty()) {
                continue;
            }

            if (parameters.size() != arguments.size()) {
                candidates.add(new CandidateResult(constructor, parameters, List.of(), 0, null, false));
                continue;
            }

            List<TargetValueResolver.ValuePlan> plans = new ArrayList<>(arguments.size());
            TargetValueResolver.CandidateFailure failure = null;

            for (int i = 0; i < arguments.size(); ++i) {
                NamedArgumentMetadata.Parameter parameter = parameters.get(i);

                TargetValueResolver.TargetContext argumentTarget =
                    TargetValueResolver.TargetContext.constructorParameter(
                        target.type(), parameter.name(), parameter.type(), target.invokingType(),
                        target.parentsUnderInitialization(),
                        target.currentObjectUnderInitialization(),
                        arguments.get(i).getSourceInfo());

                TargetValueResolver.ResolutionResult result = TargetValueResolver.resolve(
                    transformContext, arguments.get(i), argumentTarget);

                if (result instanceof TargetValueResolver.ResolutionResult.Invalid invalid) {
                    return new Result.Invalid(invalid.diagnostic());
                }

                if (result instanceof TargetValueResolver.ResolutionResult.NotApplicable notApplicable) {
                    failure = notApplicable.failure();
                    break;
                }

                TargetValueResolver.ValuePlan plan = ((TargetValueResolver.ResolutionResult.Applicable)result).plan();
                if (plan.kind() != TargetValueResolver.PlanKind.VALUE) {
                    return new Result.Invalid(GeneralErrors.expressionNotApplicable(
                        arguments.get(i).getSourceInfo(), false));
                }

                plans.add(plan);
            }

            var candidate = new CandidateResult(constructor, parameters, plans, plans.size(), failure, true);
            candidates.add(candidate);
        }

        return selectCandidates(candidates, arguments, target, sourceInfo);
    }

    private static Result selectCandidates(
            List<CandidateResult> candidates,
            List<? extends Node> arguments,
            TargetValueResolver.TargetContext target,
            SourceInfo sourceInfo) {
        List<CandidateResult> applicable = candidates.stream()
            .filter(CandidateResult::applicable)
            .toList();

        if (applicable.isEmpty()) {
            return new Result.NotApplicable(new NoMatch(
                candidates, createNoMatchDiagnostic(
                    target.type(), arguments.size(), candidates, sourceInfo)));
        }

        List<ApplicableInvocationCandidate> retainedCandidates = applicable.stream()
            .map(candidate -> toMethodCandidate(candidate, target, arguments))
            .toList();

        List<ApplicableInvocationCandidate> selected =
            MethodFinder.selectApplicableCandidates(retainedCandidates);

        if (selected.size() != 1) {
            if (selected.size() > 1) {
                return new Result.Invalid(GeneralErrors.ambiguousMethodOrConstructorCall(
                    sourceInfo, target.type().declaration().simpleName(),
                    selected.stream()
                        .map(candidate -> candidate.candidate().behavior())
                        .toArray(BehaviorDeclaration[]::new)));
            }

            return new Result.NotApplicable(new NoMatch(
                candidates, createNoMatchDiagnostic(
                    target.type(), arguments.size(), candidates, sourceInfo)));
        }

        ConstructorDeclaration selectedConstructor =
            (ConstructorDeclaration)selected.get(0).candidate().behavior();

        CandidateResult selectedCandidate = applicable.stream()
            .filter(candidate -> candidate.constructor().equals(selectedConstructor))
            .findFirst()
            .orElseThrow();

        return new Result.Applicable(new ConstructorPlan(
            target.type(), selectedConstructor, selectedCandidate.argumentPlans(),
            target.constructionSite(),
            target.subject().kind() == TargetValueResolver.TargetKind.OBJECT
                ? target.constructionSite().sourceInfo() : sourceInfo));
    }

    private static ApplicableInvocationCandidate toMethodCandidate(
            CandidateResult candidate,
            TargetValueResolver.TargetContext target,
            List<? extends Node> arguments) {
        int phase = candidate.argumentPlans().stream()
            .mapToInt(plan -> plan.conversionKind() == TargetValueResolver.ConversionKind.LOOSE ? 1 : 0)
            .max()
            .orElse(0);

        List<ArgumentConversion> conversions = new ArrayList<>(arguments.size());

        for (int i = 0; i < arguments.size(); ++i) {
            TargetValueResolver.ValuePlan plan = candidate.argumentPlans().get(i);
            conversions.add(new ArgumentConversion(
                candidate.parameters().get(i).type(),
                plan.sourceTypes(),
                toConversionCategory(plan.conversionKind()),
                arguments.get(i).getSourceInfo()));
        }

        InvocationCandidate invocationCandidate = new InvocationCandidate(
            candidate.constructor(), List.of(target.type()), List.of(), false, target.type());

        return new ApplicableInvocationCandidate(
            invocationCandidate,
            candidate.parameters().stream().map(NamedArgumentMetadata.Parameter::type).toList(),
            target.type(),
            phase,
            false,
            conversions);
    }

    private static ConversionCategory toConversionCategory(TargetValueResolver.ConversionKind conversionKind) {
        return switch (conversionKind) {
            case IDENTITY -> ConversionCategory.IDENTITY;
            case STRICT -> ConversionCategory.STRICT;
            case LOOSE -> ConversionCategory.LOOSE;
            case LITERAL, STRUCTURAL, IMPLICIT_CONSTRUCTION -> ConversionCategory.TARGET;
            case PROPERTY_CONSUMER -> throw new IllegalArgumentException(
                "A property consumer cannot be an implicit-constructor argument");
        };
    }

    private static @Nullable MarkupException createNoMatchDiagnostic(
            TypeInstance targetType,
            int argumentCount,
            List<CandidateResult> candidates,
            SourceInfo sourceInfo) {
        if (candidates.isEmpty()) {
            return null;
        }

        List<CandidateResult> arityEligible = candidates.stream()
            .filter(CandidateResult::arityEligible)
            .toList();

        if (arityEligible.isEmpty()) {
            Diagnostic[] causes = candidates.stream()
                .map(candidate -> Diagnostic.newDiagnostic(
                    ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH,
                    signature(candidate), candidate.parameters().size(), argumentCount))
                .toArray(Diagnostic[]::new);

            return ObjectInitializationErrors.constructorNotFound(
                sourceInfo, targetType.declaration(), causes);
        }

        int matchedPrefix = arityEligible.stream()
            .mapToInt(CandidateResult::matchedPrefix)
            .max()
            .orElse(0);

        List<CandidateResult> best = arityEligible.stream()
            .filter(candidate -> candidate.matchedPrefix() == matchedPrefix)
            .toList();

        SourceInfo failureSource = best.stream()
            .map(CandidateResult::firstFailure)
            .filter(Objects::nonNull)
            .map(TargetValueResolver.CandidateFailure::sourceInfo)
            .findFirst()
            .orElse(sourceInfo);

        Diagnostic[] causes = best.stream()
            .map(ImplicitConstructorResolver::argumentFailureDiagnostic)
            .toArray(Diagnostic[]::new);

        return ObjectInitializationErrors.constructorNotFound(
            failureSource, targetType.declaration(), causes);
    }

    private static Diagnostic argumentFailureDiagnostic(CandidateResult candidate) {
        TargetValueResolver.CandidateFailure failure = Objects.requireNonNull(candidate.firstFailure());
        int argumentIndex = candidate.matchedPrefix();
        NamedArgumentMetadata.Parameter parameter = candidate.parameters().get(argumentIndex);

        String sourceType = failure.sourceTypes().isEmpty()
            ? "value"
            : String.join(" | ", failure.sourceTypes().stream().map(TypeInstance::javaName).toList());

        Diagnostic[] causes = failure.diagnostic() != null
            ? new Diagnostic[] {failure.diagnostic().getDiagnostic()}
            : new Diagnostic[0];

        return Diagnostic.newDiagnosticVariant(
            ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, "expected", causes,
            signature(candidate), argumentIndex + 1, sourceType, parameter.type().javaName());
    }

    private static String signature(CandidateResult candidate) {
        TypeInstance[] types = candidate.parameters().stream()
            .map(NamedArgumentMetadata.Parameter::type)
            .toArray(TypeInstance[]::new);

        String[] names = candidate.parameters().stream()
            .map(NamedArgumentMetadata.Parameter::name)
            .toArray(String[]::new);

        return NameHelper.getDisplaySignature(candidate.constructor(), types, names);
    }
}

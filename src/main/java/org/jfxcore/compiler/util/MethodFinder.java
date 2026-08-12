// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.DiagnosticInfo;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.BehaviorDeclaration;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import java.util.ArrayList;
import java.util.List;

import static org.jfxcore.compiler.type.TypeInstance.AssignmentContext.*;
import static org.jfxcore.compiler.type.KnownSymbols.*;

public class MethodFinder {

    private final List<TypeInstance> invocationContext;
    private final TypeDeclaration declaringType;

    public MethodFinder(TypeInstance invokingType, TypeDeclaration declaringType) {
        this(List.of(invokingType), declaringType);
    }

    public MethodFinder(List<TypeInstance> invocationContext, TypeDeclaration declaringType) {
        this.invocationContext = List.copyOf(invocationContext);
        this.declaringType = declaringType;
    }

    public @Nullable ConstructorDeclaration findConstructor(
            List<TypeInstance> typeWitnesses,
            List<TypeInstance> argumentTypes,
            List<SourceInfo> argumentSourceInfo,
            @Nullable List<DiagnosticInfo> diagnostics,
            SourceInfo sourceInfo) {
        return resolveOverloadedMethod(
            declaringType.constructors(),
            false,
            null,
            typeWitnesses,
            argumentTypes,
            argumentSourceInfo,
            diagnostics,
            sourceInfo);
    }

    public @Nullable MethodDeclaration findMethod(
            String methodName,
            boolean staticInvocation,
            @Nullable TypeInstance returnType,
            List<TypeInstance> typeWitnesses,
            List<TypeInstance> argumentTypes,
            List<SourceInfo> argumentSourceInfo,
            @Nullable List<DiagnosticInfo> diagnostics,
            SourceInfo sourceInfo) {
        return resolveOverloadedMethod(
            declaringType.methods(methodName),
            staticInvocation,
            returnType,
            typeWitnesses,
            argumentTypes,
            argumentSourceInfo,
            diagnostics,
            sourceInfo);
    }

    /**
     * Resolves candidates from one or more callable categories as a single overload set. The result
     * contains every maximally-specific candidate in the first applicable phase; a single element is
     * an unambiguous selection.
     */
    public static List<ApplicableInvocationCandidate> resolveInvocationCandidates(
            List<InvocationCandidate> candidates,
            @Nullable TypeInstance targetType,
            List<TypeInstance> argumentTypes,
            List<SourceInfo> argumentSourceInfo,
            @Nullable List<DiagnosticInfo> diagnostics,
            SourceInfo sourceInfo) {
        for (int phase = 0; phase < 3; ++phase) {
            TypeInstance.AssignmentContext assignmentContext = phase == 0 ? STRICT : LOOSE;
            boolean allowVarargInvocation = phase == 2;
            List<ApplicableInvocationCandidate> applicable = new ArrayList<>();

            for (InvocationCandidate candidate : candidates) {
                ApplicableInvocationCandidate resolved = evaluateInvocationCandidate(
                    candidate, assignmentContext, allowVarargInvocation, targetType, argumentTypes,
                    argumentSourceInfo, phase == 2 ? diagnostics : null, sourceInfo, phase);

                if (resolved != null) {
                    applicable.add(resolved);
                }
            }

            if (!applicable.isEmpty()) {
                return selectApplicableCandidates(applicable);
            }
        }

        return List.of();
    }

    /**
     * Selects from candidates whose applicability and target-specific conversions have already
     * been established by the caller.
     */
    public static List<ApplicableInvocationCandidate> selectApplicableCandidates(
            List<ApplicableInvocationCandidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        int bestPhase = candidates.stream().mapToInt(ApplicableInvocationCandidate::phase).min().orElseThrow();

        List<ApplicableInvocationCandidate> phased = candidates.stream()
            .filter(candidate -> candidate.phase() == bestPhase)
            .toList();

        List<ApplicableInvocationCandidate> conversionMaximal = new ArrayList<>();

        for (int i = 0; i < phased.size(); ++i) {
            boolean maximal = true;
            for (int j = 0; j < phased.size(); ++j) {
                if (i != j && conversionDominates(phased.get(j), phased.get(i))) {
                    maximal = false;
                    break;
                }
            }

            if (maximal) {
                conversionMaximal.add(phased.get(i));
            }
        }

        return findMaximallySpecificCandidates(conversionMaximal);
    }

    private static @Nullable ApplicableInvocationCandidate evaluateInvocationCandidate(
            InvocationCandidate candidate,
            TypeInstance.AssignmentContext assignmentContext,
            boolean allowVarargInvocation,
            @Nullable TypeInstance targetType,
            List<TypeInstance> argumentTypes,
            List<SourceInfo> argumentSourceInfo,
            @Nullable List<DiagnosticInfo> diagnostics,
            SourceInfo sourceInfo,
            int phase) {
        BehaviorDeclaration behavior = candidate.behavior();

        try {
            TypeInvoker invoker = new TypeInvoker(sourceInfo);
            TypeInstance[] parameterTypes = behavior instanceof ConstructorDeclaration
                ? invoker.invokeSourceParameterTypes(behavior, candidate.invocationContext(), candidate.typeWitnesses())
                : invoker.invokeParameterTypes(behavior, candidate.invocationContext(), candidate.typeWitnesses());

            if (!behavior.isStatic() && candidate.staticInvocation()) {
                if (diagnostics != null) {
                    diagnostics.add(new DiagnosticInfo(
                        Diagnostic.newDiagnostic(
                            ErrorCode.METHOD_NOT_STATIC,
                            NameHelper.getDisplaySignature(behavior, parameterTypes)),
                        sourceInfo));
                }

                return null;
            }

            int numParams = parameterTypes.length;
            int numArgs = argumentTypes.size();
            boolean varargs = allowVarargInvocation && behavior.isVarArgs();
            boolean expandedVarargs = false;

            if (numParams > numArgs && !(varargs && numParams == 1) || numParams < numArgs && !varargs) {
                if (diagnostics != null) {
                    diagnostics.add(new DiagnosticInfo(
                        Diagnostic.newDiagnostic(
                            ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH,
                            NameHelper.getDisplaySignature(behavior, parameterTypes),
                            numParams, numArgs),
                        sourceInfo));
                }

                return null;
            }

            int fixedCount = varargs ? Math.max(0, numParams - 1) : numParams;

            for (int i = 0; i < fixedCount; ++i) {
                if (!parameterTypes[i].isAssignableFrom(argumentTypes.get(i), assignmentContext)) {
                    addArgumentDiagnostic(
                        diagnostics, behavior, parameterTypes, argumentTypes,
                        argumentSourceInfo, i, sourceInfo);
                    return null;
                }
            }

            if (varargs && numParams > 0) {
                int varargIndex = numParams - 1;
                TypeInstance arrayType = parameterTypes[varargIndex];
                boolean fixedArityArray = numArgs == numParams
                    && arrayType.isAssignableFrom(argumentTypes.get(varargIndex), assignmentContext);

                if (!fixedArityArray) {
                    if (!arrayType.isArray()) {
                        return null;
                    }

                    TypeInstance componentType = arrayType.componentType();
                    expandedVarargs = true;
                    for (int i = varargIndex; i < numArgs; ++i) {
                        if (!componentType.isAssignableFrom(argumentTypes.get(i), assignmentContext)) {
                            addArgumentDiagnostic(
                                diagnostics, behavior, parameterTypes, argumentTypes,
                                argumentSourceInfo, i, sourceInfo);
                            return null;
                        }
                    }
                }
            }

            TypeInstance resultType = candidate.resultType() != null
                ? candidate.resultType()
                : invoker.invokeReturnType(behavior, candidate.invocationContext(), candidate.typeWitnesses());

            if (targetType != null && !targetType.isAssignableFrom(resultType)) {
                if (diagnostics != null) {
                    diagnostics.add(new DiagnosticInfo(
                        Diagnostic.newDiagnostic(
                            ErrorCode.INCOMPATIBLE_RETURN_VALUE,
                            NameHelper.getDisplaySignature(behavior, parameterTypes),
                            targetType.javaName()),
                        sourceInfo));
                }

                return null;
            }

            List<ArgumentConversion> conversions = new ArrayList<>(numArgs);
            for (int i = 0; i < numArgs; ++i) {
                int parameterIndex = Math.min(i, parameterTypes.length - 1);
                TypeInstance formalType = parameterTypes[parameterIndex];
                if (expandedVarargs && parameterIndex == parameterTypes.length - 1) {
                    formalType = formalType.componentType();
                }

                TypeInstance sourceType = argumentTypes.get(i);
                conversions.add(new ArgumentConversion(
                    formalType,
                    List.of(sourceType),
                    conversionCategory(formalType, sourceType),
                    i < argumentSourceInfo.size() ? argumentSourceInfo.get(i) : sourceInfo));
            }

            return new ApplicableInvocationCandidate(
                candidate, List.of(parameterTypes), resultType, phase,
                expandedVarargs, conversions);
        } catch (MarkupException ex) {
            if (diagnostics != null) {
                diagnostics.add(new DiagnosticInfo(ex.getDiagnostic(), ex.getSourceInfo()));
            }
        } catch (RuntimeException ignored) {
        }

        return null;
    }

    private static void addArgumentDiagnostic(
            @Nullable List<DiagnosticInfo> diagnostics,
            BehaviorDeclaration behavior,
            TypeInstance[] parameterTypes,
            List<TypeInstance> argumentTypes,
            List<SourceInfo> argumentSourceInfo,
            int argumentIndex,
            SourceInfo sourceInfo) {
        if (diagnostics == null) {
            return;
        }

        int parameterIndex = Math.min(argumentIndex, parameterTypes.length - 1);
        TypeInstance parameterType = parameterTypes[parameterIndex];

        if (behavior.isVarArgs() && parameterIndex == parameterTypes.length - 1 && parameterType.isArray()) {
            parameterType = parameterType.componentType();
        }

        String argumentName = argumentTypes.get(argumentIndex).javaName();
        TypeDeclaration declaredParameterType = behavior.parameters().get(parameterIndex).type();

        Diagnostic diagnostic = parameterType.equals(declaredParameterType)
            ? Diagnostic.newDiagnostic(
                ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT,
                NameHelper.getDisplaySignature(behavior, parameterTypes),
                argumentIndex + 1,
                argumentName)
            : Diagnostic.newDiagnosticVariant(
                ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT,
                "expected",
                NameHelper.getDisplaySignature(behavior, parameterTypes),
                argumentIndex + 1,
                argumentName,
                parameterType.javaName());

        diagnostics.add(new DiagnosticInfo(
            diagnostic,
            argumentIndex < argumentSourceInfo.size()
                ? argumentSourceInfo.get(argumentIndex) : sourceInfo));
    }

    private static List<ApplicableInvocationCandidate> findMaximallySpecificCandidates(
            List<ApplicableInvocationCandidate> candidates) {
        List<ApplicableInvocationCandidate> result = new ArrayList<>();

        for (int i = 0; i < candidates.size(); ++i) {
            boolean maximal = true;

            for (int j = 0; j < candidates.size(); ++j) {
                if (i != j && isMoreSpecific(candidates.get(j), candidates.get(i))) {
                    maximal = false;
                    break;
                }
            }

            if (maximal) {
                result.add(candidates.get(i));
            }
        }

        return result;
    }

    private static boolean isMoreSpecific(
            ApplicableInvocationCandidate first,
            ApplicableInvocationCandidate second) {
        boolean moreSpecific = false;
        int argumentCount = Math.max(first.argumentConversions().size(), second.argumentConversions().size());
        int comparisons = argumentCount == 0
            ? Math.max(first.parameterTypes().size(), second.parameterTypes().size())
            : argumentCount;

        for (int i = 0; i < comparisons; ++i) {
            TypeInstance argumentType = comparisonSourceType(first, second, i);
            TypeInstance firstType = effectiveParameterType(first, i);
            TypeInstance secondType = effectiveParameterType(second, i);

            if (firstType == null || secondType == null) {
                return false;
            }

            if (firstType.equals(secondType)) {
                continue;
            }

            if (!isInvocationTypeMoreSpecific(firstType, secondType, argumentType)) {
                return false;
            }

            moreSpecific = true;
        }

        return moreSpecific;
    }

    private static @Nullable TypeInstance effectiveParameterType(
            ApplicableInvocationCandidate candidate,
            int argumentIndex) {
        if (argumentIndex < candidate.argumentConversions().size()) {
            return candidate.argumentConversions().get(argumentIndex).formalType();
        }

        List<TypeInstance> parameters = candidate.parameterTypes();
        if (parameters.isEmpty()) {
            return null;
        }

        int index = Math.min(argumentIndex, parameters.size() - 1);
        TypeInstance result = parameters.get(index);
        boolean expandedVarargs = candidate.expandedVarargs()
            && index == parameters.size() - 1;

        if (expandedVarargs && result.isArray()) {
            result = result.componentType();
        }

        return result;
    }

    private static @Nullable TypeInstance comparisonSourceType(
            ApplicableInvocationCandidate first,
            ApplicableInvocationCandidate second,
            int argumentIndex) {
        List<TypeInstance> alternatives = new ArrayList<>();
        addSourceTypes(alternatives, first, argumentIndex);
        addSourceTypes(alternatives, second, argumentIndex);

        return alternatives.isEmpty()
            ? null
            : alternatives.size() == 1
                ? alternatives.get(0)
                : TypeInstance.ofUnion(alternatives);
    }

    private static void addSourceTypes(
            List<TypeInstance> result,
            ApplicableInvocationCandidate candidate,
            int argumentIndex) {
        if (argumentIndex >= candidate.argumentConversions().size()) {
            return;
        }

        for (TypeInstance sourceType : candidate.argumentConversions().get(argumentIndex).sourceTypes()) {
            if (!result.contains(sourceType)) {
                result.add(sourceType);
            }
        }
    }

    private static boolean conversionDominates(
            ApplicableInvocationCandidate first, ApplicableInvocationCandidate second) {
        if (first.argumentConversions().size() != second.argumentConversions().size()) {
            return false;
        }

        boolean better = false;

        for (int i = 0; i < first.argumentConversions().size(); ++i) {
            int firstRank = conversionRank(first.argumentConversions().get(i).category());
            int secondRank = conversionRank(second.argumentConversions().get(i).category());
            if (firstRank > secondRank) {
                return false;
            }

            better |= firstRank < secondRank;
        }

        return better;
    }

    private static int conversionRank(ConversionCategory category) {
        return switch (category) {
            case IDENTITY -> 0;
            case STRICT -> 1;
            case LOOSE -> 2;
            case TARGET -> 3;
        };
    }

    private static ConversionCategory conversionCategory(TypeInstance formalType, TypeInstance sourceType) {
        if (formalType.equals(sourceType)) {
            return ConversionCategory.IDENTITY;
        }

        return formalType.isAssignableFrom(sourceType, STRICT)
            ? ConversionCategory.STRICT
            : ConversionCategory.LOOSE;
    }

    private static boolean isInvocationTypeMoreSpecific(
            TypeInstance first, TypeInstance second, @Nullable TypeInstance argumentType) {
        if (first.subtypeOf(second)) {
            return true;
        }

        if (second.subtypeOf(first)) {
            return false;
        }

        if (argumentType == null) {
            return false;
        }

        boolean firstAssignable = first.isAssignableFrom(argumentType, STRICT);
        boolean secondAssignable = second.isAssignableFrom(argumentType, STRICT);

        if (firstAssignable != secondAssignable) {
            return firstAssignable;
        }

        TypeDeclaration argumentDeclaration = argumentType.declaration();

        TypeDeclaration primitiveArgumentDeclaration = argumentDeclaration.isNumericPrimitive()
            ? argumentDeclaration
            : argumentDeclaration.primitive().filter(TypeDeclaration::isNumericPrimitive).orElse(null);

        TypeInstance primitiveArgument = primitiveArgumentDeclaration != null
            ? TypeInstance.of(primitiveArgumentDeclaration)
            : null;

        if (primitiveArgumentDeclaration != null && primitiveArgumentDeclaration.isIntegralPrimitive()) {
            if (first.declaration().isIntegralPrimitive()) {
                if (!second.declaration().isIntegralPrimitive()) {
                    return true;
                }

                return maxWideningConversions(primitiveArgument, first)
                    < maxWideningConversions(primitiveArgument, second);
            }

            if (first.equals(floatDecl()) && second.equals(doubleDecl())) {
                return true;
            }
        }

        if (primitiveArgumentDeclaration != null
                && primitiveArgumentDeclaration.isFloatingPointPrimitive()
                && first.declaration().isFloatingPointPrimitive()) {
            if (!second.declaration().isFloatingPointPrimitive()) {
                return true;
            }

            return maxWideningConversions(primitiveArgument, first)
                < maxWideningConversions(primitiveArgument, second);
        }

        return false;
    }

    private static int maxWideningConversions(TypeInstance from, TypeInstance to) {
        TypeDeclaration fromType = from.declaration();
        TypeDeclaration toType = to.declaration();

        if (toType.equals(longDecl())) {
            if (fromType.equals(intDecl())) return 1;
            if (fromType.equals(shortDecl())) return 2;
            if (fromType.equals(charDecl())) return 3;
            if (fromType.equals(byteDecl())) return 3;
        } else if (toType.equals(intDecl())) {
            if (fromType.equals(shortDecl())) return 1;
            if (fromType.equals(charDecl())) return 2;
            if (fromType.equals(byteDecl())) return 2;
        } else if (toType.equals(shortDecl())) {
            if (fromType.equals(charDecl())) return 1;
            if (fromType.equals(byteDecl())) return 1;
        } else if (toType.equals(doubleDecl()) && fromType.equals(floatDecl())) {
            return 1;
        }

        return 0;
    }

    private <T extends BehaviorDeclaration> T resolveOverloadedMethod(
            List<T> methods,
            boolean staticInvocation,
            @Nullable TypeInstance returnType,
            List<TypeInstance> typeWitnesses,
            List<TypeInstance> argumentTypes,
            List<SourceInfo> argumentSourceInfo,
            @Nullable List<DiagnosticInfo> diagnostics,
            SourceInfo sourceInfo) {
        TypeInstance constructorResult = !methods.isEmpty() && methods.get(0) instanceof ConstructorDeclaration
            ? invocationContext.get(invocationContext.size() - 1)
            : null;

        List<InvocationCandidate> candidates = methods.stream()
            .map(method -> new InvocationCandidate(
                method, invocationContext, typeWitnesses, staticInvocation, constructorResult))
            .toList();

        List<ApplicableInvocationCandidate> resolved = resolveInvocationCandidates(
            candidates, returnType, argumentTypes, argumentSourceInfo, diagnostics, sourceInfo);

        if (resolved.size() == 1) {
            @SuppressWarnings("unchecked")
            T result = (T)resolved.get(0).candidate().behavior();
            return result;
        }

        if (resolved.size() > 1 && diagnostics != null) {
            diagnostics.clear();
            diagnostics.add(new DiagnosticInfo(Diagnostic.newDiagnosticCauses(
                ErrorCode.AMBIGUOUS_METHOD_CALL,
                resolved.stream()
                    .map(candidate -> candidate.candidate().behavior().longName())
                    .toArray(String[]::new),
                methods.get(0).name()), sourceInfo));
        }

        return null;
    }
}

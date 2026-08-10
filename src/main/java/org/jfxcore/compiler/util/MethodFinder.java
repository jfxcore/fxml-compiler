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
import java.util.Objects;

import static org.jfxcore.compiler.type.TypeInstance.AssignmentContext.*;
import static org.jfxcore.compiler.type.KnownSymbols.*;

public class MethodFinder {

    /**
     * One overload candidate with the invocation metadata needed to instantiate its generic parameter and
     * result types. Different candidates may have different invocation contexts and witness lists.
     */
    public record InvocationCandidate(
            BehaviorDeclaration behavior,
            List<TypeInstance> invocationContext,
            List<TypeInstance> typeWitnesses,
            boolean staticInvocation,
            @Nullable TypeInstance resultType) {

        public InvocationCandidate {
            Objects.requireNonNull(behavior);
            invocationContext = List.copyOf(invocationContext);
            typeWitnesses = List.copyOf(typeWitnesses);
        }
    }

    /**
     * A candidate that is applicable in the selected overload phase.
     */
    public record ResolvedCandidate(
            InvocationCandidate candidate,
            List<TypeInstance> parameterTypes,
            TypeInstance resultType,
            int phase) {

        public ResolvedCandidate {
            Objects.requireNonNull(candidate);
            parameterTypes = List.copyOf(parameterTypes);
            Objects.requireNonNull(resultType);
        }
    }

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
    public static List<ResolvedCandidate> resolveInvocationCandidates(
            List<InvocationCandidate> candidates,
            @Nullable TypeInstance targetType,
            List<TypeInstance> argumentTypes,
            List<SourceInfo> argumentSourceInfo,
            @Nullable List<DiagnosticInfo> diagnostics,
            SourceInfo sourceInfo) {
        for (int phase = 0; phase < 3; ++phase) {
            TypeInstance.AssignmentContext assignmentContext = phase == 0 ? STRICT : LOOSE;
            boolean allowVarargInvocation = phase == 2;
            List<ResolvedCandidate> applicable = new ArrayList<>();

            for (InvocationCandidate candidate : candidates) {
                ResolvedCandidate resolved = evaluateInvocationCandidate(
                    candidate, assignmentContext, allowVarargInvocation, targetType, argumentTypes,
                    argumentSourceInfo, phase == 2 ? diagnostics : null, sourceInfo, phase);

                if (resolved != null) {
                    applicable.add(resolved);
                }
            }

            if (!applicable.isEmpty()) {
                return findMaximallySpecificCandidates(applicable, argumentTypes);
            }
        }

        return List.of();
    }

    private static @Nullable ResolvedCandidate evaluateInvocationCandidate(
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

            return new ResolvedCandidate(candidate, List.of(parameterTypes), resultType, phase);
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

    private static List<ResolvedCandidate> findMaximallySpecificCandidates(
            List<ResolvedCandidate> candidates,
            List<TypeInstance> argumentTypes) {
        List<ResolvedCandidate> result = new ArrayList<>();

        for (int i = 0; i < candidates.size(); ++i) {
            boolean maximal = true;

            for (int j = 0; j < candidates.size(); ++j) {
                if (i != j && isMoreSpecific(candidates.get(j), candidates.get(i), argumentTypes)) {
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
            ResolvedCandidate first,
            ResolvedCandidate second,
            List<TypeInstance> argumentTypes) {
        boolean moreSpecific = false;
        int comparisons = argumentTypes.isEmpty()
            ? Math.max(first.parameterTypes().size(), second.parameterTypes().size())
            : argumentTypes.size();

        for (int i = 0; i < comparisons; ++i) {
            TypeInstance argumentType = argumentTypes.isEmpty() ? null : argumentTypes.get(i);
            TypeInstance firstType = effectiveParameterType(first, i, argumentType);
            TypeInstance secondType = effectiveParameterType(second, i, argumentType);

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
            ResolvedCandidate candidate,
            int argumentIndex,
            @Nullable TypeInstance argumentType) {
        List<TypeInstance> parameters = candidate.parameterTypes();
        if (parameters.isEmpty()) {
            return null;
        }

        int index = Math.min(argumentIndex, parameters.size() - 1);
        TypeInstance result = parameters.get(index);
        boolean expandedVarargs = candidate.phase() == 2
            && candidate.candidate().behavior().isVarArgs()
            && index == parameters.size() - 1;

        if (expandedVarargs && result.isArray()
                && (argumentIndex >= parameters.size()
                    || argumentType == null
                    || !result.isAssignableFrom(argumentType, LOOSE))) {
            result = result.componentType();
        }

        return result;
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
        if (argumentDeclaration.isIntegralPrimitive()) {
            if (first.declaration().isIntegralPrimitive()) {
                if (!second.declaration().isIntegralPrimitive()) {
                    return true;
                }

                return maxWideningConversions(argumentType, first) < maxWideningConversions(argumentType, second);
            }

            if (first.equals(floatDecl()) && second.equals(doubleDecl())) {
                return true;
            }
        }

        if (argumentDeclaration.isFloatingPointPrimitive() && first.declaration().isFloatingPointPrimitive()) {
            if (!second.declaration().isFloatingPointPrimitive()) {
                return true;
            }

            return maxWideningConversions(argumentType, first) < maxWideningConversions(argumentType, second);
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

        List<ResolvedCandidate> resolved = resolveInvocationCandidates(
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

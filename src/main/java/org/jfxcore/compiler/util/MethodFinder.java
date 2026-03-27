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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jfxcore.compiler.type.TypeInstance.*;
import static org.jfxcore.compiler.type.TypeInstance.AssignmentContext.*;
import static org.jfxcore.compiler.type.KnownSymbols.*;

public class MethodFinder {

    private final TypeInstance invokingType;
    private final TypeDeclaration declaringType;
    private final Map<BehaviorDeclaration, TypeInstance[]> parameterCache = new HashMap<>();

    public MethodFinder(TypeInstance invokingType, TypeDeclaration declaringType) {
        this.invokingType = invokingType;
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

    private <T extends BehaviorDeclaration> T resolveOverloadedMethod(
            List<T> methods,
            boolean staticInvocation,
            @Nullable TypeInstance returnType,
            List<TypeInstance> typeWitnesses,
            List<TypeInstance> argumentTypes,
            List<SourceInfo> argumentSourceInfo,
            @Nullable List<DiagnosticInfo> diagnostics,
            SourceInfo sourceInfo) {
        List<T> applicableMethods;

        var phase1 = new InvocationContext(
            STRICT, staticInvocation, false, returnType, typeWitnesses, argumentTypes, argumentSourceInfo);
        applicableMethods = methods.stream().filter(method -> evaluateApplicability(method, phase1, null, sourceInfo)).toList();
        if (!applicableMethods.isEmpty()) {
            return findMostSpecificMethod(applicableMethods, argumentTypes, diagnostics, sourceInfo);
        }

        var phase2 = new InvocationContext(
            LOOSE, staticInvocation, false, returnType, typeWitnesses, argumentTypes, argumentSourceInfo);
        applicableMethods = methods.stream().filter(method -> evaluateApplicability(method, phase2, null, sourceInfo)).toList();
        if (!applicableMethods.isEmpty()) {
            return findMostSpecificMethod(applicableMethods, argumentTypes, diagnostics, sourceInfo);
        }

        var phase3 = new InvocationContext(
            LOOSE, staticInvocation, true, returnType, typeWitnesses, argumentTypes, argumentSourceInfo);
        applicableMethods = methods.stream().filter(method -> evaluateApplicability(method, phase3, diagnostics, sourceInfo)).toList();
        if (!applicableMethods.isEmpty()) {
            return findMostSpecificMethod(applicableMethods, argumentTypes, diagnostics, sourceInfo);
        }

        return null;
    }

    /**
     * Determines whether a method is applicable for a given invocation context.
     * If a method matches by name but is not applicable, a diagnostic is generated.
     */
    private boolean evaluateApplicability(
            BehaviorDeclaration method,
            InvocationContext context,
            @Nullable List<DiagnosticInfo> diagnostics,
            SourceInfo sourceInfo) {
        try {
            TypeInvoker invoker = new TypeInvoker(sourceInfo);
            TypeInstance[] paramTypes = invoker.invokeParameterTypes(
                method, List.of(invokingType), context.typeWitnesses());

            if (!method.isStatic() && context.staticInvocation()) {
                if (diagnostics != null) {
                    diagnostics.add(new DiagnosticInfo(
                        Diagnostic.newDiagnostic(
                            ErrorCode.METHOD_NOT_STATIC,
                            NameHelper.getDisplaySignature(method, paramTypes)),
                        sourceInfo));
                }

                return false;
            }

            int numParams = paramTypes.length;
            int numArgs = context.arguments().size();
            boolean isVarArgs = context.allowVarargInvocation() && method.isVarArgs();

            if (((numParams > numArgs) && !(isVarArgs && numParams == 1)) || (numParams < numArgs && !isVarArgs)) {
                if (diagnostics != null) {
                    diagnostics.add(new DiagnosticInfo(
                        Diagnostic.newDiagnostic(
                            ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH,
                            NameHelper.getDisplaySignature(method, paramTypes),
                            numParams, numArgs),
                        sourceInfo));
                }

                return false;
            }

            for (int i = 0; i < numParams; ++i) {
                if (isVarArgs && numArgs == 0) {
                    break;
                }

                SourceInfo argSourceInfo = context.argumentSourceInfo().get(i);
                TypeInstance argumentType = context.arguments().get(i);
                TypeInstance parameterType = paramTypes[i];

                if (!parameterType.isAssignableFrom(argumentType, context.assignmentContext())) {
                    boolean valid = true;

                    if (i < numParams - 1 || !isVarArgs) {
                        valid = false;
                    } else {
                        if (!paramTypes[i].isArray()) {
                            valid = false;
                        } else {
                            TypeInstance componentType = paramTypes[i].componentType();

                            for (int j = i; j < numArgs; ++j) {
                                if (!componentType.isAssignableFrom(
                                        context.arguments().get(j), context.assignmentContext())) {
                                    valid = false;
                                    break;
                                }
                            }
                        }
                    }

                    if (!valid) {
                        if (diagnostics != null) {
                            String argName = argumentType.javaName();
                            TypeDeclaration paramType = method.parameters().get(i).type();

                            if (parameterType.equals(paramType)) {
                                diagnostics.add(new DiagnosticInfo(Diagnostic.newDiagnostic(
                                    ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT,
                                    NameHelper.getDisplaySignature(method, paramTypes),
                                    i + 1, argName), argSourceInfo));
                            } else {
                                diagnostics.add(new DiagnosticInfo(Diagnostic.newDiagnosticVariant(
                                    ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, "expected",
                                    NameHelper.getDisplaySignature(method, paramTypes),
                                    i + 1, argName,
                                    parameterType.javaName()), argSourceInfo));
                            }
                        }

                        return false;
                    }
                }
            }

            if (context.returnType() != null) {
                TypeInstance returnType = invoker.invokeReturnType(method, List.of(invokingType), context.typeWitnesses());
                if (!context.returnType().isAssignableFrom(returnType)) {
                    if (diagnostics != null) {
                        diagnostics.add(new DiagnosticInfo(
                            Diagnostic.newDiagnostic(
                                ErrorCode.INCOMPATIBLE_RETURN_VALUE,
                                NameHelper.getDisplaySignature(method, paramTypes),
                                context.returnType().javaName()),
                            sourceInfo));
                    }

                    return false;
                }
            }

            return true;
        } catch (MarkupException ex) {
            if (diagnostics != null) {
                diagnostics.add(new DiagnosticInfo(ex.getDiagnostic(), ex.getSourceInfo()));
            }
        } catch (RuntimeException ignored) {
        }

        return false;
    }

    /**
     * The most specific method of a set of applicable methods is the single maximally specific method.
     *
     * A method is maximally specific if there are no other applicable methods in the set that are more specific.
     * It is possible that a set of applicable methods contains more than one maximally specific method; in this
     * case the method call is ambiguous.
     */
    private <T extends BehaviorDeclaration> T findMostSpecificMethod(
            List<T> methods, List<TypeInstance> argumentTypes, List<DiagnosticInfo> diagnostics, SourceInfo sourceInfo) {
        List<T> maximallySpecificMethods = new ArrayList<>();

        for (int i = 0; i < methods.size(); ++i) {
            T currentMethod = methods.get(i);
            boolean maximallySpecific = true;

            for (int j = 0; j < methods.size(); ++j) {
                if (j == i) {
                    continue;
                }

                if (isMethodMoreSpecific(methods.get(j), currentMethod, argumentTypes)) {
                    maximallySpecific = false;
                    break;
                }
            }

            if (maximallySpecific) {
                maximallySpecificMethods.add(currentMethod);
            }
        }

        if (maximallySpecificMethods.size() == 1) {
            return maximallySpecificMethods.get(0);
        }

        if (diagnostics != null) {
            diagnostics.clear();
            diagnostics.add(new DiagnosticInfo(Diagnostic.newDiagnosticCauses(
                ErrorCode.AMBIGUOUS_METHOD_CALL,
                maximallySpecificMethods.stream().map(BehaviorDeclaration::longName).toArray(String[]::new),
                methods.get(0).name()), sourceInfo));
        }

        return null;
    }

    /**
     * A method m1(a1_0..a1_n) is more specific than m2(a2_0..a2_n) if
     *   1. there is at least one pair (a1_i, a2_i) for which a1_i is more specific than a2_i, and
     *   2. there is no pair (a1_i, a2_i) for which a2_i is more specific than a1_i.
     */
    private boolean isMethodMoreSpecific(BehaviorDeclaration m1, BehaviorDeclaration m2, List<TypeInstance> argumentTypes) {
        TypeInvoker invoker = new TypeInvoker(SourceInfo.none());

        TypeInstance[] params1 = parameterCache.get(m1);
        if (params1 == null) {
            parameterCache.put(m1, params1 = invoker.invokeParameterTypes(m1, List.of(invokingType)));
        }

        TypeInstance[] params2 = parameterCache.get(m2);
        if (params2 == null) {
            parameterCache.put(m2, params2 = invoker.invokeParameterTypes(m2, List.of(invokingType)));
        }

        if (params1.length != params2.length) {
            throw new IllegalArgumentException();
        }

        Boolean m1IsMoreSpecific = null;

        for (int i = 0; i < params1.length; ++i) {
            if (params1[i].equals(params2[i])) {
                continue;
            }

            TypeInstance m1Type = params1[i].isAssignableFrom(argumentTypes.get(i)) ?
                params1[i] : (params1[i].isArray() ? params1[i].componentType() : null);

            TypeInstance m2Type = params2[i].isAssignableFrom(argumentTypes.get(i)) ?
                params2[i] : (params2[i].isArray() ? params2[i].componentType() : null);

            if (m1Type == null || m2Type == null) {
                return false;
            }

            if (m1IsMoreSpecific == null) {
                m1IsMoreSpecific = isTypeMoreSpecific(m1Type, m2Type, argumentTypes.get(i));
            } else if (m1IsMoreSpecific && isTypeMoreSpecific(m2Type, m1Type, argumentTypes.get(i))) {
                return false;
            }
        }

        return m1IsMoreSpecific != null && m1IsMoreSpecific;
    }

    private boolean isTypeMoreSpecific(TypeInstance t1, TypeInstance t2, TypeInstance e) {
        if (t1.subtypeOf(t2)) {
            return true;
        }

        if (t2.subtypeOf(t1)) {
            return false;
        }

        if (e.subtypeOf(t1)) {
            return true;
        }

        boolean t1Assignable = t1.isAssignableFrom(e, STRICT);
        boolean t2Assignable = t2.isAssignableFrom(e, STRICT);

        if (t1Assignable && !t2Assignable) {
            return true;
        }

        if (!t1Assignable && t2Assignable) {
            return false;
        }

        if (e.declaration().isIntegralPrimitive()) {
            if (t1.declaration().isIntegralPrimitive()) {
                if (!t2.declaration().isIntegralPrimitive()) {
                    return true;
                }

                return maxWideningConversions(e, t1) < maxWideningConversions(e, t2);
            } else if (t1.equals(floatDecl()) && t2.equals(doubleDecl())) {
                return true;
            }
        }

        if (e.declaration().isFloatingPointPrimitive() && t1.declaration().isFloatingPointPrimitive()) {
            if (!t2.declaration().isFloatingPointPrimitive()) {
                return true;
            }

            return maxWideningConversions(e, t1) < maxWideningConversions(e, t2);
        }

        return false;
    }

    private int maxWideningConversions(TypeInstance from, TypeInstance to) {
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
            return 0;
        } else if (toType.equals(shortDecl())) {
            if (fromType.equals(charDecl())) return 1;
            if (fromType.equals(byteDecl())) return 1;
        } else if (toType.equals(doubleDecl())) {
            if (fromType.equals(floatDecl())) return 1;
        }

        return 0;
    }

    private record InvocationContext(
        AssignmentContext assignmentContext,
        boolean staticInvocation,
        boolean allowVarargInvocation,
        @Nullable TypeInstance returnType,
        List<TypeInstance> typeWitnesses,
        List<TypeInstance> arguments,
        List<SourceInfo> argumentSourceInfo) {}
}

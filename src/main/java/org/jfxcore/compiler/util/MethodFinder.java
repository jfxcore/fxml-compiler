// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.Modifier;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.DiagnosticInfo;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jfxcore.compiler.util.MethodFinder.InvocationType.INSTANCE;
import static org.jfxcore.compiler.util.MethodFinder.InvocationType.STATIC;

public class MethodFinder {

    private final TypeInstance invokingType;
    private final CtClass declaringType;
    private final Map<CtBehavior, TypeInstance[]> parameterCache = new HashMap<>();

    public MethodFinder(TypeInstance invokingType, CtClass declaringType){
        this.invokingType = invokingType;
        this.declaringType = declaringType;
    }

    public CtConstructor findConstructor(
            List<TypeInstance> argumentTypes,
            List<SourceInfo> argumentSourceInfo,
            @Nullable List<DiagnosticInfo> diagnostics,
            SourceInfo sourceInfo) {
        List<CtConstructor> applicableConstructors = new ArrayList<>();

        for (CtConstructor constructor : declaringType.getConstructors()) {
            InvocationContext ctx = new InvocationContext(
                false, declaringType.getSimpleName(), argumentTypes, argumentSourceInfo);

            if (evaluateApplicability(constructor, ctx, diagnostics, sourceInfo)) {
                applicableConstructors.add(constructor);
            }
        }

        if (!applicableConstructors.isEmpty()) {
            return findMostSpecificMethod(applicableConstructors, argumentTypes, diagnostics, sourceInfo);
        }

        return null;
    }

    public CtMethod findMethod(
            String methodName,
            List<TypeInstance> argumentTypes,
            List<SourceInfo> argumentSourceInfo,
            @Nullable List<DiagnosticInfo> diagnostics,
            InvocationType invocationType,
            SourceInfo sourceInfo) {
        List<CtMethod> applicableMethods = new ArrayList<>();

        for (CtMethod method : declaringType.getMethods()) {
            boolean staticMethod = Modifier.isStatic(method.getModifiers());
            if (staticMethod && invocationType == INSTANCE || !staticMethod && invocationType == STATIC) {
                continue;
            }

            InvocationContext ctx = new InvocationContext(staticMethod, methodName, argumentTypes, argumentSourceInfo);
            if (evaluateApplicability(method, ctx, diagnostics, sourceInfo)) {
                applicableMethods.add(method);
            }
        }

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
            CtBehavior method,
            InvocationContext invocationContext,
            @Nullable List<DiagnosticInfo> diagnostics,
            SourceInfo sourceInfo) {
        if (!invocationContext.methodName().equals(method.getName())) {
            return false;
        }

        if (invocationContext.staticInvocation() && !Modifier.isStatic(method.getModifiers())) {
            if (diagnostics != null) {
                diagnostics.add(new DiagnosticInfo(
                    Diagnostic.newDiagnostic(ErrorCode.METHOD_NOT_STATIC, method.getLongName()), sourceInfo));
            }

            return false;
        }

        try {
            Resolver resolver = new Resolver(sourceInfo);
            TypeInstance[] paramTypes = resolver.getParameterTypes(method, List.of(invokingType));
            int numParams = paramTypes.length;
            if (numParams == 0) {
                return invocationContext.arguments().isEmpty();
            }

            int numArgs = invocationContext.arguments().size();
            boolean isVarArgs = Modifier.isVarArgs(method.getModifiers());

            if (numParams > numArgs || (numParams < numArgs && !isVarArgs)) {
                if (diagnostics != null) {
                    diagnostics.add(new DiagnosticInfo(Diagnostic.newDiagnostic(
                        ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, method.getLongName(), numParams, numArgs), sourceInfo));
                }

                return false;
            }

            for (int i = 0; i < numParams; ++i) {
                SourceInfo argSourceInfo = invocationContext.argumentSourceInfo().get(i);
                TypeInstance argumentType = invocationContext.arguments().get(i);
                TypeInstance parameterType = paramTypes[i];

                if (!parameterType.isConvertibleFrom(argumentType)) {
                    boolean valid = true;

                    if (i < numParams - 1 || !isVarArgs) {
                        valid = false;
                    } else {
                        TypeInstance componentType = TypeHelper.tryGetArrayComponentType(method, i);

                        if (componentType == null) {
                            valid = false;
                        } else {
                            for (int j = i; j < numArgs; ++j) {
                                if (!componentType.isConvertibleFrom(invocationContext.arguments().get(j))) {
                                    valid = false;
                                    break;
                                }
                            }
                        }
                    }

                    if (!valid) {
                        String argName = argumentType.getName();
                        if (TypeHelper.isNumeric(argumentType.jvmType())) {
                            argName = "number";
                        } else if (argumentType.equals(Classes.BooleanType())) {
                            argName = "boolean";
                        }

                        if (diagnostics != null) {
                            diagnostics.add(new DiagnosticInfo(Diagnostic.newDiagnostic(
                                ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT,
                                method.getLongName(), i + 1, argName), argSourceInfo));
                        }

                        return false;
                    }
                }
            }

            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * The most specific method of a set of applicable methods is the single maximally specific method.
     *
     * A method is maximally specific if there are no other applicable methods in the set that are more specific.
     * It is possible that a set of applicable methods contains more than one maximally specific method; in this
     * case the method call is ambiguous.
     */
    private <T extends CtBehavior> T findMostSpecificMethod(
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
                maximallySpecificMethods.stream().map(CtBehavior::getLongName).toArray(String[]::new),
                methods.get(0).getName()), sourceInfo));
        }

        return null;
    }

    /**
     * A method m1(a1_0..a1_n) is more specific than m2(a2_0..a2_n) if
     *   1. there is at least one pair (a1_i, a2_i) for which a1_i is more specific than a2_i, and
     *   2. there is no pair (a1_i, a2_i) for which a2_i is more specific than a1_i.
     */
    private boolean isMethodMoreSpecific(CtBehavior m1, CtBehavior m2, List<TypeInstance> argumentTypes) {
        Resolver resolver = new Resolver(SourceInfo.none());

        TypeInstance[] params1 = parameterCache.get(m1);
        if (params1 == null) {
            parameterCache.put(m1, params1 = resolver.getParameterTypes(m1, List.of(invokingType)));
        }

        TypeInstance[] params2 = parameterCache.get(m2);
        if (params2 == null) {
            parameterCache.put(m2, params2 = resolver.getParameterTypes(m2, List.of(invokingType)));
        }

        if (params1.length != params2.length) {
            throw new IllegalArgumentException();
        }

        Boolean m1IsMoreSpecific = null;

        for (int i = 0; i < params1.length; ++i) {
            if (params1[i].equals(params2[i])) {
                continue;
            }

            TypeInstance m1Type = params1[i].isConvertibleFrom(argumentTypes.get(i)) ?
                params1[i] : TypeHelper.tryGetArrayComponentType(m1, i);

            TypeInstance m2Type = params2[i].isConvertibleFrom(argumentTypes.get(i)) ?
                params2[i] : TypeHelper.tryGetArrayComponentType(m2, i);

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

    /**
     * A type t1 is more specific than t2 with regards to an invocation type e if
     *   1. t1 is a subtype of t2
     *   2. e is a subtype of t1
     */
    private boolean isTypeMoreSpecific(TypeInstance t1, TypeInstance t2, TypeInstance e) {
        if (t1.subtypeOf(t2)) {
            return true;
        }

        if (t2.subtypeOf(t1)) {
            return false;
        }

        return e.subtypeOf(t1);
    }

    public enum InvocationType {
        INSTANCE, STATIC, BOTH
    }

    private record InvocationContext(
        boolean staticInvocation, String methodName, List<TypeInstance> arguments,
        List<SourceInfo> argumentSourceInfo) {}

}

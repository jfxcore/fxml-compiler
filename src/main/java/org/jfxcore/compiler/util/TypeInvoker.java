// Copyright (c) 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.SignatureAttribute;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TypeInvoker {

    private static final SignatureAttribute.TypeParameter[] EMPTY_TYPE_PARAMS =
            new SignatureAttribute.TypeParameter[0];

    private final SourceInfo sourceInfo;
    private final boolean cacheEnabled;
    private Resolver resolver;

    public TypeInvoker(SourceInfo sourceInfo) {
        this.sourceInfo = sourceInfo;
        this.cacheEnabled = true;
    }

    public TypeInvoker(SourceInfo sourceInfo, boolean cacheEnabled) {
        this.sourceInfo = sourceInfo;
        this.cacheEnabled = cacheEnabled;
    }

    public TypeInstance invokeType(CtClass clazz) {
        CacheKey key = new CacheKey("invokeType", clazz);
        CacheEntry entry = getCache().get(key);
        if (entry.found() && cacheEnabled) {
            return (TypeInstance)entry.value();
        }

        try {
            TypeInstance typeInstance = invokeType(clazz.getDeclaringClass(), clazz, Map.of()).freeze(sourceInfo);
            return getCache().put(key, typeInstance);
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.classNotFound(sourceInfo, ex.getMessage());
        } catch (BadBytecode ex) {
            throw ExceptionHelper.unchecked(ex);
        }
    }

    public TypeInstance invokeType(CtClass clazz, List<TypeInstance> arguments) {
        CacheKey key = new CacheKey("invokeType", clazz, arguments);
        CacheEntry entry = getCache().get(key);
        if (entry.found() && cacheEnabled) {
            return (TypeInstance)entry.value();
        }

        try {
            SignatureAttribute.ClassSignature classSignature = getGenericClassSignature(clazz);
            if (classSignature == null) {
                if (!arguments.isEmpty()) {
                    throw GeneralErrors.numTypeArgumentsMismatch(sourceInfo, clazz, 0, arguments.size());
                }

                return invokeType(clazz);
            }

            Map<String, TypeInstance> providedArguments = associateProvidedArguments(
                clazz, arguments, classSignature.getParameters(), EMPTY_TYPE_PARAMS);

            return getCache().put(key, invokeType(clazz.getDeclaringClass(), clazz, providedArguments).freeze(sourceInfo));
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.classNotFound(sourceInfo, ex.getMessage());
        } catch (BadBytecode ex) {
            throw ExceptionHelper.unchecked(ex);
        }
    }

    public TypeInstance invokeFieldType(CtField field, List<TypeInstance> invocationChain) {
        CacheKey key = new CacheKey("invokeFieldType", field, invocationChain);
        CacheEntry entry = getCache().get(key);
        if (entry.found() && cacheEnabled) {
            return (TypeInstance)entry.value();
        }

        try {
            SignatureAttribute.ObjectType fieldType = getGenericFieldSignature(field);
            if (fieldType == null) {
                return invokeType(field.getDeclaringClass(), field.getType(), Map.of());
            }

            SignatureAttribute.ClassSignature classSignature = getGenericClassSignature(field.getDeclaringClass());
            SignatureAttribute.TypeParameter[] classTypeParams = classSignature != null ?
                classSignature.getParameters() : EMPTY_TYPE_PARAMS;

            TypeInstance typeInstance = invokeType(
                field.getDeclaringClass(),
                fieldType,
                TypeInstance.WildcardType.NONE,
                classTypeParams,
                new SignatureAttribute.TypeParameter[0],
                invocationChain,
                Map.of());

            return getCache().put(key, Objects.requireNonNullElse(typeInstance, TypeInstance.ObjectType()).freeze(sourceInfo));
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.classNotFound(sourceInfo, ex.getMessage());
        } catch (BadBytecode ex) {
            throw ExceptionHelper.unchecked(ex);
        }
    }

    public TypeInstance invokeReturnType(CtBehavior method, List<TypeInstance> invocationChain) {
        return invokeReturnType(method, invocationChain, List.of());
    }

    public TypeInstance invokeReturnType(CtBehavior behavior,
                                         List<TypeInstance> invocationChain,
                                         List<TypeInstance> providedArguments) {
        CacheKey key = new CacheKey("invokeReturnType", behavior, invocationChain, providedArguments);
        CacheEntry entry = getCache().get(key);
        if (entry.found() && cacheEnabled) {
            return (TypeInstance)entry.value();
        }

        if (behavior instanceof CtConstructor constructor) {
            return invokeType(constructor.getDeclaringClass(), providedArguments);
        }

        CtMethod method = (CtMethod)behavior;

        try {
            SignatureAttribute.MethodSignature methodSignature = getGenericMethodSignature(method);
            SignatureAttribute.ClassSignature classSignature = getGenericClassSignature(method.getDeclaringClass());
            TypeInstance typeInstance;

            if (methodSignature == null) {
                if (!providedArguments.isEmpty()) {
                    throw GeneralErrors.numTypeArgumentsMismatch(sourceInfo, method, 0, providedArguments.size());
                }

                typeInstance = invokeType(method.getDeclaringClass(), method.getReturnType(), Map.of());
            } else {
                SignatureAttribute.TypeParameter[] classTypeParams =
                    classSignature != null ? classSignature.getParameters() : EMPTY_TYPE_PARAMS;

                typeInstance = invokeType(
                    method.getDeclaringClass(),
                    methodSignature.getReturnType(),
                    TypeInstance.WildcardType.NONE,
                    classTypeParams,
                    methodSignature.getTypeParameters(),
                    invocationChain,
                    associateProvidedArguments(
                        method,
                        providedArguments,
                        methodSignature.getTypeParameters()));
            }

            return getCache().put(key, Objects.requireNonNullElse(typeInstance, TypeInstance.ObjectType()).freeze(sourceInfo));
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.classNotFound(sourceInfo, ex.getMessage());
        } catch (BadBytecode ex) {
            throw ExceptionHelper.unchecked(ex);
        }
    }

    public TypeInstance[] invokeParameterTypes(CtBehavior behavior, List<TypeInstance> invocationChain) {
        return invokeParameterTypes(behavior, invocationChain, List.of());
    }

    public TypeInstance[] invokeParameterTypes(CtBehavior behavior,
                                               List<TypeInstance> invocationChain,
                                               List<TypeInstance> providedArguments) {
        try {
            CacheKey key = new CacheKey("getParameterTypes", behavior, invocationChain, providedArguments);
            CacheEntry entry = getCache().get(key);
            if (entry.found() && cacheEnabled) {
                return (TypeInstance[])entry.value();
            }

            TypeInstance[] result;
            SignatureAttribute.MethodSignature methodSignature = getGenericMethodSignature(behavior);

            if (methodSignature == null) {
                CtClass[] paramTypes = behavior.getParameterTypes();
                result = new TypeInstance[paramTypes.length];

                for (int i = 0; i < paramTypes.length; ++i) {
                    var typeInst = invokeType(paramTypes[i]);
                    result[i] = new TypeInstance(
                        typeInst.jvmType(), List.of(), typeInst.getSuperTypes(), typeInst.getWildcardType());
                }
            } else {
                SignatureAttribute.ClassSignature classSignature = getGenericClassSignature(behavior.getDeclaringClass());
                result = new TypeInstance[methodSignature.getParameterTypes().length];

                Map<String, TypeInstance> associatedTypeVariables = associateTypeVariables(
                    behavior, methodSignature, invocationChain, providedArguments);

                for (int i = 0; i < methodSignature.getParameterTypes().length; ++i) {
                    result[i] = Objects.requireNonNullElse(
                        invokeType(
                            behavior.getDeclaringClass(),
                            methodSignature.getParameterTypes()[i],
                            TypeInstance.WildcardType.NONE,
                            classSignature != null ? classSignature.getParameters() : EMPTY_TYPE_PARAMS,
                            methodSignature.getTypeParameters(),
                            invocationChain,
                            associatedTypeVariables),
                        TypeInstance.ObjectType());
                }
            }

            return getCache().put(key, result);
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.classNotFound(sourceInfo, ex.getMessage());
        } catch (BadBytecode ex) {
            throw ExceptionHelper.unchecked(ex);
        }
    }

    private SignatureAttribute.ObjectType getGenericFieldSignature(CtField field) throws BadBytecode {
        String signature = field.getGenericSignature();
        return signature != null ? SignatureAttribute.toFieldSignature(signature) : null;
    }

    private SignatureAttribute.ClassSignature getGenericClassSignature(CtClass clazz) throws BadBytecode {
        String signature = clazz.getGenericSignature();
        return signature != null ? SignatureAttribute.toClassSignature(signature) : null;
    }

    private SignatureAttribute.MethodSignature getGenericMethodSignature(CtBehavior method) throws BadBytecode {
        String signature = method.getGenericSignature();
        return signature != null ? SignatureAttribute.toMethodSignature(signature) : null;
    }

    private Map<String, TypeInstance> associateTypeVariables(
            CtBehavior behavior,
            SignatureAttribute.MethodSignature methodSignature,
            List<TypeInstance> invocationChain,
            List<TypeInstance> providedArguments)
                throws NotFoundException, BadBytecode {
        class Algorithms {
            static TypeInstance findTypeInstance(TypeInstance typeInstance, CtClass type) {
                if (typeInstance.equals(type)) {
                    return typeInstance;
                }

                for (TypeInstance superType : typeInstance.getSuperTypes()) {
                    typeInstance = findTypeInstance(superType, type);
                    if (typeInstance != null) {
                        return typeInstance;
                    }
                }

                return null;
            }
        }

        Map<String, TypeInstance> result = new HashMap<>();
        SignatureAttribute.TypeParameter[] methodTypeParams = methodSignature.getTypeParameters();

        if (methodTypeParams.length != providedArguments.size()) {
            throw GeneralErrors.numTypeArgumentsMismatch(
                sourceInfo, behavior, methodTypeParams.length, providedArguments.size());
        }

        for (int i = 0; i < methodTypeParams.length; ++i) {
            result.put(methodTypeParams[i].getName(), providedArguments.get(i));
        }

        for (int i = 0; i < methodTypeParams.length; ++i) {
            checkProvidedArgument(
                providedArguments.get(i), methodTypeParams[i], EMPTY_TYPE_PARAMS, methodTypeParams, result);
        }

        CtClass declaringClass = behavior.getDeclaringClass();
        SignatureAttribute.ClassSignature classSignature = getGenericClassSignature(declaringClass);
        if (classSignature == null) {
            return result;
        }

        for (SignatureAttribute.Type paramType : methodSignature.getParameterTypes()) {
            if (!(paramType instanceof SignatureAttribute.TypeVariable typeVar)
                    || result.containsKey(typeVar.getName())) {
                continue;
            }

            for (int i = invocationChain.size() - 1; i >= 0; --i) {
                TypeInstance invokingType = Algorithms.findTypeInstance(invocationChain.get(i), declaringClass);
                if (invokingType == null) {
                    continue;
                }

                for (int j = 0; j < classSignature.getParameters().length; ++j) {
                    SignatureAttribute.TypeParameter typeParam = classSignature.getParameters()[j];
                    if (!typeParam.getName().equals(typeVar.getName())) {
                        continue;
                    }

                    result.put(typeVar.getName(), invokingType.getArguments().get(j));
                }
            }
        }

        return result;
    }

    private Map<String, TypeInstance> associateProvidedArguments(
            CtClass type,
            List<TypeInstance> providedArguments,
            SignatureAttribute.TypeParameter[] classTypeParams,
            SignatureAttribute.TypeParameter[] methodTypeParams)
                throws NotFoundException, BadBytecode {
        if (providedArguments.isEmpty()) {
            return Map.of();
        }

        var typeParams = Arrays.copyOf(classTypeParams, classTypeParams.length + methodTypeParams.length);
        System.arraycopy(methodTypeParams, 0, typeParams, classTypeParams.length, methodTypeParams.length);

        if (typeParams.length != providedArguments.size()) {
            throw GeneralErrors.numTypeArgumentsMismatch(
                sourceInfo, type, typeParams.length, providedArguments.size());
        }

        Map<String, TypeInstance> result = new HashMap<>();

        for (int i = 0; i < typeParams.length; ++i) {
            result.put(typeParams[i].getName(), providedArguments.get(i));
        }

        for (int i = 0; i < typeParams.length; ++i) {
            checkProvidedArgument(providedArguments.get(i), typeParams[i], classTypeParams, methodTypeParams, result);
        }

        return result;
    }

    private Map<String, TypeInstance> associateProvidedArguments(
            CtBehavior behavior,
            List<TypeInstance> providedArguments,
            SignatureAttribute.TypeParameter[] methodTypeParams)
                throws NotFoundException, BadBytecode {
        if (providedArguments.isEmpty()) {
            return Map.of();
        }

        if (methodTypeParams.length != providedArguments.size()) {
            throw GeneralErrors.numTypeArgumentsMismatch(
                sourceInfo, behavior, methodTypeParams.length, providedArguments.size());
        }

        Map<String, TypeInstance> result = new HashMap<>();

        for (int i = 0; i < methodTypeParams.length; ++i) {
            result.put(methodTypeParams[i].getName(), providedArguments.get(i));
        }

        for (int i = 0; i < methodTypeParams.length; ++i) {
            checkProvidedArgument(
                providedArguments.get(i), methodTypeParams[i], EMPTY_TYPE_PARAMS, methodTypeParams, result);
        }

        return result;
    }

    private void checkProvidedArgument(
            TypeInstance argumentType,
            SignatureAttribute.TypeParameter requiredType,
            SignatureAttribute.TypeParameter[] classTypeParams,
            SignatureAttribute.TypeParameter[] methodTypeParams,
            Map<String, TypeInstance> providedArguments)
                throws NotFoundException, BadBytecode {
        TypeInstance bound = requiredType.getClassBound() != null ?
            invokeType(
                null, requiredType.getClassBound(), TypeInstance.WildcardType.NONE,
                classTypeParams, methodTypeParams, List.of(), providedArguments) :
            invokeType(
                null, requiredType.getInterfaceBound()[0], TypeInstance.WildcardType.NONE,
                classTypeParams, methodTypeParams, List.of(), providedArguments);

        if (bound != null && !bound.isAssignableFrom(argumentType)) {
            throw GeneralErrors.typeArgumentOutOfBound(sourceInfo, argumentType, bound);
        }
    }

    private TypeInstance invokeType(CtClass invokingClass, CtClass invokedClass,
                                    Map<String, TypeInstance> providedArguments)
            throws NotFoundException, BadBytecode {
        SignatureAttribute.ClassSignature classSignature = getGenericClassSignature(invokedClass);
        List<TypeInstance> arguments = new ArrayList<>();
        List<TypeInstance> superTypes = new ArrayList<>();
        TypeInstance invokedTypeInstance = new TypeInstance(
            invokedClass, arguments, superTypes, TypeInstance.WildcardType.NONE);

        if (classSignature != null) {
            if (providedArguments.size() > 0 && providedArguments.size() != classSignature.getParameters().length) {
                throw GeneralErrors.numTypeArgumentsMismatch(
                    sourceInfo, invokedClass, classSignature.getParameters().length, providedArguments.size());
            }

            for (int i = 0; i < classSignature.getParameters().length; ++i) {
                SignatureAttribute.TypeParameter typeParam = classSignature.getParameters()[i];

                if (providedArguments.size() > 0) {
                    arguments.add(providedArguments.get(typeParam.getName()));
                } else {
                    TypeInstance bound = null;

                    if (typeParam.getClassBound() != null) {
                        bound = invokeType(
                            invokingClass, typeParam.getClassBound(), TypeInstance.WildcardType.NONE,
                            EMPTY_TYPE_PARAMS, EMPTY_TYPE_PARAMS, Collections.emptyList(), providedArguments);
                    } else if (typeParam.getInterfaceBound().length > 0) {
                        bound = invokeType(
                            invokingClass, typeParam.getInterfaceBound()[0], TypeInstance.WildcardType.NONE,
                            EMPTY_TYPE_PARAMS, EMPTY_TYPE_PARAMS, Collections.emptyList(), providedArguments);
                    }

                    if (bound != null) {
                        arguments.add(TypeInstance.ofErased(bound));
                    } else {
                        arguments.add(TypeInstance.ofErased(TypeInstance.ObjectType()));
                    }
                }
            }

            TypeInstance superType = invokeType(
                invokedClass,
                classSignature.getSuperClass(),
                TypeInstance.WildcardType.NONE,
                classSignature.getParameters(),
                EMPTY_TYPE_PARAMS,
                List.of(invokedTypeInstance),
                Map.of());

            if (superType != null) {
                superTypes.add(superType);
            }

            for (SignatureAttribute.ClassType intfType : classSignature.getInterfaces()) {
                superType = invokeType(
                    invokedClass,
                    intfType,
                    TypeInstance.WildcardType.NONE,
                    classSignature.getParameters(),
                    EMPTY_TYPE_PARAMS,
                    List.of(invokedTypeInstance),
                    Map.of());

                if (superType != null) {
                    superTypes.add(superType);
                }
            }
        } else {
            if (invokedClass.getSuperclass() != null) {
                superTypes.add(invokeType(null, invokedClass.getSuperclass(), Map.of()));
            }

            for (CtClass intfClass : invokedClass.getInterfaces()) {
                superTypes.add(invokeType(null, intfClass, Map.of()));
            }
        }

        return invokedTypeInstance;
    }

    private @Nullable TypeInstance invokeType(
            CtClass invokingClass,
            SignatureAttribute.Type invokedType,
            TypeInstance.WildcardType wildcard,
            SignatureAttribute.TypeParameter[] classTypeParams,
            SignatureAttribute.TypeParameter[] methodTypeParams,
            List<TypeInstance> invocationChain,
            Map<String, TypeInstance> providedArguments)
                throws NotFoundException, BadBytecode {
        if (invokedType instanceof SignatureAttribute.BaseType baseType) {
            return new TypeInstance(baseType.getCtlass(), List.of(), List.of(), wildcard);
        }

        if (invokedType instanceof SignatureAttribute.ArrayType arrayType) {
            SignatureAttribute.Type componentType = arrayType.getComponentType();
            int dimension = arrayType.getDimension();
            TypeInstance typeInst = invokeType(
                invokingClass, componentType, TypeInstance.WildcardType.NONE, classTypeParams,
                methodTypeParams, invocationChain, providedArguments);

            return typeInst.withDimensions(dimension).withWildcard(wildcard);
        }

        if (invokedType instanceof SignatureAttribute.ClassType classType) {
            CtClass clazz = resolveClass(invokedType.jvmTypeName());
            TypeInstance typeInstance;
            SignatureAttribute.ClassSignature classSignature = getGenericClassSignature(clazz);

            if (classSignature != null) {
                List<TypeInstance> arguments;

                if (classType.getTypeArguments() == null || providedArguments.isEmpty()) {
                    arguments = invokeTypeArguments(
                        invokingClass, classType, classTypeParams, methodTypeParams, invocationChain);
                } else {
                    arguments = new ArrayList<>();

                    for (SignatureAttribute.TypeArgument typeArg : classType.getTypeArguments()) {
                        TypeInstance typeInst;

                        if (typeArg.isWildcard() && typeArg.getType() == null) {
                            typeInst = new TypeInstance(
                                Classes.ObjectType(),
                                TypeInstance.ObjectType().getArguments(),
                                TypeInstance.ObjectType().getSuperTypes(),
                                TypeInstance.WildcardType.ANY);
                        } else {
                            typeInst = invokeType(
                                invokingClass, typeArg.getType(), TypeInstance.WildcardType.of(typeArg.getKind()),
                                classTypeParams, methodTypeParams, invocationChain, providedArguments);
                        }

                        arguments.add(Objects.requireNonNull(typeInst));
                    }
                }

                typeInstance = new TypeInstance(clazz, arguments, new ArrayList<>(), wildcard);
                List<TypeInstance> extendedInvocationChain = new ArrayList<>(invocationChain.size() + 1);
                extendedInvocationChain.addAll(invocationChain);
                extendedInvocationChain.add(typeInstance);

                SignatureAttribute.TypeParameter[] typeParams = classSignature.getParameters();

                TypeInstance superType = invokeType(
                    clazz, classSignature.getSuperClass(), TypeInstance.WildcardType.NONE, typeParams,
                    EMPTY_TYPE_PARAMS, extendedInvocationChain, Map.of());

                if (superType != null) {
                    typeInstance.getSuperTypes().add(superType);
                }

                for (SignatureAttribute.ClassType intfClass : classSignature.getInterfaces()) {
                    superType = invokeType(
                        clazz, intfClass, TypeInstance.WildcardType.NONE, typeParams,
                        EMPTY_TYPE_PARAMS, extendedInvocationChain, Map.of());

                    if (superType != null) {
                        typeInstance.getSuperTypes().add(superType);
                    }
                }
            } else {
                typeInstance = new TypeInstance(clazz, Collections.emptyList(), new ArrayList<>(), wildcard);

                if (clazz.getSuperclass() != null) {
                    typeInstance.getSuperTypes().add(
                        invokeType(null, clazz.getSuperclass(), Map.of()));
                }

                for (CtClass intfClass : clazz.getInterfaces()) {
                    typeInstance.getSuperTypes().add(invokeType(null, intfClass, Map.of()));
                }
            }

            return typeInstance;
        }

        if (invokedType instanceof SignatureAttribute.TypeVariable typeVar) {
            if (providedArguments.size() > 0) {
                TypeInstance result = providedArguments.get(typeVar.getName());
                if (wildcard == TypeInstance.WildcardType.NONE) {
                    return result;
                }

                return result.withWildcard(wildcard);
            }

            return findTypeParameter(
                invokingClass, typeVar.getName(), classTypeParams, methodTypeParams, invocationChain);
        }

        throw new IllegalArgumentException();
    }

    private List<TypeInstance> invokeTypeArguments(
            CtClass invokingClass,
            SignatureAttribute.ClassType classType,
            SignatureAttribute.TypeParameter[] classTypeParams,
            SignatureAttribute.TypeParameter[] methodTypeParams,
            List<TypeInstance> invocationChain)
                throws NotFoundException, BadBytecode {
        List<TypeInstance> arguments = null;

        if (classType.getTypeArguments() == null) {
            return Collections.emptyList();
        }

        for (SignatureAttribute.TypeArgument typeArg : classType.getTypeArguments()) {
            if (arguments == null) {
                arguments = new ArrayList<>(2);
            }

            if (typeArg.getType() == null) {
                arguments.add(
                    invokeType(Classes.ObjectsType())
                        .withWildcard(TypeInstance.WildcardType.of(typeArg.getKind())));
            }

            if (typeArg.getType() instanceof SignatureAttribute.ClassType classTypeArg) {
                CtClass argClass = resolveClass(typeArg.getType().jvmTypeName());
                TypeInstance existingInstance = null;

                List<TypeInstance> typeArgs = invokeTypeArguments(
                    argClass, classTypeArg, classTypeParams, methodTypeParams, invocationChain);

                for (int i = invocationChain.size() - 1; i >= 0; --i) {
                    TypeInstance instance = invocationChain.get(i);

                    if (TypeHelper.equals(instance.jvmType(), argClass)
                            && instance.isRaw() == typeArgs.stream().anyMatch(TypeInstance::isRaw)
                            && instance.getArguments().equals(typeArgs)) {
                        existingInstance = invocationChain.get(i);
                        break;
                    }
                }

                if (existingInstance == null) {
                    existingInstance = invokeType(
                        invokingClass, typeArg.getType(), TypeInstance.WildcardType.of(typeArg.getKind()),
                        classTypeParams, methodTypeParams, invocationChain, Map.of());
                }

                if (existingInstance != null) {
                    arguments.add(existingInstance);
                }
            }

            if (typeArg.getType() instanceof SignatureAttribute.TypeVariable typeVarArg) {
                TypeInstance typeParam = findTypeParameter(
                    invokingClass, typeVarArg.getName(), classTypeParams, methodTypeParams, invocationChain);

                if (typeParam != null) {
                    var wildcard = TypeInstance.WildcardType.of(typeArg.getKind());
                    if (wildcard != TypeInstance.WildcardType.NONE) {
                        typeParam = typeParam.withWildcard(wildcard);
                    }

                    arguments.add(typeParam);
                }
            }
        }

        return arguments != null ? arguments : Collections.emptyList();
    }

    private @Nullable TypeInstance findTypeParameter(
            CtClass invokingClass,
            String typeVariableName,
            SignatureAttribute.TypeParameter[] classTypeParams,
            SignatureAttribute.TypeParameter[] methodTypeParams,
            List<TypeInstance> invocationChain)
                throws NotFoundException, BadBytecode {
        for (SignatureAttribute.TypeParameter typeParam : methodTypeParams) {
            if (!typeParam.getName().equals(typeVariableName)) {
                continue;
            }

            return typeParam.getClassBound() != null ?
                invokeType(
                    invokingClass, typeParam.getClassBound(), TypeInstance.WildcardType.NONE, classTypeParams,
                    methodTypeParams, invocationChain, Map.of()) :
                new TypeInstance(
                    resolveClass(typeParam.getInterfaceBound()[0].jvmTypeName()),
                    List.of(), List.of(), TypeInstance.WildcardType.NONE);
        }

        for (int i = 0; i < classTypeParams.length; ++i) {
            SignatureAttribute.TypeParameter typeParam = classTypeParams[i];
            if (!typeParam.getName().equals(typeVariableName)) {
                continue;
            }

            if (!invocationChain.isEmpty()) {
                TypeInstance invoker = findInvoker(invokingClass, invocationChain.get(invocationChain.size() - 1));
                if (invoker == null || invoker.getArguments().isEmpty()) {
                    continue;
                }

                return invoker.getArguments().get(i);
            }

            return typeParam.getClassBound() != null ?
                invokeType(
                    invokingClass, typeParam.getClassBound(), TypeInstance.WildcardType.NONE, classTypeParams,
                    methodTypeParams, invocationChain, Map.of()) :
                new TypeInstance(
                    resolveClass(typeParam.getInterfaceBound()[0].jvmTypeName()),
                    List.of(), List.of(), TypeInstance.WildcardType.NONE);
        }

        return null;
    }

    private @Nullable TypeInstance findInvoker(CtClass invokingClass, TypeInstance potentialInvoker) {
        if (TypeHelper.equals(potentialInvoker.jvmType(), invokingClass)) {
            return potentialInvoker;
        }

        for (TypeInstance superType : potentialInvoker.getSuperTypes()) {
            potentialInvoker = findInvoker(invokingClass, superType);
            if (potentialInvoker != null) {
                return potentialInvoker;
            }
        }

        return null;
    }

    private CtClass resolveClass(String fullyQualifiedName) {
        if (resolver == null) {
            resolver = new Resolver(sourceInfo, cacheEnabled);
        }

        return resolver.resolveClass(fullyQualifiedName);
    }

    private static Cache getCache() {
        return (Cache)CompilationContext.getCurrent().computeIfAbsent(TypeInvoker.class, key -> new Cache());
    }
}

// Copyright (c) 2025, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.type;

import javassist.bytecode.BadBytecode;
import javassist.bytecode.SignatureAttribute;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.ExceptionHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.jfxcore.compiler.type.KnownSymbols.*;

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

    public int getTypeParameterCount(TypeDeclaration type) {
        try {
            SignatureAttribute.ClassSignature signature = getGenericClassSignature(type);
            return signature != null ? signature.getParameters().length : 0;
        } catch (BadBytecode ex) {
            throw ExceptionHelper.unchecked(ex);
        }
    }

    public TypeInstance invokeType(TypeDeclaration clazz) {
        CacheKey key = new CacheKey("invokeType", clazz);
        CacheEntry entry = getCache().get(key);
        if (entry.found() && cacheEnabled) {
            return (TypeInstance)entry.value();
        }

        try {
            TypeInstance typeInstance = invokeType(clazz.declaringType().orElse(null), clazz, Map.of());
            typeInstance.freeze(sourceInfo);
            return getCache().put(key, typeInstance);
        } catch (BadBytecode ex) {
            throw ExceptionHelper.unchecked(ex);
        }
    }

    public TypeInstance invokeType(TypeDeclaration clazz, List<TypeInstance> arguments) {
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

            Map<String, TypeInstance> providedArguments = associateClassArguments(
                clazz, arguments, classSignature.getParameters(), List.of());

            TypeInstance invokedType = invokeType(clazz.declaringType().orElse(null), clazz, providedArguments);
            invokedType.freeze(sourceInfo);
            return getCache().put(key, invokedType);
        } catch (BadBytecode ex) {
            throw ExceptionHelper.unchecked(ex);
        }
    }

    public TypeInstance invokeType(TypeInstance owner, TypeDeclaration clazz, List<TypeInstance> arguments) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(clazz, "clazz");
        Objects.requireNonNull(arguments, "arguments");

        TypeDeclaration declaringType = clazz.declaringType().orElseThrow(
            () -> new IllegalArgumentException("clazz is not a member type"));

        if (clazz.isStatic()) {
            throw new IllegalArgumentException("clazz is a static member type");
        }

        if (!owner.subtypeOf(declaringType)) {
            throw new IllegalArgumentException(
                owner.javaName() + " is not compatible with " + declaringType.javaName());
        }

        CacheKey key = new CacheKey("invokeType", owner, clazz, arguments);
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

                TypeInstance invokedType = invokeType(owner, declaringType, clazz, Map.of());
                invokedType.freeze(sourceInfo);
                return getCache().put(key, invokedType);
            }

            Map<String, TypeInstance> providedArguments = associateClassArguments(
                clazz, arguments, classSignature.getParameters(), List.of(owner));

            TypeInstance invokedType = invokeType(owner, declaringType, clazz, providedArguments);
            invokedType.freeze(sourceInfo);
            return getCache().put(key, invokedType);
        } catch (BadBytecode ex) {
            throw ExceptionHelper.unchecked(ex);
        }
    }

    public TypeInstance invokeFieldType(FieldDeclaration field, List<TypeInstance> invocationContext) {
        CacheKey key = new CacheKey("invokeFieldType", field, invocationContext);
        CacheEntry entry = getCache().get(key);
        if (entry.found() && cacheEnabled) {
            return (TypeInstance)entry.value();
        }

        try {
            SignatureAttribute.ObjectType fieldType = getGenericFieldSignature(field);
            if (fieldType == null) {
                return invokeType(field.declaringType(), field.type(), Map.of());
            }

            SignatureAttribute.ClassSignature classSignature = getGenericClassSignature(field.declaringType());
            SignatureAttribute.TypeParameter[] classTypeParams = classSignature != null ?
                classSignature.getParameters() : EMPTY_TYPE_PARAMS;

            TypeInstance typeInstance = invokeType(
                field.declaringType(),
                fieldType,
                TypeInstance.WildcardType.NONE,
                classTypeParams,
                new SignatureAttribute.TypeParameter[0],
                invocationContext,
                Map.of());

            return getCache().put(key, Objects.requireNonNullElse(typeInstance, TypeInstance.ObjectType()).freeze(sourceInfo));
        } catch (BadBytecode ex) {
            throw ExceptionHelper.unchecked(ex);
        }
    }

    public TypeInstance invokeReturnType(BehaviorDeclaration method, List<TypeInstance> invocationContext) {
        return invokeReturnType(method, invocationContext, List.of());
    }

    public TypeInstance invokeReturnType(BehaviorDeclaration behavior,
                                         List<TypeInstance> invocationContext,
                                         List<TypeInstance> providedArguments) {
        CacheKey key = new CacheKey("invokeReturnType", behavior, invocationContext, providedArguments);
        CacheEntry entry = getCache().get(key);
        if (entry.found() && cacheEnabled) {
            return (TypeInstance)entry.value();
        }

        if (behavior instanceof ConstructorDeclaration constructor) {
            return invokeType(constructor.declaringType(), providedArguments);
        }

        MethodDeclaration method = (MethodDeclaration)behavior;

        try {
            SignatureAttribute.MethodSignature methodSignature = getGenericMethodSignature(method);
            SignatureAttribute.ClassSignature classSignature = getGenericClassSignature(method.declaringType());
            TypeInstance typeInstance;

            if (methodSignature == null) {
                if (!providedArguments.isEmpty()) {
                    throw GeneralErrors.numTypeArgumentsMismatch(sourceInfo, method, 0, providedArguments.size());
                }

                typeInstance = invokeType(method.declaringType(), method.returnType(), Map.of());
            } else {
                SignatureAttribute.TypeParameter[] classTypeParams =
                    classSignature != null ? classSignature.getParameters() : EMPTY_TYPE_PARAMS;

                typeInstance = invokeType(
                    method.declaringType(),
                    methodSignature.getReturnType(),
                    TypeInstance.WildcardType.NONE,
                    classTypeParams,
                    methodSignature.getTypeParameters(),
                    invocationContext,
                    associateMethodArguments(
                        method,
                        providedArguments,
                        classTypeParams,
                        methodSignature.getTypeParameters(),
                        invocationContext));
            }

            return getCache().put(key, Objects.requireNonNullElse(typeInstance, TypeInstance.ObjectType()).freeze(sourceInfo));
        } catch (BadBytecode ex) {
            throw ExceptionHelper.unchecked(ex);
        }
    }

    public TypeInstance[] invokeParameterTypes(BehaviorDeclaration behavior, List<TypeInstance> invocationContext) {
        return invokeParameterTypes(behavior, invocationContext, List.of());
    }

    public TypeInstance[] invokeParameterTypes(BehaviorDeclaration behavior,
                                               List<TypeInstance> invocationContext,
                                               List<TypeInstance> providedArguments) {
        try {
            CacheKey key = new CacheKey("invokeParameterTypes", behavior, invocationContext, providedArguments);
            CacheEntry entry = getCache().get(key);
            if (entry.found() && cacheEnabled) {
                return (TypeInstance[])entry.value();
            }

            TypeInstance[] result;
            SignatureAttribute.MethodSignature methodSignature = getGenericMethodSignature(behavior);

            if (methodSignature == null) {
                List<BehaviorDeclaration.Parameter> params = behavior.parameters();
                result = new TypeInstance[params.size()];

                for (int i = 0; i < params.size(); ++i) {
                    result[i] = invokeType(params.get(i).type());
                }
            } else {
                SignatureAttribute.ClassSignature classSignature = getGenericClassSignature(behavior.declaringType());
                result = new TypeInstance[methodSignature.getParameterTypes().length];

                Map<String, TypeInstance> associatedTypeVariables = associateTypeVariables(
                    behavior, methodSignature, invocationContext, providedArguments);

                for (int i = 0; i < methodSignature.getParameterTypes().length; ++i) {
                    result[i] = Objects.requireNonNullElse(
                        invokeType(
                            behavior.declaringType(),
                            methodSignature.getParameterTypes()[i],
                            TypeInstance.WildcardType.NONE,
                            classSignature != null ? classSignature.getParameters() : EMPTY_TYPE_PARAMS,
                            methodSignature.getTypeParameters(),
                            invocationContext,
                            associatedTypeVariables),
                        TypeInstance.ObjectType());
                }
            }

            return getCache().put(key, result);
        } catch (BadBytecode ex) {
            throw ExceptionHelper.unchecked(ex);
        }
    }

    /**
     * Resolves the parameters visible at a source-level invocation. A non-static member-class
     * constructor has a hidden leading enclosing-instance parameter in its descriptor, while its
     * generic signature may already omit that parameter. This method normalizes both shapes to the
     * explicit source parameter list.
     */
    public TypeInstance[] invokeSourceParameterTypes(
            BehaviorDeclaration behavior,
            List<TypeInstance> invocationContext,
            List<TypeInstance> providedArguments) {
        TypeInstance[] result = invokeParameterTypes(behavior, invocationContext, providedArguments);

        if (behavior instanceof ConstructorDeclaration constructor
                && constructor.requiresEnclosingInstance()
                && result.length == constructor.parameters().size()) {
            return java.util.Arrays.copyOfRange(result, 1, result.length);
        }

        return result;
    }

    private SignatureAttribute.ObjectType getGenericFieldSignature(FieldDeclaration field) throws BadBytecode {
        String signature = field.genericSignature();
        return signature != null ? SignatureAttribute.toFieldSignature(signature) : null;
    }

    private SignatureAttribute.ClassSignature getGenericClassSignature(TypeDeclaration clazz) throws BadBytecode {
        String signature = clazz.genericSignature();
        return signature != null ? SignatureAttribute.toClassSignature(signature) : null;
    }

    private SignatureAttribute.MethodSignature getGenericMethodSignature(BehaviorDeclaration method) throws BadBytecode {
        String signature = method.genericSignature();
        return signature != null ? SignatureAttribute.toMethodSignature(signature) : null;
    }

    private Map<String, TypeInstance> associateTypeVariables(
            BehaviorDeclaration behavior,
            SignatureAttribute.MethodSignature methodSignature,
            List<TypeInstance> invocationContext,
            List<TypeInstance> providedArguments)
                throws BadBytecode {
        class Algorithms {
            static TypeInstance findTypeInstance(TypeInstance typeInstance, TypeDeclaration type) {
                if (typeInstance.equals(type)) {
                    return typeInstance;
                }

                TypeInstance owner = typeInstance.owner();
                if (owner != null) {
                    TypeInstance result = findTypeInstance(owner, type);
                    if (result != null) {
                        return result;
                    }
                }

                for (TypeInstance superType : typeInstance.superTypes()) {
                    typeInstance = findTypeInstance(superType, type);
                    if (typeInstance != null) {
                        return typeInstance;
                    }
                }

                return null;
            }
        }

        SignatureAttribute.TypeParameter[] methodTypeParams = methodSignature.getTypeParameters();
        TypeDeclaration declaringClass = behavior.declaringType();
        SignatureAttribute.ClassSignature classSignature = getGenericClassSignature(declaringClass);
        SignatureAttribute.TypeParameter[] classTypeParams = classSignature != null ?
            classSignature.getParameters() : EMPTY_TYPE_PARAMS;

        if (methodTypeParams.length != providedArguments.size()) {
            throw GeneralErrors.numTypeArgumentsMismatch(
                sourceInfo, behavior, methodTypeParams.length, providedArguments.size());
        }

        Map<String, TypeInstance> result = associateArguments(
            declaringClass, behavior, providedArguments, methodTypeParams,
            classTypeParams, methodTypeParams, invocationContext);

        if (classSignature == null) {
            return result;
        }

        for (SignatureAttribute.Type paramType : methodSignature.getParameterTypes()) {
            if (!(paramType instanceof SignatureAttribute.TypeVariable typeVar)
                    || result.containsKey(typeVar.getName())) {
                continue;
            }

            for (int i = invocationContext.size() - 1; i >= 0; --i) {
                TypeInstance invokingType = Algorithms.findTypeInstance(invocationContext.get(i), declaringClass);
                if (invokingType == null) {
                    continue;
                }

                for (int j = 0; j < classSignature.getParameters().length; ++j) {
                    SignatureAttribute.TypeParameter typeParam = classSignature.getParameters()[j];
                    if (!typeParam.getName().equals(typeVar.getName())) {
                        continue;
                    }

                    result.put(typeVar.getName(), invokingType.arguments().get(j));
                }
            }
        }

        return result;
    }

    private Map<String, TypeInstance> associateClassArguments(
            TypeDeclaration type,
            List<TypeInstance> providedArguments,
            SignatureAttribute.TypeParameter[] classTypeParams,
            List<TypeInstance> invocationContext)
                throws BadBytecode {
        if (providedArguments.isEmpty()) {
            return Map.of();
        }

        if (classTypeParams.length != providedArguments.size()) {
            throw GeneralErrors.numTypeArgumentsMismatch(
                sourceInfo, type, classTypeParams.length, providedArguments.size());
        }

        return associateArguments(
            type, null, providedArguments, classTypeParams,
            classTypeParams, EMPTY_TYPE_PARAMS, invocationContext);
    }

    private Map<String, TypeInstance> associateMethodArguments(
            BehaviorDeclaration behavior,
            List<TypeInstance> providedArguments,
            SignatureAttribute.TypeParameter[] classTypeParams,
            SignatureAttribute.TypeParameter[] methodTypeParams,
            List<TypeInstance> invocationContext)
                throws BadBytecode {
        if (providedArguments.isEmpty()) {
            return Map.of();
        }

        if (methodTypeParams.length != providedArguments.size()) {
            throw GeneralErrors.numTypeArgumentsMismatch(
                sourceInfo, behavior, methodTypeParams.length, providedArguments.size());
        }

        return associateArguments(
            behavior.declaringType(), behavior, providedArguments, methodTypeParams,
            classTypeParams, methodTypeParams, invocationContext);
    }

    private Map<String, TypeInstance> associateArguments(
            TypeDeclaration invokingClass,
            @Nullable BehaviorDeclaration behavior,
            List<TypeInstance> providedArguments,
            SignatureAttribute.TypeParameter[] providedTypeParams,
            SignatureAttribute.TypeParameter[] classTypeParams,
            SignatureAttribute.TypeParameter[] methodTypeParams,
            List<TypeInstance> invocationContext)
                throws BadBytecode {
        Map<String, TypeInstance> result = new HashMap<>();

        for (int i = 0; i < providedTypeParams.length; ++i) {
            result.put(providedTypeParams[i].getName(), providedArguments.get(i));
        }

        for (int i = 0; i < providedTypeParams.length; ++i) {
            checkProvidedArgument(
                invokingClass, behavior, providedArguments.get(i), providedTypeParams[i],
                classTypeParams, methodTypeParams, invocationContext, result);
        }

        return result;
    }

    private void checkProvidedArgument(
            TypeDeclaration invokingClass,
            @Nullable BehaviorDeclaration behavior,
            TypeInstance argumentType,
            SignatureAttribute.TypeParameter requiredType,
            SignatureAttribute.TypeParameter[] classTypeParams,
            SignatureAttribute.TypeParameter[] methodTypeParams,
            List<TypeInstance> invocationContext,
            Map<String, TypeInstance> providedArguments)
                throws BadBytecode {
        if (argumentType.isPrimitive()) {
            throw behavior != null ?
                GeneralErrors.typeArgumentNotReference(sourceInfo, behavior, argumentType) :
                GeneralErrors.typeArgumentNotReference(sourceInfo, invokingClass, argumentType);
        }

        TypeInstance bound = requiredType.getClassBound() != null ?
            invokeType(
                invokingClass, requiredType.getClassBound(), TypeInstance.WildcardType.NONE,
                classTypeParams, methodTypeParams, invocationContext, providedArguments) :
            invokeType(
                invokingClass, requiredType.getInterfaceBound()[0], TypeInstance.WildcardType.NONE,
                classTypeParams, methodTypeParams, invocationContext, providedArguments);

        if (bound != null && !bound.isAssignableFrom(argumentType)) {
            throw GeneralErrors.typeArgumentOutOfBound(sourceInfo, argumentType, bound);
        }
    }

    private TypeInstance invokeType(TypeDeclaration invokingClass, TypeDeclaration invokedClass,
                                    Map<String, TypeInstance> providedArguments) throws BadBytecode {
        return invokeType(null, invokingClass, invokedClass, providedArguments);
    }

    private TypeInstance invokeType(@Nullable TypeInstance owner,
                                    TypeDeclaration invokingClass,
                                    TypeDeclaration invokedClass,
                                    Map<String, TypeInstance> providedArguments) throws BadBytecode {
        SignatureAttribute.ClassSignature classSignature = getGenericClassSignature(invokedClass);
        List<TypeInstance> arguments = new ArrayList<>();
        List<TypeInstance> superTypes = new ArrayList<>();

        TypeInstance invokedTypeInstance = new TypeInstance(
            owner, invokedClass, arguments, superTypes, TypeInstance.WildcardType.NONE);

        List<TypeInstance> invocationContext = owner != null ?
            List.of(owner, invokedTypeInstance) : List.of(invokedTypeInstance);

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
                            EMPTY_TYPE_PARAMS, EMPTY_TYPE_PARAMS, invocationContext, providedArguments);
                    } else if (typeParam.getInterfaceBound().length > 0) {
                        bound = invokeType(
                            invokingClass, typeParam.getInterfaceBound()[0], TypeInstance.WildcardType.NONE,
                            EMPTY_TYPE_PARAMS, EMPTY_TYPE_PARAMS, invocationContext, providedArguments);
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
                invocationContext,
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
                    invocationContext,
                    Map.of());

                if (superType != null) {
                    superTypes.add(superType);
                }
            }
        } else {
            TypeDeclaration superClass = invokedClass.superClass().orElse(null);
            if (superClass != null) {
                superTypes.add(invokeType(null, superClass, Map.of()));
            }

            for (TypeDeclaration intfClass : invokedClass.interfaces()) {
                superTypes.add(invokeType(null, intfClass, Map.of()));
            }
        }

        return invokedTypeInstance;
    }

    private @Nullable TypeInstance invokeType(
            TypeDeclaration invokingClass,
            SignatureAttribute.Type invokedType,
            TypeInstance.WildcardType wildcard,
            SignatureAttribute.TypeParameter[] classTypeParams,
            SignatureAttribute.TypeParameter[] methodTypeParams,
            List<TypeInstance> invocationContext,
            Map<String, TypeInstance> providedArguments)
                throws BadBytecode {
        if (invokedType instanceof SignatureAttribute.BaseType baseType) {
            return new TypeInstance(TypeDeclaration.of(baseType.getCtlass()), List.of(), List.of(), wildcard);
        }

        if (invokedType instanceof SignatureAttribute.ArrayType arrayType) {
            SignatureAttribute.Type componentType = arrayType.getComponentType();
            int dimension = arrayType.getDimension();
            TypeInstance typeInst = invokeType(
                invokingClass, componentType, TypeInstance.WildcardType.NONE, classTypeParams,
                methodTypeParams, invocationContext, providedArguments);

            return typeInst.withDimensions(dimension).withWildcard(wildcard);
        }

        if (invokedType instanceof SignatureAttribute.ClassType classType) {
            TypeDeclaration clazz = resolveDeclaration(invokedType.jvmTypeName());
            TypeInstance typeInstance;
            TypeInstance owner = invokeOwnerType(
                invokingClass, clazz, classType, classTypeParams,
                methodTypeParams, invocationContext, providedArguments);

            SignatureAttribute.ClassSignature classSignature = getGenericClassSignature(clazz);

            if (classSignature != null) {
                List<TypeInstance> arguments;

                if (classType.getTypeArguments() == null || providedArguments.isEmpty()) {
                    arguments = invokeTypeArguments(
                        invokingClass, classType, classTypeParams, methodTypeParams, invocationContext);
                } else {
                    arguments = new ArrayList<>();

                    for (SignatureAttribute.TypeArgument typeArg : classType.getTypeArguments()) {
                        TypeInstance typeInst;

                        if (typeArg.isWildcard() && typeArg.getType() == null) {
                            typeInst = new TypeInstance(
                                ObjectDecl(),
                                TypeInstance.ObjectType().arguments(),
                                TypeInstance.ObjectType().superTypes(),
                                TypeInstance.WildcardType.ANY);
                        } else {
                            typeInst = invokeType(
                                invokingClass, typeArg.getType(), TypeInstance.WildcardType.of(typeArg.getKind()),
                                classTypeParams, methodTypeParams, invocationContext, providedArguments);
                        }

                        arguments.add(Objects.requireNonNull(typeInst));
                    }
                }

                typeInstance = new TypeInstance(owner, clazz, arguments, new ArrayList<>(), wildcard);
                List<TypeInstance> extendedInvocationContext = new ArrayList<>(invocationContext.size() + 2);
                extendedInvocationContext.addAll(invocationContext);
                if (owner != null) {
                    extendedInvocationContext.add(owner);
                }

                extendedInvocationContext.add(typeInstance);

                SignatureAttribute.TypeParameter[] typeParams = classSignature.getParameters();

                TypeInstance superType = invokeType(
                    clazz, classSignature.getSuperClass(), TypeInstance.WildcardType.NONE, typeParams,
                    EMPTY_TYPE_PARAMS, extendedInvocationContext, Map.of());

                if (superType != null) {
                    typeInstance.superTypes().add(superType);
                }

                for (SignatureAttribute.ClassType intfClass : classSignature.getInterfaces()) {
                    superType = invokeType(
                        clazz, intfClass, TypeInstance.WildcardType.NONE, typeParams,
                        EMPTY_TYPE_PARAMS, extendedInvocationContext, Map.of());

                    if (superType != null) {
                        typeInstance.superTypes().add(superType);
                    }
                }
            } else {
                typeInstance = new TypeInstance(owner, clazz, Collections.emptyList(), new ArrayList<>(), wildcard);
                TypeDeclaration superClass = clazz.superClass().orElse(null);

                if (superClass != null) {
                    typeInstance.superTypes().add(invokeType(null, superClass, Map.of()));
                }

                for (TypeDeclaration intfClass : clazz.interfaces()) {
                    typeInstance.superTypes().add(invokeType(null, intfClass, Map.of()));
                }
            }

            return typeInstance;
        }

        if (invokedType instanceof SignatureAttribute.TypeVariable typeVar) {
            TypeInstance result = providedArguments.get(typeVar.getName());
            if (result != null) {
                if (wildcard == TypeInstance.WildcardType.NONE) {
                    return result;
                }

                return result.withWildcard(wildcard);
            }

            return findTypeParameter(
                invokingClass, typeVar.getName(), classTypeParams, methodTypeParams, invocationContext);
        }

        throw new IllegalArgumentException();
    }

    private @Nullable TypeInstance invokeOwnerType(
            TypeDeclaration invokingClass,
            TypeDeclaration clazz,
            SignatureAttribute.ClassType classType,
            SignatureAttribute.TypeParameter[] classTypeParams,
            SignatureAttribute.TypeParameter[] methodTypeParams,
            List<TypeInstance> invocationContext,
            Map<String, TypeInstance> providedArguments)
                throws BadBytecode {
        SignatureAttribute.ClassType declaringClass = classType.getDeclaringClass();
        if (declaringClass == null || clazz.isStatic()) {
            return null;
        }

        return invokeType(
            invokingClass, declaringClass, TypeInstance.WildcardType.NONE,
            classTypeParams, methodTypeParams, invocationContext, providedArguments);
    }

    private List<TypeInstance> invokeTypeArguments(
            TypeDeclaration invokingClass,
            SignatureAttribute.ClassType classType,
            SignatureAttribute.TypeParameter[] classTypeParams,
            SignatureAttribute.TypeParameter[] methodTypeParams,
            List<TypeInstance> invocationContext)
                throws BadBytecode {
        List<TypeInstance> arguments = null;

        if (classType.getTypeArguments() == null) {
            return Collections.emptyList();
        }

        for (SignatureAttribute.TypeArgument typeArg : classType.getTypeArguments()) {
            if (arguments == null) {
                arguments = new ArrayList<>(2);
            }

            if (typeArg.getType() == null) {
                arguments.add(invokeType(ObjectsDecl()).withWildcard(TypeInstance.WildcardType.of(typeArg.getKind())));
            }

            if (typeArg.getType() instanceof SignatureAttribute.ClassType classTypeArg) {
                TypeDeclaration argClass = resolveDeclaration(typeArg.getType().jvmTypeName());
                TypeInstance existingInstance = null;
                TypeInstance owner = invokeOwnerType(
                    invokingClass, argClass, classTypeArg, classTypeParams,
                    methodTypeParams, invocationContext, Map.of());

                List<TypeInstance> typeArgs = invokeTypeArguments(
                    argClass, classTypeArg, classTypeParams, methodTypeParams, invocationContext);

                for (int i = invocationContext.size() - 1; i >= 0; --i) {
                    TypeInstance instance = invocationContext.get(i);

                    if (instance.equals(argClass)
                            && instance.isRaw() == typeArgs.stream().anyMatch(TypeInstance::isRaw)
                            && Objects.equals(instance.owner(), owner)
                            && instance.arguments().equals(typeArgs)) {
                        existingInstance = invocationContext.get(i);
                        break;
                    }
                }

                if (existingInstance == null) {
                    existingInstance = invokeType(
                        invokingClass, typeArg.getType(), TypeInstance.WildcardType.of(typeArg.getKind()),
                        classTypeParams, methodTypeParams, invocationContext, Map.of());
                }

                if (existingInstance != null) {
                    arguments.add(existingInstance);
                }
            }

            if (typeArg.getType() instanceof SignatureAttribute.TypeVariable typeVarArg) {
                TypeInstance typeParam = findTypeParameter(
                    invokingClass, typeVarArg.getName(), classTypeParams, methodTypeParams, invocationContext);

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
            TypeDeclaration invokingClass,
            String typeVariableName,
            SignatureAttribute.TypeParameter[] classTypeParams,
            SignatureAttribute.TypeParameter[] methodTypeParams,
            List<TypeInstance> invocationContext)
                throws BadBytecode {
        for (SignatureAttribute.TypeParameter typeParam : methodTypeParams) {
            if (!typeParam.getName().equals(typeVariableName)) {
                continue;
            }

            return typeParam.getClassBound() != null ?
                invokeType(
                    invokingClass, typeParam.getClassBound(), TypeInstance.WildcardType.NONE, classTypeParams,
                    methodTypeParams, invocationContext, Map.of()) :
                new TypeInstance(
                    resolveDeclaration(typeParam.getInterfaceBound()[0].jvmTypeName()),
                    List.of(), List.of(), TypeInstance.WildcardType.NONE);
        }

        for (int i = 0; i < classTypeParams.length; ++i) {
            SignatureAttribute.TypeParameter typeParam = classTypeParams[i];
            if (!typeParam.getName().equals(typeVariableName)) {
                continue;
            }

            if (!invocationContext.isEmpty()) {
                for (int j = invocationContext.size() - 1; j >= 0; --j) {
                    TypeInstance invoker = findInvoker(invokingClass, invocationContext.get(j));
                    if (invoker == null || i >= invoker.arguments().size()) {
                        continue;
                    }

                    return invoker.arguments().get(i);
                }
            }

            return typeParam.getClassBound() != null ?
                invokeType(
                    invokingClass, typeParam.getClassBound(), TypeInstance.WildcardType.NONE, classTypeParams,
                    methodTypeParams, invocationContext, Map.of()) :
                new TypeInstance(
                    resolveDeclaration(typeParam.getInterfaceBound()[0].jvmTypeName()),
                    List.of(), List.of(), TypeInstance.WildcardType.NONE);
        }

        Set<TypeInstance> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        for (int i = invocationContext.size() - 1; i >= 0; --i) {
            TypeInstance result = findTypeArgument(invocationContext.get(i), typeVariableName, visited);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private @Nullable TypeInstance findTypeArgument(
            TypeInstance typeInstance, String typeVariableName, Set<TypeInstance> visited) throws BadBytecode {
        if (!visited.add(typeInstance)) {
            return null;
        }

        SignatureAttribute.ClassSignature signature = getGenericClassSignature(typeInstance.declaration());
        if (signature != null) {
            SignatureAttribute.TypeParameter[] parameters = signature.getParameters();
            for (int i = 0; i < parameters.length && i < typeInstance.arguments().size(); ++i) {
                if (parameters[i].getName().equals(typeVariableName)) {
                    return typeInstance.arguments().get(i);
                }
            }
        }

        if (typeInstance.owner() != null) {
            TypeInstance result = findTypeArgument(typeInstance.owner(), typeVariableName, visited);
            if (result != null) {
                return result;
            }
        }

        for (TypeInstance superType : typeInstance.superTypes()) {
            TypeInstance result = findTypeArgument(superType, typeVariableName, visited);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private @Nullable TypeInstance findInvoker(TypeDeclaration invokingClass, TypeInstance potentialInvoker) {
        if (potentialInvoker.equals(invokingClass)) {
            return potentialInvoker;
        }

        TypeInstance owner = potentialInvoker.owner();
        if (owner != null) {
            TypeInstance result = findInvoker(invokingClass, owner);
            if (result != null) {
                return result;
            }
        }

        for (TypeInstance superType : potentialInvoker.superTypes()) {
            potentialInvoker = findInvoker(invokingClass, superType);
            if (potentialInvoker != null) {
                return potentialInvoker;
            }
        }

        return null;
    }

    private TypeDeclaration resolveDeclaration(String fullyQualifiedName) {
        if (resolver == null) {
            resolver = new Resolver(sourceInfo, cacheEnabled);
        }

        return resolver.resolveClass(withoutTypeArguments(fullyQualifiedName));
    }

    private String withoutTypeArguments(String name) {
        StringBuilder result = null;
        int depth = 0;

        for (int i = 0; i < name.length(); ++i) {
            char ch = name.charAt(i);
            if (ch == '<') {
                if (result == null) {
                    result = new StringBuilder(name.length());
                    result.append(name, 0, i);
                }

                ++depth;
            } else if (ch == '>') {
                --depth;
            } else if (depth == 0 && result != null) {
                result.append(ch);
            }
        }

        return result != null ? result.toString() : name;
    }

    private static Cache getCache() {
        return (Cache)CompilationContext.getCurrent().computeIfAbsent(TypeInvoker.class, key -> new Cache());
    }
}

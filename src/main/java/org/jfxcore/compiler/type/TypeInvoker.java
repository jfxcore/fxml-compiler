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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.jfxcore.compiler.type.Types.*;

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

            Map<String, TypeInstance> providedArguments = associateProvidedArguments(
                clazz, arguments, classSignature.getParameters(), EMPTY_TYPE_PARAMS);

            TypeInstance invokedType = invokeType(clazz.declaringType().orElse(null), clazz, providedArguments);
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
                    associateProvidedArguments(
                        method,
                        providedArguments,
                        methodSignature.getTypeParameters()));
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
                    var typeInst = invokeType(params.get(i).type());
                    result[i] = new TypeInstance(
                        typeInst.declaration(), List.of(), typeInst.superTypes(), typeInst.wildcardType());
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

                for (TypeInstance superType : typeInstance.superTypes()) {
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

        TypeDeclaration declaringClass = behavior.declaringType();
        SignatureAttribute.ClassSignature classSignature = getGenericClassSignature(declaringClass);
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

    private Map<String, TypeInstance> associateProvidedArguments(
            TypeDeclaration type,
            List<TypeInstance> providedArguments,
            SignatureAttribute.TypeParameter[] classTypeParams,
            SignatureAttribute.TypeParameter[] methodTypeParams)
                throws BadBytecode {
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
            BehaviorDeclaration behavior,
            List<TypeInstance> providedArguments,
            SignatureAttribute.TypeParameter[] methodTypeParams)
                throws BadBytecode {
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
                throws BadBytecode {
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

    private TypeInstance invokeType(TypeDeclaration invokingClass, TypeDeclaration invokedClass,
                                    Map<String, TypeInstance> providedArguments)
            throws BadBytecode {
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

                typeInstance = new TypeInstance(clazz, arguments, new ArrayList<>(), wildcard);
                List<TypeInstance> extendedInvocationContext = new ArrayList<>(invocationContext.size() + 1);
                extendedInvocationContext.addAll(invocationContext);
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
                typeInstance = new TypeInstance(clazz, Collections.emptyList(), new ArrayList<>(), wildcard);
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
            if (providedArguments.size() > 0) {
                TypeInstance result = providedArguments.get(typeVar.getName());
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

                List<TypeInstance> typeArgs = invokeTypeArguments(
                    argClass, classTypeArg, classTypeParams, methodTypeParams, invocationContext);

                for (int i = invocationContext.size() - 1; i >= 0; --i) {
                    TypeInstance instance = invocationContext.get(i);

                    if (instance.equals(argClass)
                            && instance.isRaw() == typeArgs.stream().anyMatch(TypeInstance::isRaw)
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
                TypeInstance invoker = findInvoker(invokingClass, invocationContext.get(invocationContext.size() - 1));
                if (invoker == null || invoker.arguments().isEmpty()) {
                    continue;
                }

                return invoker.arguments().get(i);
            }

            return typeParam.getClassBound() != null ?
                invokeType(
                    invokingClass, typeParam.getClassBound(), TypeInstance.WildcardType.NONE, classTypeParams,
                    methodTypeParams, invocationContext, Map.of()) :
                new TypeInstance(
                    resolveDeclaration(typeParam.getInterfaceBound()[0].jvmTypeName()),
                    List.of(), List.of(), TypeInstance.WildcardType.NONE);
        }

        return null;
    }

    private @Nullable TypeInstance findInvoker(TypeDeclaration invokingClass, TypeInstance potentialInvoker) {
        if (potentialInvoker.equals(invokingClass)) {
            return potentialInvoker;
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

        return resolver.resolveClass(fullyQualifiedName);
    }

    private static Cache getCache() {
        return (Cache)CompilationContext.getCurrent().computeIfAbsent(TypeInvoker.class, key -> new Cache());
    }
}

// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.SyntheticAttribute;
import javassist.bytecode.annotation.Annotation;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public class Resolver {

    private final SourceInfo sourceInfo;
    private final boolean cacheEnabled;

    public Resolver(SourceInfo sourceInfo) {
        this.sourceInfo = sourceInfo;
        this.cacheEnabled = true;
    }

    public Resolver(SourceInfo sourceInfo, boolean cacheEnabled) {
        this.sourceInfo = sourceInfo;
        this.cacheEnabled = cacheEnabled;
    }

    /**
     * Returns the class for the specified fully-qualified type name.
     *
     * @throws MarkupException if the class was not found.
     */
    public CtClass resolveClass(String fullyQualifiedName) {
        CtClass ctclass = tryResolveClass(fullyQualifiedName);
        if (ctclass != null) {
            return ctclass;
        }

        throw SymbolResolutionErrors.classNotFound(sourceInfo, fullyQualifiedName);
    }

    /**
     * Returns the class for the specified fully-qualified type name, or <code>null</code> if the class was not found.
     */
    public CtClass tryResolveClass(String fullyQualifiedName) {
        int dimensions = 0;

        while (fullyQualifiedName.endsWith("[]")) {
            fullyQualifiedName = fullyQualifiedName.substring(0, fullyQualifiedName.length() - 2);
            ++dimensions;
        }

        int generic = fullyQualifiedName.indexOf('<');
        if (generic >= 0) {
            fullyQualifiedName = fullyQualifiedName.substring(0, generic);
        }

        String[] parts;
        String className = fullyQualifiedName;

        do {
            try {
                return CompilationContext.getCurrent().getClassPool().get(className + "[]".repeat(dimensions));
            } catch (NotFoundException ignored) {
            }

            parts = className.split("\\.");
            className = java.lang.String.join(
                ".", Arrays.copyOf(parts, parts.length - 1)) + "$" + parts[parts.length - 1];
        } while (parts.length > 1);

        return null;
    }

    public CtClass tryResolveNestedClass(CtClass enclosingType, String nestedTypeName) {
        while (enclosingType != null) {
            CtClass ctclass = tryResolveClass(enclosingType.getName() + "." + nestedTypeName);
            if (ctclass != null) {
                return ctclass;
            }

            try {
                enclosingType = enclosingType.getSuperclass();
            } catch (NotFoundException e) {
                throw SymbolResolutionErrors.classNotFound(sourceInfo, e.getMessage());
            }
        }

        return null;
    }

    /**
     * Returns the class for the specified type name.
     * If the type name is fully qualified, it is resolved directly; otherwise the name is resolved
     * against the document's import list.
     *
     * @throws MarkupException if the class was not found.
     */
    public CtClass resolveClassAgainstImports(String typeName) {
        CtClass ctclass = tryResolveClassAgainstImports(typeName);
        if (ctclass == null) {
            throw SymbolResolutionErrors.classNotFound(sourceInfo, typeName);
        }

        return ctclass;
    }

    /**
     * Returns the class for the specified unqualified type name, or <code>null</code> if the class was not found.
     */
    public CtClass tryResolveClassAgainstImports(String typeName) {
        List<String> imports = CompilationContext.getCurrent().getImports();

        for (String potentialClassName : getPotentialClassNames(imports, typeName)) {
            CtClass ctclass = tryResolveClass(potentialClassName);
            if (ctclass != null) {
                return ctclass;
            }
        }

        return null;
    }

    private List<String> getPotentialClassNames(Collection<String> imports, String typeName) {
        for (String imp : imports) {
            if (imp.endsWith("." + typeName)) {
                int lastDot = imp.lastIndexOf(".");
                return List.of(typeName, imp, imp.substring(0, lastDot) + "$" + imp.substring(lastDot + 1));
            }
        }

        List<String> res = new ArrayList<>();
        res.add(typeName);
        boolean javaLang = false;

        for (String imp : imports) {
            if (!javaLang && imp.equals("java.lang.*")) {
                javaLang = true;
            }

            if (imp.endsWith(".*")) {
                res.add(imp.replace("*", "") + typeName);
            }
        }

        if (!javaLang) {
            res.add("java.lang." + typeName);
        }

        return res;
    }

    public PropertyInfo resolveProperty(TypeInstance declaringClass, boolean allowQualifiedName, String... names) {
        PropertyInfo propertyInfo = tryResolveProperty(declaringClass, allowQualifiedName, names);
        if (propertyInfo == null) {
            throw SymbolResolutionErrors.propertyNotFound(
                sourceInfo, declaringClass.jvmType(), String.join(".", names));
        }

        return propertyInfo;
    }

    public PropertyInfo tryResolveProperty(TypeInstance declaringClass, boolean allowQualifiedName, String... names) {
        CacheKey key = new CacheKey("tryResolveProperty", declaringClass, allowQualifiedName, names);
        CacheEntry entry = getCache().get(key);
        if (entry.found() && cacheEnabled) {
            return (PropertyInfo)entry.value();
        }

        var invoker = new TypeInvoker(sourceInfo, cacheEnabled);
        boolean validLocalName = true;
        String propertyName = names[names.length - 1];

        if (names.length > 1) {
            TypeInstance receiverClass = declaringClass;
            String className = String.join(".", Arrays.copyOf(names, names.length - 1));
            CtClass clazz = tryResolveClassAgainstImports(className);
            if (clazz != null) {
                TypeInstance type = invoker.invokeType(clazz);
                validLocalName = allowQualifiedName && declaringClass.subtypeOf(type);
                declaringClass = type;
            } else {
                return null;
            }

            PropertyInfo propertyInfo = tryResolveStaticPropertyImpl(declaringClass, receiverClass, propertyName);
            if (propertyInfo != null) {
                return getCache().put(key, propertyInfo);
            }
        }

        // A regular (non-static) property needs a valid local name, otherwise we don't accept it.
        if (!validLocalName) {
            return getCache().put(key, null);
        }

        GetterInfo propertyGetter = tryResolvePropertyGetter(declaringClass.jvmType(), null, propertyName);
        TypeInstance typeInstance = null;
        TypeInstance observableTypeInstance = null;

        // If we have a property getter, extract the type of the generic parameter.
        // For primitive wrappers like ObservableBooleanValue, we define the type of the property
        // to be the primitive value type (i.e. not the boxed value type).
        if (propertyGetter != null) {
            observableTypeInstance = invoker.invokeReturnType(propertyGetter.method(), List.of(declaringClass));
            typeInstance = findObservableArgument(observableTypeInstance);
        }

        // Look for a getter that matches the previously determined property type.
        // If we didn't find a property getter in the previous step, this selects the first getter that
        // matches by name, or in case this is a static property, matches by name and signature.
        // We don't look for a getter if we matched the property getter verbatim, as this indicates that
        // the JavaFX Beans naming schema was not used, or the "Property" suffix was specified explicitly.
        CtMethod getter = null;
        if (propertyGetter == null || !propertyGetter.verbatim()) {
            getter = tryResolveGetter(declaringClass.jvmType(), propertyName, false,
                                      typeInstance != null ? typeInstance.jvmType() : null);
        }

        try {
            if (typeInstance == null && getter != null) {
                typeInstance = invoker.invokeReturnType(getter, List.of(declaringClass));
            }

            // If we still haven't been able to determine a property type, this means that we haven't
            // found a property getter, nor a regular getter. This means the property doesn't exist.
            if (typeInstance == null) {
                return getCache().put(key, null);
            }

            // We only look for a setter if we have a getter (i.e. write-only properties don't exist).
            CtMethod setter = null;
            if (getter != null) {
                setter = tryResolveSetter(declaringClass.jvmType(), propertyName, false, getter.getReturnType());
            }

            PropertyInfo propertyInfo = new PropertyInfo(
                propertyName, propertyGetter != null ? propertyGetter.method() : null, getter, setter,
                typeInstance, observableTypeInstance, declaringClass, false);

            return getCache().put(key, propertyInfo);
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.classNotFound(sourceInfo, ex.getMessage());
        }
    }

    private PropertyInfo tryResolveStaticPropertyImpl(TypeInstance declaringType, TypeInstance receiverType,
                                                      String propertyName) {
        try {
            TypeInvoker invoker = new TypeInvoker(sourceInfo, cacheEnabled);
            CtMethod getter = tryResolveStaticGetter(declaringType.jvmType(), receiverType.jvmType(), propertyName, false);
            GetterInfo propertyGetter = tryResolvePropertyGetter(declaringType.jvmType(), receiverType.jvmType(), propertyName);
            TypeInstance observableType = null;

            if (propertyGetter != null) {
                try {
                    observableType = invoker.invokeReturnType(
                        propertyGetter.method(), List.of(declaringType), List.of(receiverType));
                } catch (MarkupException ex) {
                    if (ex.getDiagnostic().getCode() != ErrorCode.NUM_TYPE_ARGUMENTS_MISMATCH) {
                        throw ex;
                    }

                    observableType = invoker.invokeReturnType(propertyGetter.method(), List.of(declaringType));
                }
            }

            TypeInstance propertyType = observableType != null ? findObservableArgument(observableType) : null;

            // If the return type of the getter doesn't match the type of the property getter,
            // we discard the getter and only use the property getter.
            if (propertyType != null && getter != null
                    && !propertyType.equals(invoker.invokeReturnType(getter, List.of(declaringType)))) {
                getter = null;
            }

            // We also discard the getter if the property getter matched verbatim, as this indicates
            // that the JavaFX Beans naming scheme was not used.
            if (propertyGetter != null && propertyGetter.verbatim()) {
                getter = null;
            }

            if (propertyGetter == null && getter != null) {
                try {
                    propertyType = invoker.invokeReturnType(getter, List.of(declaringType), List.of(receiverType));
                } catch (MarkupException ex) {
                    if (ex.getDiagnostic().getCode() != ErrorCode.NUM_TYPE_ARGUMENTS_MISMATCH) {
                        throw ex;
                    }

                    propertyType = invoker.invokeReturnType(getter, List.of(declaringType));
                }
            }

            CtMethod setter = null;
            if (propertyType != null && getter != null) {
                setter = tryResolveStaticSetter(declaringType.jvmType(), receiverType.jvmType(),
                                                propertyName, false, propertyType.jvmType());
            }

            if (propertyGetter == null && getter == null) {
                return null;
            }

            return new PropertyInfo(
                propertyName, propertyGetter != null ? propertyGetter.method() : null, getter, setter,
                propertyType, observableType, declaringType, true);
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.classNotFound(sourceInfo, ex.getMessage());
        }
    }

    /**
     * Returns the public field with the specified name.
     */
    public CtField resolveField(CtClass ctclass, String name) {
        CtField field = tryResolveField(ctclass, name);
        if (field == null) {
            throw SymbolResolutionErrors.memberNotFound(sourceInfo, ctclass, name);
        }

        return field;
    }

    /**
     * Returns the field with the specified name.
     */
    public CtField resolveField(CtClass ctclass, String name, boolean publicOnly) {
        CtField field = tryResolveField(ctclass, name, publicOnly);
        if (field == null) {
            throw SymbolResolutionErrors.memberNotFound(sourceInfo, ctclass, name);
        }

        return field;
    }

    /**
     * Returns the public field with the specified name, or {@code null} if no such public field can be found.
     */
    public CtField tryResolveField(CtClass ctclass, String name) {
        return tryResolveField(ctclass, name, true);
    }

    /**
     * Returns the field with the specified name, or {@code null} if no such field can be found.
     */
    public CtField tryResolveField(CtClass ctclass, String name, boolean publicOnly) {
        for (CtField field : ctclass.getDeclaredFields()) {
            if (publicOnly && Modifier.isPrivate(field.getModifiers())) {
                continue;
            }

            if (field.getName().equals(name)) {
                return field;
            }
        }

        CtClass superclass;
        try {
            superclass = ctclass.getSuperclass();
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.classNotFound(sourceInfo, ex.getMessage());
        }

        if (superclass != null) {
            return tryResolveField(superclass, name, publicOnly);
        }

        return null;
    }

    /**
     * Returns the method that satisfies the predicate, or {@code null} if no such method can be found.
     */
    public CtMethod tryResolveMethod(CtClass ctclass, Predicate<CtMethod> predicate) {
        for (CtMethod method : ctclass.getDeclaredMethods()) {
            if (!Modifier.isPrivate(method.getModifiers()) && !isSynthetic(method) && predicate.test(method)) {
                return method;
            }
        }

        try {
            var superClass = ctclass.getSuperclass();
            return superClass != null ? tryResolveMethod(superClass, predicate) : null;
        } catch (NotFoundException ex) {
            return null;
        }
    }

    /**
     * Returns all methods that satisfy the predicate.
     */
    public CtMethod[] resolveMethods(CtClass ctclass, Predicate<CtMethod> predicate) {
        List<CtMethod> methods = new ArrayList<>();
        CtClass clazz = ctclass;

        while (clazz != null) {
            for (CtMethod method : clazz.getDeclaredMethods()) {
                if (!Modifier.isPrivate(method.getModifiers()) && !isSynthetic(method) && predicate.test(method)) {
                    methods.add(method);
                }
            }

            try {
                clazz = clazz.getSuperclass();
            } catch (NotFoundException ex) {
                clazz = null;
            }
        }

        return methods.toArray(new CtMethod[0]);
    }

    /**
     * Returns a getter method with a name corresponding to the specified name.
     *
     * If {@code verbatim} is true, only returns methods that match the name exactly; otherwise, also returns getter
     * methods with names like getMethodName or isMethodName.
     *
     * If {@code returnType} is non-null, only getter methods returning this type are considered.
     */
    public CtMethod resolveGetter(CtClass type, String name, boolean verbatim, @Nullable CtClass returnType) {
        CtMethod method = tryResolveGetter(type, name, verbatim, returnType);
        if (method == null) {
            throw SymbolResolutionErrors.memberNotFound(sourceInfo, type, name);
        }

        return method;
    }

    /**
     * Returns a getter method with a name corresponding to the specified name.
     *
     * If {@code verbatim} is true, only returns methods that match the name exactly; otherwise, also returns getter
     * methods with names like getMethodName or isMethodName.
     *
     * If {@code returnType} is non-null, only getter methods returning this type are considered.
     *
     * @return The method, or {@code null} if no getter can be found.
     */
    public CtMethod tryResolveGetter(CtClass type, String name, boolean verbatim, @Nullable CtClass returnType) {
        CacheKey key = new CacheKey("tryResolveGetter", type, name, verbatim, returnType);
        CacheEntry entry = getCache().get(key);
        if (entry.found() && cacheEnabled) {
            return (CtMethod)entry.value();
        }

        String alt0, alt1;

        if (!verbatim && Character.isLowerCase(name.charAt(0))) {
            alt0 = NameHelper.getGetterName(name, false);
            alt1 = NameHelper.getGetterName(name, true);
        } else {
            alt0 = null;
            alt1 = null;
        }

        CtMethod method = findMethod(type, m -> {
            try {
                CtClass ret = m.getReturnType();

                if (Modifier.isPublic(m.getModifiers())
                        && (m.getName().equals(name) || (!verbatim && (m.getName().equals(alt0) || m.getName().equals(alt1))))
                        && m.getParameterTypes().length == 0
                        && !TypeHelper.equals(ret, CtClass.voidType)
                        && (returnType == null || TypeHelper.equals(returnType, ret))
                        && !isSynthetic(m)) {
                    return true;
                }
            } catch (NotFoundException ignored) {
            }

            return false;
        });

        getCache().put(key, method);
        return method;
    }

    /**
     * Returns a setter method with a name corresponding to the specified name.
     *
     * If {@code verbatim} is true, only returns methods that match the name exactly; otherwise, also returns setter
     * methods with names like setMethodName.
     *
     * If {@code paramType} is non-null, only setter methods accepting this parameter type are considered.
     *
     * @return The method, or {@code null} if no setter can be found.
     */
    public CtMethod tryResolveSetter(CtClass type, String name, boolean verbatim, @Nullable CtClass paramType) {
        CacheKey key = new CacheKey("tryResolveSetter", type, name, verbatim, paramType);
        CacheEntry entry = getCache().get(key);
        if (entry.found() && cacheEnabled) {
            return (CtMethod)entry.value();
        }

        String setterName;

        if (!verbatim && Character.isLowerCase(name.charAt(0))) {
            setterName = NameHelper.getSetterName(name);
        } else {
            setterName = null;
        }

        CtMethod method = findMethod(type, m -> {
            try {
                if (Modifier.isPublic(m.getModifiers())
                        && (m.getName().equals(name) || (!verbatim && m.getName().equals(setterName)))
                        && m.getParameterTypes().length == 1
                        && TypeHelper.equals(m.getReturnType(), CtClass.voidType)
                        && (paramType == null || TypeHelper.equals(paramType, m.getParameterTypes()[0]))
                        && !isSynthetic(m)) {
                    return true;
                }
            } catch (NotFoundException ignored) {
            }

            return false;
        });

        getCache().put(key, method);
        return method;
    }

    /**
     * Returns a static property setter method with a name corresponding to the specified name.
     *
     * If {@code verbatim} is true, only returns methods that match the name exactly; otherwise, also returns setter
     * methods with names like setMethodName.
     *
     * If {@code paramType} is non-null, only setter methods accepting this parameter type are considered.
     *
     * @return The method, or {@code null} if no setter can be found.
     */
    public CtMethod tryResolveStaticSetter(
            CtClass declaringClass, CtClass receiverClass, String name, boolean verbatim, @Nullable CtClass paramType) {
        CacheKey key = new CacheKey("tryResolveStaticSetter", declaringClass, receiverClass, name, paramType);
        CacheEntry entry = getCache().get(key);
        if (entry.found() && cacheEnabled) {
            return (CtMethod)entry.value();
        }

        String setterName;

        if (!verbatim && Character.isLowerCase(name.charAt(0))) {
            setterName = NameHelper.getSetterName(name);
        } else {
            setterName = null;
        }

        CtMethod method = findMethod(declaringClass, m -> {
            try {
                if (Modifier.isPublic(m.getModifiers())
                        && (m.getName().equals(name) || (!verbatim && m.getName().equals(setterName)))
                        && m.getParameterTypes().length == 2
                        && receiverClass.subtypeOf(m.getParameterTypes()[0])
                        && TypeHelper.equals(m.getReturnType(), CtClass.voidType)
                        && (paramType == null || TypeHelper.equals(m.getParameterTypes()[1], paramType))
                        && !isSynthetic(m)) {
                    return true;
                }
            } catch (NotFoundException ignored) {
            }

            return false;
        });

        getCache().put(key, method);
        return method;
    }

    /**
     * Returns the getter for the specified static property (i.e. a method like GridPane.getRowIndex).
     *
     * @param declaringClass the class that declares the getter method
     * @param receiverClass the parameter of the getter method (usually a subclass of Node)
     * @param name the name of the property
     * @param verbatim if {@code true}, only methods matching the name exactly are considered
     * @return The method, or {@code null} if no getter can be found.
     */
    public CtMethod tryResolveStaticGetter(
            CtClass declaringClass, CtClass receiverClass, String name, boolean verbatim) {
        CacheKey key = new CacheKey("tryResolveStaticGetter", declaringClass, name, verbatim);
        CacheEntry entry = getCache().get(key);
        if (entry.found() && cacheEnabled) {
            return (CtMethod)entry.value();
        }

        String alt0, alt1;

        if (!verbatim && Character.isLowerCase(name.charAt(0))) {
            alt0 = NameHelper.getGetterName(name, false);
            alt1 = NameHelper.getGetterName(name, true);
        } else {
            alt0 = null;
            alt1 = null;
        }

        CtMethod method = findMethod(declaringClass, m -> {
            int modifiers = m.getModifiers();

            try {
                if (Modifier.isPublic(modifiers)
                        && Modifier.isStatic(modifiers)
                        && (m.getName().equals(name) || (!verbatim && (m.getName().equals(alt0) || m.getName().equals(alt1))))
                        && m.getParameterTypes().length == 1
                        && receiverClass.subtypeOf(m.getParameterTypes()[0])
                        && !TypeHelper.equals(m.getReturnType(), CtClass.voidType)
                        && !isSynthetic(m)) {
                    return true;
                }
            } catch (NotFoundException ignored) {
            }

            return false;
        });

        getCache().put(key, method);
        return method;
    }

    private record GetterInfo(CtMethod method, boolean verbatim) {}

    /**
     * Returns a property getter method for the specified property (i.e. a method like Button.textProperty()).
     *
     * @return The method, or {@code null} if no getter can be found.
     */
    private GetterInfo tryResolvePropertyGetter(
            CtClass declaringClass, @Nullable CtClass receiverClass, String name) {
        CacheKey key = new CacheKey("tryResolvePropertyGetter", declaringClass, receiverClass, name);
        CacheEntry entry = getCache().get(key);
        if (entry.found() && cacheEnabled) {
            return (GetterInfo)entry.value();
        }

        List<String> potentialNames = new ArrayList<>(4);
        potentialNames.add(name);
        potentialNames.add(NameHelper.getPropertyGetterName(name));

        if (Character.isLowerCase(name.charAt(0))) {
            potentialNames.add(NameHelper.getGetterName(name, false));
            potentialNames.add(NameHelper.getGetterName(name, true));
        }

        CtMethod method = potentialNames.stream()
            .map(n -> resolvePropertyGetterImpl(declaringClass, receiverClass, n))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

        GetterInfo getter = method != null ? new GetterInfo(method, method.getName().equals(name)) : null;
        getCache().put(key, getter);
        return getter;
    }

    private CtMethod resolvePropertyGetterImpl(
            CtClass declaringClass, @Nullable CtClass receiverClass, String methodName) {
        return findMethod(declaringClass, m -> {
            int modifiers = m.getModifiers();

            try {
                if (Modifier.isPublic(modifiers)
                        && (receiverClass == null || Modifier.isStatic(modifiers))
                        && m.getName().equals(methodName)
                        && m.getReturnType().subtypeOf(Classes.ObservableValueType())
                        && !isSynthetic(m)) {
                    CtClass[] paramTypes = m.getParameterTypes();

                    if (receiverClass != null) {
                        return paramTypes.length == 1 && receiverClass.subtypeOf(paramTypes[0]);
                    } else {
                        return paramTypes.length == 0;
                    }
                }
            } catch (NotFoundException ignored) {
            }

            return false;
        });
    }

    /**
     * Returns the class annotation for the specified name, or <code>null</code> if the annotation was not found.
     */
    public Annotation tryResolveClassAnnotation(CtClass type, String annotationName) {
        CacheKey key = new CacheKey("tryResolveClassAnnotation", type, annotationName);
        CacheEntry entry = getCache().get(key);
        if (entry.found() && cacheEnabled) {
            return (Annotation)entry.value();
        }

        Annotation annotation = tryResolveClassAnnotationRecursive(type, annotationName, true);
        if (annotation == null) {
            annotation = tryResolveClassAnnotationRecursive(type, annotationName, false);
        }

        getCache().put(key, annotation);
        return annotation;
    }

    private Annotation tryResolveClassAnnotationRecursive(CtClass type, String annotationName, boolean runtimeVisible) {
        AnnotationsAttribute attr = null;

        try {
            attr = (AnnotationsAttribute)type.getClassFile().getAttribute(
                runtimeVisible ? AnnotationsAttribute.visibleTag : AnnotationsAttribute.invisibleTag);

            if (attr == null || attr.getAnnotation(annotationName) == null) {
                try {
                    CtClass superclass = type.getSuperclass();
                    if (superclass != null) {
                        return tryResolveClassAnnotationRecursive(superclass, annotationName, runtimeVisible);
                    }
                } catch (NotFoundException ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        return attr != null ? attr.getAnnotation(annotationName) : null;
    }

    /**
     * Returns the method annotation for the specified name, or <code>null</code> if the annotation was not found.
     */
    public Annotation tryResolveMethodAnnotation(CtBehavior method, String annotationName, boolean simpleName) {
        AnnotationsAttribute attr = (AnnotationsAttribute)method
            .getMethodInfo2()
            .getAttribute(AnnotationsAttribute.visibleTag);

        if (attr == null) {
            attr = (AnnotationsAttribute)method
                .getMethodInfo2()
                .getAttribute(AnnotationsAttribute.invisibleTag);
        }

        if (attr == null){
            return null;
        }

        if (simpleName) {
            for (Annotation annotation : attr.getAnnotations()) {
                String[] names = annotation.getTypeName().split("\\.");
                if (names[names.length - 1].equals(annotationName)) {
                    return annotation;
                }
            }
        }

        return attr.getAnnotation(annotationName);
    }

    public CtClass getWritableClass(CtClass type, boolean requestProperty) {
        try {
            if (type == CtClass.booleanType || type == Classes.BooleanType()
                    || type.subtypeOf(Classes.WritableBooleanValueType())) {
                return requestProperty && type.subtypeOf(Classes.BooleanPropertyType()) ?
                    Classes.BooleanPropertyType() : Classes.WritableBooleanValueType();
            } else if (type == CtClass.intType || type == Classes.IntegerType()
                    || type == CtClass.shortType || type == Classes.ShortType()
                    || type == CtClass.byteType || type == Classes.ByteType()
                    || type == CtClass.charType || type == Classes.CharacterType()
                    || type.subtypeOf(Classes.WritableIntegerValueType())) {
                return requestProperty && type.subtypeOf(Classes.IntegerPropertyType()) ?
                    Classes.IntegerPropertyType() : Classes.WritableIntegerValueType();
            } else if (type == CtClass.longType || type == Classes.LongType()
                    || type.subtypeOf(Classes.WritableLongValueType())) {
                return requestProperty && type.subtypeOf(Classes.LongPropertyType()) ?
                    Classes.LongPropertyType() : Classes.WritableLongValueType();
            } else if (type == CtClass.floatType || type == Classes.FloatType()
                    || type.subtypeOf(Classes.WritableFloatValueType())) {
                return requestProperty && type.subtypeOf(Classes.FloatPropertyType()) ?
                    Classes.FloatPropertyType() : Classes.WritableFloatValueType();
            } else if (type == CtClass.doubleType || type == Classes.DoubleType()
                    || type.subtypeOf(Classes.WritableDoubleValueType())) {
                return requestProperty && type.subtypeOf(Classes.DoublePropertyType()) ?
                    Classes.DoublePropertyType() : Classes.WritableDoubleValueType();
            } else {
                return requestProperty && type.subtypeOf(Classes.PropertyType()) ?
                    Classes.PropertyType() : Classes.WritableValueType();
            }
        } catch (NotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

    public CtClass getObservableClass(CtClass type, boolean requestProperty) {
        try {
            if (type == CtClass.booleanType || type == Classes.BooleanType()
                    || type.subtypeOf(Classes.ObservableBooleanValueType())) {
                return requestProperty && type.subtypeOf(Classes.BooleanPropertyType()) ?
                    Classes.BooleanPropertyType() : Classes.ObservableBooleanValueType();
            } else if (type == CtClass.intType || type == Classes.IntegerType()
                    || type == CtClass.shortType || type == Classes.ShortType()
                    || type == CtClass.byteType || type == Classes.ByteType()
                    || type == CtClass.charType || type == Classes.CharacterType()
                    || type.subtypeOf(Classes.ObservableIntegerValueType())) {
                return requestProperty && type.subtypeOf(Classes.IntegerPropertyType()) ?
                    Classes.IntegerPropertyType() : Classes.ObservableIntegerValueType();
            } else if (type == CtClass.longType || type == Classes.LongType()
                    || type.subtypeOf(Classes.ObservableLongValueType())) {
                return requestProperty && type.subtypeOf(Classes.LongPropertyType()) ?
                    Classes.LongPropertyType() : Classes.ObservableLongValueType();
            } else if (type == CtClass.floatType || type == Classes.FloatType()
                    || type.subtypeOf(Classes.ObservableFloatValueType())) {
                return requestProperty && type.subtypeOf(Classes.FloatPropertyType()) ?
                    Classes.FloatPropertyType() : Classes.ObservableFloatValueType();
            } else if (type == CtClass.doubleType || type == Classes.DoubleType()
                    || type.subtypeOf(Classes.ObservableDoubleValueType())) {
                return requestProperty && type.subtypeOf(Classes.DoublePropertyType()) ?
                    Classes.DoublePropertyType() : Classes.ObservableDoubleValueType();
            } else {
                return requestProperty && type.subtypeOf(Classes.PropertyType()) ?
                    Classes.PropertyType() : Classes.ObservableValueType();
            }
        } catch (NotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

    public TypeInstance getObservableClass(TypeInstance type) {
        var invoker = new TypeInvoker(sourceInfo, cacheEnabled);

        if (type.equals(CtClass.booleanType) || type.equals(Classes.BooleanType())) {
            return invoker.invokeType(Classes.ObservableBooleanValueType());
        } else if (type.equals(CtClass.intType) || type.equals(Classes.IntegerType())
                || type.equals(CtClass.shortType) || type.equals(Classes.ShortType())
                || type.equals(CtClass.byteType) || type.equals(Classes.ByteType())
                || type.equals(CtClass.charType) || type.equals(Classes.CharacterType())) {
            return invoker.invokeType(Classes.ObservableIntegerValueType());
        } else if (type.equals(CtClass.longType) || type.equals(Classes.LongType())) {
            return invoker.invokeType(Classes.ObservableLongValueType());
        } else if (type.equals(CtClass.floatType) || type.equals(Classes.FloatType())) {
            return invoker.invokeType(Classes.ObservableFloatValueType());
        } else if (type.equals(CtClass.doubleType) || type.equals(Classes.DoubleType())) {
            return invoker.invokeType(Classes.ObservableDoubleValueType());
        } else {
            return invoker.invokeType(Classes.ObservableValueType(), List.of(type));
        }
    }

    public TypeInstance tryFindArgument(TypeInstance actualType, CtClass targetType) {
        if (TypeHelper.equals(actualType.jvmType(), targetType)) {
            return actualType.getArguments().size() == 0 ? null : actualType.getArguments().get(0);
        }

        for (TypeInstance superType : actualType.getSuperTypes()) {
            TypeInstance argType = tryFindArgument(superType, targetType);
            if (argType != null) {
                return argType;
            }
        }

        return null;
    }

    public TypeInstance findObservableArgument(TypeInstance propertyType) {
        TypeInstance typeArg = tryFindObservableArgument(propertyType);
        if (typeArg == null) {
            throw new IllegalArgumentException("propertyType");
        }

        return typeArg;
    }

    public @Nullable TypeInstance tryFindObservableArgument(TypeInstance propertyType) {
        if (propertyType.subtypeOf(Classes.ObservableBooleanValueType())) {
            return TypeInstance.booleanType();
        } else if (propertyType.subtypeOf(Classes.ObservableIntegerValueType())) {
            return TypeInstance.intType();
        } else if (propertyType.subtypeOf(Classes.ObservableLongValueType())) {
            return TypeInstance.longType();
        } else if (propertyType.subtypeOf(Classes.ObservableFloatValueType())) {
            return TypeInstance.floatType();
        } else if (propertyType.subtypeOf(Classes.ObservableDoubleValueType())) {
            return TypeInstance.doubleType();
        } else if (TypeHelper.equals(propertyType.jvmType(), Classes.ObservableValueType())) {
            return propertyType.getArguments().isEmpty() ?
                TypeInstance.ObjectType() : propertyType.getArguments().get(0);
        }

        for (TypeInstance superType : propertyType.getSuperTypes()) {
            TypeInstance argType = tryFindObservableArgument(superType);
            if (argType != null) {
                return argType;
            }
        }

        return null;
    }

    public TypeInstance findWritableArgument(TypeInstance propertyType) {
        TypeInstance typeArg = tryFindWritableArgument(propertyType);
        if (typeArg == null) {
            throw new IllegalArgumentException("propertyType");
        }

        return typeArg;
    }

    public @Nullable TypeInstance tryFindWritableArgument(TypeInstance propertyType) {
        if (propertyType.subtypeOf(Classes.WritableBooleanValueType())) {
            return TypeInstance.booleanType();
        } else if (propertyType.subtypeOf(Classes.WritableIntegerValueType())) {
            return TypeInstance.intType();
        } else if (propertyType.subtypeOf(Classes.WritableLongValueType())) {
            return TypeInstance.longType();
        } else if (propertyType.subtypeOf(Classes.WritableFloatValueType())) {
            return TypeInstance.floatType();
        } else if (propertyType.subtypeOf(Classes.WritableDoubleValueType())) {
            return TypeInstance.doubleType();
        } else if (TypeHelper.equals(propertyType.jvmType(), Classes.WritableValueType())) {
            return propertyType.getArguments().isEmpty() ?
                TypeInstance.ObjectType() : propertyType.getArguments().get(0);
        }

        for (TypeInstance superType : propertyType.getSuperTypes()) {
            TypeInstance argType = tryFindWritableArgument(superType);
            if (argType != null) {
                return argType;
            }
        }

        return null;
    }

    private CtMethod findMethod(CtClass type, Function<CtMethod, Boolean> consumer) {
        for (CtMethod method : type.getDeclaredMethods()) {
            if (!isSynthetic(method) && consumer.apply(method)) {
                return method;
            }
        }

        try {
            for (CtClass intf : type.getInterfaces()) {
                CtMethod method = findMethod(intf, consumer);
                if (method != null) {
                    return method;
                }
            }
        } catch (NotFoundException ignored) {
        }

        try {
            type = type.getSuperclass();
            if (type != null) {
                CtMethod method = findMethod(type, consumer);
                if (method != null) {
                    return method;
                }
            }
        } catch (NotFoundException ignored) {
        }

        return null;
    }

    private static boolean isSynthetic(CtMethod method) {
        return (method.getModifiers() & AccessFlag.SYNTHETIC) != 0
            || method.getMethodInfo2().getAttribute(SyntheticAttribute.tag) != null;
    }

    private static final SignatureAttribute.TypeParameter[] EMPTY_TYPE_PARAMS =
            new SignatureAttribute.TypeParameter[0];

    private static Cache getCache() {
        return (Cache)CompilationContext.getCurrent().computeIfAbsent(Resolver.class, key -> new Cache());
    }
}

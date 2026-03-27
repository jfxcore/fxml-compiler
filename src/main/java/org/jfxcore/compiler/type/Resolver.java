// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.type;

import javassist.NotFoundException;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.PropertyInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.jfxcore.compiler.type.TypeSymbols.*;

public final class Resolver {

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
    public TypeDeclaration resolveClass(String fullyQualifiedName) {
        TypeDeclaration declaration = tryResolveClass(fullyQualifiedName);
        if (declaration != null) {
            return declaration;
        }

        throw SymbolResolutionErrors.classNotFound(sourceInfo, fullyQualifiedName);
    }

    /**
     * Returns the class for the specified fully-qualified type name, or <code>null</code> if the class was not found.
     */
    public @Nullable TypeDeclaration tryResolveClass(String fullyQualifiedName) {
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
                return TypeDeclaration.of(
                    CompilationContext.getCurrent().getClassPool().get(className + "[]".repeat(dimensions)));
            } catch (NotFoundException ignored) {
            }

            parts = className.split("\\.");
            className = java.lang.String.join(
                ".", Arrays.copyOf(parts, parts.length - 1)) + "$" + parts[parts.length - 1];
        } while (parts.length > 1);

        return null;
    }

    public @Nullable TypeDeclaration tryResolveNestedClass(TypeDeclaration enclosingType, String nestedTypeName) {
        while (enclosingType != null) {
            TypeDeclaration declaration = tryResolveClass(enclosingType.name() + "." + nestedTypeName);
            if (declaration != null) {
                return declaration;
            }

            enclosingType = enclosingType.superClass().orElse(null);
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
    public TypeDeclaration resolveClassAgainstImports(String typeName) {
        TypeDeclaration declaration = tryResolveClassAgainstImports(typeName);
        if (declaration == null) {
            throw SymbolResolutionErrors.classNotFound(sourceInfo, typeName);
        }

        return declaration;
    }

    /**
     * Returns the class for the specified unqualified type name, or <code>null</code> if the class was not found.
     */
    public @Nullable TypeDeclaration tryResolveClassAgainstImports(String typeName) {
        List<String> imports = CompilationContext.getCurrent().getImports();

        for (String potentialClassName : getPotentialClassNames(imports, typeName)) {
            TypeDeclaration declaration = tryResolveClass(potentialClassName);
            if (declaration != null) {
                return declaration;
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
                sourceInfo, declaringClass.declaration(), String.join(".", names));
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
            TypeDeclaration clazz = tryResolveClassAgainstImports(className);
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

        GetterInfo propertyGetter = tryResolvePropertyGetter(declaringClass.declaration(), null, propertyName);
        TypeInstance typeInstance = null;
        TypeInstance observableTypeInstance = null;

        // If we have a property getter, extract the type of the generic parameter.
        // For primitive wrappers like ObservableBooleanValue, we define the type of the property
        // to be the primitive value type (i.e. not the boxed value type).
        if (propertyGetter != null) {
            observableTypeInstance = invoker.invokeReturnType(
                propertyGetter.method(), List.of(declaringClass));
            typeInstance = findObservableArgument(observableTypeInstance);
        }

        // Look for a getter that matches the previously determined property type.
        // If we didn't find a property getter in the previous step, this selects the first getter that
        // matches by name, or in case this is a static property, matches by name and signature.
        // We don't look for a getter if we matched the property getter verbatim, as this indicates that
        // the JavaFX Beans naming schema was not used, or the "Property" suffix was specified explicitly.
        MethodDeclaration getter = null;
        if (propertyGetter == null || !propertyGetter.verbatim()) {
            getter = tryResolveGetter(declaringClass.declaration(), propertyName, false,
                                      typeInstance != null ? typeInstance.declaration() : null);
        }

        if (typeInstance == null && getter != null) {
            typeInstance = invoker.invokeReturnType(getter, List.of(declaringClass));
        }

        // If we still haven't been able to determine a property type, this means that we haven't
        // found a property getter, nor a regular getter. This means the property doesn't exist.
        if (typeInstance == null) {
            return getCache().put(key, null);
        }

        // We only look for a setter if we have a getter (i.e. write-only properties don't exist).
        MethodDeclaration setter = null;
        if (getter != null) {
            setter = tryResolveSetter(declaringClass.declaration(), propertyName, false, getter.returnType());
        }

        return getCache().put(key, new PropertyInfo(
            propertyName,
            propertyGetter != null ? propertyGetter.method() : null,
            getter,
            setter,
            typeInstance,
            observableTypeInstance,
            declaringClass,
            false));
    }

    private PropertyInfo tryResolveStaticPropertyImpl(TypeInstance declaringType, TypeInstance receiverType,
                                                      String propertyName) {
        TypeInvoker invoker = new TypeInvoker(sourceInfo, cacheEnabled);
        MethodDeclaration getter = tryResolveStaticGetter(
            declaringType.declaration(), receiverType.declaration(), propertyName, false);
        GetterInfo propertyGetter = tryResolvePropertyGetter(
            declaringType.declaration(), receiverType.declaration(), propertyName);
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

        MethodDeclaration setter = null;
        if (propertyType != null && getter != null) {
            setter = tryResolveStaticSetter(
                declaringType.declaration(), receiverType.declaration(), propertyName, false, propertyType.declaration());
        }

        if (propertyGetter == null && getter == null) {
            return null;
        }

        return new PropertyInfo(
            propertyName,
            propertyGetter != null ? propertyGetter.method() : null,
            getter,
            setter,
            propertyType,
            observableType,
            declaringType,
            true);
    }

    /**
     * Returns the public field with the specified name.
     */
    public FieldDeclaration resolveField(TypeDeclaration type, String name) {
        FieldDeclaration field = tryResolveField(type, name);
        if (field == null) {
            throw SymbolResolutionErrors.memberNotFound(sourceInfo, type, name);
        }

        return field;
    }

    /**
     * Returns the field with the specified name.
     */
    public FieldDeclaration resolveField(TypeDeclaration type, String name, boolean publicOnly) {
        FieldDeclaration field = tryResolveField(type, name, publicOnly);
        if (field == null) {
            throw SymbolResolutionErrors.memberNotFound(sourceInfo, type, name);
        }

        return field;
    }

    /**
     * Returns the public field with the specified name, or {@code null} if no such public field can be found.
     */
    public @Nullable FieldDeclaration tryResolveField(TypeDeclaration type, String name) {
        return tryResolveField(type, name, true);
    }

    /**
     * Returns the field with the specified name, or {@code null} if no such field can be found.
     */
    public @Nullable FieldDeclaration tryResolveField(TypeDeclaration type, String name, boolean publicOnly) {
        for (FieldDeclaration field : type.declaredFields()) {
            if (publicOnly && field.accessModifier() == AccessModifier.PRIVATE) {
                continue;
            }

            if (field.name().equals(name)) {
                return field;
            }
        }

        return type.superClass()
            .map(superClass -> tryResolveField(superClass, name, publicOnly))
            .orElse(null);
    }

    /**
     * Returns the method that satisfies the predicate, or {@code null} if no such method can be found.
     */
    public @Nullable MethodDeclaration tryResolveMethod(TypeDeclaration type, Predicate<MethodDeclaration> predicate) {
        for (MethodDeclaration method : type.declaredMethods()) {
            if (method.accessModifier() != AccessModifier.PRIVATE
                    && !method.isSynthetic()
                    && predicate.test(method)) {
                return method;
            }
        }

        return type.superClass()
            .map(superClass -> tryResolveMethod(superClass, predicate))
            .orElse(null);
    }

    /**
     * Returns all methods that satisfy the predicate.
     */
    public MethodDeclaration[] resolveMethods(TypeDeclaration type, Predicate<MethodDeclaration> predicate) {
        List<MethodDeclaration> methods = new ArrayList<>();
        TypeDeclaration clazz = type;

        while (clazz != null) {
            for (MethodDeclaration method : clazz.declaredMethods()) {
                if (method.accessModifier() != AccessModifier.PRIVATE
                        && !method.isSynthetic()
                        && predicate.test(method)) {
                    methods.add(method);
                }
            }

            clazz = clazz.superClass().orElse(null);
        }

        return methods.toArray(MethodDeclaration[]::new);
    }

    /**
     * Returns a getter method with a name corresponding to the specified name.
     *
     * If {@code verbatim} is true, only returns methods that match the name exactly; otherwise, also returns getter
     * methods with names like getMethodName or isMethodName.
     *
     * If {@code returnType} is non-null, only getter methods returning this type are considered.
     */
    public MethodDeclaration resolveGetter(
            TypeDeclaration type, String name, boolean verbatim, @Nullable TypeDeclaration returnType) {
        MethodDeclaration method = tryResolveGetter(type, name, verbatim, returnType);
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
    public @Nullable MethodDeclaration tryResolveGetter(
            TypeDeclaration type, String name, boolean verbatim, @Nullable TypeDeclaration returnType) {
        CacheKey key = new CacheKey("tryResolveGetter", type, name, verbatim, returnType);
        CacheEntry entry = getCache().get(key);
        if (entry.found() && cacheEnabled) {
            return (MethodDeclaration)entry.value();
        }

        String alt0, alt1;

        if (!verbatim && Character.isLowerCase(name.charAt(0))) {
            alt0 = NameHelper.getGetterName(name, false);
            alt1 = NameHelper.getGetterName(name, true);
        } else {
            alt0 = null;
            alt1 = null;
        }

        MethodDeclaration method = findMethod(type, m ->
            m.accessModifier() == AccessModifier.PUBLIC
                && (m.name().equals(name) || (!verbatim && (m.name().equals(alt0) || m.name().equals(alt1))))
                && m.parameters().isEmpty()
                && !m.returnType().equals(voidDecl())
                && (returnType == null || m.returnType().equals(returnType))
                && !m.isSynthetic());

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
    public @Nullable MethodDeclaration tryResolveSetter(
            TypeDeclaration type, String name, boolean verbatim, @Nullable TypeDeclaration paramType) {
        CacheKey key = new CacheKey("tryResolveSetter", type, name, verbatim, paramType);
        CacheEntry entry = getCache().get(key);
        if (entry.found() && cacheEnabled) {
            return (MethodDeclaration)entry.value();
        }

        String setterName;

        if (!verbatim && Character.isLowerCase(name.charAt(0))) {
            setterName = NameHelper.getSetterName(name);
        } else {
            setterName = null;
        }

        MethodDeclaration method = findMethod(type, m ->
            m.accessModifier() == AccessModifier.PUBLIC
                && (m.name().equals(name) || (!verbatim && m.name().equals(setterName)))
                && m.parameters().size() == 1
                && m.returnType().equals(voidDecl())
                && (paramType == null || m.parameters().get(0).type().equals(paramType))
                && !m.isSynthetic());

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
    public @Nullable MethodDeclaration tryResolveStaticSetter(
            TypeDeclaration declaringClass,
            TypeDeclaration receiverClass,
            String name,
            boolean verbatim,
            @Nullable TypeDeclaration paramType) {
        CacheKey key = new CacheKey("tryResolveStaticSetter", declaringClass, receiverClass, name, paramType);
        CacheEntry entry = getCache().get(key);
        if (entry.found() && cacheEnabled) {
            return (MethodDeclaration)entry.value();
        }

        String setterName;

        if (!verbatim && Character.isLowerCase(name.charAt(0))) {
            setterName = NameHelper.getSetterName(name);
        } else {
            setterName = null;
        }

        MethodDeclaration method = findMethod(declaringClass, m ->
            m.accessModifier() == AccessModifier.PUBLIC
                && m.isStatic()
                && (m.name().equals(name) || (!verbatim && m.name().equals(setterName)))
                && m.parameters().size() == 2
                && receiverClass.subtypeOf(m.parameters().get(0).type())
                && m.returnType().equals(voidDecl())
                && (paramType == null || m.parameters().get(1).type().equals(paramType))
                && !m.isSynthetic());

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
    public @Nullable MethodDeclaration tryResolveStaticGetter(
            TypeDeclaration declaringClass, TypeDeclaration receiverClass, String name, boolean verbatim) {
        CacheKey key = new CacheKey("tryResolveStaticGetter", declaringClass, name, verbatim);
        CacheEntry entry = getCache().get(key);
        if (entry.found() && cacheEnabled) {
            return (MethodDeclaration)entry.value();
        }

        String alt0, alt1;

        if (!verbatim && Character.isLowerCase(name.charAt(0))) {
            alt0 = NameHelper.getGetterName(name, false);
            alt1 = NameHelper.getGetterName(name, true);
        } else {
            alt0 = null;
            alt1 = null;
        }

        MethodDeclaration method = findMethod(declaringClass, m ->
            m.accessModifier() == AccessModifier.PUBLIC
                && m.isStatic()
                && (m.name().equals(name) || (!verbatim && (m.name().equals(alt0) || m.name().equals(alt1))))
                && m.parameters().size() == 1
                && receiverClass.subtypeOf(m.parameters().get(0).type())
                && !m.returnType().equals(voidDecl())
                && !m.isSynthetic());

        getCache().put(key, method);
        return method;
    }

    private record GetterInfo(MethodDeclaration method, boolean verbatim) {}

    /**
     * Returns a property getter method for the specified property (i.e. a method like Button.textProperty()).
     *
     * @return The method, or {@code null} if no getter can be found.
     */
    private GetterInfo tryResolvePropertyGetter(
            TypeDeclaration declaringClass, @Nullable TypeDeclaration receiverClass, String name) {
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

        MethodDeclaration method = potentialNames.stream()
            .map(n -> resolvePropertyGetterImpl(declaringClass, receiverClass, n))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

        GetterInfo getter = method != null ? new GetterInfo(method, method.name().equals(name)) : null;
        getCache().put(key, getter);
        return getter;
    }

    private MethodDeclaration resolvePropertyGetterImpl(
            TypeDeclaration declaringClass, @Nullable TypeDeclaration receiverClass, String methodName) {
        return findMethod(declaringClass, m -> {
            if (m.accessModifier() != AccessModifier.PUBLIC
                    || (receiverClass != null && !m.isStatic())
                    || !m.name().equals(methodName)
                    || !m.returnType().subtypeOf(ObservableValueDecl())
                    || m.isSynthetic()) {
                return false;
            }

            List<BehaviorDeclaration.Parameter> params = m.parameters();
            return receiverClass != null ?
                params.size() == 1 && receiverClass.subtypeOf(params.get(0).type()) :
                params.isEmpty();
        });
    }

    public TypeDeclaration getWritableClass(TypeDeclaration type, boolean requestProperty) {
        if (type.equals(booleanDecl()) || type.equals(BooleanDecl())
                || type.subtypeOf(WritableBooleanValueDecl())) {
            return requestProperty && type.subtypeOf(BooleanPropertyDecl()) ?
                BooleanPropertyDecl() : WritableBooleanValueDecl();
        } else if (type.equals(intDecl()) || type.equals(IntegerDecl())
                || type.equals(shortDecl()) || type.equals(ShortDecl())
                || type.equals(byteDecl()) || type.equals(ByteDecl())
                || type.equals(charDecl()) || type.equals(CharacterDecl())
                || type.subtypeOf(WritableIntegerValueDecl())) {
            return requestProperty && type.subtypeOf(IntegerPropertyDecl()) ?
                IntegerPropertyDecl() : WritableIntegerValueDecl();
        } else if (type.equals(longDecl()) || type.equals(LongDecl())
                || type.subtypeOf(WritableLongValueDecl())) {
            return requestProperty && type.subtypeOf(LongPropertyDecl()) ?
                LongPropertyDecl() : WritableLongValueDecl();
        } else if (type.equals(floatDecl()) || type.equals(FloatDecl())
                || type.subtypeOf(WritableFloatValueDecl())) {
            return requestProperty && type.subtypeOf(FloatPropertyDecl()) ?
                FloatPropertyDecl() : WritableFloatValueDecl();
        } else if (type.equals(doubleDecl()) || type.equals(DoubleDecl())
                || type.subtypeOf(WritableDoubleValueDecl())) {
            return requestProperty && type.subtypeOf(DoublePropertyDecl()) ?
                DoublePropertyDecl() : WritableDoubleValueDecl();
        } else {
            return requestProperty && type.subtypeOf(PropertyDecl()) ?
                PropertyDecl() : WritableValueDecl();
        }
    }

    public TypeDeclaration getObservableClass(TypeDeclaration type, boolean requestProperty) {
        if (type.equals(booleanDecl()) || type.equals(BooleanDecl())
                || type.subtypeOf(ObservableBooleanValueDecl())) {
            return requestProperty && type.subtypeOf(BooleanPropertyDecl()) ?
                BooleanPropertyDecl() : ObservableBooleanValueDecl();
        } else if (type.equals(intDecl()) || type.equals(IntegerDecl())
                || type.equals(shortDecl()) || type.equals(ShortDecl())
                || type.equals(byteDecl()) || type.equals(ByteDecl())
                || type.equals(charDecl()) || type.equals(CharacterDecl())
                || type.subtypeOf(ObservableIntegerValueDecl())) {
            return requestProperty && type.subtypeOf(IntegerPropertyDecl()) ?
                IntegerPropertyDecl() : ObservableIntegerValueDecl();
        } else if (type.equals(longDecl()) || type.equals(LongDecl())
                || type.subtypeOf(ObservableLongValueDecl())) {
            return requestProperty && type.subtypeOf(LongPropertyDecl()) ?
                LongPropertyDecl() : ObservableLongValueDecl();
        } else if (type.equals(floatDecl()) || type.equals(FloatDecl())
                || type.subtypeOf(ObservableFloatValueDecl())) {
            return requestProperty && type.subtypeOf(FloatPropertyDecl()) ?
                FloatPropertyDecl() : ObservableFloatValueDecl();
        } else if (type.equals(doubleDecl()) || type.equals(DoubleDecl())
                || type.subtypeOf(ObservableDoubleValueDecl())) {
            return requestProperty && type.subtypeOf(DoublePropertyDecl()) ?
                DoublePropertyDecl() : ObservableDoubleValueDecl();
        } else {
            return requestProperty && type.subtypeOf(PropertyDecl()) ?
                PropertyDecl() : ObservableValueDecl();
        }
    }

    public TypeInstance getObservableClass(TypeInstance type) {
        var invoker = new TypeInvoker(sourceInfo, cacheEnabled);

        if (type.equals(booleanDecl()) || type.equals(BooleanDecl())) {
            return invoker.invokeType(ObservableBooleanValueDecl());
        } else if (type.equals(intDecl()) || type.equals(IntegerDecl())
                || type.equals(shortDecl()) || type.equals(ShortDecl())
                || type.equals(byteDecl()) || type.equals(ByteDecl())
                || type.equals(charDecl()) || type.equals(CharacterDecl())) {
            return invoker.invokeType(ObservableIntegerValueDecl());
        } else if (type.equals(longDecl()) || type.equals(LongDecl())) {
            return invoker.invokeType(ObservableLongValueDecl());
        } else if (type.equals(floatDecl()) || type.equals(FloatDecl())) {
            return invoker.invokeType(ObservableFloatValueDecl());
        } else if (type.equals(doubleDecl()) || type.equals(DoubleDecl())) {
            return invoker.invokeType(ObservableDoubleValueDecl());
        } else {
            return invoker.invokeType(ObservableValueDecl(), List.of(type));
        }
    }

    public TypeInstance tryFindArgument(TypeInstance actualType, TypeDeclaration targetType) {
        if (actualType.equals(targetType)) {
            return actualType.arguments().isEmpty() ? null : actualType.arguments().get(0);
        }

        for (TypeInstance superType : actualType.superTypes()) {
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
        if (propertyType.subtypeOf(ObservableBooleanValueDecl())) {
            return TypeInstance.booleanType();
        } else if (propertyType.subtypeOf(ObservableIntegerValueDecl())) {
            return TypeInstance.intType();
        } else if (propertyType.subtypeOf(ObservableLongValueDecl())) {
            return TypeInstance.longType();
        } else if (propertyType.subtypeOf(ObservableFloatValueDecl())) {
            return TypeInstance.floatType();
        } else if (propertyType.subtypeOf(ObservableDoubleValueDecl())) {
            return TypeInstance.doubleType();
        } else if (propertyType.equals(ObservableValueDecl())) {
            return propertyType.arguments().isEmpty() ?
                TypeInstance.ObjectType() : propertyType.arguments().get(0);
        }

        for (TypeInstance superType : propertyType.superTypes()) {
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
        if (propertyType.subtypeOf(WritableBooleanValueDecl())) {
            return TypeInstance.booleanType();
        } else if (propertyType.subtypeOf(WritableIntegerValueDecl())) {
            return TypeInstance.intType();
        } else if (propertyType.subtypeOf(WritableLongValueDecl())) {
            return TypeInstance.longType();
        } else if (propertyType.subtypeOf(WritableFloatValueDecl())) {
            return TypeInstance.floatType();
        } else if (propertyType.subtypeOf(WritableDoubleValueDecl())) {
            return TypeInstance.doubleType();
        } else if (propertyType.equals(WritableValueDecl())) {
            return propertyType.arguments().isEmpty() ?
                TypeInstance.ObjectType() : propertyType.arguments().get(0);
        }

        for (TypeInstance superType : propertyType.superTypes()) {
            TypeInstance argType = tryFindWritableArgument(superType);
            if (argType != null) {
                return argType;
            }
        }

        return null;
    }

    private @Nullable MethodDeclaration findMethod(
            TypeDeclaration type, Function<MethodDeclaration, Boolean> consumer) {
        for (MethodDeclaration method : type.declaredMethods()) {
            if (!method.isSynthetic() && consumer.apply(method)) {
                return method;
            }
        }

        for (TypeDeclaration intf : type.interfaces()) {
            MethodDeclaration method = findMethod(intf, consumer);
            if (method != null) {
                return method;
            }
        }

        return type.superClass()
            .map(superClass -> findMethod(superClass, consumer))
            .orElse(null);
    }

    private static Cache getCache() {
        return (Cache)CompilationContext.getCurrent().computeIfAbsent(Resolver.class, key -> new Cache());
    }
}

// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.type;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.CompilationContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jfxcore.compiler.type.Types.*;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class TypeDeclaration {

    private final CtClass clazz;

    private Optional<TypeDeclaration> declaringType;
    private Optional<TypeDeclaration> superClass;
    private Optional<TypeDeclaration> componentType;

    private List<TypeDeclaration> interfaces;
    private List<TypeDeclaration> unmodifiableInterfaces;
    private List<TypeDeclaration> nestedClasses;
    private List<TypeDeclaration> unmodifiableNestedClasses;

    private List<FieldDeclaration> fields;
    private List<FieldDeclaration> declaredFields;
    private List<FieldDeclaration> unmodifiableFields;
    private List<FieldDeclaration> unmodifiableDeclaredFields;

    private List<MethodDeclaration> methods;
    private List<MethodDeclaration> declaredMethods;
    private List<MethodDeclaration> unmodifiableMethods;
    private List<MethodDeclaration> unmodifiableDeclaredMethods;

    private List<ConstructorDeclaration> constructors;
    private List<ConstructorDeclaration> declaredConstructors;
    private List<ConstructorDeclaration> unmodifiableConstructors;
    private List<ConstructorDeclaration> unmodifiableDeclaredConstructors;

    private List<AnnotationDeclaration> annotations;
    private List<AnnotationDeclaration> unmodifiableAnnotations;

    public static TypeDeclaration of(CtClass clazz) {
        Map<String, TypeDeclaration> cache = getClassCache();
        TypeDeclaration declaration = cache.get(clazz.getName());

        //noinspection Java8MapApi
        if (declaration == null) {
            declaration = new TypeDeclaration(clazz);
            cache.put(clazz.getName(), declaration);
        }

        return declaration;
    }

    TypeDeclaration(CtClass clazz) {
        this.clazz = clazz;
    }

    public CtClass jvmType() {
        return clazz;
    }

    public String name() {
        return clazz.getName();
    }

    public String simpleName() {
        return clazz.getSimpleName();
    }

    public String javaName() {
        char[] name = name().toCharArray();
        TypeDeclaration declaringClass = declaringType().orElse(null);

        while (declaringClass != null) {
            name[declaringClass.name().length()] = '.';
            declaringClass = declaringClass.declaringType().orElse(null);
        }

        return new String(name);
    }

    public String packageName() {
        int index = name().lastIndexOf('.');
        return index >= 0 ? name().substring(0, index) : "";
    }

    public String genericSignature() {
        return clazz.getGenericSignature();
    }

    public int dimensions() {
        String name = clazz.getSimpleName();
        int d = 0;

        for (int i = name.length() - 2; i >= 0; i -= 2, ++d) {
            if (name.charAt(i) != '[' || name.charAt(i + 1) != ']') {
                break;
            }
        }

        return d;
    }

    public int slots() {
        return clazz == CtClass.doubleType || clazz == CtClass.longType ? 2 : 1;
    }

    public AccessModifier accessModifier() {
        int modifiers = clazz.getModifiers();
        if (Modifier.isPrivate(modifiers)) return AccessModifier.PRIVATE;
        if (Modifier.isProtected(modifiers)) return AccessModifier.PROTECTED;
        if (Modifier.isPublic(modifiers)) return AccessModifier.PUBLIC;
        return AccessModifier.PACKAGE;
    }

    public boolean isAbstract() {
        return Modifier.isAbstract(clazz.getModifiers());
    }

    public boolean isFinal() {
        return Modifier.isFinal(clazz.getModifiers());
    }

    public boolean isStatic() {
        return Modifier.isStatic(clazz.getModifiers());
    }

    public boolean isArray() {
        return clazz.isArray();
    }

    public boolean isInterface() {
        return clazz.isInterface();
    }

    public boolean isEnum() {
        return clazz.isEnum();
    }

    public boolean isAnnotation() {
        return clazz.isAnnotation();
    }

    public boolean isPrimitive() {
        return clazz.isPrimitive();
    }

    public boolean isIntegralPrimitive() {
        return equals(byteDecl())
            || equals(charDecl())
            || equals(shortDecl())
            || equals(intDecl())
            || equals(longDecl());
    }

    public boolean isIntegralBox() {
        return equals(ByteDecl())
            || equals(CharacterDecl())
            || equals(ShortDecl())
            || equals(IntegerDecl())
            || equals(LongDecl());
    }

    public boolean isFloatingPointPrimitive() {
        return equals(floatDecl()) || equals(doubleDecl());
    }

    public boolean isFloatingPointBox() {
        return equals(FloatDecl()) || equals(DoubleDecl());
    }

    public boolean isNumeric() {
        return isNumericPrimitive() || isNumericBox();
    }

    public boolean isNumericPrimitive() {
        return isIntegralPrimitive() || isFloatingPointPrimitive();
    }

    public boolean isNumericBox() {
        return isIntegralBox() || isFloatingPointBox() || equals(NumberDecl());
    }

    public boolean isPrimitiveBox() {
        return isNumericBox() || equals(BooleanDecl());
    }

    public boolean isBoxOf(TypeDeclaration primitiveType) {
        return switch (name()) {
            case BooleanName -> primitiveType.name().equals("boolean");
            case ByteName -> primitiveType.name().equals("byte");
            case CharacterName -> primitiveType.name().equals("char");
            case ShortName -> primitiveType.name().equals("short");
            case IntegerName -> primitiveType.name().equals("int");
            case LongName -> primitiveType.name().equals("long");
            case FloatName -> primitiveType.name().equals("float");
            case DoubleName -> primitiveType.name().equals("double");
            case NumberName -> switch (primitiveType.name()) {
                case "byte", "short", "int", "long", "float", "double" -> true;
                default -> false;
            };

            default -> false;
        };
    }

    public Optional<TypeDeclaration> primitive() {
        if (isPrimitive()) {
            return Optional.of(this);
        } else if (equals(BooleanDecl())) {
            return Optional.of(booleanDecl());
        } else if (equals(ByteDecl())) {
            return Optional.of(byteDecl());
        } else if (equals(CharacterDecl())) {
            return Optional.of(charDecl());
        } else if (equals(ShortDecl())) {
            return Optional.of(shortDecl());
        } else if (equals(IntegerDecl())) {
            return Optional.of(intDecl());
        } else if (equals(LongDecl())) {
            return Optional.of(longDecl());
        } else if (equals(FloatDecl())) {
            return Optional.of(floatDecl());
        } else if (equals(DoubleDecl())) {
            return Optional.of(doubleDecl());
        }

        return Optional.empty();
    }

    public TypeDeclaration boxed() {
        if (!isPrimitive()) {
            return this;
        } else if (equals(booleanDecl())) {
            return BooleanDecl();
        } else if (equals(byteDecl())) {
            return ByteDecl();
        } else if (equals(charDecl())) {
            return CharacterDecl();
        } else if (equals(shortDecl())) {
            return ShortDecl();
        } else if (equals(intDecl())) {
            return IntegerDecl();
        } else if (equals(longDecl())) {
            return LongDecl();
        } else if (equals(floatDecl())) {
            return FloatDecl();
        } else if (equals(doubleDecl())) {
            return DoubleDecl();
        }

        return this;
    }

    public TypeDeclaration requireComponentType() {
        return componentType().orElseThrow(() -> GeneralErrors.internalError(name() + " is not an array type"));
    }

    @SuppressWarnings("OptionalAssignedToNull")
    public Optional<TypeDeclaration> componentType() {
        if (componentType == null) {
            try {
                componentType = Optional.ofNullable(clazz.getComponentType()).map(TypeDeclaration::of);
            } catch (NotFoundException ex) {
                throw SymbolResolutionErrors.classNotFound(SourceInfo.none(), ex.getMessage());
            }
        }

        return componentType;
    }

    public TypeDeclaration arrayType(int dimensions) {
        if (dimensions < 1) {
            throw GeneralErrors.internalError("Invalid array dimension: " + dimensions);
        }

        try {
            return of(clazz.getClassPool().get(clazz.getName() + "[]".repeat(dimensions)));
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.classNotFound(SourceInfo.none(), ex.getMessage());
        }
    }

    public TypeDeclaration requireDeclaringType() {
        return declaringType().orElseThrow(() -> GeneralErrors.internalError(name() + " is not a nested type"));
    }

    @SuppressWarnings("OptionalAssignedToNull")
    public Optional<TypeDeclaration> declaringType() {
        if (declaringType == null) {
            try {
                declaringType = Optional.ofNullable(clazz.getDeclaringClass()).map(TypeDeclaration::of);
            } catch (NotFoundException ex) {
                throw SymbolResolutionErrors.classNotFound(SourceInfo.none(), ex.getMessage());
            }
        }

        return declaringType;
    }

    public TypeDeclaration requireSuperClass() {
        return superClass().orElseThrow(() -> GeneralErrors.internalError(name() + " does not have a superclass"));
    }

    @SuppressWarnings("OptionalAssignedToNull")
    public Optional<TypeDeclaration> superClass() {
        if (superClass == null) {
            try {
                superClass = Optional.ofNullable(clazz.getSuperclass()).map(TypeDeclaration::of);
            } catch (NotFoundException ex) {
                throw SymbolResolutionErrors.classNotFound(SourceInfo.none(), ex.getMessage());
            }
        }

        return superClass;
    }

    public List<TypeDeclaration> interfaces() {
        if (unmodifiableInterfaces == null) {
            unmodifiableInterfaces = Collections.unmodifiableList(interfacesInternal());
        }

        return unmodifiableInterfaces;
    }

    private List<TypeDeclaration> interfacesInternal() {
        if (interfaces == null) {
            try {
                interfaces = Arrays.stream(clazz.getInterfaces())
                    .map(TypeDeclaration::of)
                    .collect(Collectors.toCollection(ArrayList::new));
            } catch (NotFoundException ex) {
                throw SymbolResolutionErrors.classNotFound(SourceInfo.none(), ex.getMessage());
            }
        }

        return interfaces;
    }

    public List<TypeDeclaration> nestedClasses() {
        if (unmodifiableNestedClasses == null) {
            unmodifiableNestedClasses = Collections.unmodifiableList(nestedClassesInternal());
        }

        return unmodifiableNestedClasses;
    }

    private List<TypeDeclaration> nestedClassesInternal() {
        if (nestedClasses == null) {
            nestedClasses = new ArrayList<>();

            try {
                Arrays.stream(clazz.getNestedClasses())
                    .map(TypeDeclaration::of)
                    .forEach(nestedClasses::add);
            } catch (NotFoundException e) {
                throw SymbolResolutionErrors.classNotFound(SourceInfo.none(), e.getMessage());
            }
        }

        return nestedClasses;
    }

    public List<FieldDeclaration> fields() {
        if (unmodifiableFields == null) {
            fields = new ArrayList<>();
            updateFields();
            unmodifiableFields = Collections.unmodifiableList(fields);
        }

        return unmodifiableFields;
    }

    void updateFields() {
        if (fields == null) {
            return;
        }

        fields.clear();
        Set<FieldDeclaration> fieldSet = new HashSet<>();

        for (FieldDeclaration method : declaredFieldsInternal()) {
            if (method.accessModifier() != AccessModifier.PRIVATE) {
                fields.add(method);
                fieldSet.add(method);
            }
        }

        superClass().ifPresent(superClass -> {
            for (FieldDeclaration field : superClass.fields()) {
                if (fieldSet.add(field)) {
                    fields.add(field);
                }
            }
        });

        for (TypeDeclaration interfaceType : interfacesInternal()) {
            for (FieldDeclaration method : interfaceType.fields()) {
                if (fieldSet.add(method)) {
                    fields.add(method);
                }
            }
        }
    }

    public List<FieldDeclaration> declaredFields() {
        if (unmodifiableDeclaredFields == null) {
            unmodifiableDeclaredFields = Collections.unmodifiableList(declaredFieldsInternal());
        }

        return unmodifiableDeclaredFields;
    }

    List<FieldDeclaration> declaredFieldsInternal() {
        if (declaredFields == null) {
            declaredFields = Arrays.stream(clazz.getDeclaredFields())
                .map(f -> new FieldDeclaration(f, this))
                .collect(Collectors.toCollection(ArrayList::new));
        }

        return declaredFields;
    }

    public FieldDeclaration requireDeclaredField(String name) {
        return declaredField(name).orElseThrow(() -> GeneralErrors.internalError(
            String.format("Field '%s' not found in %s", name, javaName())));
    }

    public Optional<FieldDeclaration> declaredField(String name) {
        return declaredFieldsInternal().stream().filter(field -> field.name().equals(name)).findFirst();
    }

    public FieldDeclaration requireField(String name) {
        return field(name).orElseThrow(() -> GeneralErrors.internalError(
            String.format("Non-private field '%s' not found in %s", name, javaName())));
    }

    public Optional<FieldDeclaration> field(String name) {
        return fields().stream().filter(field -> field.name().equals(name)).findFirst();
    }

    public List<MethodDeclaration> declaredMethods() {
        if (unmodifiableDeclaredMethods == null) {
            unmodifiableDeclaredMethods = Collections.unmodifiableList(declaredMethodsInternal());
        }

        return unmodifiableDeclaredMethods;
    }

    List<MethodDeclaration> declaredMethodsInternal() {
        if (declaredMethods == null) {
            declaredMethods = Arrays.stream(clazz.getDeclaredMethods())
                .map(m -> new MethodDeclaration(m, this))
                .collect(Collectors.toCollection(ArrayList::new));
        }

        return declaredMethods;
    }

    public List<MethodDeclaration> declaredMethods(String name) {
        return declaredMethodsInternal().stream().filter(method -> method.name().equals(name)).toList();
    }

    public MethodDeclaration requireDeclaredMethod(String name, TypeDeclaration... parameters) {
        return declaredMethod(name, parameters).orElseThrow(() -> GeneralErrors.internalError(
            String.format("Method '%s' not found in %s", name, javaName())));
    }

    public Optional<MethodDeclaration> declaredMethod(String name, TypeDeclaration... parameters) {
        return declaredMethods().stream().filter(method -> {
            if (!method.name().equals(name)) {
                return false;
            }

            List<BehaviorDeclaration.Parameter> params = method.parameters();
            if (params.size() != parameters.length) {
                return false;
            }

            for (int i = 0; i < params.size(); i++) {
                if (!params.get(i).type().equals(parameters[i])) {
                    return false;
                }
            }

            return true;
        }).findFirst();
    }

    public MethodDeclaration requireMethod(String name, TypeDeclaration... parameters) {
        return method(name, parameters).orElseThrow(() -> GeneralErrors.internalError(
            String.format("Method '%s' not found in %s", name, javaName())));
    }

    public Optional<MethodDeclaration> method(String name, TypeDeclaration... parameters) {
        return methods().stream().filter(method -> {
            if (!method.name().equals(name)) {
                return false;
            }

            List<BehaviorDeclaration.Parameter> params = method.parameters();
            if (params.size() != parameters.length) {
                return false;
            }

            for (int i = 0; i < params.size(); i++) {
                if (!params.get(i).type().equals(parameters[i])) {
                    return false;
                }
            }

            return true;
        }).findFirst();
    }

    public List<MethodDeclaration> methods() {
        if (unmodifiableMethods == null) {
            methods = new ArrayList<>();
            updateMethods();
            unmodifiableMethods = Collections.unmodifiableList(methods);
        }

        return unmodifiableMethods;
    }

    void updateMethods() {
        if (methods == null) {
            return;
        }

        methods.clear();
        Set<String> methodSet = new HashSet<>();

        for (MethodDeclaration method : declaredMethodsInternal()) {
            if (method.accessModifier() != AccessModifier.PRIVATE) {
                methods.add(method);
                methodSet.add(methodKey(method));
            }
        }

        superClass().ifPresent(superClass -> {
            for (MethodDeclaration method : superClass.methods()) {
                if (methodSet.add(methodKey(method))) {
                    methods.add(method);
                }
            }
        });

        for (TypeDeclaration interfaceType : interfacesInternal()) {
            for (MethodDeclaration method : interfaceType.methods()) {
                if (methodSet.add(methodKey(method))) {
                    methods.add(method);
                }
            }
        }
    }

    private static String methodKey(MethodDeclaration method) {
        String signature = method.signature();
        return method.name() + signature.substring(0, signature.indexOf(")") + 1);
    }

    public List<MethodDeclaration> methods(String name) {
        return methods().stream().filter(method -> method.name().equals(name)).toList();
    }

    public ConstructorDeclaration requireDeclaredConstructor(TypeDeclaration... parameters) {
        return declaredConstructor(parameters).orElseThrow(() -> GeneralErrors.internalError(
            String.format("Constructor <init>(%s) not found in %s", Arrays.toString(parameters), javaName())));
    }

    public Optional<ConstructorDeclaration> declaredConstructor(TypeDeclaration... parameters) {
        return declaredConstructors().stream().filter(constructor -> {
            List<BehaviorDeclaration.Parameter> params = constructor.parameters();
            if (params.size() != parameters.length) {
                return false;
            }

            for (int i = 0; i < params.size(); i++) {
                if (!params.get(i).type().equals(parameters[i])) {
                    return false;
                }
            }

            return true;
        }).findFirst();
    }

    public List<ConstructorDeclaration> declaredConstructors() {
        if (unmodifiableDeclaredConstructors == null) {
            unmodifiableDeclaredConstructors = Collections.unmodifiableList(declaredConstructorsInternal());
        }

        return unmodifiableDeclaredConstructors;
    }

    private List<ConstructorDeclaration> declaredConstructorsInternal() {
        if (declaredConstructors == null) {
            declaredConstructors = Arrays.stream(clazz.getDeclaredConstructors())
                .map(ConstructorDeclaration::of)
                .collect(Collectors.toCollection(ArrayList::new));
        }

        return declaredConstructors;
    }

    public ConstructorDeclaration requireConstructor(TypeDeclaration... parameters) {
        return constructor(parameters).orElseThrow(() -> GeneralErrors.internalError(
            String.format("Constructor <init>(%s) not found in %s", Arrays.toString(parameters), javaName())));
    }

    public Optional<ConstructorDeclaration> constructor(TypeDeclaration... parameters) {
        return constructors().stream().filter(constructor -> {
            if (constructor.accessModifier() == AccessModifier.PRIVATE) {
                return false;
            }

            List<BehaviorDeclaration.Parameter> params = constructor.parameters();
            if (params.size() != parameters.length) {
                return false;
            }

            for (int i = 0; i < params.size(); i++) {
                if (!params.get(i).type().equals(parameters[i])) {
                    return false;
                }
            }

            return true;
        }).findFirst();
    }

    public List<ConstructorDeclaration> constructors() {
        if (unmodifiableConstructors == null) {
            constructors = new ArrayList<>();
            updateConstructors();
            unmodifiableConstructors = Collections.unmodifiableList(constructors);
        }

        return unmodifiableConstructors;
    }

    void updateConstructors() {
        if (constructors == null) {
            return;
        }

        constructors.clear();

        for (ConstructorDeclaration constructor : declaredConstructorsInternal()) {
            if (constructor.accessModifier() != AccessModifier.PRIVATE) {
                constructors.add(constructor);
            }
        }
    }

    public Optional<AnnotationDeclaration> annotation(String typeName) {
        return annotations().stream()
            .filter(annotation -> annotation.typeName().equals(typeName))
            .findFirst();
    }

    public List<AnnotationDeclaration> annotations() {
        if (unmodifiableAnnotations == null) {
            annotations = new ArrayList<>();
            updateAnnotations();
            unmodifiableAnnotations = Collections.unmodifiableList(annotations);
        }

        return unmodifiableAnnotations;
    }

    private void updateAnnotations() {
        if (annotations == null) {
            return;
        }

        annotations.clear();
        annotations.addAll(
            AnnotationDeclaration.collect(tag -> {
                ClassFile classFile = clazz.getClassFile2();
                return classFile != null
                    ? (AnnotationsAttribute)classFile.getAttribute(tag)
                    : null;
            }, clazz.getClassPool()));

        superClass().ifPresent(superClass -> {
            for (AnnotationDeclaration annotation : superClass.annotations()) {
                if (annotation.isInherited()) {
                    annotations.add(annotation);
                }
            }
        });
    }

    public TypeDeclaration setModifiers(int modifiers) {
        jvmType().setModifiers(modifiers);
        return this;
    }

    @SuppressWarnings("OptionalAssignedToNull")
    public TypeDeclaration setSuperClass(TypeDeclaration superClass) {
        try {
            jvmType().setSuperclass(superClass.jvmType());
            this.superClass = null;
            updateAnnotations();
            updateMethods();
            updateFields();
            return this;
        } catch (CannotCompileException e) {
            throw GeneralErrors.internalError(String.format(
                    "Failed to set super class %s for %s",
                    superClass.jvmType().getName(), jvmType().getName()));
        }
    }

    public TypeDeclaration addInterface(TypeDeclaration interfaceType) {
        List<TypeDeclaration> interfaces = interfacesInternal();
        if (!interfaces.contains(interfaceType)) {
            jvmType().addInterface(interfaceType.jvmType());
            interfaces.add(interfaceType);
            updateMethods();
            updateFields();
        }

        return this;
    }

    public FieldDeclaration createField(String name, TypeDeclaration type) {
        if (declaredField(name).isPresent()) {
            throw new IllegalArgumentException(String.format(
                "Field '%s' already exists in %s", name, javaName()));
        }

        try {
            var field = new FieldDeclaration(new CtField(type.jvmType(), name, jvmType()), this);

            if (declaredFields != null) {
                declaredFields.add(field);
            }

            try {
                clazz.addField(field.member());
            } catch (CannotCompileException ex) {
                throw GeneralErrors.internalError(String.format(
                    "Failed to add field '%s' to %s", field.name(), clazz.getName()));
            }

            updateFields();
            return field;
        } catch (CannotCompileException ex) {
            throw GeneralErrors.internalError(String.format(
                "Failed to create field '%s' of %s in declaring type %s",
                name, type.jvmType().getName(), jvmType().getName()));
        }
    }

    public MethodDeclaration createMethod(String name, TypeDeclaration returnType, TypeDeclaration... parameterTypes) {
        if (declaredMethod(name, parameterTypes).isPresent()) {
            throw new IllegalArgumentException(String.format(
                "Method %s(%s) already exists in %s", name,
                String.join(", ", Arrays.stream(parameterTypes).map(TypeDeclaration::name).toList()), javaName()));
        }

        var method = new MethodDeclaration(new CtMethod(
            returnType.jvmType(),
            name,
            Arrays.stream(parameterTypes).map(TypeDeclaration::jvmType).toArray(CtClass[]::new),
            jvmType()), this);

        if (declaredMethods != null) {
            declaredMethods.add(method);
        }

        try {
            boolean isAbstract = Modifier.isAbstract(clazz.getModifiers());
            clazz.addMethod(method.member());

            if (!isAbstract) {
                clazz.setModifiers(clazz.getModifiers() & ~Modifier.ABSTRACT);
            }
        } catch (CannotCompileException ex) {
            throw GeneralErrors.internalError(String.format(
                "Failed to add method '%s' to %s", method.name(), clazz.getName()));
        }

        updateMethods();
        return method;
    }

    public ConstructorDeclaration createConstructor(TypeDeclaration... parameterTypes) {
        if (declaredConstructor(parameterTypes).isPresent()) {
            throw new IllegalArgumentException(String.format(
                "Constructor <init>(%s) already exists in %s",
                String.join(", ", Arrays.stream(parameterTypes).map(TypeDeclaration::name).toList()), javaName()));
        }

        var constructor = new ConstructorDeclaration(new CtConstructor(
            Arrays.stream(parameterTypes).map(TypeDeclaration::jvmType).toArray(CtClass[]::new),
            jvmType()), this);

        if (declaredConstructors != null) {
            declaredConstructors.add(constructor);
        }

        try {
            boolean isAbstract = Modifier.isAbstract(clazz.getModifiers());
            clazz.addConstructor(constructor.member());

            if (!isAbstract) {
                clazz.setModifiers(clazz.getModifiers() & ~Modifier.ABSTRACT);
            }
        } catch (CannotCompileException ex) {
            throw GeneralErrors.internalError(String.format("Failed to add constructor to %s", clazz.getName()));
        }

        updateConstructors();
        return constructor;
    }

    public ConstructorDeclaration createDefaultConstructor() {
        return createConstructor().setCode(new Bytecode(this, 1)
            .aload(0)
            .invoke(requireSuperClass().requireDeclaredConstructor())
            .vreturn());
    }

    public TypeDeclaration createNestedClass(String simpleName) {
        TypeDeclaration existingClass = nestedClassesInternal().stream()
            .filter(c -> c.simpleName().equals(simpleName))
            .findFirst()
            .orElse(null);

        if (existingClass != null) {
            return existingClass;
        }

        TypeDeclaration newClass = of(clazz.makeNestedClass(simpleName, true));
        nestedClassesInternal().add(newClass);

        return newClass;
    }

    public boolean subtypeOf(TypeDeclaration other) {
        try {
            return other != null && clazz.subtypeOf(other.clazz);
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.classNotFound(SourceInfo.none(), ex.getMessage());
        }
    }

    public boolean isAssignableFrom(TypeDeclaration other) {
        return other != null && other.subtypeOf(this);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof TypeDeclaration other && clazz.getName().equals(other.clazz.getName());
    }

    @Override
    public int hashCode() {
        return clazz.getName().hashCode();
    }

    @Override
    public String toString() {
        return javaName();
    }

    @SuppressWarnings("unchecked")
    static Map<String, TypeDeclaration> getClassCache() {
        return (Map<String, TypeDeclaration>)CompilationContext.getCurrent()
            .computeIfAbsent(TypeDeclaration.class, key -> new HashMap<String, TypeDeclaration>());
    }
}

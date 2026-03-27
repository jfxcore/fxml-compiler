// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeSymbols;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.CompilationContext;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public abstract class ClassGenerator implements Generator {

    protected TypeDeclaration generatedClass;

    protected final FieldDeclaration createField(String name, TypeDeclaration type) {
        return generatedClass.createField(name, type);
    }

    protected final MethodDeclaration createMethod(String name, TypeDeclaration returnType,
                                                          TypeDeclaration... parameterTypes) {
        return generatedClass.createMethod(name, returnType, parameterTypes);
    }

    protected final ConstructorDeclaration createDefaultConstructor() {
        return generatedClass.createDefaultConstructor();
    }

    protected final ConstructorDeclaration createConstructor(TypeDeclaration... parameterTypes) {
        return generatedClass.createConstructor(parameterTypes);
    }

    protected final TypeDeclaration requireSuperClass() {
        return generatedClass.requireSuperClass();
    }

    protected final FieldDeclaration requireDeclaredField(String name) {
        return generatedClass.requireDeclaredField(name);
    }

    protected final MethodDeclaration requireDeclaredMethod(String name, TypeDeclaration... parameters) {
        return generatedClass.requireDeclaredMethod(name, parameters);
    }

    public abstract String getClassName();

    public abstract TypeInstance getTypeInstance();

    public final TypeDeclaration getGeneratedClass() {
        return generatedClass;
    }

    public TypeDeclaration emitClass(BytecodeEmitContext context) {
        return generatedClass = context.getNestedClasses().create(getClassName());
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean consume(BytecodeEmitContext context) {
        String className = getClassName();
        List<String> classNames = (List<String>) CompilationContext.getCurrent().computeIfAbsent(
            ClassGenerator.class, key -> new ArrayList<>());

        if (classNames.contains(className)) {
            return false;
        }

        classNames.add(className);
        return true;
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) {
        generatedClass
            .createMethod("forceInit", TypeSymbols.voidDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL | Modifier.STATIC)
            .setCode(new Bytecode(generatedClass, 0).vreturn());
    }

    public static TypeDeclaration emit(BytecodeEmitContext context, ClassGenerator generator) {
        generator.emitClass(context);
        generator.emitFields(context);
        generator.emitMethods(context);
        generator.emitCode(context);
        return context.getNestedClasses().find(generator.getClassName());
    }
}

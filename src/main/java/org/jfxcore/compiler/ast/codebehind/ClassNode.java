// Copyright (c) 2022, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.codebehind;

import javassist.Modifier;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.VersionInfo;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.TypeNode;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class ClassNode extends ObjectNode implements JavaEmitterNode {

    private final String packageName;
    private final String className;
    private final String markupClassName;
    private final String[] parameters;
    private final String typeArguments;
    private final int classModifiers;
    private final boolean hasCodeBehind;

    public ClassNode(
            @Nullable String packageName,
            String className,
            String markupClassName,
            int classModifiers,
            String[] parameters,
            String typeArguments,
            boolean hasCodeBehind,
            TypeNode type,
            Collection<PropertyNode> properties,
            SourceInfo sourceInfo) {
        super(type, properties, Collections.emptyList(), sourceInfo);
        this.packageName = packageName;
        this.className = checkNotNull(className);
        this.markupClassName = markupClassName;
        this.classModifiers = classModifiers;
        this.parameters = parameters;
        this.typeArguments = typeArguments;
        this.hasCodeBehind = hasCodeBehind;
    }

    public @Nullable String getPackageName() {
        return packageName;
    }

    public String getClassName() {
        return className;
    }

    public String getMarkupClassName() {
        return markupClassName;
    }

    public int getClassModifiers() {
        return classModifiers;
    }

    public String[] getParameters() {
        return parameters;
    }

    public boolean hasCodeBehind() {
        return hasCodeBehind;
    }

    @Override
    public void emit(JavaEmitContext context) {
        String modifiers;

        if (Modifier.isPublic(classModifiers)) {
            modifiers = "public ";
        } else if (Modifier.isProtected(classModifiers)) {
            modifiers = "protected ";
        } else {
            modifiers = "";
        }

        if (hasCodeBehind) {
            modifiers += "abstract ";
        }

        StringBuilder code = context.getOutput();
        String className = hasCodeBehind ? markupClassName : this.className;
        String superClassName = getType().getMarkupName();

        if (typeArguments != null) {
            superClassName += "<" + typeArguments + ">";
        }

        code.append(String.format("@javax.annotation.processing.Generated(\"%s:%s\")\r\n",
                    VersionInfo.getGroup(), VersionInfo.getName()))
            .append(String.format("%sclass %s extends %s {\r\n", modifiers, className, superClassName));

        for (PropertyNode propertyNode : getProperties()) {
            context.emit(propertyNode);
        }

        if (parameters.length > 0 || !hasCodeBehind) {
            if (!hasCodeBehind) {
                code.append(String.format(
                    "\t/** Initializes a new instance of the {@link %s} class. */\r\n",
                    (packageName != null ? packageName + "." : "") + className));
            }

            code.append(String.format("\tpublic %s(", className));

            for (int i = 0; i < parameters.length; ++i) {
                if (i > 0) {
                    code.append(", ");
                }

                code.append(String.format("%s arg%s", parameters[i], i));
            }

            code.append(") {\r\n");

            if (parameters.length > 0) {
                code.append("\t\tsuper(");

                for (int i = 0; i < parameters.length; ++i) {
                    if (i > 0) {
                        code.append(", ");
                    }

                    code.append(String.format("arg%s", i));
                }

                code.append(");\r\n");
            }

            if (!hasCodeBehind) {
                code.append("\t\tinitializeComponent();\r\n");
            }

            code.append("\t}\r\n");
        }

        if (hasCodeBehind) {
            code.append("\t/** Loads and initializes the scene graph of this component. */\r\n");
            code.append("\tprotected final void initializeComponent() {}\r\n");
        } else {
            code.append("\tprivate void initializeComponent() {}\r\n");
        }

        code.append("}");
    }

    @Override
    public ClassNode deepClone() {
        return new ClassNode(
            packageName, className, markupClassName, classModifiers, parameters, typeArguments,
            hasCodeBehind, getType(), getProperties(), getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassNode other = (ClassNode)o;
        return classModifiers == other.classModifiers
            && hasCodeBehind == other.hasCodeBehind
            && Objects.equals(packageName, other.packageName)
            && Objects.equals(className, other.className)
            && Objects.equals(markupClassName, other.markupClassName)
            && Objects.equals(typeArguments, other.typeArguments)
            && Arrays.equals(parameters, other.parameters);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(packageName, className, markupClassName, typeArguments, classModifiers, hasCodeBehind);
        result = 31 * result + Arrays.hashCode(parameters);
        return result;
    }

}

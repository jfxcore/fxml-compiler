// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.type;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ParameterAnnotationsAttribute;
import javassist.bytecode.SyntheticAttribute;
import javassist.bytecode.annotation.Annotation;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.ExceptionHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.jfxcore.compiler.type.Types.*;

public abstract sealed class BehaviorDeclaration
        extends MemberDeclaration
        permits ConstructorDeclaration, MethodDeclaration {

    public record Parameter(String name, TypeDeclaration type, List<AnnotationDeclaration> annotations) {
        public Parameter {
            Objects.requireNonNull(type);
            Objects.requireNonNull(annotations);
        }

        public Optional<AnnotationDeclaration> annotation(String typeName) {
            return annotations.stream()
                .filter(annotation -> annotation.typeName().equals(typeName))
                .findFirst();
        }
    }

    private List<Parameter> parameters;

    BehaviorDeclaration(CtBehavior behavior, TypeDeclaration declaringType) {
        super(behavior, declaringType);
    }

    public abstract int occupiedSlots();

    public abstract String displaySignature(boolean simpleNames, boolean paramNames);

    public final String signature() {
        return this.<CtBehavior>member().getSignature();
    }

    public final String longName() {
        return this.<CtBehavior>member().getLongName();
    }

    public final boolean isVarArgs() {
        return Modifier.isVarArgs(this.<CtBehavior>member().getModifiers());
    }

    @Override
    public final boolean isSynthetic() {
        return this.<CtBehavior>member().getMethodInfo2().getAttribute(SyntheticAttribute.tag) != null;
    }

    public final List<Parameter> parameters() {
        if (parameters == null) {
            try {
                var visibleAnnotationsAttribute = (ParameterAnnotationsAttribute)this.<CtBehavior>member()
                    .getMethodInfo2().getAttribute(ParameterAnnotationsAttribute.visibleTag);

                var invisibleAnnotationsAttribute = (ParameterAnnotationsAttribute)this.<CtBehavior>member()
                    .getMethodInfo2().getAttribute(ParameterAnnotationsAttribute.invisibleTag);

                CtClass[] paramTypes = this.<CtBehavior>member().getParameterTypes();
                List<Parameter> parameters = new ArrayList<>(paramTypes.length);

                Object[][] visibleAnnotations = visibleAnnotationsAttribute != null
                    ? visibleAnnotationsAttribute.getAnnotations()
                    : null;

                Object[][] invisibleAnnotations = invisibleAnnotationsAttribute != null
                    ? invisibleAnnotationsAttribute.getAnnotations()
                    : null;

                for (int i = 0; i < paramTypes.length; i++) {
                    String name = null;
                    TypeDeclaration type = TypeDeclaration.of(paramTypes[i]);
                    List<AnnotationDeclaration> annotations = new ArrayList<>();

                    if (visibleAnnotations != null) {
                        for (Object paramAnnotation : visibleAnnotations[i]) {
                            if (paramAnnotation instanceof Annotation a) {
                                var annotation = new AnnotationDeclaration(a, true, false);
                                annotations.add(annotation);

                                if (annotation.typeName().equals(NamedArgAnnotationName)) {
                                    name = annotation.getString("value");
                                }
                            }
                        }
                    }

                    if (invisibleAnnotations != null) {
                        for (Object paramAnnotation : invisibleAnnotations[i]) {
                            if (paramAnnotation instanceof Annotation annotation) {
                                annotations.add(new AnnotationDeclaration(annotation, false, false));
                            }
                        }
                    }

                    parameters.add(new Parameter(name, type, List.copyOf(annotations)));
                }

                this.parameters = parameters;
            } catch (NotFoundException ex) {
                throw SymbolResolutionErrors.classNotFound(SourceInfo.none(), ex.getMessage());
            }
        }

        return parameters;
    }

    public BehaviorDeclaration setCode(Bytecode bytecode) {
        this.<CtBehavior>member().getMethodInfo().setCodeAttribute(bytecode.toCodeAttribute());

        ExceptionHelper.unchecked(SourceInfo.none(), () ->
            this.<CtBehavior>member().getMethodInfo().rebuildStackMap(
                declaringType().jvmType().getClassPool()));

        return this;
    }

    @Override
    public final int hashCode() {
        return this.<CtBehavior>member().getLongName().hashCode();
    }

    @Override
    public final boolean equals(Object obj) {
        return obj instanceof BehaviorDeclaration other
            && declaringType().equals(other.declaringType())
            && longName().equals(other.longName());
    }

    @Override
    final AnnotationsAttribute annotationsAttribute(String tag) {
        return (AnnotationsAttribute)this.<CtBehavior>member().getMethodInfo2().getAttribute(tag);
    }

    final String displaySignature(String behaviorName, boolean simpleNames, boolean paramNames) {
        var builder = new StringBuilder(behaviorName).append('(');
        var parameters = parameters();

        for (int i = 0; i < parameters.size(); i++) {
            if (paramNames && parameters.get(i).name() != null) {
                builder.append(parameters.get(i).name());
                builder.append(": ");
            }

            builder.append(simpleNames ? parameters.get(i).type().simpleName() : parameters.get(i).type().name());

            if (i < parameters.size() - 1) {
                builder.append(", ");
            }
        }

        return builder.append(')').toString();
    }
}

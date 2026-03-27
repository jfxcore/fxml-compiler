// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.type;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.AnnotationMemberValue;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.BooleanMemberValue;
import javassist.bytecode.annotation.ByteMemberValue;
import javassist.bytecode.annotation.CharMemberValue;
import javassist.bytecode.annotation.ClassMemberValue;
import javassist.bytecode.annotation.DoubleMemberValue;
import javassist.bytecode.annotation.EnumMemberValue;
import javassist.bytecode.annotation.FloatMemberValue;
import javassist.bytecode.annotation.IntegerMemberValue;
import javassist.bytecode.annotation.LongMemberValue;
import javassist.bytecode.annotation.MemberValue;
import javassist.bytecode.annotation.MemberValueVisitor;
import javassist.bytecode.annotation.ShortMemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import org.jetbrains.annotations.Nullable;
import java.lang.annotation.Inherited;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class AnnotationDeclaration {

    static List<AnnotationDeclaration> collect(Function<String, AnnotationsAttribute> attributeProvider,
                                               ClassPool classPool) {
        AnnotationsAttribute visibleAnnotationsAttribute = attributeProvider.apply(AnnotationsAttribute.visibleTag);
        AnnotationsAttribute invisibleAnnotationsAttribute = attributeProvider.apply(AnnotationsAttribute.invisibleTag);

        Annotation[] visibleAnnotations = visibleAnnotationsAttribute != null
            ? visibleAnnotationsAttribute.getAnnotations()
            : null;

        Annotation[] invisibleAnnotations = invisibleAnnotationsAttribute != null
            ? invisibleAnnotationsAttribute.getAnnotations()
            : null;

        List<AnnotationDeclaration> annotations = new ArrayList<>(
            (visibleAnnotations != null ? visibleAnnotations.length : 0) +
            (invisibleAnnotations != null ? invisibleAnnotations.length : 0));

        if (visibleAnnotations != null) {
            for (Annotation annotation : visibleAnnotations) {
                annotations.add(new AnnotationDeclaration(
                    annotation, true, classPool != null && isInheritedAnnotation(classPool, annotation)));
            }
        }

        if (invisibleAnnotations != null) {
            for (Annotation annotation : invisibleAnnotations) {
                annotations.add(new AnnotationDeclaration(
                    annotation, false, classPool != null && isInheritedAnnotation(classPool, annotation)));
            }
        }

        return annotations;
    }

    private static boolean isInheritedAnnotation(ClassPool classPool, Annotation annotation) {
        try {
            CtClass annotationClass = classPool.get(annotation.getTypeName());
            Object[] annotationAnnotations = annotationClass.getAnnotations();

            for (Object annotationAnnotation : annotationAnnotations) {
                if (annotationAnnotation instanceof Inherited) {
                    return true;
                }
            }
        } catch (NotFoundException | ClassNotFoundException ignored) {
        }

        return false;
    }

    private final Annotation annotation;
    private final boolean runtimeVisible;
    private final boolean inherited;

    AnnotationDeclaration(Annotation annotation, boolean runtimeVisible, boolean inherited) {
        this.annotation = Objects.requireNonNull(annotation);
        this.runtimeVisible = runtimeVisible;
        this.inherited = inherited;
    }

    public String typeName() {
        return annotation.getTypeName();
    }

    public boolean isRuntimeVisible() {
        return runtimeVisible;
    }

    public boolean isInherited() {
        return inherited;
    }

    public @Nullable String getString(String memberName) {
        String[] value = new String[1];
        MemberValue memberValue = annotation.getMemberValue(memberName);
        if (memberValue == null) {
            return null;
        }

        memberValue.accept(new MemberValueVisitorAdapter() {
            @Override
            public void visitStringMemberValue(StringMemberValue node) {
                value[0] = node.getValue();
            }
        });

        return value[0];
    }

    public int getInt(String memberName) {
        int[] value = new int[1];
        MemberValue memberValue = annotation.getMemberValue(memberName);
        if (memberValue == null) {
            return 0;
        }

        memberValue.accept(new MemberValueVisitorAdapter() {
            @Override
            public void visitIntegerMemberValue(IntegerMemberValue node) {
                value[0] = node.getValue();
            }
        });

        return value[0];
    }

    public int[] getIntArray(String memberName) {
        List<Integer> values = new ArrayList<>();
        MemberValue memberValue = annotation.getMemberValue(memberName);
        if (memberValue == null) {
            return new int[0];
        }

        memberValue.accept(new MemberValueVisitorAdapter() {
            @Override
            public void visitArrayMemberValue(ArrayMemberValue node) {
                Arrays.stream(node.getValue()).forEach(value -> value.accept(new MemberValueVisitorAdapter() {
                    @Override
                    public void visitIntegerMemberValue(IntegerMemberValue node) {
                        values.add(node.getValue());
                    }
                }));
            }
        });

        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); ++i) {
            result[i] = values.get(i);
        }

        return result;
    }

    public String[] getStringArray(String memberName) {
        List<String> values = new ArrayList<>();
        MemberValue memberValue = annotation.getMemberValue(memberName);
        if (memberValue == null) {
            return new String[0];
        }

        memberValue.accept(new MemberValueVisitorAdapter() {
            @Override
            public void visitArrayMemberValue(ArrayMemberValue node) {
                Arrays.stream(node.getValue()).forEach(value -> value.accept(new MemberValueVisitorAdapter() {
                    @Override
                    public void visitStringMemberValue(StringMemberValue node) {
                        values.add(node.getValue());
                    }
                }));
            }
        });

        return values.toArray(String[]::new);
    }

    public String[] getClassArray(String memberName) {
        List<String> values = new ArrayList<>();
        MemberValue memberValue = annotation.getMemberValue(memberName);
        if (memberValue == null) {
            return new String[0];
        }

        memberValue.accept(new MemberValueVisitorAdapter() {
            @Override
            public void visitArrayMemberValue(ArrayMemberValue node) {
                Arrays.stream(node.getValue()).forEach(value -> value.accept(new MemberValueVisitorAdapter() {
                    @Override
                    public void visitClassMemberValue(ClassMemberValue node) {
                        values.add(node.getValue());
                    }
                }));
            }
        });

        return values.toArray(String[]::new);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof AnnotationDeclaration other
            && annotation.getTypeName().equals(other.annotation.getTypeName())
            && annotation.toString().equals(other.annotation.toString());
    }

    @Override
    public int hashCode() {
        return 31 * annotation.getTypeName().hashCode() + annotation.toString().hashCode();
    }

    @Override
    public String toString() {
        return annotation.toString();
    }

    private static class MemberValueVisitorAdapter implements MemberValueVisitor {
        @Override public void visitAnnotationMemberValue(AnnotationMemberValue node) {}
        @Override public void visitArrayMemberValue(ArrayMemberValue node) {}
        @Override public void visitBooleanMemberValue(BooleanMemberValue node) {}
        @Override public void visitByteMemberValue(ByteMemberValue node) {}
        @Override public void visitCharMemberValue(CharMemberValue node) {}
        @Override public void visitDoubleMemberValue(DoubleMemberValue node) {}
        @Override public void visitEnumMemberValue(EnumMemberValue node) {}
        @Override public void visitFloatMemberValue(FloatMemberValue node) {}
        @Override public void visitIntegerMemberValue(IntegerMemberValue node) {}
        @Override public void visitLongMemberValue(LongMemberValue node) {}
        @Override public void visitShortMemberValue(ShortMemberValue node) {}
        @Override public void visitClassMemberValue(ClassMemberValue node) {}
        @Override public void visitStringMemberValue(StringMemberValue node) {}
    }
}

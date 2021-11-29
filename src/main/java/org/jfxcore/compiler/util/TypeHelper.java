// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import java.lang.String;
import java.lang.Object;
import java.lang.RuntimeException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMember;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.SignatureAttribute;
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
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.parse.TypeParser;

import static org.jfxcore.compiler.util.Classes.*;

public class TypeHelper {

    /**
     * Determines whether a type is the box type of a primitive type.
     */
    public static boolean isPrimitiveBox(CtClass boxType, CtClass primitiveType) {
        switch (boxType.getName()) {
            case BooleanName:
                return primitiveType.getName().equals("boolean");
            case ByteName:
                return primitiveType.getName().equals("byte");
            case CharacterName:
                return primitiveType.getName().equals("char");
            case ShortName:
                return primitiveType.getName().equals("short");
            case IntegerName:
                return primitiveType.getName().equals("int");
            case LongName:
                return primitiveType.getName().equals("long");
            case FloatName:
                return primitiveType.getName().equals("float");
            case DoubleName:
                return primitiveType.getName().equals("double");
            case NumberName:
                switch (primitiveType.getName()) {
                    case "byte":
                    case "short":
                    case "int":
                    case "long":
                    case "float":
                    case "double":
                        return true;
                    default:
                        return false;
                }
            default:
                return false;
        }
    }

    /**
     * Returns the primitive numeric type for the specified boxed numeric type.
     * If the specified type is not a boxed numeric type, <code>null</code> is returned.
     */
    public static @Nullable CtClass getPrimitiveType(CtClass type) {
        switch (type.getName()) {
            case BooleanName: return CtClass.booleanType;
            case ByteName: return CtClass.byteType;
            case CharacterName: return CtClass.charType;
            case ShortName: return CtClass.shortType;
            case IntegerName: return CtClass.intType;
            case LongName: return CtClass.longType;
            case FloatName: return CtClass.floatType;
            case DoubleName: return CtClass.doubleType;
        }

        return null;
    }

    /**
     * If the specified type is a primitive type, returns the primitive box.
     * Otherwise, returns the same type.
     */
    public static CtClass getBoxedType(CtClass type) {
        if (type == CtClass.booleanType) {
            return Classes.BooleanType();
        } else if (type == CtClass.byteType) {
            return Classes.ByteType();
        } if (type == CtClass.charType) {
            return Classes.CharacterType();
        } if (type == CtClass.shortType) {
            return Classes.ShortType();
        } if (type == CtClass.intType) {
            return Classes.IntegerType();
        } if (type == CtClass.longType) {
            return Classes.LongType();
        } if (type == CtClass.floatType) {
            return Classes.FloatType();
        } if (type == CtClass.doubleType) {
            return Classes.DoubleType();
        }

        return type;
    }

    /**
     * Returns whether the specified type is a primitive or boxed number.
     */
    public static boolean isNumeric(CtClass type) {
        return isNumericPrimitive(type) || isNumericBox(type);
    }

    /**
     * Returns whether the specified type is a primitive number.
     */
    public static boolean isNumericPrimitive(CtClass type) {
        return
            type == CtClass.byteType || type == CtClass.charType || type == CtClass.shortType
                || type == CtClass.intType || type == CtClass.longType || type == CtClass.floatType
                || type == CtClass.doubleType;
    }

    /**
     * Returns whether the specified type is a boxed number.
     */
    public static boolean isNumericBox(CtClass type) {
        return
            equals(type, ByteType()) || equals(type, CharacterType()) || equals(type, ShortType()) || equals(type, IntegerType())
                || equals(type, LongType()) || equals(type, FloatType()) || equals(type, DoubleType()) || equals(type, NumberType());
    }

    /**
     * Returns whether the specified type is a primitive box.
     */
    public static boolean isPrimitiveBox(CtClass type) {
        return isNumericBox(type) || equals(type, Classes.BooleanType());
    }

    /**
     * If the specified type is short, byte or char, returns int.
     * If the specified type is Short, Byte or Character, returns Integer.
     * Otherwise, returns the specified type.
     */
    public static TypeInstance getWidenedNumericType(TypeInstance type) {
        switch (type.getName()) {
            case "short":
            case "byte":
            case "char":
                return new TypeInstance(CtClass.intType);

            case ShortName:
            case ByteName:
            case CharacterName:
                return new TypeInstance(Classes.IntegerType());
        }

        return type;
    }

    /**
     * If the specified type is short, byte or char, returns int.
     * If the specified type is Short, Byte or Character, returns Integer.
     * Otherwise, returns the specified type.
     */
    public static CtClass getWidenedNumericType(CtClass type) {
        switch (type.getName()) {
            case "short":
            case "byte":
            case "char":
                return CtClass.intType;

            case ShortName:
            case ByteName:
            case CharacterName:
                return Classes.IntegerType();
        }

        return type;
    }

    /**
     * Gets the boxed default value for the specified type.
     */
    public static Object getDefaultValue(CtClass type) {
        switch (type.getName()) {
            case "byte":
            case ByteName:
                return (byte)0;
            case "short":
            case ShortName:
                return (short)0;
            case "int":
            case IntegerName:
                return 0;
            case "long":
            case LongName:
                return 0L;
            case "char":
            case CharacterName:
                return (char)0;
            case "float":
            case FloatName:
                return 0.0F;
            case "double":
            case DoubleName:
                return 0.0D;
            case "boolean":
            case BooleanName:
                return false;
            default:
                throw new IllegalArgumentException();
        }
    }

    public static class MemberValueVisitorAdapter implements MemberValueVisitor {
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

    /**
     * Returns the value of the specified annotation member.
     */
    public static String getAnnotationString(Annotation annotation, String memberName) {
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

    /**
     * Returns the value of the specified annotation member.
     */
    public static int getAnnotationInt(Annotation annotation, String memberName) {
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

    /**
     * Returns the value of the specified annotation member.
     */
    public static int[] getAnnotationIntArray(Annotation annotation, String memberName) {
        List<Integer> list = new ArrayList<>();
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
                        list.add(node.getValue());
                    }
                }));
            }
        });

        int[] result = new int[list.size()];
        for (int i = 0; i < list.size(); ++i) {
            result[i] = list.get(i);
        }

        return result;
    }

    /**
     * Returns the value of the specified annotation member.
     */
    public static String[] getAnnotationStringArray(Annotation annotation, String memberName) {
        List<String> list = new ArrayList<>();
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
                        list.add(node.getValue());
                    }
                }));
            }
        });

        return list.toArray(String[]::new);
    }

    public static @Nullable TypeInstance tryGetArrayComponentType(CtBehavior method, int paramIndex) {
        try {
            SignatureAttribute.MethodSignature signature =
                SignatureAttribute.toMethodSignature(method.getSignature());
            SignatureAttribute.Type type = signature.getParameterTypes()[paramIndex];

            if (type instanceof SignatureAttribute.ArrayType) {
                SignatureAttribute.ArrayType arrayType = (SignatureAttribute.ArrayType)type;
                if (arrayType.getDimension() != 1) {
                    return null;
                }

                TypeParser parser = new TypeParser(arrayType.getComponentType().jvmTypeName(), new Location(0, 0));
                return parser.parse().get(0);
            }

            return null;
        } catch (BadBytecode ex) {
            throw GeneralErrors.internalError(ex.getMessage());
        }
    }

    public static TypeInstance getTypeInstance(Node node) {
        if (!(node instanceof ValueNode)) {
            throw new RuntimeException("Expected " + ValueNode.class.getSimpleName());
        }

        TypeNode typeNode = ((ValueNode)node).getType();
        if (!(typeNode instanceof ResolvedTypeNode)) {
            throw new RuntimeException("Expected " + ResolvedTypeNode.class.getSimpleName());
        }

        return ((ResolvedTypeNode)typeNode).getTypeInstance();
    }

    public static CtClass getJvmType(Node node) {
        if (!(node instanceof ValueNode)) {
            throw new RuntimeException("Expected " + ValueNode.class.getSimpleName());
        }

        TypeNode typeNode = ((ValueNode)node).getType();
        if (!(typeNode instanceof ResolvedTypeNode)) {
            throw new RuntimeException("Expected " + ResolvedTypeNode.class.getSimpleName());
        }

        return ((ResolvedTypeNode)typeNode).getJvmType();
    }

    public static int getSlots(CtClass type) {
        return type == CtClass.longType || type == CtClass.doubleType ? 2 : 1;
    }

    public static int hashCode(Collection<?> types) {
        int result = 1;

        for (Object type : types) {
            int c;

            if (type instanceof CtClass) {
                c = hashCode((CtClass)type);
            } else if (type instanceof CtMember) {
                c = hashCode((CtMember)type);
            } else {
                throw new IllegalArgumentException();
            }

            result = 31 * result + c;
        }

        return result;
    }

    public static int hashCode(CtClass type) {
        return type != null ? type.getName().hashCode() : 0;
    }

    public static int hashCode(CtMember member) {
        if (member == null) {
            return 0;
        }

        if (member instanceof CtField) {
            int result = 31 + hashCode(member.getDeclaringClass());
            return 31 * result + member.getName().hashCode();
        }

        return ((CtBehavior)member).getLongName().hashCode();
    }

    public static boolean equals(Collection<? extends CtClass> type0, Collection<? extends CtClass> type1) {
        if (type0.size() != type1.size()) {
            return false;
        }

        Iterator<? extends CtClass> it0 = type0.iterator();
        Iterator<? extends CtClass> it1 = type0.iterator();

        while (it0.hasNext()) {
            if (!equals(it0.next(), it1.next())) {
                return false;
            }
        }

        return true;
    }

    public static boolean equals(CtClass type0, CtClass type1) {
        if (type0 == null && type1 != null || type0 != null && type1 == null) {
            return false;
        }

        if (type0 == null) {
            return true;
        }

        return type0.getName().equals(type1.getName());
    }

    public static boolean equals(CtMember member0, CtMember member1) {
        if (member0 == null && member1 != null || member0 != null && member1 == null) {
            return false;
        }

        if (member0 == null) {
            return true;
        }

        if (!equals(member0.getDeclaringClass(), member1.getDeclaringClass())) {
            return false;
        }

        if (member0 instanceof CtField && member1 instanceof CtField) {
            return member0.getName().equals(member1.getName());
        }

        if (member0 instanceof CtBehavior && member1 instanceof CtBehavior) {
            return ((CtBehavior)member0).getLongName().equals(((CtBehavior)member1).getLongName());
        }

        return false;
    }

}

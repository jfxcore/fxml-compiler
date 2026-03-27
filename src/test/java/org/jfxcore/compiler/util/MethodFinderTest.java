// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.TestBase;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.BehaviorDeclaration;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.jfxcore.compiler.type.TypeSymbols.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unused")
public class MethodFinderTest extends TestBase {

    public static void Long_v_Double(long x) {}
    public static void Long_v_Double(double x) {}
    public static void Long_v_Float(long x) {}
    public static void Long_v_Float(float x) {}
    public static void Long_v_Int(long x) {}
    public static void Long_v_Int(int x) {}
    public static void Long_v_Short(long x) {}
    public static void Long_v_Short(short x) {}
    public static void Long_v_Char(long x) {}
    public static void Long_v_Char(char x) {}
    public static void Long_v_Byte(long x) {}
    public static void Long_v_Byte(byte x) {}

    public static void Int_v_Double(int x) {}
    public static void Int_v_Double(double x) {}
    public static void Int_v_Float(int x) {}
    public static void Int_v_Float(float x) {}
    public static void Int_v_Short(int x) {}
    public static void Int_v_Short(short x) {}
    public static void Int_v_Char(int x) {}
    public static void Int_v_Char(char x) {}
    public static void Int_v_Byte(int x) {}
    public static void Int_v_Byte(byte x) {}
    
    public static void Short_v_Double(short x) {}
    public static void Short_v_Double(double x) {}
    public static void Short_v_Float(short x) {}
    public static void Short_v_Float(float x) {}
    public static void Short_v_Char(short x) {}
    public static void Short_v_Char(char x) {}
    public static void Short_v_Byte(short x) {}
    public static void Short_v_Byte(byte x) {}

    public static void Char_v_Double(char x) {}
    public static void Char_v_Double(double x) {}
    public static void Char_v_Float(char x) {}
    public static void Char_v_Float(float x) {}

    public static void Float_v_Double(float x) {}
    public static void Float_v_Double(double x) {}

    public static void String_v_Object(String x) {}
    public static void String_v_Object(Object x) {}

    public static void Fixed_v_Varargs(Object x) {}
    public static void Fixed_v_Varargs(String... x) {}

    public static void ExplicitArray_v_Varargs(Object[] x) {}
    public static void ExplicitArray_v_Varargs(String... x) {}

    public static void Varargs_String_v_Object(String... x) {}
    public static void Varargs_String_v_Object(Object... x) {}

    public static void Ambiguous_Varargs(String... x) {}
    public static void Ambiguous_Varargs(Integer... x) {}

    public static void Phase_Strict_v_Loose(int x) {}
    public static void Phase_Strict_v_Loose(Integer x) {}

    public static void Phase_Loose_v_Varargs(Integer x) {}
    public static void Phase_Loose_v_Varargs(int... x) {}

    public static void Loose_Boxing_Only(Integer x) {}
    public static void Loose_Unboxing_Only(int x) {}
    public static void Loose_Boxing_Widening_Reference(Number x) {}

    public static void ZeroArg_Varargs(String... x) {}

    public static void Mixed_Fixed_And_Varargs(String first, Object... rest) {}
    public static void Mixed_Fixed_And_Varargs(Object first, Object... rest) {}

    public static void Ambiguous_NonVarargs(StringBuilder x) {}
    public static void Ambiguous_NonVarargs(StringBuffer x) {}

    public static Object ReturnType_Filter(String x) { return x; }
    public static String ReturnType_Filter(Object x) { return null; }
    public static Integer ReturnType_Incompatible(String x) { return 0; }

    public void Static_Filter(String x) {}
    public static void Static_Filter(Object x) {}

    public static void Arity_Mismatch(String x) {}

    public static <T> T Generic_Method_With_Witness(T x) { return x; }

    public static class ConstructorTarget {
        public ConstructorTarget(Object x) {}
        public ConstructorTarget(String... x) {}
    }

    public static class GenericConstructorTarget {
        public <T extends Number> GenericConstructorTarget(T x) {}
    }

    public record Execution(TypeDeclaration argType, TypeDeclaration selectedParamType, String methodName) {
        @Override
        public String toString() {
            return methodName + "(" + argType.name() + ")";
        }
    }
    
    static Stream<?> params() {
        return Arrays.stream(new Execution[] {
            new Execution(byteDecl(), longDecl(), "Long_v_Double"),
            new Execution(charDecl(), longDecl(), "Long_v_Double"),
            new Execution(shortDecl(), longDecl(), "Long_v_Double"),
            new Execution(intDecl(), longDecl(), "Long_v_Double"),
            new Execution(longDecl(), longDecl(), "Long_v_Double"),
            new Execution(floatDecl(), doubleDecl(), "Long_v_Double"),
            new Execution(doubleDecl(), doubleDecl(), "Long_v_Double"),
            new Execution(byteDecl(), longDecl(), "Long_v_Float"),
            new Execution(charDecl(), longDecl(), "Long_v_Float"),
            new Execution(shortDecl(), longDecl(), "Long_v_Float"),
            new Execution(intDecl(), longDecl(), "Long_v_Float"),
            new Execution(longDecl(), longDecl(), "Long_v_Float"),
            new Execution(floatDecl(), floatDecl(), "Long_v_Float"),
            new Execution(doubleDecl(), voidDecl(), "Long_v_Float"),
            new Execution(byteDecl(), intDecl(), "Long_v_Int"),
            new Execution(charDecl(), intDecl(), "Long_v_Int"),
            new Execution(shortDecl(), intDecl(), "Long_v_Int"),
            new Execution(intDecl(), intDecl(), "Long_v_Int"),
            new Execution(longDecl(), longDecl(), "Long_v_Int"),
            new Execution(floatDecl(), voidDecl(), "Long_v_Int"),
            new Execution(doubleDecl(), voidDecl(), "Long_v_Int"),
            new Execution(byteDecl(), shortDecl(), "Long_v_Short"),
            new Execution(charDecl(), longDecl(), "Long_v_Short"),
            new Execution(shortDecl(), shortDecl(), "Long_v_Short"),
            new Execution(intDecl(), longDecl(), "Long_v_Short"),
            new Execution(longDecl(), longDecl(), "Long_v_Short"),
            new Execution(floatDecl(), voidDecl(), "Long_v_Short"),
            new Execution(doubleDecl(), voidDecl(), "Long_v_Short"),
            new Execution(byteDecl(), longDecl(), "Long_v_Char"),
            new Execution(charDecl(), charDecl(), "Long_v_Char"),
            new Execution(shortDecl(), longDecl(), "Long_v_Char"),
            new Execution(intDecl(), longDecl(), "Long_v_Char"),
            new Execution(longDecl(), longDecl(), "Long_v_Char"),
            new Execution(floatDecl(), voidDecl(), "Long_v_Char"),
            new Execution(doubleDecl(), voidDecl(), "Long_v_Char"),
            new Execution(byteDecl(), byteDecl(), "Long_v_Byte"),
            new Execution(charDecl(), longDecl(), "Long_v_Byte"),
            new Execution(shortDecl(), longDecl(), "Long_v_Byte"),
            new Execution(intDecl(), longDecl(), "Long_v_Byte"),
            new Execution(longDecl(), longDecl(), "Long_v_Byte"),
            new Execution(floatDecl(), voidDecl(), "Long_v_Byte"),
            new Execution(doubleDecl(), voidDecl(), "Long_v_Byte"),
                
            new Execution(byteDecl(), intDecl(), "Int_v_Double"),
            new Execution(charDecl(), intDecl(), "Int_v_Double"),
            new Execution(shortDecl(), intDecl(), "Int_v_Double"),
            new Execution(intDecl(), intDecl(), "Int_v_Double"),
            new Execution(longDecl(), doubleDecl(), "Int_v_Double"),
            new Execution(floatDecl(), doubleDecl(), "Int_v_Double"),
            new Execution(doubleDecl(), doubleDecl(), "Int_v_Double"),
            new Execution(byteDecl(), intDecl(), "Int_v_Float"),
            new Execution(charDecl(), intDecl(), "Int_v_Float"),
            new Execution(shortDecl(), intDecl(), "Int_v_Float"),
            new Execution(intDecl(), intDecl(), "Int_v_Float"),
            new Execution(longDecl(), floatDecl(), "Int_v_Float"),
            new Execution(floatDecl(), floatDecl(), "Int_v_Float"),
            new Execution(doubleDecl(), voidDecl(), "Int_v_Float"),
            new Execution(byteDecl(), intDecl(), "Int_v_Char"),
            new Execution(charDecl(), charDecl(), "Int_v_Char"),
            new Execution(shortDecl(), intDecl(), "Int_v_Char"),
            new Execution(intDecl(), intDecl(), "Int_v_Char"),
            new Execution(longDecl(), voidDecl(), "Int_v_Char"),
            new Execution(floatDecl(), voidDecl(), "Int_v_Char"),
            new Execution(doubleDecl(), voidDecl(), "Int_v_Char"),
            new Execution(byteDecl(), byteDecl(), "Int_v_Byte"),
            new Execution(charDecl(), intDecl(), "Int_v_Byte"),
            new Execution(shortDecl(), intDecl(), "Int_v_Byte"),
            new Execution(intDecl(), intDecl(), "Int_v_Byte"),
            new Execution(longDecl(), voidDecl(), "Int_v_Byte"),
            new Execution(floatDecl(), voidDecl(), "Int_v_Byte"),
            new Execution(doubleDecl(), voidDecl(), "Int_v_Byte"),

            new Execution(byteDecl(), shortDecl(), "Short_v_Double"),
            new Execution(charDecl(), doubleDecl(), "Short_v_Double"),
            new Execution(shortDecl(), shortDecl(), "Short_v_Double"),
            new Execution(intDecl(), doubleDecl(), "Short_v_Double"),
            new Execution(longDecl(), doubleDecl(), "Short_v_Double"),
            new Execution(floatDecl(), doubleDecl(), "Short_v_Double"),
            new Execution(doubleDecl(), doubleDecl(), "Short_v_Double"),
            new Execution(byteDecl(), shortDecl(), "Short_v_Float"),
            new Execution(charDecl(), floatDecl(), "Short_v_Float"),
            new Execution(shortDecl(), shortDecl(), "Short_v_Float"),
            new Execution(intDecl(), floatDecl(), "Short_v_Float"),
            new Execution(longDecl(), floatDecl(), "Short_v_Float"),
            new Execution(floatDecl(), floatDecl(), "Short_v_Float"),
            new Execution(doubleDecl(), voidDecl(), "Short_v_Float"),
            new Execution(byteDecl(), shortDecl(), "Short_v_Char"),
            new Execution(charDecl(), charDecl(), "Short_v_Char"),
            new Execution(shortDecl(), shortDecl(), "Short_v_Char"),
            new Execution(intDecl(), voidDecl(), "Short_v_Char"),
            new Execution(longDecl(), voidDecl(), "Short_v_Char"),
            new Execution(floatDecl(), voidDecl(), "Short_v_Char"),
            new Execution(doubleDecl(), voidDecl(), "Short_v_Char"),
            new Execution(byteDecl(), byteDecl(), "Short_v_Byte"),
            new Execution(charDecl(), voidDecl(), "Short_v_Byte"),
            new Execution(shortDecl(), shortDecl(), "Short_v_Byte"),
            new Execution(intDecl(), voidDecl(), "Short_v_Byte"),
            new Execution(longDecl(), voidDecl(), "Short_v_Byte"),
            new Execution(floatDecl(), voidDecl(), "Short_v_Byte"),
            new Execution(doubleDecl(), voidDecl(), "Short_v_Byte"),

            new Execution(byteDecl(), doubleDecl(), "Char_v_Double"),
            new Execution(charDecl(), charDecl(), "Char_v_Double"),
            new Execution(shortDecl(), doubleDecl(), "Char_v_Double"),
            new Execution(intDecl(), doubleDecl(), "Char_v_Double"),
            new Execution(longDecl(), doubleDecl(), "Char_v_Double"),
            new Execution(floatDecl(), doubleDecl(), "Char_v_Double"),
            new Execution(doubleDecl(), doubleDecl(), "Char_v_Double"),
            new Execution(byteDecl(), floatDecl(), "Char_v_Float"),
            new Execution(charDecl(), charDecl(), "Char_v_Float"),
            new Execution(shortDecl(), floatDecl(), "Char_v_Float"),
            new Execution(intDecl(), floatDecl(), "Char_v_Float"),
            new Execution(longDecl(), floatDecl(), "Char_v_Float"),
            new Execution(floatDecl(), floatDecl(), "Char_v_Float"),
            new Execution(doubleDecl(), voidDecl(), "Char_v_Float"),

            new Execution(byteDecl(), floatDecl(), "Float_v_Double"),
            new Execution(charDecl(), floatDecl(), "Float_v_Double"),
            new Execution(shortDecl(), floatDecl(), "Float_v_Double"),
            new Execution(intDecl(), floatDecl(), "Float_v_Double"),
            new Execution(longDecl(), floatDecl(), "Float_v_Double"),
            new Execution(floatDecl(), floatDecl(), "Float_v_Double"),
            new Execution(doubleDecl(), doubleDecl(), "Float_v_Double"),
        });
    }

    @ParameterizedTest
    @MethodSource("params")
    public void PrimitiveOverloadTest(Execution execution) {
        var argType = new TypeInvoker(SourceInfo.none()).invokeType(execution.argType());
        var method = findMethod(execution.methodName(), argType);

        if (execution.selectedParamType().equals(voidDecl())) {
            assertNull(method);
        } else {
            assertSelectedParameterTypes(method, execution.selectedParamType().name());
        }
    }

    @Test
    public void ReferenceOverloadTest() {
        assertSelectedParameterTypes(
            findMethod("String_v_Object", TypeInstance.StringType()),
            String.class.getName());
    }

    @Test
    public void Fixed_Arity_Method_Beats_Varargs_Method() {
        assertSelectedParameterTypes(
            findMethod("Fixed_v_Varargs", TypeInstance.StringType()),
            Object.class.getName());
    }

    @Test
    public void Explicit_Array_Argument_Uses_Array_Parameter_Type_For_Specificity() {
        assertSelectedParameterTypes(
            findMethod("ExplicitArray_v_Varargs", TypeInstance.StringType().withDimensions(1)),
            String.class.getName() + "[]");
    }

    @Test
    public void Most_Specific_Varargs_Method_Is_Selected_For_Expanded_Arguments() {
        assertSelectedParameterTypes(
            findMethod("Varargs_String_v_Object", TypeInstance.StringType(), TypeInstance.StringType()),
            String.class.getName() + "[]");
    }

    @Test
    public void Unrelated_Varargs_Overloads_Are_Ambiguous_For_Null_Argument() {
        assertNull(findMethod("Ambiguous_Varargs", TypeInstance.nullType()));
    }

    @Test
    public void Strict_Phase_Method_Beats_Loose_Phase_Method() {
        assertSelectedParameterTypes(
            findMethod("Phase_Strict_v_Loose", TypeInstance.intType()),
            int.class.getName());
    }

    @Test
    public void Loose_Phase_Method_Beats_Varargs_Phase_Method() {
        assertSelectedParameterTypes(
            findMethod("Phase_Loose_v_Varargs", TypeInstance.intType()),
            Integer.class.getName());
    }

    @Test
    public void Boxing_Is_Allowed_In_Loose_Phase() {
        assertSelectedParameterTypes(
            findMethod("Loose_Boxing_Only", TypeInstance.intType()),
            Integer.class.getName());
    }

    @Test
    public void Unboxing_Is_Allowed_In_Loose_Phase() {
        assertSelectedParameterTypes(
            findMethod("Loose_Unboxing_Only", TypeInstance.IntegerType()),
            int.class.getName());
    }

    @Test
    public void Boxing_Then_Widening_Reference_Is_Allowed_In_Loose_Phase() {
        assertSelectedParameterTypes(
            findMethod("Loose_Boxing_Widening_Reference", TypeInstance.intType()),
            Number.class.getName());
    }

    @Test
    public void Zero_Argument_Varargs_Method_Is_Applicable() {
        assertSelectedParameterTypes(findMethod("ZeroArg_Varargs"), String.class.getName() + "[]");
    }

    @Test
    public void Mixed_Fixed_And_Varargs_Uses_More_Specific_Fixed_Prefix() {
        assertSelectedParameterTypes(
            findMethod("Mixed_Fixed_And_Varargs", TypeInstance.StringType(), TypeInstance.StringType()),
            String.class.getName(), Object.class.getName() + "[]");
    }

    @Test
    public void Unrelated_NonVarargs_Overloads_Are_Ambiguous_For_Null_Argument() {
        assertNull(findMethod("Ambiguous_NonVarargs", TypeInstance.nullType()));
    }

    @Test
    public void Return_Type_Filter_Selects_Compatible_Overload() {
        assertSelectedParameterTypes(
            findMethod("ReturnType_Filter", false, TypeInstance.StringType(), List.of(), TypeInstance.StringType()),
            Object.class.getName());
    }

    @Test
    public void Return_Type_Filter_Rejects_Incompatible_Method() {
        assertNull(findMethod("ReturnType_Incompatible", false, TypeInstance.StringType(),
                              List.of(), TypeInstance.StringType()));
    }

    @Test
    public void Static_Invocation_Rejects_Instance_Methods() {
        assertSelectedParameterTypes(
            findMethod("Static_Filter", true, null, List.of(), TypeInstance.StringType()),
            Object.class.getName());
    }

    @Test
    public void Instance_Invocation_Can_Select_Instance_Methods() {
        assertSelectedParameterTypes(
            findMethod("Static_Filter", false, null, List.of(), TypeInstance.StringType()),
            String.class.getName());
    }

    @Test
    public void Arity_Mismatch_Returns_Null_For_Too_Few_Arguments() {
        assertNull(findMethod("Arity_Mismatch"));
    }

    @Test
    public void Arity_Mismatch_Returns_Null_For_Too_Many_Arguments() {
        assertNull(findMethod("Arity_Mismatch", TypeInstance.StringType(), TypeInstance.StringType()));
    }

    @Test
    public void Constructor_Resolution_Prefers_Fixed_Arity_Over_Varargs() {
        assertSelectedParameterTypes(
            findConstructor(ConstructorTarget.class, List.of(), TypeInstance.StringType()),
            Object.class.getName());
    }

    @Test
    public void Generic_Method_Type_Witness_Instantiates_Parameters_And_Return_Type() {
        MethodDeclaration method = findMethod(
            "Generic_Method_With_Witness",
            false,
            TypeInstance.StringType(),
            List.of(TypeInstance.StringType()),
            TypeInstance.StringType());

        assertNotNull(method);
        assertInvokedParameterTypes(method, List.of(TypeInstance.StringType()), String.class.getName());
        assertInvokedReturnType(method, List.of(TypeInstance.StringType()), String.class.getName());
    }

    @Test
    public void Generic_Constructor_Type_Witness_Instantiates_Parameters() {
        ConstructorDeclaration constructor = findConstructor(
            GenericConstructorTarget.class,
            List.of(TypeInstance.IntegerType()),
            TypeInstance.IntegerType());

        assertNotNull(constructor);
        assertInvokedParameterTypes(constructor, List.of(TypeInstance.IntegerType()), Integer.class.getName());
    }

    private static MethodDeclaration findMethod(String methodName, TypeInstance... argumentTypes) {
        return findMethod(methodName, false, null, List.of(), argumentTypes);
    }

    private static MethodDeclaration findMethod(
            String methodName,
            boolean staticInvocation,
            TypeInstance returnType,
            List<TypeInstance> typeWitnesses,
            TypeInstance... argumentTypes) {
        var resolver = new Resolver(SourceInfo.none());
        var invoker = new TypeInvoker(SourceInfo.none());
        var declaringClass = resolver.resolveClass(MethodFinderTest.class.getName());
        var invokingClass = invoker.invokeType(declaringClass);

        return new MethodFinder(invokingClass, declaringClass).findMethod(
            methodName,
            staticInvocation,
            returnType,
            typeWitnesses,
            List.of(argumentTypes),
            Arrays.stream(argumentTypes).map(type -> SourceInfo.none()).toList(),
            null,
            SourceInfo.none());
    }

    private static ConstructorDeclaration findConstructor(
            Class<?> declaringClass,
            List<TypeInstance> typeWitnesses,
            TypeInstance... argumentTypes) {
        var resolver = new Resolver(SourceInfo.none());
        var invoker = new TypeInvoker(SourceInfo.none());
        var resolvedClass = resolver.resolveClass(declaringClass.getName());
        var invokingClass = invoker.invokeType(resolver.resolveClass(MethodFinderTest.class.getName()));

        return new MethodFinder(invokingClass, resolvedClass).findConstructor(
            typeWitnesses,
            List.of(argumentTypes),
            Arrays.stream(argumentTypes).map(type -> SourceInfo.none()).toList(),
            null,
            SourceInfo.none());
    }

    private static void assertSelectedParameterTypes(BehaviorDeclaration behavior, String... expectedTypeNames) {
        var params = behavior.parameters();
        assertEquals(expectedTypeNames.length, params.size());

        for (int i = 0; i < expectedTypeNames.length; ++i) {
            assertEquals(expectedTypeNames[i], params.get(i).type().name());
        }
    }

    private static void assertInvokedParameterTypes(
            BehaviorDeclaration behavior,
            List<TypeInstance> typeWitnesses,
            String... expectedTypeNames) {
        TypeInstance[] paramTypes = new TypeInvoker(SourceInfo.none()).invokeParameterTypes(
            behavior,
            List.of(typeOf(MethodFinderTest.class)),
            typeWitnesses);

        assertEquals(expectedTypeNames.length, paramTypes.length);

        for (int i = 0; i < expectedTypeNames.length; ++i) {
            assertEquals(expectedTypeNames[i], paramTypes[i].javaName());
        }
    }

    private static void assertInvokedReturnType(
            MethodDeclaration method,
            List<TypeInstance> typeWitnesses,
            String expectedTypeName) {
        TypeInstance returnType = new TypeInvoker(SourceInfo.none()).invokeReturnType(
            method,
            List.of(typeOf(MethodFinderTest.class)),
            typeWitnesses);

        assertEquals(expectedTypeName, returnType.javaName());
    }

    private static TypeInstance typeOf(Class<?> type) {
        return new TypeInvoker(SourceInfo.none()).invokeType(
            new Resolver(SourceInfo.none()).resolveClass(type.getName()));
    }
}

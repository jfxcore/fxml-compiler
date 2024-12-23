// Copyright (c) 2022, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CtClass;
import org.jfxcore.compiler.TestBase;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

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

    private static record Execution(CtClass argType, CtClass selectedParamType, String methodName) {
        @Override
        public String toString() {
            return methodName + "(" + argType.getName() + ")";
        }
    }
    
    static Stream<?> params() {
        return Arrays.stream(new Execution[] {
            new Execution(CtClass.byteType, CtClass.longType, "Long_v_Double"),
            new Execution(CtClass.charType, CtClass.longType, "Long_v_Double"),
            new Execution(CtClass.shortType, CtClass.longType, "Long_v_Double"),
            new Execution(CtClass.intType, CtClass.longType, "Long_v_Double"),
            new Execution(CtClass.longType, CtClass.longType, "Long_v_Double"),
            new Execution(CtClass.floatType, CtClass.doubleType, "Long_v_Double"),
            new Execution(CtClass.doubleType, CtClass.doubleType, "Long_v_Double"),
            new Execution(CtClass.byteType, CtClass.longType, "Long_v_Float"),
            new Execution(CtClass.charType, CtClass.longType, "Long_v_Float"),
            new Execution(CtClass.shortType, CtClass.longType, "Long_v_Float"),
            new Execution(CtClass.intType, CtClass.longType, "Long_v_Float"),
            new Execution(CtClass.longType, CtClass.longType, "Long_v_Float"),
            new Execution(CtClass.floatType, CtClass.floatType, "Long_v_Float"),
            new Execution(CtClass.doubleType, CtClass.voidType, "Long_v_Float"),
            new Execution(CtClass.byteType, CtClass.intType, "Long_v_Int"),
            new Execution(CtClass.charType, CtClass.intType, "Long_v_Int"),
            new Execution(CtClass.shortType, CtClass.intType, "Long_v_Int"),
            new Execution(CtClass.intType, CtClass.intType, "Long_v_Int"),
            new Execution(CtClass.longType, CtClass.longType, "Long_v_Int"),
            new Execution(CtClass.floatType, CtClass.voidType, "Long_v_Int"),
            new Execution(CtClass.doubleType, CtClass.voidType, "Long_v_Int"),
            new Execution(CtClass.byteType, CtClass.shortType, "Long_v_Short"),
            new Execution(CtClass.charType, CtClass.longType, "Long_v_Short"),
            new Execution(CtClass.shortType, CtClass.shortType, "Long_v_Short"),
            new Execution(CtClass.intType, CtClass.longType, "Long_v_Short"),
            new Execution(CtClass.longType, CtClass.longType, "Long_v_Short"),
            new Execution(CtClass.floatType, CtClass.voidType, "Long_v_Short"),
            new Execution(CtClass.doubleType, CtClass.voidType, "Long_v_Short"),
            new Execution(CtClass.byteType, CtClass.longType, "Long_v_Char"),
            new Execution(CtClass.charType, CtClass.charType, "Long_v_Char"),
            new Execution(CtClass.shortType, CtClass.longType, "Long_v_Char"),
            new Execution(CtClass.intType, CtClass.longType, "Long_v_Char"),
            new Execution(CtClass.longType, CtClass.longType, "Long_v_Char"),
            new Execution(CtClass.floatType, CtClass.voidType, "Long_v_Char"),
            new Execution(CtClass.doubleType, CtClass.voidType, "Long_v_Char"),
            new Execution(CtClass.byteType, CtClass.byteType, "Long_v_Byte"),
            new Execution(CtClass.charType, CtClass.longType, "Long_v_Byte"),
            new Execution(CtClass.shortType, CtClass.longType, "Long_v_Byte"),
            new Execution(CtClass.intType, CtClass.longType, "Long_v_Byte"),
            new Execution(CtClass.longType, CtClass.longType, "Long_v_Byte"),
            new Execution(CtClass.floatType, CtClass.voidType, "Long_v_Byte"),
            new Execution(CtClass.doubleType, CtClass.voidType, "Long_v_Byte"),
                
            new Execution(CtClass.byteType, CtClass.intType, "Int_v_Double"),
            new Execution(CtClass.charType, CtClass.intType, "Int_v_Double"),
            new Execution(CtClass.shortType, CtClass.intType, "Int_v_Double"),
            new Execution(CtClass.intType, CtClass.intType, "Int_v_Double"),
            new Execution(CtClass.longType, CtClass.doubleType, "Int_v_Double"),
            new Execution(CtClass.floatType, CtClass.doubleType, "Int_v_Double"),
            new Execution(CtClass.doubleType, CtClass.doubleType, "Int_v_Double"),
            new Execution(CtClass.byteType, CtClass.intType, "Int_v_Float"),
            new Execution(CtClass.charType, CtClass.intType, "Int_v_Float"),
            new Execution(CtClass.shortType, CtClass.intType, "Int_v_Float"),
            new Execution(CtClass.intType, CtClass.intType, "Int_v_Float"),
            new Execution(CtClass.longType, CtClass.floatType, "Int_v_Float"),
            new Execution(CtClass.floatType, CtClass.floatType, "Int_v_Float"),
            new Execution(CtClass.doubleType, CtClass.voidType, "Int_v_Float"),
            new Execution(CtClass.byteType, CtClass.intType, "Int_v_Char"),
            new Execution(CtClass.charType, CtClass.charType, "Int_v_Char"),
            new Execution(CtClass.shortType, CtClass.intType, "Int_v_Char"),
            new Execution(CtClass.intType, CtClass.intType, "Int_v_Char"),
            new Execution(CtClass.longType, CtClass.voidType, "Int_v_Char"),
            new Execution(CtClass.floatType, CtClass.voidType, "Int_v_Char"),
            new Execution(CtClass.doubleType, CtClass.voidType, "Int_v_Char"),
            new Execution(CtClass.byteType, CtClass.byteType, "Int_v_Byte"),
            new Execution(CtClass.charType, CtClass.intType, "Int_v_Byte"),
            new Execution(CtClass.shortType, CtClass.intType, "Int_v_Byte"),
            new Execution(CtClass.intType, CtClass.intType, "Int_v_Byte"),
            new Execution(CtClass.longType, CtClass.voidType, "Int_v_Byte"),
            new Execution(CtClass.floatType, CtClass.voidType, "Int_v_Byte"),
            new Execution(CtClass.doubleType, CtClass.voidType, "Int_v_Byte"),

            new Execution(CtClass.byteType, CtClass.shortType, "Short_v_Double"),
            new Execution(CtClass.charType, CtClass.doubleType, "Short_v_Double"),
            new Execution(CtClass.shortType, CtClass.shortType, "Short_v_Double"),
            new Execution(CtClass.intType, CtClass.doubleType, "Short_v_Double"),
            new Execution(CtClass.longType, CtClass.doubleType, "Short_v_Double"),
            new Execution(CtClass.floatType, CtClass.doubleType, "Short_v_Double"),
            new Execution(CtClass.doubleType, CtClass.doubleType, "Short_v_Double"),
            new Execution(CtClass.byteType, CtClass.shortType, "Short_v_Float"),
            new Execution(CtClass.charType, CtClass.floatType, "Short_v_Float"),
            new Execution(CtClass.shortType, CtClass.shortType, "Short_v_Float"),
            new Execution(CtClass.intType, CtClass.floatType, "Short_v_Float"),
            new Execution(CtClass.longType, CtClass.floatType, "Short_v_Float"),
            new Execution(CtClass.floatType, CtClass.floatType, "Short_v_Float"),
            new Execution(CtClass.doubleType, CtClass.voidType, "Short_v_Float"),
            new Execution(CtClass.byteType, CtClass.shortType, "Short_v_Char"),
            new Execution(CtClass.charType, CtClass.charType, "Short_v_Char"),
            new Execution(CtClass.shortType, CtClass.shortType, "Short_v_Char"),
            new Execution(CtClass.intType, CtClass.voidType, "Short_v_Char"),
            new Execution(CtClass.longType, CtClass.voidType, "Short_v_Char"),
            new Execution(CtClass.floatType, CtClass.voidType, "Short_v_Char"),
            new Execution(CtClass.doubleType, CtClass.voidType, "Short_v_Char"),
            new Execution(CtClass.byteType, CtClass.byteType, "Short_v_Byte"),
            new Execution(CtClass.charType, CtClass.voidType, "Short_v_Byte"),
            new Execution(CtClass.shortType, CtClass.shortType, "Short_v_Byte"),
            new Execution(CtClass.intType, CtClass.voidType, "Short_v_Byte"),
            new Execution(CtClass.longType, CtClass.voidType, "Short_v_Byte"),
            new Execution(CtClass.floatType, CtClass.voidType, "Short_v_Byte"),
            new Execution(CtClass.doubleType, CtClass.voidType, "Short_v_Byte"),

            new Execution(CtClass.byteType, CtClass.doubleType, "Char_v_Double"),
            new Execution(CtClass.charType, CtClass.charType, "Char_v_Double"),
            new Execution(CtClass.shortType, CtClass.doubleType, "Char_v_Double"),
            new Execution(CtClass.intType, CtClass.doubleType, "Char_v_Double"),
            new Execution(CtClass.longType, CtClass.doubleType, "Char_v_Double"),
            new Execution(CtClass.floatType, CtClass.doubleType, "Char_v_Double"),
            new Execution(CtClass.doubleType, CtClass.doubleType, "Char_v_Double"),
            new Execution(CtClass.byteType, CtClass.floatType, "Char_v_Float"),
            new Execution(CtClass.charType, CtClass.charType, "Char_v_Float"),
            new Execution(CtClass.shortType, CtClass.floatType, "Char_v_Float"),
            new Execution(CtClass.intType, CtClass.floatType, "Char_v_Float"),
            new Execution(CtClass.longType, CtClass.floatType, "Char_v_Float"),
            new Execution(CtClass.floatType, CtClass.floatType, "Char_v_Float"),
            new Execution(CtClass.doubleType, CtClass.voidType, "Char_v_Float"),

            new Execution(CtClass.byteType, CtClass.floatType, "Float_v_Double"),
            new Execution(CtClass.charType, CtClass.floatType, "Float_v_Double"),
            new Execution(CtClass.shortType, CtClass.floatType, "Float_v_Double"),
            new Execution(CtClass.intType, CtClass.floatType, "Float_v_Double"),
            new Execution(CtClass.longType, CtClass.floatType, "Float_v_Double"),
            new Execution(CtClass.floatType, CtClass.floatType, "Float_v_Double"),
            new Execution(CtClass.doubleType, CtClass.doubleType, "Float_v_Double"),
        });
    }

    @ParameterizedTest
    @MethodSource("params")
    public void PrimitiveOverloadTest(Execution execution) throws Exception {
        var resolver = new Resolver(SourceInfo.none());
        var declaringClass = resolver.resolveClass(MethodFinderTest.class.getName());
        var invokingClass = resolver.getTypeInstance(declaringClass);
        var method = new MethodFinder(invokingClass, declaringClass).findMethod(
            execution.methodName(), false, null, List.of(resolver.getTypeInstance(execution.argType())),
            List.of(SourceInfo.none()), null, SourceInfo.none());

        if (execution.selectedParamType() == CtClass.voidType) {
            assertNull(method);
        } else {
            var paramTypes = method.getParameterTypes();
            assertEquals(1, paramTypes.length);
            assertEquals(execution.selectedParamType().getName(), paramTypes[0].getName());
        }
    }

}

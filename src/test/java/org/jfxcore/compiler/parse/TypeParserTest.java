// Copyright (c) 2023, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.TestBase;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.TypeInstance;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TypeParserTest extends TestBase {

    @Test
    public void Braces_Generic_Syntax_Is_Acceptable() {
        TypeInstance typeInstance = new TypeParser("Comparable(String)").parse().get(0);
        assertEquals("java.lang.Comparable<java.lang.String>", typeInstance.getJavaName());
    }

    @Test
    public void Bracket_Generic_Syntax_Is_Not_Acceptable() {
        var ex = assertThrows(MarkupException.class, () -> new TypeParser("Comparable[String]").parse());
        assertEquals(ErrorCode.EXPECTED_TOKEN, ex.getDiagnostic().getCode());
    }

    @Test
    public void Empty_MethodName_Is_Not_Acceptable() {
        var ex = assertThrows(MarkupException.class, () -> new TypeParser("     ").parseMethod());
        assertEquals(ErrorCode.EXPECTED_IDENTIFIER, ex.getDiagnostic().getCode());
    }

    @Test
    public void Parse_MethodName_With_TypeWitness() {
        var methodInfo = new TypeParser("method<String>").parseMethod();
        assertEquals("method", methodInfo.methodName());
        assertEquals(List.of(TypeInstance.StringType()), methodInfo.typeWitnesses());
    }

    @Test
    public void Parse_MethodName_With_TypeWitness_Missing_Closing_Curly() {
        var ex = assertThrows(MarkupException.class, () -> new TypeParser("method<String").parseMethod());
        assertEquals(ErrorCode.EXPECTED_TOKEN, ex.getDiagnostic().getCode());
    }

    @Test
    public void Parse_MethodName_With_Multiple_TypeWitnesses() {
        var methodInfo = new TypeParser("  method  <String, java.lang.Integer, Double>  ").parseMethod();
        assertEquals("method", methodInfo.methodName());
        assertEquals(List.of(TypeInstance.StringType(), TypeInstance.IntegerType(), TypeInstance.DoubleType()),
                     methodInfo.typeWitnesses());
    }
}

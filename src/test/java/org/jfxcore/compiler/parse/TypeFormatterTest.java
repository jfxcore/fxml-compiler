// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TypeFormatterTest {

    @Test
    public void TestTypeFormatter() {
        assertEquals("Foo<Bar>", format("Foo<Bar>"));
        assertEquals("Foo<Bar[]>", format("Foo<Bar []>"));
        assertEquals("bar.Foo<com.baz.Bar<Baz[]>>[][]", format("bar.Foo <com.baz.Bar <Baz[] >>[] []"));
        assertEquals("Foo, Bar<Baz>, Qux[][]", format("Foo , Bar <Baz>,Qux[][]"));
        assertEquals("Foo<? extends Bar>", format("Foo <? extends  Bar >"));
        assertEquals("Foo<? super Bar>", format("Foo <? super  Bar >"));
    }

    @Test
    public void Unexpected_End_Of_Type_Declaration() {
        var ex = assertThrows(MarkupException.class, () -> format("Double,"));
        assertEquals(ErrorCode.UNEXPECTED_END_OF_TYPE_DECLARATION, ex.getDiagnostic().getCode());

        ex = assertThrows(MarkupException.class, () -> format("Double<? extends "));
        assertEquals(ErrorCode.UNEXPECTED_END_OF_TYPE_DECLARATION, ex.getDiagnostic().getCode());
    }

    private String format(String type) {
        return String.join(", ", new TypeFormatter(type).format());
    }
}

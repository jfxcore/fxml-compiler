// Copyright (c) 2022, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TypeFormatterTest {

    @Test
    public void TestTypeFormatter() {
        assertEquals("Foo<Bar>", new TypeFormatter("Foo<Bar>").format());
        assertEquals("Foo<Bar[]>", new TypeFormatter("Foo<Bar []>").format());
        assertEquals("bar.Foo<com.baz.Bar<Baz[]>>[][]", new TypeFormatter("bar.Foo <com.baz.Bar <Baz[] >>[] []").format());
        assertEquals("Foo, Bar<Baz>, Qux[][]", new TypeFormatter("Foo , Bar <Baz>,Qux[][]").format());
        assertEquals("Foo<? extends Bar>", new TypeFormatter("Foo <? extends  Bar >").format());
        assertEquals("Foo<? super Bar>", new TypeFormatter("Foo <? super  Bar >").format());
    }

}

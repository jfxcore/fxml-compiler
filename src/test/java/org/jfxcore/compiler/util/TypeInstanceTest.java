// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CtClass;
import javassist.CtMethod;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.parse.TypeParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jfxcore.compiler.TestBase;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unused")
public class TypeInstanceTest extends TestBase {

    private Resolver resolver;
    private TypeInvoker invoker;

    @BeforeEach
    public void setup() {
        resolver = new Resolver(SourceInfo.none());
        invoker = new TypeInvoker(SourceInfo.none());
    }

    @Test
    public void PrimitiveType_IsAssignableFrom_PrimitiveType() {
        // byte
        TypeInstance t0 = new TypeParser("byte").parse().get(0);
        TypeInstance t1 = new TypeParser("char").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("byte").parse().get(0);
        t1 = new TypeParser("byte").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("byte").parse().get(0);
        t1 = new TypeParser("short").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("byte").parse().get(0);
        t1 = new TypeParser("int").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("byte").parse().get(0);
        t1 = new TypeParser("long").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("byte").parse().get(0);
        t1 = new TypeParser("float").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("byte").parse().get(0);
        t1 = new TypeParser("double").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        // short
        t0 = new TypeParser("short").parse().get(0);
        t1 = new TypeParser("char").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("short").parse().get(0);
        t1 = new TypeParser("short").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("short").parse().get(0);
        t1 = new TypeParser("int").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("short").parse().get(0);
        t1 = new TypeParser("long").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("short").parse().get(0);
        t1 = new TypeParser("float").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("short").parse().get(0);
        t1 = new TypeParser("double").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        // int
        t0 = new TypeParser("int").parse().get(0);
        t1 = new TypeParser("char").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("int").parse().get(0);
        t1 = new TypeParser("int").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("int").parse().get(0);
        t1 = new TypeParser("long").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("int").parse().get(0);
        t1 = new TypeParser("float").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("int").parse().get(0);
        t1 = new TypeParser("double").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        // long
        t0 = new TypeParser("long").parse().get(0);
        t1 = new TypeParser("char").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("long").parse().get(0);
        t1 = new TypeParser("long").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("long").parse().get(0);
        t1 = new TypeParser("float").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("long").parse().get(0);
        t1 = new TypeParser("double").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        // float
        t0 = new TypeParser("float").parse().get(0);
        t1 = new TypeParser("char").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("float").parse().get(0);
        t1 = new TypeParser("float").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("float").parse().get(0);
        t1 = new TypeParser("double").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        // double
        t0 = new TypeParser("double").parse().get(0);
        t1 = new TypeParser("char").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("double").parse().get(0);
        t1 = new TypeParser("double").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        // char
        t0 = new TypeParser("char").parse().get(0);
        t1 = new TypeParser("char").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));
    }

    @Test
    public void PrimitiveType_IsAssignableFrom_PrimitiveBoxType() {
        // byte
        TypeInstance t0 = new TypeParser("java.lang.Byte").parse().get(0);
        TypeInstance t1 = new TypeParser("char").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Byte").parse().get(0);
        t1 = new TypeParser("byte").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Byte").parse().get(0);
        t1 = new TypeParser("short").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Byte").parse().get(0);
        t1 = new TypeParser("int").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Byte").parse().get(0);
        t1 = new TypeParser("long").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Byte").parse().get(0);
        t1 = new TypeParser("float").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Byte").parse().get(0);
        t1 = new TypeParser("double").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        // short
        t0 = new TypeParser("java.lang.Short").parse().get(0);
        t1 = new TypeParser("byte").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Short").parse().get(0);
        t1 = new TypeParser("char").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Short").parse().get(0);
        t1 = new TypeParser("short").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Short").parse().get(0);
        t1 = new TypeParser("int").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Short").parse().get(0);
        t1 = new TypeParser("long").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Short").parse().get(0);
        t1 = new TypeParser("float").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Short").parse().get(0);
        t1 = new TypeParser("double").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        // int
        t0 = new TypeParser("java.lang.Integer").parse().get(0);
        t1 = new TypeParser("byte").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Integer").parse().get(0);
        t1 = new TypeParser("char").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Integer").parse().get(0);
        t1 = new TypeParser("short").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Integer").parse().get(0);
        t1 = new TypeParser("int").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Integer").parse().get(0);
        t1 = new TypeParser("long").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Integer").parse().get(0);
        t1 = new TypeParser("float").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Integer").parse().get(0);
        t1 = new TypeParser("double").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        // long
        t0 = new TypeParser("java.lang.Long").parse().get(0);
        t1 = new TypeParser("byte").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Long").parse().get(0);
        t1 = new TypeParser("char").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Long").parse().get(0);
        t1 = new TypeParser("short").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Long").parse().get(0);
        t1 = new TypeParser("int").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Long").parse().get(0);
        t1 = new TypeParser("long").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Long").parse().get(0);
        t1 = new TypeParser("float").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Long").parse().get(0);
        t1 = new TypeParser("double").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        // float
        t0 = new TypeParser("java.lang.Float").parse().get(0);
        t1 = new TypeParser("byte").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Float").parse().get(0);
        t1 = new TypeParser("char").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Float").parse().get(0);
        t1 = new TypeParser("short").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Float").parse().get(0);
        t1 = new TypeParser("int").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Float").parse().get(0);
        t1 = new TypeParser("long").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Float").parse().get(0);
        t1 = new TypeParser("float").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Float").parse().get(0);
        t1 = new TypeParser("double").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        // double
        t0 = new TypeParser("java.lang.Double").parse().get(0);
        t1 = new TypeParser("byte").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Double").parse().get(0);
        t1 = new TypeParser("char").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Double").parse().get(0);
        t1 = new TypeParser("short").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Double").parse().get(0);
        t1 = new TypeParser("int").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Double").parse().get(0);
        t1 = new TypeParser("long").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Double").parse().get(0);
        t1 = new TypeParser("float").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Double").parse().get(0);
        t1 = new TypeParser("double").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        // char
        t0 = new TypeParser("java.lang.Character").parse().get(0);
        t1 = new TypeParser("byte").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Character").parse().get(0);
        t1 = new TypeParser("char").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Character").parse().get(0);
        t1 = new TypeParser("short").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Character").parse().get(0);
        t1 = new TypeParser("int").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Character").parse().get(0);
        t1 = new TypeParser("long").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Character").parse().get(0);
        t1 = new TypeParser("float").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Character").parse().get(0);
        t1 = new TypeParser("double").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));
    }

    @Test
    public void PrimitiveBoxType_IsAssignableFrom_PrimitiveBoxType() {
        // byte
        TypeInstance t0 = new TypeParser("java.lang.Byte").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Character").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Byte").parse().get(0);
        t1 = new TypeParser("java.lang.Byte").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Byte").parse().get(0);
        t1 = new TypeParser("java.lang.Short").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Byte").parse().get(0);
        t1 = new TypeParser("java.lang.Integer").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Byte").parse().get(0);
        t1 = new TypeParser("java.lang.Long").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Byte").parse().get(0);
        t1 = new TypeParser("java.lang.Float").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Byte").parse().get(0);
        t1 = new TypeParser("java.lang.Double").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        // short
        t0 = new TypeParser("java.lang.Short").parse().get(0);
        t1 = new TypeParser("java.lang.Character").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Short").parse().get(0);
        t1 = new TypeParser("java.lang.Short").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Short").parse().get(0);
        t1 = new TypeParser("java.lang.Integer").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Short").parse().get(0);
        t1 = new TypeParser("java.lang.Long").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Short").parse().get(0);
        t1 = new TypeParser("java.lang.Float").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Short").parse().get(0);
        t1 = new TypeParser("java.lang.Double").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        // int
        t0 = new TypeParser("java.lang.Integer").parse().get(0);
        t1 = new TypeParser("java.lang.Character").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Integer").parse().get(0);
        t1 = new TypeParser("java.lang.Integer").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Integer").parse().get(0);
        t1 = new TypeParser("java.lang.Long").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Integer").parse().get(0);
        t1 = new TypeParser("java.lang.Float").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Integer").parse().get(0);
        t1 = new TypeParser("java.lang.Double").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        // long
        t0 = new TypeParser("java.lang.Long").parse().get(0);
        t1 = new TypeParser("java.lang.Character").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Long").parse().get(0);
        t1 = new TypeParser("java.lang.Long").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Long").parse().get(0);
        t1 = new TypeParser("java.lang.Float").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Long").parse().get(0);
        t1 = new TypeParser("java.lang.Double").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        // float
        t0 = new TypeParser("java.lang.Float").parse().get(0);
        t1 = new TypeParser("java.lang.Character").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Float").parse().get(0);
        t1 = new TypeParser("java.lang.Float").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Float").parse().get(0);
        t1 = new TypeParser("java.lang.Double").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        // double
        t0 = new TypeParser("java.lang.Double").parse().get(0);
        t1 = new TypeParser("java.lang.Character").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));

        t0 = new TypeParser("java.lang.Double").parse().get(0);
        t1 = new TypeParser("java.lang.Double").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));

        // char
        t0 = new TypeParser("java.lang.Character").parse().get(0);
        t1 = new TypeParser("java.lang.Character").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
        assertTrue(t1.isAssignableFrom(t0));
    }

    @Test
    public void ReferenceType_Is_Assignable_From_NullType() {
        TypeInstance t0 = new TypeParser("java.lang.String").parse().get(0);
        assertTrue(t0.isAssignableFrom(TypeInstance.nullType()));
        assertTrue(TypeInstance.nullType().isAssignableFrom(TypeInstance.nullType()));
        assertFalse(TypeInstance.nullType().isAssignableFrom(t0));
    }

    @Test
    public void PrimitiveType_Is_Not_Assignable_From_NullType() {
        TypeInstance t0 = new TypeParser("double").parse().get(0);
        assertFalse(t0.isAssignableFrom(TypeInstance.nullType()));
        assertFalse(TypeInstance.nullType().isAssignableFrom(t0));
    }

    @Test
    public void TypeArgument_Cannot_Be_PrimitiveType() {
        MarkupException ex = assertThrows(MarkupException.class,
            () -> new TypeParser("java.lang.Iterable<double>").parse());

        assertEquals(ErrorCode.TYPE_ARGUMENT_NOT_REFERENCE, ex.getDiagnostic().getCode());

        ex = assertThrows(MarkupException.class,
            () -> new TypeParser("java.lang.Iterable<java.lang.Comparable<char>>").parse());

        assertEquals(ErrorCode.TYPE_ARGUMENT_NOT_REFERENCE, ex.getDiagnostic().getCode());
    }

    public static class Type1<A> {}
    public static class Type2<B> extends Type1<B> {}
    public static class Type3 extends Type2<String> {}

    @Test
    public void Generic_Invocation_Tree_Is_Resolved() {
        TypeInstance typeInstance = invoker.invokeType(resolver.resolveClass(Type3.class.getName()));

        assertEquals("TypeInstanceTest$Type3", typeInstance.toString());
        assertEquals("TypeInstanceTest$Type2<String>", typeInstance.getSuperTypes().get(0).toString());
        assertEquals("TypeInstanceTest$Type1<String>", typeInstance.getSuperTypes().get(0).getSuperTypes().get(0).toString());
    }

    public static class RecurringType<T> implements Comparable<RecurringType<String>> {
        @Override
        public int compareTo(RecurringType<String> o) {
            return 0;
        }
    }

    @Test
    public void Recurring_Generic_Type_Is_Resolved() {
        TypeInstance typeInstance = invoker.invokeType(resolver.resolveClass(RecurringType.class.getName()));

        assertEquals("TypeInstanceTest$RecurringType", typeInstance.toString());
        assertEquals("Object", typeInstance.getSuperTypes().get(0).toString());
        assertEquals("Comparable<TypeInstanceTest$RecurringType<String>>", typeInstance.getSuperTypes().get(1).toString());
    }

    public static class Type4<D, S> {}
    public static class Type5<S, D> extends Type4<D, S> {}

    @Test
    public void Parameterize_Generic_Type() {
        TypeInstance typeInstance = invoker.invokeType(
            resolver.resolveClass(Type5.class.getName()),
            List.of(TypeInstance.StringType(), TypeInstance.DoubleType()));

        assertEquals("TypeInstanceTest$Type5<String,Double>", typeInstance.toString());
        assertEquals("TypeInstanceTest$Type4<Double,String>", typeInstance.getSuperTypes().get(0).toString());
    }

    public static class Type13<T extends Type1<R>, R> {}

    public static class Type14 extends Type1<Boolean> {}

    @Test
    public void Parameterize_Generic_Type_With_Upper_Bound() {
        TypeInstance arg0 = invoker.invokeType(resolver.resolveClass(Type14.class.getName()));
        TypeInstance arg1 = invoker.invokeType(resolver.resolveClass(Boolean.class.getName()));
        TypeInstance typeInstance = invoker.invokeType(
            resolver.resolveClass(Type13.class.getName()), List.of(arg0, arg1));

        assertEquals("TypeInstanceTest$Type13<TypeInstanceTest$Type14,Boolean>", typeInstance.toString());
    }

    @Test
    public void Parameterize_Generic_Type_With_Upper_Bound_Out_Of_Bound() {
        TypeInstance arg0 = invoker.invokeType(resolver.resolveClass(Type14.class.getName()));
        TypeInstance arg1 = invoker.invokeType(resolver.resolveClass(Integer.class.getName()));
        MarkupException ex = assertThrows(MarkupException.class, () -> invoker.invokeType(
            resolver.resolveClass(Type13.class.getName()), List.of(arg0, arg1)));

        assertEquals(ErrorCode.TYPE_ARGUMENT_OUT_OF_BOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void IsAssignableFrom_CompatibleGenericTypes() {
        TypeInstance t0 = new TypeParser("java.lang.Comparable<java.lang.String>").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Comparable<java.lang.String>").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_CompatibleGenericArrayTypes() {
        TypeInstance t0 = new TypeParser("java.lang.Comparable<java.lang.String[]>").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Comparable<java.lang.String[]>").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_IncompatibleGenericTypes() {
        TypeInstance t0 = new TypeParser("java.lang.Comparable<java.lang.String>").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Comparable<java.lang.Double>").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_IncompatibleGenericArrayTypes() {
        TypeInstance t0 = new TypeParser("java.lang.Comparable<java.lang.String[]>").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Comparable<java.lang.Double[]>").parse().get(0);
        assertFalse(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_RawType_GenericType() {
        TypeInstance t0 = new TypeParser("java.lang.Comparable").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Comparable<java.lang.Double>").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_Wildcard_GenericType() {
        TypeInstance t0 = new TypeParser("java.lang.Comparable<?>").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Comparable<java.lang.Double>").parse().get(0);
        assertEquals("java.lang.Comparable<?>", t0.getJavaName());
        assertEquals("java.lang.Comparable<java.lang.Double>", t1.getJavaName());
        assertTrue(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_Wildcard_UpperBound() {
        TypeInstance t0 = new TypeParser("java.lang.Comparable<? extends Number>").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Comparable<Double>").parse().get(0);
        assertEquals("java.lang.Comparable<? extends java.lang.Number>", t0.getJavaName());
        assertEquals("java.lang.Comparable<java.lang.Double>", t1.getJavaName());
        assertTrue(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));
    }

    @Test
    public void IsAssignableFrom_Wildcard_LowerBound() {
        TypeInstance t0 = new TypeParser("java.lang.Comparable<? super Number>").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Comparable<Double>").parse().get(0);
        assertEquals("java.lang.Comparable<? super java.lang.Number>", t0.getJavaName());
        assertEquals("java.lang.Comparable<java.lang.Double>", t1.getJavaName());
        assertFalse(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));
    }

    @Test
    public void IsAssignableFrom_Object_DoubleArray() {
        TypeInstance t0 = new TypeParser("java.lang.Object").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Double[]").parse().get(0);
        assertEquals("java.lang.Object", t0.getJavaName());
        assertEquals("java.lang.Double[]", t1.getJavaName());
        assertTrue(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));
    }

    @Test
    public void IsAssignableFrom_Object_DoubleMultiArray() {
        TypeInstance t0 = new TypeParser("java.lang.Object").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Double[][][]").parse().get(0);
        assertEquals("java.lang.Object", t0.getJavaName());
        assertEquals("java.lang.Double[][][]", t1.getJavaName());
        assertTrue(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));
    }

    @Test
    public void IsAssignableFrom_Object_primitiveDoubleArray() {
        TypeInstance t0 = new TypeParser("java.lang.Object").parse().get(0);
        TypeInstance t1 = new TypeParser("double[]").parse().get(0);
        assertEquals("java.lang.Object", t0.getJavaName());
        assertEquals("double[]", t1.getJavaName());
        assertTrue(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));
    }

    @Test
    public void IsAssignableFrom_Object_primitiveDoubleMultiArray() {
        TypeInstance t0 = new TypeParser("java.lang.Object").parse().get(0);
        TypeInstance t1 = new TypeParser("double[][][]").parse().get(0);
        assertEquals("java.lang.Object", t0.getJavaName());
        assertEquals("double[][][]", t1.getJavaName());
        assertTrue(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));
    }

    @Test
    public void IsAssignableFrom_ObjectArray_DoubleArray() {
        TypeInstance t0 = new TypeParser("java.lang.Object[]").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Double[]").parse().get(0);
        assertEquals("java.lang.Object[]", t0.getJavaName());
        assertEquals("java.lang.Double[]", t1.getJavaName());
        assertTrue(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));
    }

    @Test
    public void IsAssignableFrom_ObjectArray_DoubleMultiArray() {
        TypeInstance t0 = new TypeParser("java.lang.Object[][][]").parse().get(0);
        TypeInstance t1 = new TypeParser("java.lang.Double[][][]").parse().get(0);
        assertEquals("java.lang.Object[][][]", t0.getJavaName());
        assertEquals("java.lang.Double[][][]", t1.getJavaName());
        assertTrue(t0.isAssignableFrom(t1));
        assertFalse(t1.isAssignableFrom(t0));
    }

    @Test
    public void IsAssignableFrom_ObjectArray_primitiveDoubleArray() {
        TypeInstance t0 = new TypeParser("java.lang.Object[]").parse().get(0);
        TypeInstance t1 = new TypeParser("double[]").parse().get(0);
        assertEquals("java.lang.Object[]", t0.getJavaName());
        assertEquals("double[]", t1.getJavaName());
        assertFalse(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_ObjectArray_primitiveDoubleMultiArray() {
        TypeInstance t0 = new TypeParser("java.lang.Object[][][]").parse().get(0);
        TypeInstance t1 = new TypeParser("double[][][]").parse().get(0);
        assertEquals("java.lang.Object[][][]", t0.getJavaName());
        assertEquals("double[][][]", t1.getJavaName());
        assertFalse(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_primitiveDoubleArray_primitiveDoubleArray() {
        TypeInstance t0 = new TypeParser("double[]").parse().get(0);
        TypeInstance t1 = new TypeParser("double[]").parse().get(0);
        assertEquals("double[]", t0.getJavaName());
        assertEquals("double[]", t1.getJavaName());
        assertTrue(t0.isAssignableFrom(t1));
    }

    @Test
    public void IsAssignableFrom_primitiveDoubleMultiArray2_primitiveDoubleMultiArray3() {
        TypeInstance t0 = new TypeParser("double[][]").parse().get(0);
        TypeInstance t1 = new TypeParser("double[][][]").parse().get(0);
        assertEquals("double[][]", t0.getJavaName());
        assertEquals("double[][][]", t1.getJavaName());
        assertFalse(t0.isAssignableFrom(t1));
    }

    public static class Type6<T> {}
    public static class Type7 extends Type6<String> {}

    @Test
    public void IsAssignableFrom_Subtype() {
        TypeInstance t6 = invoker.invokeType(
            resolver.resolveClass(Type6.class.getName()), List.of(TypeInstance.StringType()));
        TypeInstance t7 = invoker.invokeType(resolver.resolveClass(Type7.class.getName()));
        assertTrue(t6.isAssignableFrom(t7));
    }

    @Test
    public void IsAssignableFrom_Subtype_ArgUpperBound() {
        CtMethod method = resolver.tryResolveMethod(Classes.ParentType(), m -> m.getName().equals("getChildrenUnmodifiable"));
        TypeInstance t0 = invoker.invokeReturnType(method, Collections.emptyList()).getArguments().get(0);
        TypeInstance t1 = new TypeParser("javafx.scene.Parent").parse().get(0);
        assertTrue(t0.isAssignableFrom(t1));
    }

    public static class Type15<T, P> {}
    public static class Type16<T, R> extends Type15<String, T> {}

    @Test
    public void IsAssignableFrom_Supertype_With_Equal_Number_Of_Type_Arguments() {
        TypeInstance t16 = invoker.invokeType(
            resolver.resolveClass(Type16.class.getName()),
            List.of(TypeInstance.BooleanType(), TypeInstance.DoubleType()));
        TypeInstance t15 = invoker.invokeType(
            resolver.resolveClass(Type15.class.getName()),
            List.of(TypeInstance.StringType().withWildcard(TypeInstance.WildcardType.LOWER),
                    TypeInstance.BooleanType().withWildcard(TypeInstance.WildcardType.LOWER)));

        assertTrue(t15.isAssignableFrom(t16));
    }

    @Test
    public void Scalar_Not_SubtypeOf_Array() {
        TypeInstance t0 = new TypeParser("java.lang.Object[]").parse().get(0);
        TypeInstance t1 = invoker.invokeType(Classes.NodeType());
        assertFalse(t1.subtypeOf(t0));
    }

    public static class Type8<T extends String> {}

    @Test
    public void TypeArgument_Out_Of_Bounds_Throws() {
        MarkupException ex = assertThrows(MarkupException.class, () -> invoker.invokeType(
            resolver.resolveClass(Type8.class.getName()), List.of(TypeInstance.DoubleType())));
        assertEquals(ErrorCode.TYPE_ARGUMENT_OUT_OF_BOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void IsAssignable_With_RawUsage() {
        var clazz = resolver.resolveClass(Type5.class.getName());
        var args = List.of(TypeInstance.DoubleType(), TypeInstance.StringType());
        var type1 = invoker.invokeType(clazz, args);
        var type2 = invoker.invokeType(clazz, Collections.emptyList());

        assertTrue(type1.isAssignableFrom(type2));
        assertTrue(type2.isAssignableFrom(type1));
    }

    public static class Type9<T> {}
    public static class Type10<T> extends Type9<T> {}
    public static class Type11 extends Type10<String> {}

    @Test
    public void RawBaseType_IsAssignable_From_DerivedType() {
        TypeInvoker invoker = new TypeInvoker(SourceInfo.none());
        var type1Raw = invoker.invokeType(resolver.resolveClass(Type9.class.getName()), Collections.emptyList());
        var type2 = invoker.invokeType(resolver.resolveClass(Type11.class.getName()));

        assertTrue(type1Raw.isAssignableFrom(type2));
    }

    public static class Type12<T extends String> {}

    @Test
    public void TypeArgument_Is_Erased_To_UpperBound() {
        var rawType = invoker.invokeType(resolver.resolveClass(Type12.class.getName()), Collections.emptyList());
        var stringType = TypeInstance.StringType();

        assertTrue(rawType.isRaw());
        assertTrue(stringType.isAssignableFrom(rawType.getArguments().get(0)));
    }

    @Test
    public void Types_With_Different_Bounds_Are_Not_Equal() {
        var inst1 = invoker.invokeType(
            resolver.resolveClass(Comparable.class.getName()), List.of(TypeInstance.DoubleType()));
        var inst2 = invoker.invokeType(
            resolver.resolveClass(Comparable.class.getName()),
            List.of(TypeInstance.DoubleType().withWildcard(TypeInstance.WildcardType.LOWER)));

        assertNotEquals(inst1, inst2);
        assertTrue(inst2.isAssignableFrom(inst1));
        assertFalse(inst1.isAssignableFrom(inst2));
    }

    @Test
    public void WithDimensions_Returns_Correct_ArrayType() throws Exception {
        TypeInstance type = new TypeParser("java.lang.String").parse().get(0);

        CtClass jvmType = type.withDimensions(1).jvmType();
        assertTrue(jvmType.isArray());
        assertEquals("java.lang.String[]", jvmType.getName());
        assertEquals("java.lang.String", jvmType.getComponentType().getName());

        jvmType = type.withDimensions(2).jvmType();
        assertTrue(jvmType.isArray());
        assertEquals("java.lang.String[][]", jvmType.getName());
        assertEquals("java.lang.String[]", jvmType.getComponentType().getName());
    }
}

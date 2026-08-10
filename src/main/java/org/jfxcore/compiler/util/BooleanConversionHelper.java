// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.type.TypeDeclaration;

import static org.jfxcore.compiler.type.KnownSymbols.*;

public final class BooleanConversionHelper {

    private BooleanConversionHelper() {}

    /**
     * Replaces the value on top of the stack with primitive {@code boolean}. Numeric zero,
     * floating-point NaN, {@code false}, and {@code null} are false; other values are true. When
     * {@code invert} is set, the resulting boolean is complemented without reevaluating the value.
     */
    public static void emit(Bytecode code, TypeDeclaration type, boolean invert) {
        if (type.isPrimitive()) {
            if (type.equals(booleanDecl())) {
                if (invert) {
                    code.iconst(1).ixor();
                }

                return;
            }

            if (!type.isNumericPrimitive()) {
                throw new IllegalArgumentException("Unsupported truthiness operand: " + type.name());
            }

            emitPrimitiveNumeric(code, type, invert);
            return;
        }

        if (type.equals(BooleanDecl())) {
            emitNullable(code, type, invert, () -> {
                code.unbox(BooleanDecl(), booleanDecl());
                if (invert) {
                    code.iconst(1).ixor();
                }
            });
        } else if (type.equals(CharacterDecl())) {
            emitNullable(code, type, invert, () -> {
                code.unbox(CharacterDecl(), charDecl());
                emitPrimitiveNumeric(code, charDecl(), invert);
            });
        } else if (type.subtypeOf(NumberDecl())) {
            emitNullable(code, type, invert, () -> {
                code.invoke(NumberDecl().requireMethod("doubleValue"));
                emitPrimitiveNumeric(code, doubleDecl(), invert);
            });
        } else {
            code.ifnull(
                () -> code.iconst(invert ? 1 : 0),
                () -> code.iconst(invert ? 0 : 1));
        }
    }

    private static void emitNullable(Bytecode code, TypeDeclaration type, boolean invert, Runnable emitNonNull) {
        Local local = code.acquireLocal(type);

        code.store(type, local)
            .load(type, local)
            .ifnull(
                () -> code.iconst(invert ? 1 : 0),
                () -> {
                    code.load(type, local);
                    emitNonNull.run();
                });

        code.releaseLocal(local);
    }

    private static void emitPrimitiveNumeric(Bytecode code, TypeDeclaration type, boolean invert) {
        if (type.equals(floatDecl()) || type.equals(doubleDecl())) {
            emitFloatingPoint(code, type, invert);
            return;
        }

        if (type.equals(longDecl())) {
            code.lconst(0).lcmp();
        }

        emitZeroTest(code, invert);
    }

    private static void emitFloatingPoint(Bytecode code, TypeDeclaration type, boolean invert) {
        Local local = code.acquireLocal(type);

        code.store(type, local)
            .load(type, local)
            .load(type, local);

        if (type.equals(floatDecl())) {
            code.fcmpl();
        } else {
            code.dcmpl();
        }

        code.ifeq(
            () -> {
                code.load(type, local);

                if (type.equals(floatDecl())) {
                    code.fconst(0).fcmpl();
                } else {
                    code.dconst(0).dcmpl();
                }

                emitZeroTest(code, invert);
            },
            () -> code.iconst(invert ? 1 : 0));

        code.releaseLocal(local);
    }

    private static void emitZeroTest(Bytecode code, boolean invert) {
        code.ifeq(
            () -> code.iconst(invert ? 1 : 0),
            () -> code.iconst(invert ? 0 : 1));
    }
}

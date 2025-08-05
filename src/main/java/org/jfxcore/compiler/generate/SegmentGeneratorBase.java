// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import org.jfxcore.compiler.ast.expression.path.KotlinDelegateSegment;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.expression.path.FieldSegment;
import org.jfxcore.compiler.ast.expression.path.FoldedGroup;
import org.jfxcore.compiler.ast.expression.path.GetterSegment;
import org.jfxcore.compiler.ast.expression.path.Segment;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.ast.expression.util.KotlinDelegateHelper;
import org.jfxcore.compiler.util.ExceptionHelper;
import org.jfxcore.compiler.util.Label;
import java.util.ArrayList;
import java.util.List;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.function;

abstract class SegmentGeneratorBase extends ClassGenerator {

    public static final String NEXT_FIELD = "next";
    public static final String UPDATE_METHOD = "update";

    private final SourceInfo sourceInfo;

    final FoldedGroup[] groups;
    final int segment;

    SegmentGeneratorBase(SourceInfo sourceInfo, FoldedGroup[] groups, int segment) {
        this.sourceInfo = sourceInfo;
        this.groups = groups;
        this.segment = segment;
    }

    protected void emitInvariants(CtClass valueType, Segment[] path, Bytecode code) {
        List<Label> labels = new ArrayList<>();

        for (int i = 1; i < path.length; ++i) {
            CtClass type;

            if (path[i] instanceof FieldSegment) {
                CtField field = ((FieldSegment)path[i]).getField();
                type = ExceptionHelper.unchecked(sourceInfo, field::getType);
                code.getfield(field.getDeclaringClass(), field.getName(), type);
            } else if (path[i] instanceof GetterSegment) {
                CtMethod getter = ((GetterSegment)path[i]).getGetter();
                type = ExceptionHelper.unchecked(sourceInfo, getter::getReturnType);
                code.ext_invoke(getter);
            } else if (path[i] instanceof KotlinDelegateSegment) {
                CtField field = ((KotlinDelegateSegment)path[i]).getDelegateField();
                CtMethod getter = KotlinDelegateHelper.getKotlinDelegateGetter(sourceInfo, field);
                type = ExceptionHelper.unchecked(sourceInfo, getter::getReturnType);
                code.ext_invoke(getter);
            } else {
                throw new IllegalArgumentException();
            }

            // Widen small boxed numbers to Integer
            if (!valueType.isPrimitive() && type.isPrimitive()) {
                code.ext_box(type);
            }

            if (!path[i].isNullable()) {
                code.ldc(path[i].getName())
                    .invokestatic(ObjectsType(), "requireNonNull",
                                  function(ObjectType(), ObjectType(), StringType()))
                    .checkcast(type);
            } else if (i < path.length - 1) {
                Label L0 = code
                    .dup()
                    .ifnonnull();

                code.pop()
                    .ext_defaultconst(valueType);

                labels.add(code.goto_label());

                L0.resume();
            }
        }

        for (Label label : labels) {
            label.resume();
        }
    }

    protected final SourceInfo getSourceInfo() {
        return sourceInfo;
    }
}

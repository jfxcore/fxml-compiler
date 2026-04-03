// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import org.jfxcore.compiler.ast.expression.path.FieldSegment;
import org.jfxcore.compiler.ast.expression.path.FoldedGroup;
import org.jfxcore.compiler.ast.expression.path.GetterSegment;
import org.jfxcore.compiler.ast.expression.path.Segment;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Label;
import java.util.ArrayList;
import java.util.List;

import static org.jfxcore.compiler.type.KnownSymbols.*;

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

    protected void emitInvariants(TypeDeclaration valueType, Segment[] path, Bytecode code) {
        List<Label> labels = new ArrayList<>();

        for (int i = 1; i < path.length; ++i) {
            TypeDeclaration type;

            if (path[i] instanceof FieldSegment fieldSegment) {
                FieldDeclaration field = fieldSegment.getField();
                type = field.type();
                code.getfield(field);
            } else if (path[i] instanceof GetterSegment getterSegment) {
                MethodDeclaration getter = getterSegment.getGetter();
                type = getter.returnType();
                code.invoke(getter);
            } else {
                throw new IllegalArgumentException();
            }

            // Widen small boxed numbers to Integer
            if (!valueType.isPrimitive() && type.isPrimitive()) {
                code.box(type);
            }

            if (!path[i].isNullable()) {
                code.ldc(path[i].getName())
                    .invoke(ObjectsDecl().requireDeclaredMethod("requireNonNull", ObjectDecl(), StringDecl()))
                    .checkcast(type);
            } else if (i < path.length - 1) {
                Label L0 = code
                    .dup()
                    .ifnonnull();

                code.pop()
                    .defaultconst(valueType);

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

// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import javassist.CtClass;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.util.ObservableKind;

public class FoldedGroup {

    private final Segment[] path;
    private final String name;
    private CtClass compiledClass;

    public FoldedGroup(Segment[] path, String name) {
        this.path = path;
        this.name = name;
    }

    public Segment getFirstPathSegment() {
        return path[0];
    }

    public Segment getLastPathSegment() {
        return path[path.length -1];
    }

    public Segment[] getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public CtClass getCompiledClass() {
        return compiledClass;
    }

    public void setCompiledClass(CtClass compiledClass) {
        this.compiledClass = compiledClass;
    }

    public CtClass getType() {
        return path[path.length - 1].getTypeInstance().jvmType();
    }

    public CtClass getValueType() {
        return path[path.length - 1].getValueTypeInstance().jvmType();
    }

    public @Nullable CtClass getObservableType() {
        if (path[path.length - 1].getObservableKind() != ObservableKind.NONE) {
            return path[path.length - 1].getTypeInstance().jvmType();
        }

        return null;
    }

}

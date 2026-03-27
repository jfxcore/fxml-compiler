// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.util.ObservableKind;

public class FoldedGroup {

    private final Segment[] path;
    private final String name;
    private TypeDeclaration compiledClass;

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

    public TypeDeclaration getCompiledClass() {
        return compiledClass;
    }

    public void setCompiledClass(TypeDeclaration compiledClass) {
        this.compiledClass = compiledClass;
    }

    public TypeDeclaration getType() {
        return path[path.length - 1].getTypeInstance().declaration();
    }

    public TypeDeclaration getValueType() {
        return path[path.length - 1].getValueTypeInstance().declaration();
    }

    public @Nullable TypeDeclaration getObservableType() {
        if (path[path.length - 1].getObservableKind() != ObservableKind.NONE) {
            return path[path.length - 1].getTypeInstance().declaration();
        }

        return null;
    }
}

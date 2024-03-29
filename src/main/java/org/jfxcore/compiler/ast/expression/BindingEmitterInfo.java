// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import javassist.CtClass;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.TypeInstance;

public class BindingEmitterInfo {

    private final ValueEmitterNode value;
    private final TypeInstance valueType;
    private final TypeInstance observableType;
    private final CtClass sourceDeclaringType;
    private final String sourceName;
    private final SourceInfo sourceInfo;

    public BindingEmitterInfo(
            ValueEmitterNode value,
            TypeInstance valueType,
            TypeInstance observableType,
            CtClass sourceDeclaringType,
            String sourceName,
            SourceInfo sourceInfo) {
        this.value = value;
        this.valueType = valueType;
        this.observableType = observableType;
        this.sourceDeclaringType = sourceDeclaringType;
        this.sourceName = sourceName;
        this.sourceInfo = sourceInfo;
    }

    public ValueEmitterNode getValue() {
        return value;
    }

    public TypeInstance getType() {
        return observableType != null ? observableType : valueType;
    }

    public TypeInstance getValueType() {
        return valueType;
    }

    public TypeInstance getObservableType() {
        return observableType;
    }

    public CtClass getSourceDeclaringType() {
        return sourceDeclaringType;
    }

    public String getSourceName() {
        return sourceName;
    }

    public SourceInfo getSourceInfo() {
        return sourceInfo;
    }

}

// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.util.TypeInstance;

public interface ExpressionNode extends Node {

    BindingEmitterInfo toEmitter(BindingMode bindingMode, TypeInstance invokingType);

    @Override
    ExpressionNode deepClone();

}
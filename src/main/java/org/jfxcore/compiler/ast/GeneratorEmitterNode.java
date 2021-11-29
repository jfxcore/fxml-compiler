// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.generate.Generator;
import java.util.List;

/**
 * Identifies a node that emits a generator.
 * During the emit phase, generator emitters are executed before regular emitters.
 */
public interface GeneratorEmitterNode extends Node {

    List<Generator> emitGenerators(BytecodeEmitContext context);

}

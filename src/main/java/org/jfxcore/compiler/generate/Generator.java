// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import java.util.List;

public interface Generator {

    default List<Generator> getSubGenerators() {
        return List.of();
    }

    boolean consume(BytecodeEmitContext context);

    void emitFields(BytecodeEmitContext context) throws Exception;

    void emitMethods(BytecodeEmitContext context) throws Exception;

    void emitCode(BytecodeEmitContext context) throws Exception;

}

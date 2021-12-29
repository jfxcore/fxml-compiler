// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.NotFoundException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;

public class ExceptionHelper {

    public static void unchecked(SourceInfo sourceInfo, Action action) {
        try {
            action.run();
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.notFound(sourceInfo, ex.getMessage());
        } catch (Throwable ex) {
            throw unchecked(ex);
        }
    }

    public static <T> T unchecked(SourceInfo sourceInfo, Supplier<T> action) {
        try {
            return action.get();
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.notFound(sourceInfo, ex.getMessage());
        } catch (Throwable ex) {
            throw unchecked(ex);
        }
    }

    @SuppressWarnings("unchecked")
    public static <E extends Throwable> E unchecked(Throwable e) throws E {
        throw (E)e;
    }

}

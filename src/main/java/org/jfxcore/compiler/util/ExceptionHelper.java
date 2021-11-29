// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CannotCompileException;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;

public class ExceptionHelper {

    public static void unchecked(SourceInfo sourceInfo, Action action) {
        try {
            action.run();
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.notFound(sourceInfo, ex.getMessage());
        } catch (BadBytecode | CannotCompileException ex) {
            throw GeneralErrors.internalError(ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static <T> T unchecked(SourceInfo sourceInfo, Supplier<T> action) {
        try {
            return action.get();
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.notFound(sourceInfo, ex.getMessage());
        } catch (BadBytecode | CannotCompileException ex) {
            throw GeneralErrors.internalError(ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

}

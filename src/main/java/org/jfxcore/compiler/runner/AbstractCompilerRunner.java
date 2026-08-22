// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.runner;

import org.jfxcore.compiler.diagnostic.Logger;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;

abstract class AbstractCompilerRunner implements AutoCloseable {

    private final RunnerLogger logger;
    private final CompilerClassLoader classLoader;
    private final ExceptionHelper exceptionHelper;

    AbstractCompilerRunner(String implName, Set<Path> searchPath, RunnerLogger logger) {
        this.logger = logger;

        List<URL> urls = searchPath.stream().map(file -> {
            try {
                return file.toUri().toURL();
            } catch (IOException e) {
                return null;
            }
        }).filter(Objects::nonNull).toList();

        classLoader = checkDependencies(implName,
            new CompilerClassLoader(urls.toArray(URL[]::new), getClass().getClassLoader()));

        exceptionHelper = new ExceptionHelper(classLoader);
    }

    @Override
    public final void close() {
        try {
            classLoader.close();
        } catch (Throwable ex) {
            ExceptionHelper.throwUnchecked(ex.getCause());
        }
    }

    final CompilerClassLoader getClassLoader() {
        return classLoader;
    }

    final Class<?> getCompilerLoggerClass() {
        return classLoader.findClass(Logger.class);
    }

    final Object newCompilerLogger() {
        return Proxy.newProxyInstance(
            classLoader, new Class[] { getCompilerLoggerClass() },
            (proxy, method, args) -> switch (method.getName()) {
                case "fine" -> { logger.fine((String) args[0]); yield null; }
                case "info" -> { logger.info((String) args[0]); yield null; }
                case "warn" -> { logger.warn((String) args[0]); yield null; }
                default -> method.invoke(proxy, args);
            });
    }

    final <T> T invoke(Callable<T> supplier) {
        try {
            return supplier.call();
        } catch (InvocationTargetException ex) {
            if (exceptionHelper.isMarkupException(ex.getCause())) {
                String formattedMessage = exceptionHelper.format(ex.getCause());
                if (formattedMessage != null) {
                    logger.error(formattedMessage);
                }

                throw new RunnerException(exceptionHelper.isInternalError(ex.getCause())
                    ? "Internal compiler error; please clean and rebuild the project."
                    : "Compilation failed; see the compiler error output for details.");
            }

            ExceptionHelper.throwUnchecked(ex.getCause());
        } catch (Throwable ex) {
            ExceptionHelper.throwUnchecked(ex);
        }

        throw new AssertionError();
    }

    private <T extends ClassLoader> T checkDependencies(String implClass, T classLoader) {
        try {
            Class.forName(implClass, true, classLoader);
        } catch (ClassNotFoundException ex) {
            throw new RunnerException("Compiler not found");
        }

        List<String> missingDeps = new ArrayList<>();

        try {
            Class.forName("javafx.beans.Observable", true, classLoader);
        } catch (ClassNotFoundException ex) {
            missingDeps.add("javafx.base");
        }

        try {
            Class.forName("javafx.geometry.Bounds", true, classLoader);
        } catch (ClassNotFoundException ex) {
            missingDeps.add("javafx.graphics");
        }

        if (!missingDeps.isEmpty()) {
            throw new RunnerException("Missing module dependencies: " + String.join(", ", missingDeps));
        }

        return classLoader;
    }
}

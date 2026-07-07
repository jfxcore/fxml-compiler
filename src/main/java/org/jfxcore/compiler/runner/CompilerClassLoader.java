// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.runner;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.Enumeration;

class CompilerClassLoader extends URLClassLoader {

    private final ClassLoaderWrapper parent;

    public CompilerClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, null);
        this.parent = new ClassLoaderWrapper(parent);
    }

    /**
     * Finds and loads the specified class with this class loader.
     *
     * @param cls the class
     * @return the class as loaded by this class loader
     */
    public Class<?> findClass(Class<?> cls) {
        try {
            return Class.forName(cls.getName(), true, this);
        } catch (ClassNotFoundException ex) {
            throw new RunnerException("Class not found: " + ex.getMessage());
        }
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return super.findClass(name);
        } catch (ClassNotFoundException ex) {
            return parent.findClass(name);
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
            return super.loadClass(name, resolve);
        } catch (ClassNotFoundException ex) {
            String classFileName = name.replace('.', '/') + ".class";

            try (InputStream stream = parent.getResourceAsStream(classFileName)) {
                if (stream != null) {
                    byte[] data = stream.readAllBytes();
                    Class<?> clazz = defineClass(name, data, 0, data.length, (CodeSource)null);
                    resolveClass(clazz);
                    return clazz;
                }
            } catch (IOException ignored) {
            }

            return parent.loadClass(name, resolve);
        }
    }

    @Override
    public URL getResource(String name) {
        URL resource = super.getResource(name);
        if (resource != null) {
            return resource;
        }

        return parent.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Enumeration<URL> resources = super.getResources(name);
        if (resources.hasMoreElements()) {
            return resources;
        }

        return parent.getResources(name);
    }

    private static class ClassLoaderWrapper extends ClassLoader {
        ClassLoaderWrapper(ClassLoader classLoader) {
            super(classLoader);
        }

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            return super.findClass(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            return super.loadClass(name, resolve);
        }
    }
}

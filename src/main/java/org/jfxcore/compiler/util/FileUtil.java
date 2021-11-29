// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileUtil {

    public static Iterable<Path> enumerateDirectories(Path basePath) throws IOException {
        Iterator<Path> it;
        if (Files.isDirectory(basePath)) {
            try (Stream<Path> stream = Files.walk(basePath)) {
                it = stream.filter(Files::isDirectory).collect(Collectors.toList()).iterator();
            }

            return () -> it;
        }

        return Collections::emptyIterator;
    }

    public static Iterable<Path> enumerateFiles(Path basePath, Predicate<Path> filter) throws IOException {
        Iterator<Path> it;
        if (Files.isDirectory(basePath)) {
            try (Stream<Path> stream = Files.walk(basePath)) {
                it = stream.filter(p -> Files.isRegularFile(p) && filter.test(p)).collect(Collectors.toList()).iterator();
            }

            return () -> it;
        }

        return Collections::emptyIterator;
    }

    public static String getFileNameWithoutExtension(String file) {
        int lastIdx = file.lastIndexOf('.');
        return file.substring(0, lastIdx < 0 ? Integer.MAX_VALUE : lastIdx);
    }

    public static Path removeLastN(Path path, int n) {
        for (int i = 0; i < n; ++i) {
            path = path.getParent();
        }

        return path;
    }

}

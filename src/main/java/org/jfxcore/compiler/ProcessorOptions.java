// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javax.annotation.processing.ProcessingEnvironment;
import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

record ProcessorOptions(Set<Path> sourceDirs, Set<Path> searchPath, Path intermediateBuildDir) {

    static final String SOURCE_DIRS_OPT = "org.jfxcore.compiler.processor.sourceDirs";
    static final String SEARCH_PATH_OPT = "org.jfxcore.compiler.processor.searchPath";
    static final String INTERMEDIATE_BUILD_DIR_OPT = "org.jfxcore.compiler.processor.intermediateBuildDir";

    static ProcessorOptions parse(ProcessingEnvironment processingEnv) {
        return parse(processingEnv.getOptions());
    }

    static ProcessorOptions parse(Map<String, String> options) {
        Set<Path> searchPath = parseOption(options, SEARCH_PATH_OPT);
        Set<Path> sourceDirs = parseOption(options, SOURCE_DIRS_OPT);
        Set<Path> descDir = parseOption(options, INTERMEDIATE_BUILD_DIR_OPT);

        return new ProcessorOptions(
            sourceDirs,
            searchPath,
            descDir.iterator().next());
    }

    private static Set<Path> parseOption(Map<String, String> options, String option) {
        String value = options.get(option);
        if (value == null) {
            throw new IllegalArgumentException("Missing annotation processor option: " + option);
        }

        if (value.isBlank()) {
            throw new IllegalArgumentException("No value specified for annotation processor option: " + option);
        }

        Set<Path> result = new LinkedHashSet<>();

        for (String entry : value.split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                result.add(Path.of(entry));
            }
        }

        return result;
    }
}

// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.diagnostic.MarkupException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class MoreAssertions {

    public static <T> void assertContentEquals(Collection<T> c1, Collection<T> c2) {
        if (c1 == null && c2 != null || c1 != null && c2 == null) {
            fail(String.format("Collections not equal:\r\nc1 = %s\r\nc2 = %s", c1, c2));
        }

        if (c1 != null && !Objects.equals(new HashSet<>(c1), new HashSet<>(c2))) {
            fail(String.format("Collections not equal:\r\nc1 = %s\r\nc2 = %s", c1, c2));
        }
    }

    public static <T> void assertContentEquals(Map<T, T> c1, Map<T, T> c2) {
        if (c1 == null && c2 != null || c1 != null && c2 == null) {
            fail(String.format("Maps not equal:\r\nc1 = %s\r\nc2 = %s", c1, c2));
        }

        if (c1 != null && !Objects.equals(new HashMap<>(c1), new HashMap<>(c2))) {
            fail(String.format("Maps not equal:\r\nc1 = %s\r\nc2 = %s", c1, c2));
        }
    }

    public static <T> void assertContentEquals(Collection<T> c1, Set<T> c2) {
        assertContentEquals(new HashSet<>(c1), c2);
    }

    public static <T> void assertContentEquals(Collection<T> c1, List<T> c2) {
        assertContentEquals(new ArrayList<>(c1), c2);
    }

    public static <T> void assertContentEquals(Set<T> s1, Set<T> s2) {
        if (s1 == null && s2 != null || s1 != null && s2 == null) {
            fail("Sets not equal.");
        }

        if (s1 != null && !Objects.equals(new HashSet<>(s1), new HashSet<>(s2))) {
            fail("Sets not equal.");
        }
    }

    public static <T> void assertContentEquals(List<T> l1, List<T> l2) {
        if (l1 == null && l2 != null || l1 != null && l2 == null) {
            fail("Lists not equal.");
        }

        if (l1 != null && !Objects.equals(new ArrayList<>(l1), new ArrayList<>(l2))) {
            fail("Lists not equal.");
        }
    }

    private static final Pattern HAT_PATTERN = Pattern.compile("^\\s*\\^+\\s*$");

    public static void assertCodeHighlight(String expected, MarkupException exception) {
        String[] lines = StringHelper.splitLines(exception.getMessageWithSourceInfo(), false);
        for (int i = 0; i < lines.length; ++i) {
            String line = lines[i];
            if (HAT_PATTERN.matcher(line).matches()) {
                int first = line.indexOf('^');
                int last = line.lastIndexOf('^');
                String actual = lines[i - 1].substring(first, last + 1);
                if (actual.equals(expected)) {
                    return;
                }

                fail("Expected: " + expected + ", actual: " + actual);
            }
        }

        fail("Expected: " + expected + ", actual: <none>");
    }

}

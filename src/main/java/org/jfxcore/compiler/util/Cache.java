// Copyright (c) 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CtClass;
import javassist.CtMethod;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

record CacheEntry(Object value, boolean found) {}

class Cache {

    private static final Object NULL_ENTRY = new Object() {
        @Override
        public String toString() {
            return "<null>";
        }
    };

    private final Map<Object, Object> cache = new HashMap<>();

    public CacheEntry get(Object key) {
        Object value = cache.get(key);
        if (value == NULL_ENTRY) {
            return new CacheEntry(null, true);
        }

        return new CacheEntry(value, value != null);
    }

    public <T> T put(Object key, T value) {
        cache.put(key, value == null ? NULL_ENTRY : value);
        return value;
    }
}

final class CacheKey {
    final Object item1;
    final Object item2;
    final Object item3;
    final Object item4;
    final Object item5;

    CacheKey(Object item1, Object item2) {
        this.item1 = item1;
        this.item2 = item2;
        this.item3 = null;
        this.item4 = null;
        this.item5 = null;
    }

    CacheKey(Object item1, Object item2, Object item3) {
        this.item1 = item1;
        this.item2 = item2;
        this.item3 = item3;
        this.item4 = null;
        this.item5 = null;
    }

    CacheKey(Object item1, Object item2, Object item3, Object item4) {
        this.item1 = item1;
        this.item2 = item2;
        this.item3 = item3;
        this.item4 = item4;
        this.item5 = null;
    }

    CacheKey(Object item1, Object item2, Object item3, Object item4, Object item5) {
        this.item1 = item1;
        this.item2 = item2;
        this.item3 = item3;
        this.item4 = item4;
        this.item5 = item5;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheKey tuple = (CacheKey)o;
        return equals(item1, tuple.item1)
            && equals(item2, tuple.item2)
            && equals(item3, tuple.item3)
            && equals(item4, tuple.item4)
            && equals(item5, tuple.item5);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + hashCode(item1);
        result = 31 * result + hashCode(item2);
        result = 31 * result + hashCode(item3);
        result = 31 * result + hashCode(item4);
        result = 31 * result + hashCode(item5);
        return result;
    }

    private static boolean equals(Object item1, Object item2) {
        if (item1 instanceof CtClass c1 && item2 instanceof CtClass c2) {
            return TypeHelper.equals(c1, c2);
        }

        if (item1 instanceof CtMethod m1 && item2 instanceof CtMethod m2) {
            return TypeHelper.equals(m1, m2);
        }

        return Objects.equals(item1, item2);
    }

    private static int hashCode(Object o) {
        if (o instanceof CtClass c) {
            return TypeHelper.hashCode(c);
        }

        if (o instanceof CtMethod m) {
            return TypeHelper.hashCode(m);
        }

        return o != null ? o.hashCode() : 0;
    }
}

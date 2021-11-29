// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import java.lang.reflect.Field;

public class Reflection {

    public static <T> T getFieldValue(Object obj, String fieldName) {
        return getFieldValueImpl(obj, obj.getClass(), fieldName);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getFieldValueImpl(Object obj, Class<?> type, String fieldName) {
        try {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return (T)field.get(obj);
            } catch (NoSuchFieldException ex) {
                if (obj.getClass() == Object.class) {
                    throw ex;
                }

                return getFieldValueImpl(obj, type.getSuperclass(), fieldName);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

}

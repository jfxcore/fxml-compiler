// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.runner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

abstract class ReflectiveWrapper {

    private static final Class<?>[] NO_PARAMS = new Class<?>[0];

    protected final Object target;
    private final Class<?> targetClass;

    protected ReflectiveWrapper(Object target) {
        this.target = Objects.requireNonNull(target, "target");
        this.targetClass = target.getClass();
    }

    protected static Object requireCompatible(Object target, String wrapperName, MethodRequirement... requirements) {
        Objects.requireNonNull(target, "target");
        Class<?> type = target.getClass();

        for (MethodRequirement req : requirements) {
            try {
                Method method = type.getMethod(req.name, req.parameterTypes);

                if (req.returnType != null && !req.returnType.isAssignableFrom(method.getReturnType())) {
                    throw new IllegalArgumentException(String.format(
                        "Object is not compatible with %s: method %s returns %s instead of %s",
                        wrapperName, req.signature(), method.getReturnType().getName(), req.returnType.getName()));
                }
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(String.format(
                    "Object is not compatible with %s: missing public method %s",
                    wrapperName, req.signature()));
            }
        }

        return target;
    }

    protected final Object invoke(String methodName) {
        return invoke(methodName, NO_PARAMS);
    }

    protected final Object invoke(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            return targetClass.getMethod(methodName, parameterTypes).invoke(target, args);
        } catch (InvocationTargetException e) {
            throw unchecked(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(String.format(
                "Failed to invoke %s.%s reflectively", targetClass.getName(), methodName), e);
        }
    }

    public final Object getTarget() {
        return target;
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }

        ReflectiveWrapper other = (ReflectiveWrapper)obj;
        return (Boolean)invoke("equals", new Class<?>[] {Object.class}, other.target);
    }

    @Override
    public final int hashCode() {
        return (Integer)invoke("hashCode");
    }

    @Override
    public final String toString() {
        return (String)invoke("toString");
    }

    private static RuntimeException unchecked(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }

        if (throwable instanceof Error error) {
            throw error;
        }

        return new RuntimeException(throwable);
    }

    protected static final class MethodRequirement {
        private final String name;
        private final Class<?> returnType;
        private final Class<?>[] parameterTypes;

        MethodRequirement(String name, Class<?> returnType, Class<?>... parameterTypes) {
            this.name = Objects.requireNonNull(name, "name");
            this.returnType = returnType;
            this.parameterTypes = parameterTypes != null ? parameterTypes.clone() : NO_PARAMS;
        }

        private String signature() {
            StringBuilder builder = new StringBuilder(name).append("(");

            for (int i = 0; i < parameterTypes.length; ++i) {
                if (i > 0) {
                    builder.append(", ");
                }

                builder.append(parameterTypes[i].getName());
            }

            return builder.append(")").toString();
        }
    }
}

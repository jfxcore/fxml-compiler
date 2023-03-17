// Copyright (c) 2022, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMember;
import javassist.CtMethod;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class NameHelper {

    private static final class NameData {
        final Map<String, List<Object>> uniqueNames = new HashMap<>();
    }

    public static String getUniqueName(String name, Object obj) {
        NameData nameData = (NameData)CompilationContext.getCurrent()
            .computeIfAbsent(NameHelper.class, key -> new NameData());

        String template = "__FX$%s$%d";
        List<Object> objs = nameData.uniqueNames.computeIfAbsent(name, key -> new ArrayList<>());
        for (int i = 0; i < objs.size(); ++i) {
            if (objs.get(i) == obj) {
                return String.format(template, name, i);
            }
        }

        String uniqueName = String.format(template, name, objs.size());
        objs.add(obj);

        return uniqueName;
    }

    public static String getMangledClassName(String className) {
        return "__FX$" + className;
    }

    public static String getMangledFieldName(String fieldName) {
        return "__FX$" + fieldName;
    }

    public static String getMangledMethodName(String methodName) {
        return "__FX$" + methodName;
    }

    public static String getPropertyGetterName(String name) {
        return name + "Property";
    }

    public static String getSetterName(String name) {
        if (name.length() >= 4 && name.startsWith("set") && !Character.isLowerCase(name.charAt(3))) {
            return name;
        }

        return "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    public static String getGetterName(String name, boolean isPrefix) {
        if (name.length() >= 3 && name.startsWith("is") && !Character.isLowerCase(name.charAt(2))) {
            return name;
        }

        if (name.length() >= 4 && name.startsWith("get") && !Character.isLowerCase(name.charAt(3))) {
            return name;
        }

        return (isPrefix ? "is" : "get") + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    public static String getLongMethodSignature(CtBehavior behavior) {
        String behaviorName = getJavaClassName(SourceInfo.none(), behavior.getDeclaringClass());
        if (behavior instanceof CtMethod) {
            behaviorName += "." + behavior.getName();
        }

        return getMethodSignature(
            behaviorName,
            Arrays.stream(ExceptionHelper.unchecked(SourceInfo.none(), behavior::getParameterTypes))
                .map(CtClass::getName).toArray(String[]::new),
            new String[0]);
    }

    public static String getShortMethodSignature(CtBehavior behavior) {
        return getMethodSignature(
            behavior instanceof CtConstructor ?
                behavior.getDeclaringClass().getSimpleName() : behavior.getName(),
            Arrays.stream(ExceptionHelper.unchecked(SourceInfo.none(), behavior::getParameterTypes))
                .map(CtClass::getName).toArray(String[]::new),
            new String[0]);
    }

    public static String getShortMethodSignature(CtBehavior behavior, TypeInstance[] paramTypes, String[] paramNames) {
        return getMethodSignature(
            behavior.getDeclaringClass().getSimpleName(),
            Arrays.stream(paramTypes).map(TypeInstance::getJavaName).toArray(String[]::new),
            paramNames);
    }

    private static String getMethodSignature(String behaviorName, String[] paramTypes, String[] paramNames) {
        var builder = new StringBuilder(behaviorName).append('(');

        for (int i = 0; i < paramTypes.length; i++) {
            if (i < paramNames.length) {
                builder.append(paramNames[i]);
                builder.append(": ");
            }

            builder.append(paramTypes[i]);

            if (i < paramTypes.length - 1) {
                builder.append(", ");
            }
        }

        return builder.append(')').toString();
    }

    public static String getJavaMemberName(SourceInfo sourceInfo, CtMember member) {
        return getJavaClassName(sourceInfo, member.getDeclaringClass()) + "." + member.getName();
    }

    public static String getJavaClassName(SourceInfo sourceInfo, CtClass cls) {
        return ExceptionHelper.unchecked(sourceInfo, () -> {
            char[] name = cls.getName().toCharArray();
            CtClass declaringClass = cls.getDeclaringClass();

            while (declaringClass != null) {
                name[declaringClass.getName().length()] = '.';
                declaringClass = declaringClass.getDeclaringClass();
            }

            return new String(name);
        });
    }

    public static String formatPropertyName(PropertyInfo propertyInfo) {
        String propertyName = propertyInfo.getName();
        if (propertyName.contains(".")) {
            return propertyInfo.getDeclaringType().getSimpleName() + ".(" + propertyName + ")";
        }

        return propertyInfo.getDeclaringType().getSimpleName() + "." + propertyName;
    }

    public static String formatPropertyName(CtClass declaringClass, String name) {
        if (name.contains(".")) {
            return declaringClass.getSimpleName() + ".(" + name + ")";
        }

        return declaringClass.getSimpleName() + "." + name;
    }

    public static String formatPropertyName(String declaring, String name) {
        if (name.contains(".")) {
            return declaring + ".(" + name + ")";
        }

        return declaring + "." + name;
    }

    private static final Pattern JAVA_IDENTIFIER = Pattern.compile(
        "^(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)$");

    private static final Pattern CSS_IDENTIFIER = Pattern.compile(
        "-?[_a-zA-Z]+[_a-zA-Z0-9-]*");

    public static boolean isJavaIdentifier(String value) {
        return JAVA_IDENTIFIER.matcher(value).matches();
    }

    public static boolean isCssIdentifier(String value) {
        return CSS_IDENTIFIER.matcher(value).matches();
    }

}

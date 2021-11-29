// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CtBehavior;
import javassist.CtClass;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NameHelper {

    private static final class NameData {
        final Map<String, String> mangledNames = new HashMap<>();
        int nameIndex;
    }

    public static String getUniqueName(String name, Object obj) {
        return name + getMangledClassName(Integer.toString(System.identityHashCode(obj)));
    }

    public static String getMangledClassName(String className) {
        NameData nameData = (NameData)CompilationContext.getCurrent()
            .computeIfAbsent(NameHelper.class, key -> new NameData());

        String mangledName = nameData.mangledNames.get(className);
        if (mangledName != null) {
            return mangledName;
        }

        mangledName = "$" + nameData.nameIndex++;
        nameData.mangledNames.put(className, mangledName);

        return mangledName;
    }

    public static String getMangledMethodName(String methodName) {
        return "$" + methodName;
    }

    public static String getPropertyName(String name) {
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

    @SuppressWarnings("StringBufferReplaceableByString")
    public static String getShortMethodSignature(CtBehavior behavior) {
        StringBuilder builder = new StringBuilder(behavior.getDeclaringClass().getSimpleName());
        builder.append(".").append(behavior.getName()).append("(");
        builder.append(ExceptionHelper.unchecked(SourceInfo.none(), () ->
            Arrays.stream(behavior.getParameterTypes())
                .map(CtClass::getSimpleName)
                .collect(Collectors.joining(","))));
        builder.append(")");
        return builder.toString();
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

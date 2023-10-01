// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import static javassist.bytecode.ConstPool.*;

public class FileUtil {

    public static final String GENERATOR_NAME = "org.jfxcore.fxml";

    private static final byte[] GENERATOR_NAME_BYTES = GENERATOR_NAME.getBytes(StandardCharsets.UTF_8);

    public static boolean hasGeneratorAttribute(Path file) throws IOException {
        try (var stream = new DataInputStream(new BufferedInputStream(new FileInputStream(file.toFile())))) {
            return hasGeneratorAttributeImpl(stream);
        }
    }

    private static boolean hasGeneratorAttributeImpl(DataInputStream stream) throws IOException {
        int magic = stream.readInt();
        if (magic != 0xcafebabe) {
            return false;
        }

        int minorVersion = stream.readShort();
        int majorVersion = stream.readShort();
        int generatorAttributeNameIndex = findGeneratorAttributeNameIndex(stream, majorVersion, minorVersion);
        if (generatorAttributeNameIndex < 0) {
            return false;
        }

        stream.readShort(); // access_flags
        stream.readShort(); // this_class
        stream.readShort(); // super_class

        for (int i = 0, interfacesCount = stream.readShort(); i < interfacesCount; ++i) {
            stream.readShort();
        }

        for (int i = 0, fieldsCount = stream.readShort(); i < fieldsCount; ++i) {
            skipFieldOrMethodInfo(stream);
        }

        for (int i = 0, methodsCount = stream.readShort(); i < methodsCount; ++i) {
            skipFieldOrMethodInfo(stream);
        }

        for (int i = 0, attributesCount = stream.readShort(); i < attributesCount; ++i) {
            if (stream.readShort() == generatorAttributeNameIndex) {
                return true;
            }

            for (int j = 0, attributeLength = stream.readInt(); j < attributeLength; ++j) {
                stream.readByte();
            }
        }

        return false;
    }

    private static void skipFieldOrMethodInfo(DataInputStream stream) throws IOException {
        stream.readShort(); // access_flags
        stream.readShort(); // name_index
        stream.readShort(); // descriptor_index
        int attributesCount = stream.readShort();

        for (int i = 0; i < attributesCount; ++i) {
            stream.readShort(); // attribute_name_index

            for (int j = 0, attributeLength = stream.readInt(); j < attributeLength; ++j) {
                stream.readByte();
            }
        }
    }

    private static int findGeneratorAttributeNameIndex(
            DataInputStream stream, int majorVersion, int minorVersion) throws IOException {
        int constantPoolCount = stream.readUnsignedShort();
        if (constantPoolCount == 0) {
            return -1;
        }

        byte[] buffer = null;
        int foundIndex = -1;

        for (int i = 1; i < constantPoolCount; ++i) {
            int bytes = switch (stream.readUnsignedByte()) {
                case CONST_Class -> 2;
                case CONST_Fieldref, CONST_Methodref, CONST_InterfaceMethodref -> 4;
                case CONST_String -> 2;
                case CONST_Integer, CONST_Float -> 4;
                case CONST_Long, CONST_Double -> {
                    ++i; // long and double constants take up two entries in the constant pool
                    yield 8;
                }
                case CONST_NameAndType -> 4;
                case CONST_MethodHandle -> 3;
                case CONST_MethodType -> 2;
                case CONST_InvokeDynamic -> 4;
                case CONST_Utf8 -> {
                    int length = stream.readUnsignedShort();
                    if (foundIndex >= 0) {
                        for (int b = 0; b < length; ++b) {
                            if (stream.read() < 0) {
                                throw new IOException("Unexpected end of file");
                            }
                        }
                    } else {
                        if (buffer == null || buffer.length < length) {
                            buffer = new byte[length];
                        }

                        if (stream.read(buffer, 0, length) != length) {
                            throw new IOException("Unexpected end of file");
                        }

                        if (GENERATOR_NAME_BYTES.length != length) {
                            yield 0;
                        }

                        for (int b = 0; b < length; ++b) {
                            if (buffer[b] != GENERATOR_NAME_BYTES[b]) {
                                yield 0;
                            }
                        }

                        foundIndex = i;
                    }

                    yield 0;
                }

                default -> throw new IOException(
                    "Unsupported class file version " + majorVersion + "." + minorVersion);
            };

            for (int b = 0; b < bytes; ++b) {
                if (stream.read() < 0) {
                    throw new IOException("Unexpected end of file");
                }
            }
        }

        return foundIndex;
    }

    /**
     * Returns a path that points to the generated Java file that corresponds to the FXML document.
     */
    public static Path getMarkupJavaFile(DocumentNode document) {
        var root = document.getRoot().as(ObjectNode.class);
        if (root == null) {
            throw new IllegalArgumentException("document");
        }

        var markupClassNameProperty = root.findIntrinsicProperty(Intrinsics.MARKUP_CLASS_NAME);
        if (markupClassNameProperty != null) {
            String className = getTextNotEmpty(root, markupClassNameProperty);
            if (!NameHelper.isJavaIdentifier(className)) {
                throw PropertyAssignmentErrors.cannotCoercePropertyValue(
                    markupClassNameProperty.getSourceInfo(),
                    markupClassNameProperty.getMarkupName(),
                    className);
            }

            return document.getSourceFile().getParent().resolve(className + ".java");
        }

        var classNameProperty = root.findIntrinsicProperty(Intrinsics.CLASS);
        if (classNameProperty != null) {
            String[] parts = getTextNotEmpty(root, classNameProperty).split("\\.");
            String packageName = Arrays.stream(parts).limit(parts.length - 1).collect(Collectors.joining("."));
            String className = parts[parts.length - 1];

            if (packageName.isEmpty()) {
                throw SymbolResolutionErrors.unnamedPackageNotSupported(
                    classNameProperty.getSourceInfo(), classNameProperty.getMarkupName());
            }

            String fileName = getFileNameWithoutExtension(document.getSourceFile().getFileName().toString());
            if (!className.equals(fileName)) {
                throw GeneralErrors.codeBehindClassNameMismatch(classNameProperty.getSourceInfo());
            }

            Path file = document.getSourceFile();
            fileName = getFileNameWithoutExtension(file.getName(file.getNameCount() - 1).toString());
            String markupClassName = NameHelper.getDefaultMarkupClassName(fileName);
            return file.getParent().resolve(markupClassName + ".java");
        }

        Path file = document.getSourceFile();
        String fileName = getFileNameWithoutExtension(file.getName(file.getNameCount() - 1).toString());
        return file.getParent().resolve(fileName + ".java");
    }

    private static String getTextNotEmpty(ObjectNode parent, PropertyNode node) {
        if (node.getValues().size() > 1 || !(node.getValues().get(0) instanceof TextNode)) {
            throw PropertyAssignmentErrors.propertyMustContainText(
                node.getSourceInfo(), parent.getType().getMarkupName(), node.getMarkupName());
        }

        String text = ((TextNode)node.getValues().get(0)).getText();
        if (text.isBlank()) {
            throw PropertyAssignmentErrors.propertyCannotBeEmpty(
                node.getSourceInfo(), parent.getType().getMarkupName(), node.getMarkupName());
        }

        return text;
    }

    public static String getFileNameWithoutExtension(String file) {
        int lastIdx = file.lastIndexOf('.');
        return file.substring(0, lastIdx < 0 ? file.length() : lastIdx);
    }

    public static Path removeLastN(Path path, int n) {
        for (int i = 0; i < n; ++i) {
            path = path.getParent();
        }

        return path;
    }

}

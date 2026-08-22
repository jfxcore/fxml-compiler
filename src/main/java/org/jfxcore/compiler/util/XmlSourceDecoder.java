// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class XmlSourceDecoder {

    private static final Charset UTF_32_BE = Charset.forName("UTF-32BE");
    private static final Charset UTF_32_LE = Charset.forName("UTF-32LE");

    private static final Pattern XML_DECLARATION = Pattern.compile(
        "^<\\?xml(?=\\s).*?\\?>", Pattern.DOTALL);

    private static final Pattern ENCODING_ATTRIBUTE = Pattern.compile(
        "(?:^|\\s)encoding\\s*=\\s*(['\"])([^'\"]+)\\1");

    private XmlSourceDecoder() {}

    public static String decode(Path sourceFile) throws IOException {
        return decode(Files.readAllBytes(sourceFile));
    }

    public static String decode(byte[] bytes) throws IOException {
        Signature signature = detectSignature(bytes);
        String declarationText = readDeclarationProbe(bytes, signature);
        Charset declaredCharset = readDeclaredCharset(declarationText);

        if (signature != null && declaredCharset != null && !signature.accepts(declaredCharset)) {
            throw new IOException(String.format(
                "XML encoding declaration '%s' conflicts with the byte signature '%s'",
                declaredCharset.name(), signature.charset().name()));
        }

        Charset charset = signature != null
            ? signature.charset()
            : declaredCharset != null ? declaredCharset : StandardCharsets.UTF_8;

        int offset = signature != null ? signature.bomLength() : 0;
        String decoded = decodeStrict(bytes, offset, charset);
        return !decoded.isEmpty() && decoded.charAt(0) == '\ufeff'
            ? decoded.substring(1)
            : decoded;
    }

    private static Signature detectSignature(byte[] bytes) {
        if (startsWith(bytes, 0xff, 0xfe, 0x00, 0x00)) {
            return new Signature(UTF_32_LE, 4, Family.UTF32);
        }

        if (startsWith(bytes, 0x00, 0x00, 0xfe, 0xff)) {
            return new Signature(UTF_32_BE, 4, Family.UTF32);
        }

        if (startsWith(bytes, 0xef, 0xbb, 0xbf)) {
            return new Signature(StandardCharsets.UTF_8, 3, Family.UTF8);
        }

        if (startsWith(bytes, 0xfe, 0xff)) {
            return new Signature(StandardCharsets.UTF_16BE, 2, Family.UTF16);
        }

        if (startsWith(bytes, 0xff, 0xfe)) {
            return new Signature(StandardCharsets.UTF_16LE, 2, Family.UTF16);
        }

        if (startsWith(bytes, 0x00, 0x00, 0x00, 0x3c)) {
            return new Signature(UTF_32_BE, 0, Family.UTF32);
        }

        if (startsWith(bytes, 0x3c, 0x00, 0x00, 0x00)) {
            return new Signature(UTF_32_LE, 0, Family.UTF32);
        }

        if (startsWith(bytes, 0x00, 0x3c, 0x00, 0x3f)) {
            return new Signature(StandardCharsets.UTF_16BE, 0, Family.UTF16);
        }

        if (startsWith(bytes, 0x3c, 0x00, 0x3f, 0x00)) {
            return new Signature(StandardCharsets.UTF_16LE, 0, Family.UTF16);
        }

        return null;
    }

    private static String readDeclarationProbe(byte[] bytes, Signature signature) throws IOException {
        if (signature != null) {
            return decodeStrict(bytes, signature.bomLength(), signature.charset());
        }

        int length = Math.min(bytes.length, 4096);
        return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
    }

    private static Charset readDeclaredCharset(String text) throws IOException {
        Matcher declaration = XML_DECLARATION.matcher(text);
        if (!declaration.find()) {
            return null;
        }

        Matcher encoding = ENCODING_ATTRIBUTE.matcher(declaration.group());
        if (!encoding.find()) {
            return null;
        }

        try {
            return Charset.forName(encoding.group(2));
        } catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
            throw new IOException("Unsupported XML encoding: " + encoding.group(2), ex);
        }
    }

    private static String decodeStrict(byte[] bytes, int offset, Charset charset) throws IOException {
        try {
            CharBuffer result = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset));

            return result.toString();
        } catch (CharacterCodingException ex) {
            throw new IOException("Malformed XML input for encoding " + charset.name(), ex);
        }
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }

        for (int i = 0; i < prefix.length; ++i) {
            if ((bytes[i] & 0xff) != prefix[i]) {
                return false;
            }
        }

        return true;
    }

    private enum Family {
        UTF8,
        UTF16,
        UTF32
    }

    private record Signature(Charset charset, int bomLength, Family family) {
        private boolean accepts(Charset declared) {
            if (charset.equals(declared)) {
                return true;
            }

            String name = declared.name();
            return family == Family.UTF16 && name.equalsIgnoreCase("UTF-16")
                || family == Family.UTF32 && name.equalsIgnoreCase("UTF-32");
        }
    }
}

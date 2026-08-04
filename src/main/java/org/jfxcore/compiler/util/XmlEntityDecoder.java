// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class XmlEntityDecoder {

    private XmlEntityDecoder() {}

    public static DecodeResult decode(String rawText) {
        Objects.requireNonNull(rawText, "rawText");

        var text = new StringBuilder(rawText.length());
        var replacements = new ArrayList<Replacement>();

        for (int rawIndex = 0; rawIndex < rawText.length();) {
            if (rawText.charAt(rawIndex) != '&') {
                text.append(rawText.charAt(rawIndex++));
                continue;
            }

            String replacement = null;
            int rawEnd = -1;

            if (rawText.startsWith("&amp;", rawIndex)) {
                replacement = "&";
                rawEnd = rawIndex + 5;
            } else if (rawText.startsWith("&lt;", rawIndex)) {
                replacement = "<";
                rawEnd = rawIndex + 4;
            } else if (rawText.startsWith("&gt;", rawIndex)) {
                replacement = ">";
                rawEnd = rawIndex + 4;
            } else if (rawText.startsWith("&quot;", rawIndex)) {
                replacement = "\"";
                rawEnd = rawIndex + 6;
            } else if (rawText.startsWith("&apos;", rawIndex)) {
                replacement = "'";
                rawEnd = rawIndex + 6;
            } else if (rawText.startsWith("&#", rawIndex)) {
                rawEnd = numericAttemptEnd(rawText, rawIndex);
                replacement = decodeNumericReference(rawText, rawIndex, rawEnd);
            }

            if (replacement == null) {
                // Unknown named references and bare ampersands are intentionally retained.
                text.append(rawText.charAt(rawIndex++));
                continue;
            }

            int decodedStart = text.length();
            text.append(replacement);
            replacements.add(new Replacement(rawIndex, rawEnd, decodedStart, text.length()));
            rawIndex = rawEnd;
        }

        return new DecodeResult(rawText, text.toString(), replacements);
    }

    private static int numericAttemptEnd(String text, int rawStart) {
        for (int i = rawStart + 2; i < text.length(); ++i) {
            char ch = text.charAt(i);
            if (ch == ';') {
                return i + 1;
            }

            if (ch == '&') {
                return i;
            }
        }

        return text.length();
    }

    private static String decodeNumericReference(String text, int rawStart, int rawEnd) {
        if (rawEnd <= rawStart + 2 || text.charAt(rawEnd - 1) != ';') {
            throw new DecodeException(rawStart, rawEnd);
        }

        int index = rawStart + 2;
        int radix = 10;

        if (index < rawEnd - 1 && text.charAt(index) == 'x') {
            radix = 16;
            ++index;
        }

        if (index == rawEnd - 1) {
            throw new DecodeException(rawStart, rawEnd);
        }

        int codePoint = 0;
        for (; index < rawEnd - 1; ++index) {
            int digit = digitValue(text.charAt(index), radix);
            if (digit < 0 || codePoint > (Character.MAX_CODE_POINT - digit) / radix) {
                throw new DecodeException(rawStart, rawEnd);
            }

            codePoint = codePoint * radix + digit;
        }

        if (!isXml10Character(codePoint)) {
            throw new DecodeException(rawStart, rawEnd);
        }

        return new String(Character.toChars(codePoint));
    }

    private static int digitValue(char ch, int radix) {
        if (ch >= '0' && ch <= '9') {
            int value = ch - '0';
            return value < radix ? value : -1;
        }

        if (radix == 16 && ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 10;
        }

        if (radix == 16 && ch >= 'A' && ch <= 'F') {
            return ch - 'A' + 10;
        }

        return -1;
    }

    private static boolean isXml10Character(int codePoint) {
        return codePoint == 0x9
            || codePoint == 0xA
            || codePoint == 0xD
            || codePoint >= 0x20 && codePoint <= 0xD7FF
            || codePoint >= 0xE000 && codePoint <= 0xFFFD
            || codePoint >= 0x10000 && codePoint <= Character.MAX_CODE_POINT;
    }

    public record Replacement(int rawStart, int rawEnd, int decodedStart, int decodedEnd) {
        public Replacement {
            if (rawStart < 0 || rawEnd <= rawStart || decodedStart < 0 || decodedEnd <= decodedStart) {
                throw new IllegalArgumentException("Invalid replacement range");
            }
        }
    }

    public static final class DecodeResult {
        private final String rawText;
        private final String text;
        private final List<Replacement> replacements;

        private DecodeResult(String rawText, String text, List<Replacement> replacements) {
            this.rawText = Objects.requireNonNull(rawText, "rawText");
            this.text = Objects.requireNonNull(text, "text");
            this.replacements = List.copyOf(replacements);
            validate();
        }

        public String text() {
            return text;
        }

        public List<Replacement> replacements() {
            return replacements;
        }

        public boolean isFor(String rawText) {
            return this.rawText.equals(rawText);
        }

        private void validate() {
            int previousRawEnd = 0;
            int previousDecodedEnd = 0;

            for (Replacement replacement : replacements) {
                if (replacement.rawEnd() > rawText.length()
                        || replacement.decodedEnd() > text.length()
                        || replacement.rawStart() < previousRawEnd
                        || replacement.decodedStart() < previousDecodedEnd) {
                    throw new IllegalArgumentException("Overlapping or out-of-range replacement");
                }

                int rawIdentityLength = replacement.rawStart() - previousRawEnd;
                int decodedIdentityLength = replacement.decodedStart() - previousDecodedEnd;
                if (rawIdentityLength != decodedIdentityLength
                        || !rawText.regionMatches(previousRawEnd, text, previousDecodedEnd, rawIdentityLength)) {
                    throw new IllegalArgumentException("Invalid identity region");
                }

                previousRawEnd = replacement.rawEnd();
                previousDecodedEnd = replacement.decodedEnd();
            }

            int rawIdentityLength = rawText.length() - previousRawEnd;
            int decodedIdentityLength = text.length() - previousDecodedEnd;
            if (rawIdentityLength != decodedIdentityLength
                    || !rawText.regionMatches(previousRawEnd, text, previousDecodedEnd, rawIdentityLength)) {
                throw new IllegalArgumentException("Invalid trailing identity region");
            }
        }
    }

    public static final class DecodeException extends RuntimeException {
        private final int rawStart;
        private final int rawEnd;

        private DecodeException(int rawStart, int rawEnd) {
            super("Invalid numeric character reference at raw range " + rawStart + ".." + rawEnd);
            this.rawStart = rawStart;
            this.rawEnd = rawEnd;
        }

        public int rawStart() {
            return rawStart;
        }

        public int rawEnd() {
            return rawEnd;
        }
    }
}

// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringHelper {

    private static final Pattern ESCAPE_PATTERN = Pattern.compile(
        "\\\\b|\\\\t|\\\\n|\\\\f|\\\\r|\\\\\"|\\\\'|\\\\\\\\|\\\\u[\\da-fA-F]{4}");

    private static final Pattern XML_ESCAPE_PATTERN = Pattern.compile(
        "(?:&gt;)|(?:&lt;)|(?:&amp;)|(?:&quot;)|(?:&apos;)|(?:&#x?[\\da-fA-F]+;)");

    public static String unquote(String value) {
        if (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1);
        }

        return value;
    }

    public static String unescape(String value) {
        if (value.isEmpty()) {
            return "";
        }

        Matcher matcher = ESCAPE_PATTERN.matcher(value);

        return matcher.replaceAll(result -> switch (result.group()) {
            case "\\b" -> "\b";
            case "\\t" -> "\t";
            case "\\n" -> "\n";
            case "\\f" -> "\f";
            case "\\r" -> "\r";
            case "\\\"" -> "\"";
            case "\\'" -> "'";
            case "\\\\" -> "\\\\";
            default -> String.valueOf((char) Integer.parseInt(result.group().substring(2), 16));
        });
    }
    
    public static String unescapeXml(String value) {
        if (value.isEmpty()) {
            return "";
        }

        Matcher matcher = XML_ESCAPE_PATTERN.matcher(value);

        return matcher.replaceAll(result -> {
            switch (result.group()) {
                case "&gt;": return ">";
                case "&lt;": return "<";
                case "&amp;": return "&";
                case "&quot;": return "\"";
                case "&apos;": return "'";
                default:
                    String numValue = result.group().substring(2, result.group().length() - 1);
                    return String.valueOf((char)(numValue.startsWith("x") ?
                        Integer.parseInt(numValue.substring(1), 16) : Integer.parseInt(numValue)));
            }
        });
    }

    public static String removeInsignificantWhitespace(String value) {
        String[] lines = splitLines(value, true);
        int firstLine = -1;

        for (int i = 0; i < lines.length; ++i) {
            if (!lines[i].isBlank()) {
                firstLine = i;
                break;
            }
        }

        if (firstLine < 0) {
            return "";
        }

        int indent = getLeadingWhitespaceCount(lines[firstLine]);
        StringBuilder builder = new StringBuilder();

        for (int i = firstLine; i < lines.length; ++i) {
            builder.append(lines[i].substring(Math.min(getLeadingWhitespaceCount(lines[i]), indent)));
        }

        return builder.toString().stripTrailing();
    }

    private static int getLeadingWhitespaceCount(String line) {
        for (int i = 0; i < line.length(); ++i) {
            if (!Character.isWhitespace(line.charAt(i))) {
                return i;
            }
        }

        return line.length();
    }

    public static String concatValues(Collection<String> values) {
        Iterator<String> it = values.iterator();
        if (!it.hasNext()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(it.next());

        while (it.hasNext()) {
            String value = it.next();
            char last = builder.charAt(builder.length() - 1);
            char next = value.charAt(0);

            if ((Character.isLetterOrDigit(last) || last == '%')
                    && (Character.isLetterOrDigit(next) || last == '%')) {
                builder.append(' ');
            }

            builder.append(value);
        }

        return builder.toString();
    }

    public static String[] splitLines(String text, boolean includeNewlineChars) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder(120);

        for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            boolean newline = false;

            switch (c) {
                case 0x000A, 0x000B, 0x000C, 0x0085, 0x2028, 0x2029 -> {
                    newline = true;
                    if (includeNewlineChars) {
                        line.append(c);
                    }
                }

                case 0x000D -> {
                    if (includeNewlineChars) {
                        line.append(c);
                    }
                    if (i < text.length() - 1 && text.charAt(i + 1) == 0x000A) {
                        if (includeNewlineChars) {
                            line.append((char) 0x000A);
                        }

                        ++i;
                    }
                    newline = true;
                }
            }

            if (newline) {
                lines.add(line.toString());
                line = new StringBuilder(120);
            } else {
                line.append(c);
            }
        }

        if (line.length() > 0) {
            lines.add(line.toString());
        }

        return lines.toArray(new String[0]);
    }

    private static final String QUOTED_STRING_PATTERN =
        "\"[^\"\\\\]*(\\\\(.|\\n)[^\"\\\\]*)*\"|'[^'\\\\]*(\\\\(.|\\n)[^'\\\\]*)*'";

    private static final WeakHashMap<String[], Pattern> REPLACE_PATTERN_CACHE = new WeakHashMap<>();

    public static String replaceQuoteAware(String text, String[] replacementPairs) {
        if (replacementPairs.length % 2 != 0) {
            throw new IllegalArgumentException();
        }

        List<String> targets = new ArrayList<>();
        for (int i = 0; i < replacementPairs.length; i += 2) {
            targets.add(Matcher.quoteReplacement(replacementPairs[i]));
        }

        Pattern pattern = REPLACE_PATTERN_CACHE.get(replacementPairs);
        if (pattern == null) {
            pattern = Pattern.compile(QUOTED_STRING_PATTERN + "|" + String.join("|", targets));
            REPLACE_PATTERN_CACHE.put(replacementPairs, pattern);
        }

        return pattern.matcher(text).replaceAll(result -> {
            for (int i = 0; i < targets.size(); ++i) {
                if (result.group().equals(replacementPairs[i * 2])) {
                    return replacementPairs[i * 2 + 1];
                }
            }

            return result.group();
        });
    }

}

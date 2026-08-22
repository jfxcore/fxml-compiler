// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a version that follows Semantic Versioning.
 *
 * @param major the major version
 * @param minor the minor version
 * @param patch the patch version
 * @param preRelease the pre-release identifiers, or an empty list for a normal version
 * @param buildMetadata the build metadata identifiers, or an empty list when no metadata is present
 */
public record SemanticVersion(
        int major,
        int minor,
        int patch,
        List<String> preRelease,
        List<String> buildMetadata) implements Comparable<SemanticVersion> {

    private static final Pattern SEMVER_PATTERN = Pattern.compile(
        "^(0|[1-9][0-9]*)(?:\\.(0|[1-9][0-9]*))?(?:\\.(0|[1-9][0-9]*))?" +
        "(?:-((?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)" +
        "(?:\\.(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*))*))?" +
        "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$");

    public SemanticVersion {
        requireNonNegative(major, "major");
        requireNonNegative(minor, "minor");
        requireNonNegative(patch, "patch");
        preRelease = List.copyOf(validateIdentifiers(preRelease, true, "pre-release"));
        buildMetadata = List.copyOf(validateIdentifiers(buildMetadata, false, "build metadata"));
    }

    public SemanticVersion(int major, int minor, int patch) {
        this(major, minor, patch, List.of(), List.of());
    }

    public static SemanticVersion parse(String value) {
        Objects.requireNonNull(value, "value");

        Matcher matcher = SEMVER_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid semantic version: " + value);
        }

        try {
            return new SemanticVersion(
                Integer.parseInt(matcher.group(1)),
                parseOptionalComponent(matcher.group(2)),
                parseOptionalComponent(matcher.group(3)),
                splitIdentifiers(matcher.group(4)),
                splitIdentifiers(matcher.group(5)));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid semantic version: " + value, ex);
        }
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int result = Integer.compare(major, other.major);
        if (result != 0) {
            return result;
        }

        result = Integer.compare(minor, other.minor);
        if (result != 0) {
            return result;
        }

        result = Integer.compare(patch, other.patch);
        if (result != 0) {
            return result;
        }

        return comparePreRelease(preRelease, other.preRelease);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder()
            .append(major)
            .append('.')
            .append(minor)
            .append('.')
            .append(patch);

        if (!preRelease.isEmpty()) {
            result.append('-')
                  .append(String.join(".", preRelease));
        }

        if (!buildMetadata.isEmpty()) {
            result.append('+')
                  .append(String.join(".", buildMetadata));
        }

        return result.toString();
    }

    private static int comparePreRelease(List<String> first, List<String> second) {
        if (first.isEmpty()) {
            return second.isEmpty() ? 0 : 1;
        }

        if (second.isEmpty()) {
            return -1;
        }

        int count = Math.min(first.size(), second.size());

        for (int i = 0; i < count; ++i) {
            String firstIdentifier = first.get(i);
            String secondIdentifier = second.get(i);
            boolean firstIsNumeric = isNumeric(firstIdentifier);
            boolean secondIsNumeric = isNumeric(secondIdentifier);
            int result;

            if (firstIsNumeric && secondIsNumeric) {
                result = compareNumericIdentifier(firstIdentifier, secondIdentifier);
            } else if (firstIsNumeric != secondIsNumeric) {
                result = firstIsNumeric ? -1 : 1;
            } else {
                result = firstIdentifier.compareTo(secondIdentifier);
            }

            if (result != 0) {
                return result;
            }
        }

        return Integer.compare(first.size(), second.size());
    }

    private static int compareNumericIdentifier(String first, String second) {
        int result = Integer.compare(first.length(), second.length());
        return result != 0 ? result : first.compareTo(second);
    }

    private static List<String> splitIdentifiers(String value) {
        return value == null ? List.of() : List.of(value.split("\\."));
    }

    private static int parseOptionalComponent(String value) {
        return value != null ? Integer.parseInt(value) : 0;
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static List<String> validateIdentifiers(List<String> identifiers,
                                                    boolean rejectLeadingZeroes,
                                                    String name) {
        for (String identifier : identifiers) {
            if (!isValidIdentifier(identifier)) {
                throw new IllegalArgumentException("Invalid " + name + " identifier: " + identifier);
            }

            if (rejectLeadingZeroes &&
                    isNumeric(identifier) &&
                    identifier.length() > 1 &&
                    identifier.charAt(0) == '0') {
                throw new IllegalArgumentException("Numeric pre-release identifier must not contain leading zero");
            }
        }

        return identifiers;
    }

    private static boolean isValidIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }

        for (int i = 0; i < identifier.length(); ++i) {
            char ch = identifier.charAt(i);
            if (!(ch >= '0' && ch <= '9') &&
                    !(ch >= 'A' && ch <= 'Z') &&
                    !(ch >= 'a' && ch <= 'z') &&
                    ch != '-') {
                return false;
            }
        }

        return true;
    }

    private static boolean isNumeric(String identifier) {
        for (int i = 0; i < identifier.length(); ++i) {
            char ch = identifier.charAt(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }

        return true;
    }
}

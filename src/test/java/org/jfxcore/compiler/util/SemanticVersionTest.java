// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SemanticVersionTest {

    @Test
    public void Parse_Provides_All_SemVer_Components() {
        SemanticVersion version = SemanticVersion.parse("12.34.56-alpha.7+build.001");

        assertEquals(12, version.major());
        assertEquals(34, version.minor());
        assertEquals(56, version.patch());
        assertEquals(List.of("alpha", "7"), version.preRelease());
        assertEquals(List.of("build", "001"), version.buildMetadata());
        assertEquals("12.34.56-alpha.7+build.001", version.toString());
    }

    @Test
    public void Parse_Defaults_Missing_Core_Components_To_Zero() {
        assertEquals(new SemanticVersion(2, 0, 0), SemanticVersion.parse("2"));
        assertEquals(new SemanticVersion(1, 0, 0), SemanticVersion.parse("1.0"));
        assertEquals("2.0.0-rc.1+build", SemanticVersion.parse("2-rc.1+build").toString());
        assertEquals("1.2.0-alpha", SemanticVersion.parse("1.2-alpha").toString());
    }

    @Test
    public void Parse_Accepts_The_Largest_Integer_Core_Field() {
        String value = "2147483647.2147483647.2147483647";
        SemanticVersion version = SemanticVersion.parse(value);

        assertEquals(Integer.MAX_VALUE, version.major());
        assertEquals(Integer.MAX_VALUE, version.minor());
        assertEquals(Integer.MAX_VALUE, version.patch());
        assertEquals(value, version.toString());
    }

    @Test
    public void CompareTo_Uses_SemVer_PreRelease_Precedence() {
        List<SemanticVersion> versions = List.of(
            SemanticVersion.parse("1.0.0-alpha"),
            SemanticVersion.parse("1.0.0-alpha.1"),
            SemanticVersion.parse("1.0.0-alpha.beta"),
            SemanticVersion.parse("1.0.0-beta"),
            SemanticVersion.parse("1.0.0-beta.2"),
            SemanticVersion.parse("1.0.0-beta.11"),
            SemanticVersion.parse("1.0.0-rc.1"),
            SemanticVersion.parse("1.0.0"));

        for (int i = 1; i < versions.size(); ++i) {
            SemanticVersion lower = versions.get(i - 1);
            SemanticVersion higher = versions.get(i);
            assertTrue(lower.compareTo(higher) < 0, lower + " should precede " + higher);
            assertTrue(higher.compareTo(lower) > 0, higher + " should follow " + lower);
        }
    }

    @Test
    public void CompareTo_Orders_The_Numeric_Core_Before_PreRelease_Identifiers() {
        assertTrue(SemanticVersion.parse("2.0.0").compareTo(SemanticVersion.parse("1.99.99")) > 0);
        assertTrue(SemanticVersion.parse("1.2.0").compareTo(SemanticVersion.parse("1.1.99")) > 0);
        assertTrue(SemanticVersion.parse("1.2.4").compareTo(SemanticVersion.parse("1.2.3")) > 0);
        assertTrue(SemanticVersion.parse("1.0.0-99999999999999999999")
            .compareTo(SemanticVersion.parse("1.0.0-100000000000000000000")) < 0);
    }

    @Test
    public void CompareTo_Ignores_Build_Metadata() {
        SemanticVersion first = SemanticVersion.parse("1.2.3-alpha+build.1");
        SemanticVersion second = SemanticVersion.parse("1.2.3-alpha+build.2");

        assertEquals(0, first.compareTo(second));
        assertNotEquals(first, second);
    }

    @Test
    public void Parse_Rejects_Strings_Outside_The_SemVer_Grammar() {
        String[] invalidVersions = {
            "", ".1", "1.", "1..0", "1.2.3.4", "01", "1.02", "1.2.03", "1.2.3-", "1.2.3+",
            "1-01", "1.2.3-alpha..1", "1.2.3+build..1", "1.2.3_alpha", "v1.2.3", " 1.2.3",
            "2147483648"
        };

        for (String value : invalidVersions) {
            assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse(value), value);
        }

        assertThrows(NullPointerException.class, () -> SemanticVersion.parse(null));
    }

    @Test
    public void Constructor_Validates_And_Copies_Identifier_Lists() {
        var preRelease = new ArrayList<>(List.of("alpha"));
        SemanticVersion version = new SemanticVersion(1, 2, 3, preRelease, List.of("001"));
        preRelease.add("later");

        assertEquals(List.of("alpha"), version.preRelease());
        assertThrows(UnsupportedOperationException.class, () -> version.preRelease().add("later"));
        assertThrows(IllegalArgumentException.class, () -> new SemanticVersion(1, 2, 3, List.of("01"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SemanticVersion(-1, 2, 3, List.of(), List.of()));
    }
}

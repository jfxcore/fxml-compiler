// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.TestBase;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AccessVerifierTest extends TestBase {

    private static Resolver resolver;

    private static String C(String c) {
        return "org.jfxcore.compiler.util.verifiertest_" + c;
    }
    
    @BeforeAll
    static void setup() {
        resolver = new Resolver(SourceInfo.none());
    }

    @Test
    public void Public_TopLevel_Class_Is_Always_Accessible() {
        var publicA = resolver.resolveClass(C("a.PublicA"));
        var packageB = resolver.resolveClass(C("b.PackageB"));
        assertTrue(AccessVerifier.isAccessible(publicA, packageB, SourceInfo.none()));
    }

    @Test
    public void Public_Nested_Class_Is_Accessible_Outside_Of_Package() {
        var nested = resolver.resolveClass(C("a.PublicA.NestedPublic"));
        var packageB = resolver.resolveClass(C("b.PackageB"));
        assertTrue(AccessVerifier.isAccessible(nested, packageB, SourceInfo.none()));
    }

    @Test
    public void NonPublic_Nested_Class_Is_Not_Accessible_Outside_Of_Package() {
        var packageNested = resolver.resolveClass(C("a.PublicA.NestedPackage"));
        var protectedNested = resolver.resolveClass(C("a.PublicA.NestedProtected"));
        var publicNested = resolver.resolveClass(C("a.PublicA.NestedPackage.NestedPublic"));
        var packageB = resolver.resolveClass(C("b.PackageB"));
        assertFalse(AccessVerifier.isAccessible(packageNested, packageB, SourceInfo.none()));
        assertFalse(AccessVerifier.isAccessible(protectedNested, packageB, SourceInfo.none()));
        assertFalse(AccessVerifier.isAccessible(publicNested, packageB, SourceInfo.none()));
    }

    @Test
    public void NonPrivate_Nested_Class_Is_Accessible_Within_Package() {
        var packageNested = resolver.resolveClass(C("a.PublicA.NestedPackage"));
        var protectedNested = resolver.resolveClass(C("a.PublicA.NestedProtected"));
        var publicNested = resolver.resolveClass(C("a.PublicA.NestedPackage.NestedPublic"));
        var packageA = resolver.resolveClass(C("a.PackageA"));
        assertTrue(AccessVerifier.isAccessible(packageNested, packageA, SourceInfo.none()));
        assertTrue(AccessVerifier.isAccessible(protectedNested, packageA, SourceInfo.none()));
        assertTrue(AccessVerifier.isAccessible(publicNested, packageA, SourceInfo.none()));
    }

    @Test
    public void Package_TopLevel_Class_Is_Not_Accessible_Outside_Of_Package() {
        var publicA = resolver.resolveClass(C("a.PublicA"));
        var packageB = resolver.resolveClass(C("b.PackageB"));
        assertFalse(AccessVerifier.isAccessible(packageB, publicA, SourceInfo.none()));
    }

    @Test
    public void Protected_Members_Are_Accessible_In_Derived_Class_Outside_Of_Package() {
        var packageNested = resolver.resolveClass(C("a.PublicA.NestedPackage"));
        var protectedNested = resolver.resolveClass(C("a.PublicA.NestedProtected"));
        var publicNested = resolver.resolveClass(C("a.PublicA.NestedPackage.NestedPublic"));
        var derived = resolver.resolveClass(C("b.PackageBInheritsA"));
        assertFalse(AccessVerifier.isAccessible(packageNested, derived, SourceInfo.none()));
        assertTrue(AccessVerifier.isAccessible(protectedNested, derived, SourceInfo.none()));
        assertFalse(AccessVerifier.isAccessible(publicNested, derived, SourceInfo.none()));
    }

    @Test
    public void Nested_Protected_Members_Are_Not_Accessible_In_Derived_Class_Outside_Of_Package() {
        var protectedNested2 = resolver.resolveClass(C("a.PublicA.NestedProtected.NestedProtected2"));
        var derived = resolver.resolveClass(C("b.PackageBInheritsA"));
        assertFalse(AccessVerifier.isAccessible(protectedNested2, derived, SourceInfo.none()));
    }

    @Test
    public void Nested_Protected_Fields_Are_Not_Accessible_In_Derived_Class_Outside_Of_Package() {
        var publicField = resolver.resolveField(resolver.resolveClass(C("a.PublicA.NestedProtected")), "publicField");
        var protectedField = resolver.resolveField(resolver.resolveClass(C("a.PublicA.NestedProtected")), "protectedField");
        var derived = resolver.resolveClass(C("b.PackageBInheritsA"));
        assertTrue(AccessVerifier.isAccessible(publicField, derived, SourceInfo.none()));
        assertFalse(AccessVerifier.isAccessible(protectedField, derived, SourceInfo.none()));
    }

}

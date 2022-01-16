package org.jfxcore.compiler.util.verifiertest_b;

import org.jfxcore.compiler.util.verifiertest_a.PublicA;

@SuppressWarnings("unused")
class PackageB {
    public PackageB() {
    }
}

@SuppressWarnings("unused")
class PackageBInheritsA extends PublicA {
}
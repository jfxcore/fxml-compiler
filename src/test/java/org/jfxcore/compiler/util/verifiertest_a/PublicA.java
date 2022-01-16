package org.jfxcore.compiler.util.verifiertest_a;

@SuppressWarnings("unused")
public class PublicA {
    public static class NestedPublic {}
    protected static class NestedProtected {
        protected static class NestedProtected2 {}
        protected static int protectedField;
        public static int publicField;
    }
    static class NestedPackage {
        public static class NestedPublic {}
    }

    public static void m_public() {}
    protected static void m_protected() {}
    static void m_package() {}
}

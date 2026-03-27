// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.type;

import org.jfxcore.compiler.TestBase;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TypeDeclarationTest extends TestBase {

    @SuppressWarnings("unused")
    public static class BaseMembers {
        public int baseField;
        private int hiddenBaseField;

        public void inheritedMethod() {}
        private void hiddenBaseMethod() {}

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }
    }

    @SuppressWarnings("unused")
    public static class DerivedMembers extends BaseMembers {
        public int derivedField;
        private int hiddenDerivedField;

        public DerivedMembers() {}

        private DerivedMembers(String value) {}

        @Override
        public void inheritedMethod() {}

        private void hiddenDerivedMethod() {}
    }

    @SuppressWarnings("unused")
    public interface ParentInterface {
        void interfaceMethod();
    }

    public interface ChildInterface extends ParentInterface {}

    @Test
    public void publicFields_Return_nonPrivate_Declared_And_Inherited_Instances() {
        var resolver = new Resolver(SourceInfo.none());
        TypeDeclaration baseType = resolver.resolveClass(BaseMembers.class.getName());
        TypeDeclaration derivedType = resolver.resolveClass(DerivedMembers.class.getName());

        FieldDeclaration derivedField = derivedType.declaredField("derivedField").orElseThrow();
        FieldDeclaration inheritedField = baseType.declaredField("baseField").orElseThrow();

        assertSame(derivedField, derivedType.field("derivedField").orElseThrow());
        assertSame(inheritedField, derivedType.field("baseField").orElseThrow());
        assertTrue(derivedType.fields().contains(derivedField));
        assertTrue(derivedType.fields().contains(inheritedField));
        assertFalse(derivedType.field("hiddenDerivedField").isPresent());
        assertFalse(derivedType.field("hiddenBaseField").isPresent());
    }

    @Test
    public void publicMethods_Return_nonPrivate_Declared_And_Inherited_Instances() {
        var resolver = new Resolver(SourceInfo.none());
        TypeDeclaration baseType = resolver.resolveClass(BaseMembers.class.getName());
        TypeDeclaration derivedType = resolver.resolveClass(DerivedMembers.class.getName());
        TypeDeclaration childInterfaceType = resolver.resolveClass(ChildInterface.class.getName());
        TypeDeclaration parentInterfaceType = resolver.resolveClass(ParentInterface.class.getName());

        MethodDeclaration overridingMethod = derivedType.requireDeclaredMethod("inheritedMethod");
        MethodDeclaration inheritedMethod = baseType.requireDeclaredMethod("inheritedMethod");
        MethodDeclaration interfaceMethod = parentInterfaceType.requireDeclaredMethod("interfaceMethod");

        assertTrue(derivedType.methods("inheritedMethod").contains(overridingMethod));
        assertFalse(derivedType.methods("inheritedMethod").contains(inheritedMethod));
        assertFalse(derivedType.methods("hiddenDerivedMethod").contains(derivedType.declaredMethods("hiddenDerivedMethod").get(0)));
        assertSame(interfaceMethod, childInterfaceType.methods("interfaceMethod").get(0));
    }

    @Test
    public void publicConstructors_Return_Only_nonPrivate_Declared_Instances() {
        var resolver = new Resolver(SourceInfo.none());
        TypeDeclaration derivedType = resolver.resolveClass(DerivedMembers.class.getName());
        TypeDeclaration stringType = resolver.resolveClass(String.class.getName());

        ConstructorDeclaration publicConstructor = derivedType.declaredConstructor().orElseThrow();
        ConstructorDeclaration privateConstructor = derivedType.declaredConstructor(stringType).orElseThrow();

        assertEquals(1, derivedType.constructors().size());
        assertSame(publicConstructor, derivedType.constructors().get(0));
        assertFalse(derivedType.constructors().contains(privateConstructor));
    }

    @Test
    public void dimensions() {
        var resolver = new Resolver(SourceInfo.none());
        TypeDeclaration type = resolver.resolveClass(B.class.getName());
        assertEquals(0, type.field("field1").orElseThrow().type().dimensions());
        assertEquals(1, type.field("field2").orElseThrow().type().dimensions());
        assertEquals(2, type.field("field3").orElseThrow().type().dimensions());
    }
}

class A {}

@SuppressWarnings("unused")
class B {
    public A field1;
    public A[] field2;
    public A[][] field3;
}

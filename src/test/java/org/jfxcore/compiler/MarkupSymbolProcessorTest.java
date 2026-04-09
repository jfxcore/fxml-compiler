// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import com.google.devtools.ksp.processing.Resolver;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.KSDeclaration;
import com.google.devtools.ksp.symbol.KSName;
import kotlin.sequences.Sequence;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MarkupSymbolProcessorTest {

    @Test
    public void Backticked_Imports_Are_Normalized_For_Java() {
        assertEquals("sample.when.MyControl", MarkupSymbolProcessor.normalizeImport("sample.`when`.MyControl"));
        assertEquals("sample.when.*", MarkupSymbolProcessor.normalizeImport("sample.`when`.*"));
    }

    @Test
    public void Type_Imports_Are_Resolved() {
        Resolver resolver = createResolver(Map.of(
            "javafx.scene.layout.Pane", classDeclaration("Pane"),
            "javafx.scene.control.TableView", classDeclaration("TableView", classDeclaration("TableViewSelectionModel")),
            "sample.ViewModel", classDeclaration("ViewModel", classDeclaration("Companion"))));

        assertTrue(MarkupSymbolProcessor.resolvesToClass(resolver, "javafx.scene.layout.Pane"));
        assertTrue(MarkupSymbolProcessor.resolvesToClass(resolver, "javafx.scene.control.TableView.TableViewSelectionModel"));
        assertFalse(MarkupSymbolProcessor.resolvesToClass(resolver, "sample.extensions.bindText"));
        assertFalse(MarkupSymbolProcessor.resolvesToClass(resolver, "sample.ViewModel.Companion.create"));
    }

    private static Resolver createResolver(Map<String, KSClassDeclaration> declarations) {
        return (Resolver)Proxy.newProxyInstance(
            MarkupSymbolProcessorTest.class.getClassLoader(),
            new Class<?>[] {Resolver.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getKSNameFromString" -> ksName((String)args[0]);
                case "getClassDeclarationByName" -> declarations.get(((KSName)args[0]).asString());
                case "toString" -> "ResolverProxy";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static KSClassDeclaration classDeclaration(String simpleName, KSClassDeclaration... nestedDeclarations) {
        return (KSClassDeclaration)Proxy.newProxyInstance(
            MarkupSymbolProcessorTest.class.getClassLoader(),
            new Class<?>[] {KSClassDeclaration.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getSimpleName" -> ksName(simpleName);
                case "getDeclarations" -> sequenceOf(List.of(nestedDeclarations));
                case "toString" -> simpleName;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static KSName ksName(String name) {
        return (KSName)Proxy.newProxyInstance(
            MarkupSymbolProcessorTest.class.getClassLoader(),
            new Class<?>[] {KSName.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "asString" -> name;
                case "getQualifier" -> name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : "";
                case "getShortName" -> name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
                case "toString" -> name;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static Sequence<KSDeclaration> sequenceOf(List<? extends KSDeclaration> declarations) {
        return new Sequence<>() {
            @Override
            public Iterator<KSDeclaration> iterator() {
                return (Iterator<KSDeclaration>)declarations.iterator();
            }
        };
    }
}




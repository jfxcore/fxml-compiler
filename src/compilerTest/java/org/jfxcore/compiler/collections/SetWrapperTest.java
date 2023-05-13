// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.collections;

import org.jfxcore.compiler.generate.collections.SetWrapperGenerator;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import javafx.beans.InvalidationListener;
import javafx.beans.property.SetProperty;
import javafx.beans.property.SimpleSetProperty;
import javafx.beans.value.ObservableSetValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.scene.layout.Pane;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SetWrapperTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class TestPane extends Pane {
        public final SetProperty<String> setProp = new SimpleSetProperty<>(this, "setProp");
        public SetProperty<String> setPropProperty() { return setProp; }
        public final Set<String> set = Set.of();
    }

    private TestPane root;
    private Class<?> setWrapperClass;

    @BeforeAll
    public void compile() {
        root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      setProp="{fx:bind set}"/>
        """);

        setWrapperClass =
            Arrays.stream(root.getClass().getDeclaredClasses())
                  .filter(c -> c.getSimpleName().endsWith(SetWrapperGenerator.CLASS_NAME))
                  .findFirst()
                  .orElseThrow();
    }

    @Test
    public void Wrapped_NullValue_Is_Empty_Set() {
        ObservableSetValue<String> wrapper = newInstance(null);
        assertEquals(Set.of(), wrapper);
    }

    @Test
    public void Modifications_Of_Wrapped_Set_Are_Visible_In_Wrapper() {
        Set<String> wrappedSet = new HashSet<>();
        ObservableSetValue<String> wrapper = newInstance(wrappedSet);
        wrappedSet.addAll(Set.of("foo", "bar", "baz"));
        assertEquals(Set.of("foo", "bar", "baz"), wrapper);
    }

    @Test
    public void Modifications_Of_Wrapper_Are_Visible_In_Wrapped_Set() {
        Set<String> wrappedSet = new HashSet<>();
        ObservableSetValue<String> wrapper = newInstance(wrappedSet);
        wrapper.addAll(Set.of("foo", "bar", "baz"));
        assertEquals(Set.of("foo", "bar", "baz"), wrappedSet);
    }

    @Test
    public void Modifications_Of_Wrapped_Set_Do_Not_Fire_Notifications() {
        Set<String> wrappedSet = new HashSet<>();
        ObservableSetValue<String> wrapper = newInstance(wrappedSet);
        List<String> trace = new SetTrace(wrapper);
        wrappedSet.addAll(Set.of("foo", "bar", "baz"));
        assertEquals(0, trace.size());
    }

    @Test
    public void Modifications_Of_Wrapped_ObservableSet_Fire_Notifications() {
        ObservableSet<String> wrappedSet = FXCollections.observableSet(new HashSet<>());
        ObservableSetValue<String> wrapper = newInstance(wrappedSet);
        List<String> trace = new SetTrace(wrapper);
        wrappedSet.add("foo");
        wrappedSet.add("bar");
        wrappedSet.remove("foo");
        assertEquals(
            List.of(
                "invalidated",
                "changed (oldValue == newValue)",
                "added foo",
                "invalidated",
                "changed (oldValue == newValue)",
                "added bar",
                "invalidated",
                "changed (oldValue == newValue)",
                "removed foo"),
            trace);
    }

    public static class SetTrace extends ArrayList<String> {
        public SetTrace(ObservableSetValue<String> list) {
            list.addListener((InvalidationListener) observable -> add("invalidated"));
            list.addListener((observable, oldValue, newValue) -> add(
                String.format("changed (oldValue %s newValue)", oldValue != newValue ? "!=" : "==")));
            list.addListener((SetChangeListener<String>) change -> {
                if (change.wasAdded()) {
                    add("added " + change.getElementAdded());
                } else if (change.wasRemoved()) {
                    add("removed " + change.getElementRemoved());
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private ObservableSetValue<String> newInstance(Set<String> set) {
        try {
            Constructor<?> ctor = setWrapperClass.getConstructors()[0];
            ctor.setAccessible(true);
            return (ObservableSetValue<String>)ctor.newInstance(root, set);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

}

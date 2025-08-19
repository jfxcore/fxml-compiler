// Copyright (c) 2023, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.collections;

import org.jfxcore.compiler.generate.collections.SetObservableValueWrapperGenerator;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SetProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleSetProperty;
import javafx.beans.value.ObservableSetValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.scene.layout.Pane;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SetObservableValueWrapperTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class Holder {
        public final ObjectProperty<ObservableSet<String>> set = new SimpleObjectProperty<>();
    }

    @SuppressWarnings("unused")
    public static class TestPane extends Pane {
        public final SetProperty<String> setProp = new SimpleSetProperty<>(FXCollections.observableSet(new HashSet<>()));
        public SetProperty<String> setPropProperty() { return setProp; }
        public final ObjectProperty<Holder> holder = new SimpleObjectProperty<>(new Holder());
    }

    private TestPane root;
    private Class<?> setWrapperClass;

    @BeforeAll
    public void compile() {
        root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      setProp="${..holder.set}"/>
        """);

        setWrapperClass =
            Arrays.stream(root.getClass().getDeclaredClasses())
                  .filter(c -> c.getSimpleName().endsWith(SetObservableValueWrapperGenerator.CLASS_NAME))
                  .findFirst()
                  .orElseThrow();
    }

    @Test
    public void Wrapped_NullValue_Throws_NPE() {
        assertThrows(NullPointerException.class, () -> newInstance(null));
    }

    @Test
    public void Modifications_Of_Wrapped_Set_Are_Visible_In_Wrapper() {
        var wrappedProperty = new SimpleObjectProperty<>(FXCollections.<String>observableSet(new HashSet<>()));
        ObservableSetValue<String> wrapper = newInstance(wrappedProperty);
        wrappedProperty.get().addAll(List.of("foo", "bar", "baz"));
        assertEquals(Set.of("foo", "bar", "baz"), wrapper);
    }

    @Test
    public void Modifications_Of_Wrapper_Are_Visible_In_Wrapped_Set() {
        var wrappedProperty = new SimpleObjectProperty<>(FXCollections.<String>observableSet(new HashSet<>()));
        ObservableSetValue<String> wrapper = newInstance(wrappedProperty);
        wrapper.addAll(List.of("foo", "bar", "baz"));
        assertEquals(Set.of("foo", "bar", "baz"), wrappedProperty.get());
    }

    @Test
    public void Modifications_Of_Wrapped_Set_Do_Not_Fire_Notifications() {
        Set<String> wrappedSet = new HashSet<>();
        ObservableValue<Set<String>> wrappedProperty = new SimpleObjectProperty<>(wrappedSet);
        ObservableSetValue<String> wrapper = newInstance(wrappedProperty);
        List<String> trace = new SetTrace(wrapper);
        wrappedSet.addAll(List.of("foo", "bar", "baz"));
        assertEquals(0, trace.size());
    }

    @Test
    public void Modifications_Of_Wrapped_ObservableSet_Fire_Notifications() {
        ObservableSet<String> wrappedSet = FXCollections.observableSet();
        ObservableValue<Set<String>> wrappedProperty = new SimpleObjectProperty<>(wrappedSet);
        ObservableSetValue<String> wrapper = newInstance(wrappedProperty);
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

    @Test
    public void Replacing_Wrapped_ObservableSet_Fires_Notifications() {
        var wrappedProperty = new SimpleObjectProperty<>(FXCollections.observableSet("foo", "bar"));
        ObservableSetValue<String> wrapper = newInstance(wrappedProperty);
        List<String> trace = new SetTrace(wrapper);
        wrappedProperty.set(FXCollections.observableSet("baz", "qux"));
        assertEquals(6, trace.size());
        assertEquals("invalidated", trace.get(0));
        assertEquals("changed (oldValue != newValue)", trace.get(1));
        assertTrue(trace.subList(2, 4).containsAll(List.of("removed bar", "removed foo")));
        assertTrue(trace.subList(4, 6).containsAll(List.of("added qux", "added baz")));
    }

    @SuppressWarnings("unchecked")
    private <E extends Throwable> ObservableSetValue<String> newInstance(
            ObservableValue<? extends Set<String>> list) throws E {
        try {
            Constructor<?> ctor = setWrapperClass.getConstructors()[0];
            ctor.setAccessible(true);
            return (ObservableSetValue<String>)ctor.newInstance(root, list);
        } catch (InvocationTargetException e) {
            throw (E)e.getCause();
        } catch (ReflectiveOperationException e) {
            throw (E)e;
        }
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
}

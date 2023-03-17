// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.collections;

import org.jfxcore.compiler.generate.collections.MapWrapperGenerator;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import javafx.beans.InvalidationListener;
import javafx.beans.property.MapProperty;
import javafx.beans.property.SimpleMapProperty;
import javafx.beans.value.ObservableMapValue;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.layout.Pane;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MapWrapperTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class TestPane extends Pane {
        public final MapProperty<Integer, String> mapProp = new SimpleMapProperty<>(this, "setProp");
        public MapProperty<Integer, String> mapPropProperty() { return mapProp; }
        public final Map<Integer, String> map = Map.of();
    }

    private TestPane root;
    private Class<?> mapWrapperClass;

    @BeforeAll
    public void compile() {
        root = compileAndRun("""
            <TestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                      mapProp="{fx:bind map}"/>
        """);

        mapWrapperClass =
            Arrays.stream(root.getClass().getDeclaredClasses())
                  .filter(c -> c.getSimpleName().endsWith(MapWrapperGenerator.CLASS_NAME))
                  .findFirst()
                  .get();
    }

    @Test
    public void Wrapped_NullValue_Is_Empty_Map() {
        ObservableMapValue<Integer, String> wrapper = newInstance(null);
        assertEquals(Map.of(), wrapper);
    }

    @Test
    public void Modifications_Of_Wrapped_Map_Are_Visible_In_Wrapper() {
        Map<Integer, String> wrappedMap = new HashMap<>();
        ObservableMapValue<Integer, String> wrapper = newInstance(wrappedMap);
        wrappedMap.putAll(Map.of(0, "foo", 1, "bar", 2, "baz"));
        assertEquals(Map.of(0, "foo", 1, "bar", 2, "baz"), wrapper);
    }

    @Test
    public void Modifications_Of_Wrapper_Are_Visible_In_Wrapped_Map() {
        Map<Integer, String> wrappedMap = new HashMap<>();
        ObservableMapValue<Integer, String> wrapper = newInstance(wrappedMap);
        wrapper.putAll(Map.of(0, "foo", 1, "bar", 2, "baz"));
        assertEquals(Map.of(0, "foo", 1, "bar", 2, "baz"), wrappedMap);
    }

    @Test
    public void Modifications_Of_Wrapped_Map_Do_Not_Fire_Notifications() {
        Map<Integer, String> wrappedMap = new HashMap<>();
        ObservableMapValue<Integer, String> wrapper = newInstance(wrappedMap);
        List<String> trace = new MapTrace(wrapper);
        wrappedMap.putAll(Map.of(0, "foo", 1, "bar", 2, "baz"));
        assertEquals(0, trace.size());
    }

    @Test
    public void Modifications_Of_Wrapped_ObservableMap_Fire_Notifications() {
        ObservableMap<Integer, String> wrappedMap = FXCollections.observableMap(new HashMap<>());
        ObservableMapValue<Integer, String> wrapper = newInstance(wrappedMap);
        List<String> trace = new MapTrace(wrapper);
        wrappedMap.put(0, "foo");
        wrappedMap.put(1, "bar");
        wrappedMap.put(0, "qux");
        wrappedMap.remove(0);
        assertEquals(
            List.of(
                "invalidated",
                "changed (oldValue == newValue)",
                "added foo at key 0",
                "invalidated",
                "changed (oldValue == newValue)",
                "added bar at key 1",
                "invalidated",
                "changed (oldValue == newValue)",
                "replaced foo by qux at key 0",
                "invalidated",
                "changed (oldValue == newValue)",
                "removed qux at key 0"),
            trace);
    }

    public static class MapTrace extends ArrayList<String> {
        public MapTrace(ObservableMapValue<Integer, String> list) {
            list.addListener((InvalidationListener) observable -> add("invalidated"));
            list.addListener((observable, oldValue, newValue) -> add(
                String.format("changed (oldValue %s newValue)", oldValue != newValue ? "!=" : "==")));
            list.addListener((MapChangeListener<Integer, String>) change -> {
                StringBuilder builder = new StringBuilder();
                if (change.wasAdded()) {
                    if (change.wasRemoved()) {
                        builder.append("replaced ").append(change.getValueRemoved())
                               .append(" by ").append(change.getValueAdded());
                    } else {
                        builder.append("added ").append(change.getValueAdded());
                    }
                } else if (change.wasRemoved()) {
                    builder.append("removed ").append(change.getValueRemoved());
                }
                builder.append(" at key ").append(change.getKey());
                add(builder.toString());
            });
        }
    }

    @SuppressWarnings("unchecked")
    private ObservableMapValue<Integer, String> newInstance(Map<Integer, String> map) {
        try {
            Constructor<?> ctor = mapWrapperClass.getConstructors()[0];
            ctor.setAccessible(true);
            return (ObservableMapValue<Integer, String>)ctor.newInstance(root, map);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

}

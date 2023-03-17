// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.collections;

import org.jfxcore.compiler.generate.collections.MapObservableValueWrapperGenerator;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import javafx.beans.property.MapProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleMapProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableMapValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.scene.layout.Pane;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jfxcore.compiler.collections.MapWrapperTest.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MapObservableValueWrapperTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class TestPane extends Pane {
        public final MapProperty<Integer, String> mapProp = new SimpleMapProperty<>(this, "mapProp");
        public MapProperty<Integer, String> mapPropProperty() { return mapProp; }
        public final ObjectProperty<Map<Integer, String>> map = new SimpleObjectProperty<>();
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
                  .filter(c -> c.getSimpleName().endsWith(MapObservableValueWrapperGenerator.CLASS_NAME))
                  .findFirst()
                  .get();
    }

    @Test
    public void Wrapped_NullValue_Throws_NPE() {
        assertThrows(NullPointerException.class, () -> newInstance(null));
    }

    @Test
    public void Modifications_Of_Wrapped_Map_Are_Visible_In_Wrapper() {
        var wrappedProperty = new SimpleObjectProperty<>(FXCollections.<Integer, String>observableMap(new HashMap<>()));
        ObservableMapValue<Integer, String> wrapper = newInstance(wrappedProperty);
        wrappedProperty.get().putAll(Map.of(0, "foo", 1, "bar", 2, "baz"));
        assertEquals(Map.of(0, "foo", 1, "bar", 2, "baz"), wrapper);
    }

    @Test
    public void Modifications_Of_Wrapper_Are_Visible_In_Wrapped_Map() {
        var wrappedProperty = new SimpleObjectProperty<>(FXCollections.<Integer, String>observableMap(new HashMap<>()));
        ObservableMapValue<Integer, String> wrapper = newInstance(wrappedProperty);
        wrapper.putAll(Map.of(0, "foo", 1, "bar", 2, "baz"));
        assertEquals(Map.of(0, "foo", 1, "bar", 2, "baz"), wrappedProperty.get());
    }

    @Test
    public void Modifications_Of_Wrapped_Set_Do_Not_Fire_Notifications() {
        Map<Integer, String> wrappedMap = new HashMap<>();
        ObservableValue<Map<Integer, String>> wrappedProperty = new SimpleObjectProperty<>(wrappedMap);
        ObservableMapValue<Integer, String> wrapper = newInstance(wrappedProperty);
        List<String> trace = new MapTrace(wrapper);
        wrappedMap.putAll(Map.of(0, "foo", 1, "bar", 2, "baz"));
        assertEquals(0, trace.size());
    }

    @Test
    public void Modifications_Of_Wrapped_ObservableSet_Fire_Notifications() {
        ObservableMap<Integer, String> wrappedMap = FXCollections.observableMap(new HashMap<>());
        ObservableValue<Map<Integer, String>> wrappedProperty = new SimpleObjectProperty<>(wrappedMap);
        ObservableMapValue<Integer, String> wrapper = newInstance(wrappedProperty);
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

    @Test
    public void Replacing_Wrapped_ObservableSet_Fires_Notifications() {
        var wrappedProperty = new SimpleObjectProperty<>(FXCollections.observableMap(Map.of(0, "foo", 1, "bar")));
        ObservableMapValue<Integer, String> wrapper = newInstance(wrappedProperty);
        List<String> trace = new MapTrace(wrapper);
        wrappedProperty.set(FXCollections.observableMap(Map.of(0, "baz", 1, "qux")));
        assertEquals(4, trace.size());
        assertEquals("invalidated", trace.get(0));
        assertEquals("changed (oldValue != newValue)", trace.get(1));
        assertTrue(trace.subList(2, 4).containsAll(List.of("replaced foo by baz at key 0", "replaced bar by qux at key 1")));
    }

    @SuppressWarnings("unchecked")
    private <E extends Throwable> ObservableMapValue<Integer, String> newInstance(
            ObservableValue<? extends Map<Integer, String>> list) throws E {
        try {
            Constructor<?> ctor = mapWrapperClass.getConstructors()[0];
            ctor.setAccessible(true);
            return (ObservableMapValue<Integer, String>)ctor.newInstance(root, list);
        } catch (InvocationTargetException e) {
            throw (E)e.getCause();
        } catch (ReflectiveOperationException e) {
            throw (E)e;
        }
    }

}

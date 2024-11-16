// Copyright (c) 2023, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.collections;

import org.jfxcore.compiler.generate.collections.ListObservableValueWrapperGenerator;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableListValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.layout.Pane;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.jfxcore.compiler.collections.ListWrapperTest.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ListObservableValueWrapperTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class TestPane extends Pane {
        public final ListProperty<String> listProp = new SimpleListProperty<>(this, "listProp");
        public ListProperty<String> listPropProperty() { return listProp; }
        public final ObjectProperty<List<String>> list = new SimpleObjectProperty<>();
    }

    private TestPane root;
    private Class<?> listWrapperClass;

    @BeforeAll
    public void compile() {
        root = compileAndRun("""
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                      listProp="${list}"/>
        """);

        listWrapperClass =
            Arrays.stream(root.getClass().getDeclaredClasses())
                  .filter(c -> c.getSimpleName().endsWith(ListObservableValueWrapperGenerator.CLASS_NAME))
                  .findFirst()
                  .orElseThrow();
    }

    @Test
    public void Wrapped_NullValue_Throws_NPE() {
        assertThrows(NullPointerException.class, () -> newInstance(null));
    }

    @Test
    public void Modifications_Of_Wrapped_List_Are_Visible_In_Wrapper() {
        var wrappedProperty = new SimpleObjectProperty<>(FXCollections.<String>observableArrayList());
        ObservableListValue<String> wrapper = newInstance(wrappedProperty);
        wrappedProperty.get().addAll(List.of("foo", "bar", "baz"));
        assertEquals(List.of("foo", "bar", "baz"), wrapper);
    }

    @Test
    public void Modifications_Of_Wrapper_Are_Visible_In_Wrapped_List() {
        var wrappedProperty = new SimpleObjectProperty<>(FXCollections.<String>observableArrayList());
        ObservableListValue<String> wrapper = newInstance(wrappedProperty);
        wrapper.addAll(List.of("foo", "bar", "baz"));
        assertEquals(List.of("foo", "bar", "baz"), wrappedProperty.get());
    }

    @Test
    public void Modifications_Of_Wrapped_List_Do_Not_Fire_Notifications() {
        List<String> wrappedList = new ArrayList<>();
        ObservableValue<List<String>> wrappedProperty = new SimpleObjectProperty<>(wrappedList);
        ObservableListValue<String> wrapper = newInstance(wrappedProperty);
        List<String> trace = new ListTrace(wrapper);
        wrappedList.addAll(List.of("foo", "bar", "baz"));
        assertEquals(0, trace.size());
    }

    @Test
    public void Modifications_Of_Wrapped_ObservableList_Fire_Notifications() {
        ObservableList<String> wrappedList = FXCollections.observableArrayList();
        ObservableValue<List<String>> wrappedProperty = new SimpleObjectProperty<>(wrappedList);
        ObservableListValue<String> wrapper = newInstance(wrappedProperty);
        List<String> trace = new ListTrace(wrapper);
        wrappedList.addAll(List.of("foo", "bar"));
        wrappedList.remove(0);
        assertEquals(
            List.of(
                "invalidated",
                "changed (oldValue == newValue)",
                "{ [foo, bar] added at 0 }",
                "invalidated",
                "changed (oldValue == newValue)",
                "{ [foo] removed at 0 }"),
            trace);
    }

    @Test
    public void Replacing_Wrapped_ObservableList_Fires_Notifications() {
        var wrappedProperty = new SimpleObjectProperty<>(FXCollections.observableArrayList("foo", "bar"));
        ObservableListValue<String> wrapper = newInstance(wrappedProperty);
        List<String> trace = new ListTrace(wrapper);
        wrappedProperty.set(FXCollections.observableArrayList("baz", "qux"));
        assertEquals(
            List.of(
                "invalidated",
                "changed (oldValue != newValue)",
                "{ [foo, bar] replaced by [baz, qux] at 0 }"),
            trace);
    }

    @SuppressWarnings("unchecked")
    private <E extends Throwable> ObservableListValue<String> newInstance(
            ObservableValue<? extends List<String>> list) throws E {
        try {
            Constructor<?> ctor = listWrapperClass.getConstructors()[0];
            ctor.setAccessible(true);
            return (ObservableListValue<String>)ctor.newInstance(root, list);
        } catch (InvocationTargetException e) {
            throw (E)e.getCause();
        } catch (ReflectiveOperationException e) {
            throw (E)e;
        }
    }

}

// Copyright (c) 2023, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.collections;

import com.sun.javafx.collections.ChangeHelper;
import org.jfxcore.compiler.generate.collections.ListWrapperGenerator;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.value.ObservableListValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.layout.Pane;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ListWrapperTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class TestPane extends Pane {
        public final ListProperty<String> listProp = new SimpleListProperty<>(this, "listProp");
        public ListProperty<String> listPropProperty() { return listProp; }
        public final List<String> list = List.of();
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
                  .filter(c -> c.getSimpleName().endsWith(ListWrapperGenerator.CLASS_NAME))
                  .findFirst()
                  .orElseThrow();
    }

    @Test
    public void Wrapped_NullValue_Is_Empty_List() {
        ObservableListValue<String> listWrapper = newInstance(null);
        assertEquals(List.of(), listWrapper);
    }

    @Test
    public void Modifications_Of_Wrapped_List_Are_Visible_In_Wrapper() {
        List<String> wrappedList = new ArrayList<>();
        ObservableListValue<String> wrapper = newInstance(wrappedList);
        wrappedList.addAll(List.of("foo", "bar", "baz"));
        assertEquals(List.of("foo", "bar", "baz"), wrapper);
    }

    @Test
    public void Modifications_Of_Wrapper_Are_Visible_In_Wrapped_List() {
        List<String> wrappedList = new ArrayList<>();
        ObservableListValue<String> wrapper = newInstance(wrappedList);
        wrapper.addAll(List.of("foo", "bar", "baz"));
        assertEquals(List.of("foo", "bar", "baz"), wrappedList);
    }

    @Test
    public void Modifications_Of_Wrapped_List_Do_Not_Fire_Notifications() {
        List<String> wrappedList = new ArrayList<>();
        ObservableListValue<String> wrapper = newInstance(wrappedList);
        boolean[] flag = new boolean[1];
        wrapper.addListener((InvalidationListener) observable -> flag[0] = true);
        wrapper.addListener((observable, oldValue, newValue) -> flag[0] = true);
        wrapper.addListener((ListChangeListener<String>) change -> flag[0] = true);
        wrappedList.addAll(List.of("foo", "bar", "baz"));
        assertFalse(flag[0]);
    }

    @Test
    public void Modifications_Of_Wrapped_ObservableList_Fire_Notifications() {
        ObservableList<String> wrappedList = FXCollections.observableArrayList();
        ObservableListValue<String> wrapper = newInstance(wrappedList);
        List<String> trace = new ArrayList<>();
        wrapper.addListener((InvalidationListener) observable -> trace.add("invalidated"));
        wrapper.addListener((observable, oldValue, newValue) -> trace.add(
            String.format("changed (oldValue %s newValue)", oldValue != newValue ? "!=" : "==")));
        wrapper.addListener((ListChangeListener<String>) change -> trace.add(change.toString()));
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

    public static class ListTrace extends ArrayList<String> {
        public ListTrace(ObservableListValue<String> list) {
            list.addListener((InvalidationListener) observable -> add("invalidated"));
            list.addListener((observable, oldValue, newValue) -> add(
                String.format("changed (oldValue %s newValue)", oldValue != newValue ? "!=" : "==")));
            list.addListener((ListChangeListener<String>) change -> {
                while (change.next()) {
                    String ret;
                    if (change.wasPermutated()) {
                        ret = "permutated";
                    } else if (change.wasUpdated()) {
                        ret = ChangeHelper.updateChangeToString(change.getFrom(), change.getTo());
                    } else {
                        ret = ChangeHelper.addRemoveChangeToString(
                            change.getFrom(), change.getTo(), change.getList(), change.getRemoved());
                    }
                    add("{ " + ret + " }");
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private ObservableListValue<String> newInstance(List<String> list) {
        try {
            Constructor<?> ctor = listWrapperClass.getConstructors()[0];
            ctor.setAccessible(true);
            return (ObservableListValue<String>)ctor.newInstance(root, list);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

}

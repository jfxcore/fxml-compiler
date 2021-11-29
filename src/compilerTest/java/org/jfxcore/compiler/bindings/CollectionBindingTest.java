// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.MethodReferencedSupport;
import org.jfxcore.compiler.util.TestCompiler;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"HttpUrlsUsage", "DuplicatedCode"})
@ExtendWith(TestExtension.class)
public class CollectionBindingTest extends MethodReferencedSupport {

    public CollectionBindingTest() {
        super("org.jfxcore.compiler.classes.CollectionBindingTest");
    }

    @SuppressWarnings("unused")
    public static class IndirectContext {
        public List<String> list1 = new ArrayList<>(List.of("foo", "bar", "baz"));
        public ObservableList<String> list2 = FXCollections.observableArrayList("foo", "bar", "baz");
        public ObjectProperty<List<String>> list3 = new SimpleObjectProperty<>(new ArrayList<>(List.of("foo", "bar", "baz")));
        public ObjectProperty<ObservableList<String>> list4 = new SimpleObjectProperty<>(FXCollections.observableArrayList(List.of("foo", "bar", "baz")));
    }

    @SuppressWarnings("unused")
    public static class ListTestPane extends Pane {
        private final ObjectProperty<IndirectContext> indirect = new SimpleObjectProperty<>(new IndirectContext());
        public Property<IndirectContext> indirectProperty() { return indirect; }

        public List<Double> incompatibleList1 = new ArrayList<>();

        public List<String> list1 = new ArrayList<>(List.of("foo", "bar", "baz"));
        public ObservableList<String> list2 = FXCollections.observableArrayList("foo", "bar", "baz");
        public ObjectProperty<List<String>> list3 = new SimpleObjectProperty<>(new ArrayList<>(List.of("foo", "bar", "baz")));
        public ObjectProperty<ObservableList<String>> list4 = new SimpleObjectProperty<>(FXCollections.observableArrayList(List.of("foo", "bar", "baz")));
        public ObservableValue<ObservableList<String>> list4ReadOnly() { return list4; }

        public Set<String> set1 = new HashSet<>(Set.of("foo", "bar", "baz"));
        public ObservableSet<String> set2 = FXCollections.observableSet("foo", "bar", "baz");
        public ObjectProperty<Set<String>> set3 = new SimpleObjectProperty<>(new HashSet<>(Set.of("foo", "bar", "baz")));
        public ObjectProperty<ObservableSet<String>> set4 = new SimpleObjectProperty<>(FXCollections.observableSet(Set.of("foo", "bar", "baz")));

        public final ListProperty<String> readOnlyListProp = new SimpleListProperty<>(this, "readOnlyListProp");
        public ReadOnlyListProperty<String> readOnlyListPropProperty() { return readOnlyListProp; }

        public final ListProperty<String> listProp = new SimpleListProperty<>(this, "listProp", FXCollections.observableArrayList());
        public ListProperty<String> listPropProperty() { return listProp; }

        public final ObjectProperty<ObservableList<String>> objectProp = new SimpleObjectProperty<>(this, "objectProp");
        public ObjectProperty<ObservableList<String>> objectPropProperty() { return objectProp; }

        private final ObservableList<String> targetObservableList = FXCollections.observableArrayList();
        public Collection<String> getTargetCollection() { return targetObservableList; }
        public List<String> getTargetList() { return targetObservableList; }
        public ObservableList<String> getTargetObservableList() { return targetObservableList; }
    }

    @Test
    public void Once_Binding_To_Vanilla_List() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Once_Binding_To_Vanilla_List", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:once list1}" objectProp="{fx:once list1}"/>
            """);

        assertEquals(3, root.listProp.size());

        boolean[] flag = new boolean[1];
        root.listProp.addListener((ListChangeListener<String>)c -> flag[0] = true);

        root.list1.clear(); // Change the source list
        assertFalse(flag[0]); // ListChangeListener was not invoked
        assertEquals(0, root.listProp.size());

        assertEquals(0, root.objectProp.get().size());
        root.list1.add("qux");
        assertEquals("qux", root.objectProp.get().get(0));
    }

    @Test
    public void Once_Binding_To_ObservableList() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Once_Binding_To_ObservableList", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:once list2}" objectProp="{fx:once list2}"/>
            """);

        assertEquals(3, root.listProp.size());
        boolean[] flag1 = new boolean[1];
        root.listProp.addListener((ListChangeListener<String>)c -> flag1[0] = true);
        root.list2.clear(); // Change the source list
        assertTrue(flag1[0]); // ListChangeListener was invoked
        assertEquals(0, root.listProp.size());

        assertEquals(0, root.objectProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.objectProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.list2.add("qux"); // Change the source list
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertEquals("qux", root.objectProp.get().get(0));
    }

    @Test
    public void Once_Binding_To_ObservableValue_Of_Vanilla_List() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Once_Binding_To_ObservableValue_Of_Vanilla_List", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:once list3}" objectProp="{fx:once list3}"/>
            """);

        assertEquals(3, root.listProp.size());
        boolean[] flag1 = new boolean[1];
        root.listProp.addListener((ListChangeListener<String>)c -> flag1[0] = true);
        root.list3.getValue().clear(); // Change the source list
        assertFalse(flag1[0]); // ListChangeListener was not invoked
        assertEquals(0, root.listProp.size());

        assertEquals(0, root.objectProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.objectProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.list3.getValue().add("qux"); // Change the source list
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertEquals("qux", root.objectProp.get().get(0));

        flag1[0] = flag2[0] = false;
        root.list3.setValue(FXCollections.observableArrayList("baz")); // Replace the entire source list
        assertFalse(flag1[0]); // ListChangeListener was not invoked
        assertFalse(flag2[0]); // ChangeListener was not invoked
    }

    @Test
    public void Once_Binding_To_ObservableValue_Of_ObservableList() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Once_Binding_To_ObservableValue_Of_ObservableList", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:once list4}" objectProp="{fx:once list4}"/>
            """);

        assertEquals(3, root.listProp.size());
        boolean[] flag1 = new boolean[1];
        root.listProp.addListener((ListChangeListener<String>)c -> flag1[0] = true);
        root.list4.getValue().clear(); // Change the source list
        assertTrue(flag1[0]); // ListChangeListener was invoked
        assertEquals(0, root.listProp.size());

        assertEquals(0, root.objectProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.objectProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.list4.getValue().add("qux"); // Change the source list
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertEquals("qux", root.objectProp.get().get(0));

        flag1[0] = flag2[0] = false;
        root.list4.setValue(FXCollections.observableArrayList("baz")); // Replace the entire source list
        assertFalse(flag1[0]); // ListChangeListener was not invoked
        assertFalse(flag2[0]); // ChangeListener was not invoked
    }

    @Test
    public void Once_ContentBinding_To_Vanilla_List() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Once_ContentBinding_To_Vanilla_List", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:once list1; content=true}"/>
            """);

        assertNotReferenced("Once_ContentBinding_To_Vanilla_List", root, "observableListValue");
        assertNotReferenced("Once_ContentBinding_To_Vanilla_List", root, "observableList");

        assertEquals(3, root.listProp.size());
        root.list1.clear(); // Change the source list
        assertEquals(3, root.listProp.size()); // Target list is unchanged
    }

    @Test
    public void Once_ContentBinding_To_Vanilla_List_Indirect() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Once_ContentBinding_To_Vanilla_List_Indirect", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:once indirect.list1; content=true}"/>
            """);

        assertNotReferenced("Once_ContentBinding_To_Vanilla_List_Indirect", root, "observableListValue");
        assertNotReferenced("Once_ContentBinding_To_Vanilla_List_Indirect", root, "observableList");

        assertEquals(3, root.listProp.size());
        root.indirect.get().list1.clear(); // Change the source list
        assertEquals(3, root.listProp.size()); // Target list is unchanged
    }

    @Test
    public void Once_ContentBinding_To_Observable_List() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Once_ContentBinding_To_Observable_List", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:once list2; content=true}"/>
            """);

        assertNotReferenced("Once_ContentBinding_To_Observable_List", root, "observableListValue");
        assertNotReferenced("Once_ContentBinding_To_Observable_List", root, "observableList");

        assertEquals(3, root.listProp.size());
        root.list1.clear(); // Change the source list
        assertEquals(3, root.listProp.size()); // Target list is unchanged
    }

    @Test
    public void Once_ContentBinding_To_Observable_List_Indirect() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Once_ContentBinding_To_Observable_List_Indirect", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:once indirect.list2; content=true}"/>
            """);

        assertNotReferenced("Once_ContentBinding_To_Observable_List_Indirect", root, "observableListValue");
        assertNotReferenced("Once_ContentBinding_To_Observable_List_Indirect", root, "observableList");

        assertEquals(3, root.listProp.size());
        root.indirect.get().list1.clear(); // Change the source list
        assertEquals(3, root.listProp.size()); // Target list is unchanged
    }

    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Vanilla_List() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Once_ContentBinding_To_ObservableValue_Of_Vanilla_List", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:once list3; content=true}"/>
            """);

        assertNotReferenced("Once_ContentBinding_To_ObservableValue_Of_Vanilla_List", root, "observableListValue");
        assertNotReferenced("Once_ContentBinding_To_ObservableValue_Of_Vanilla_List", root, "observableList");

        assertEquals(3, root.listProp.size());
        root.indirect.get().list1.clear(); // Change the source list
        assertEquals(3, root.listProp.size()); // Target list is unchanged
    }

    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Vanilla_List_Indirect() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Once_ContentBinding_To_ObservableValue_Of_Vanilla_List_Indirect", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:once indirect.list3; content=true}"/>
            """);

        assertNotReferenced("Once_ContentBinding_To_ObservableValue_Of_Vanilla_List_Indirect", root, "observableListValue");
        assertNotReferenced("Once_ContentBinding_To_ObservableValue_Of_Vanilla_List_Indirect", root, "observableList");

        assertEquals(3, root.listProp.size());
        root.indirect.get().list1.clear(); // Change the source list
        assertEquals(3, root.listProp.size()); // Target list is unchanged
    }

    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Observable_List() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Once_ContentBinding_To_ObservableValue_Of_Observable_List", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:once list4; content=true}"/>
            """);

        assertNotReferenced("Once_ContentBinding_To_ObservableValue_Of_Observable_List", root, "observableListValue");
        assertNotReferenced("Once_ContentBinding_To_ObservableValue_Of_Observable_List", root, "observableList");

        assertEquals(3, root.listProp.size());
        root.indirect.get().list1.clear(); // Change the source list
        assertEquals(3, root.listProp.size()); // Target list is unchanged
    }

    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Observable_List_Indirect() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Once_ContentBinding_To_ObservableValue_Of_Observable_List_Indirect", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:once indirect.list4; content=true}"/>
            """);

        assertNotReferenced("Once_ContentBinding_To_ObservableValue_Of_Observable_List_Indirect", root, "observableListValue");
        assertNotReferenced("Once_ContentBinding_To_ObservableValue_Of_Observable_List_Indirect", root, "observableList");

        assertEquals(3, root.listProp.size());
        root.indirect.get().list1.clear(); // Change the source list
        assertEquals(3, root.listProp.size()); // Target list is unchanged
    }

    @Test
    public void Once_Binding_Fails_For_ReadOnlyListProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Once_Binding_Fails_For_ReadOnlyListProperty", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              readOnlyListProp="{fx:once list1}"/>
            """));

        assertEquals(ErrorCode.CANNOT_MODIFY_READONLY_PROPERTY, ex.getDiagnostic().getCode());
    }

    @Test
    public void Once_Binding_Fails_For_Incompatible_List() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Once_Binding_Fails_For_Incompatible_List", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:once incompatibleList1}"/>
            """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
    }

    @Test
    public void ContentBinding_With_Invalid_ContentParameter_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "ContentBinding_With_Invalid_ContentParameter_Fails", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:sync list2; content=foo}"/>
            """));

        assertEquals(ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   List
     *  expected: target.bind(FXObservables.observableListValue(source))
     */
    @Test
    public void Unidirectional_Binding_To_Vanilla_List() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Unidirectional_Binding_To_Vanilla_List", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:bind list1}" objectProp="{fx:bind list1}"/>
            """);

        assertReferenced("Unidirectional_Binding_To_Vanilla_List", root, "observableListValue");
        assertNotReferenced("Unidirectional_Binding_To_Vanilla_List", root, "observableObjectValue");
        assertEquals(3, root.listProp.size());

        boolean[] flag = new boolean[1];
        root.listProp.addListener((ListChangeListener<String>)c -> flag[0] = true);

        root.list1.clear();
        assertFalse(flag[0]);
        assertEquals(0, root.listProp.size());

        assertEquals(0, root.objectProp.get().size());
        root.list1.add("qux");
        assertEquals("qux", root.objectProp.get().get(0));
    }

    /*
     *  source:   List
     *  expected: target.bind(FXObservables.observableListValue(source))
     */
    @Test
    public void Unidirectional_Binding_To_Vanilla_List_Indirect() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Unidirectional_Binding_To_Vanilla_List_Indirect", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:bind indirect.list1}" objectProp="{fx:bind indirect.list1}"/>
            """);

        assertReferenced("Unidirectional_Binding_To_Vanilla_List_Indirect", root, "observableListValue");
        assertNotReferenced("Unidirectional_Binding_To_Vanilla_List_Indirect", root, "observableObjectValue");
        assertEquals(3, root.listProp.size());

        boolean[] flag = new boolean[1];
        root.listProp.addListener((ListChangeListener<String>)c -> flag[0] = true);

        root.indirect.get().list1.clear();
        assertFalse(flag[0]);
        assertEquals(0, root.listProp.size());
        assertEquals(0, root.objectProp.get().size());

        root.indirect.setValue(new IndirectContext());
        assertTrue(flag[0]);
        assertEquals(3, root.listProp.size());
        assertEquals(3, root.objectProp.get().size());
    }

    /*
     *  source:   List
     *  expected: error
     */
    @Test
    public void Unidirectional_ContentBinding_To_Vanilla_List_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Unidirectional_ContentBinding_To_Vanilla_List_Fails", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:bind list1; content=true}"/>
            """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   List
     *  expected: error
     */
    @Test
    public void Unidirectional_ContentBinding_Fails_For_ObjectProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Unidirectional_ContentBinding_Fails_For_ObjectProperty", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              objectProp="{fx:bind list1; content=true}"/>
            """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_TARGET, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   ObservableList
     *  expected: target.bind(FXObservables.observableObjectValue(source))
     */
    @Test
    public void Unidirectional_Binding_To_ObservableList() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Unidirectional_Binding_To_ObservableList", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:bind list2}" objectProp="{fx:bind list2}"/>
            """);

        assertReferenced("Unidirectional_Binding_To_ObservableList", root, "observableObjectValue");
        assertNotReferenced("Unidirectional_Binding_To_ObservableList", root, "observableListValue");

        assertEquals(3, root.listProp.size());
        boolean[] flag1 = new boolean[1];
        root.listProp.addListener((ListChangeListener<String>)c -> flag1[0] = true);
        root.list2.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.listProp.size());

        assertEquals(0, root.objectProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.objectProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.list2.add("qux");
        assertFalse(flag2[0]);
        assertEquals("qux", root.objectProp.get().get(0));
    }

    /*
     *  source:   ObservableList
     *  expected: target.bindContent(source)
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableList() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Unidirectional_ContentBinding_To_ObservableList", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:bind list2; content=true}"/>
            """);

        assertNotReferenced("Unidirectional_ContentBinding_To_ObservableList", root, "observableListValue");
        assertNotReferenced("Unidirectional_ContentBinding_To_ObservableList", root, "observableObjectValue");

        assertEquals(3, root.listProp.size());
        boolean[] flag1 = new boolean[1];
        root.listProp.addListener((ListChangeListener<String>)c -> flag1[0] = true);
        root.list2.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.listProp.size());
    }

    /*
     *  source:   ObservableValue<List>
     *  expected: target.bind(FXObservables.observableListValue(source))
     */
    @Test
    public void Unidirectional_Binding_To_ObservableValue_Of_Vanilla_List() throws Exception {
        ListTestPane root = TestCompiler.newInstance(
            this, "Unidirectional_Binding_To_ObservableValue_Of_Vanilla_List", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:bind list3}" objectProp="{fx:bind list3}"/>
            """);

        assertReferenced("Unidirectional_Binding_To_ObservableValue_Of_Vanilla_List", root, "observableListValue");
        assertNotReferenced("Unidirectional_Binding_To_ObservableValue_Of_Vanilla_List", root, "observableObjectValue");

        assertEquals(3, root.listProp.size());
        boolean[] flag = new boolean[1];
        root.listProp.addListener((ListChangeListener<String>)c -> flag[0] = true);
        root.list3.getValue().clear(); // Change the source list
        assertFalse(flag[0]); // ListChangeListener was not invoked
        assertEquals(0, root.listProp.size());

        flag[0] = false;
        root.list3.setValue(FXCollections.observableArrayList("baz")); // Replace the entire source list
        assertEquals(1, root.listProp.size());
        assertTrue(flag[0]); // ListChangeListener was invoked

        // create a new instance to reset all changes
        root = newInstance(root);

        flag[0] = false;
        root.objectProp.addListener((observable, oldValue, newValue) -> flag[0] = true);
        root.list3.getValue().clear(); // Change the source list
        assertFalse(flag[0]); // ChangeListener was not invoked
        assertEquals(0, root.objectProp.getValue().size());

        flag[0] = false;
        root.list3.setValue(FXCollections.observableArrayList("baz")); // Replace the entire source list
        assertEquals(1, root.objectProp.getValue().size());
        assertTrue(flag[0]); // ChangeListener was invoked
    }

    /*
     *  source:   ObservableValue<List>
     *  expected: target.bindContent(FXObservables.observableListValue(source))
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableValue_Of_Vanilla_List() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Unidirectional_ContentBinding_To_ObservableValue_Of_Vanilla_List", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:bind list3; content=true}"/>
            """);

        assertReferenced("Unidirectional_ContentBinding_To_ObservableValue_Of_Vanilla_List", root, "observableListValue");
        assertNotReferenced("Unidirectional_ContentBinding_To_ObservableValue_Of_Vanilla_List", root, "observableObjectValue");
    }

    /*
     *  source:   ObservableValue<ObservableList>
     *  expected: target.bind(source)
     */
    @Test
    public void Unidirectional_Binding_To_ObservableValue_Of_ObservableList() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Unidirectional_Binding_To_ObservableValue_Of_ObservableList", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:bind list4}" objectProp="{fx:bind list4}"/>
            """);

        assertNotReferenced("Unidirectional_Binding_To_ObservableValue_Of_ObservableList", root, "observableListValue");
        assertNotReferenced("Unidirectional_Binding_To_ObservableValue_Of_ObservableList", root, "observableObjectValue");

        assertEquals(3, root.listProp.size());
        boolean[] flag1 = new boolean[1];
        root.listProp.addListener((ListChangeListener<String>)c -> flag1[0] = true);
        root.list4.getValue().clear(); // Change the source list
        assertTrue(flag1[0]); // ListChangeListener was invoked
        assertEquals(0, root.listProp.size());

        assertEquals(0, root.objectProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.objectProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.list4.getValue().add("qux"); // Change the source list
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertEquals("qux", root.objectProp.get().get(0));

        flag1[0] = flag2[0] = false;
        root.list4.setValue(FXCollections.observableArrayList("baz")); // Replace the entire source list
        assertTrue(flag1[0]); // ListChangeListener was invoked
        assertTrue(flag2[0]); // ChangeListener was invoked
    }

    /*
     *  source:   ObservableValue<ObservableList>
     *  expected: target.bindContent(FXObservables.observableListValue(source))
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableValue_Of_ObservableList() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Unidirectional_ContentBinding_To_ObservableValue_Of_ObservableList", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:bind list4; content=true}"/>
            """);

        assertReferenced("Unidirectional_ContentBinding_To_ObservableValue_Of_ObservableList", root, "observableListValue");
        assertNotReferenced("Unidirectional_ContentBinding_To_ObservableValue_Of_ObservableList", root, "observableObjectValue");

        assertEquals(3, root.listProp.size());
        boolean[] flag1 = new boolean[1];
        root.listProp.addListener((ListChangeListener<String>)c -> flag1[0] = true);
        root.list4.getValue().clear(); // Change the source list
        assertTrue(flag1[0]); // ListChangeListener was invoked
        assertEquals(0, root.listProp.size());

        flag1[0] = false;
        root.list4.setValue(FXCollections.observableArrayList("baz")); // Replace the entire source list
        assertTrue(flag1[0]); // ListChangeListener was invoked
    }

    /*
     *  source:   List
     *  expected: error
     */
    @Test
    public void Bidirectional_Binding_To_Vanilla_List_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bidirectional_Binding_To_Vanilla_List_Fails", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:sync list1}"/>
            """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());

        ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bidirectional_Binding_To_Vanilla_List_Fails", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              objectProp="{fx:sync list1}"/>
            """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   List
     *  expected: error
     */
    @Test
    public void Bidirectional_ContentBinding_To_Vanilla_List_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bidirectional_ContentBinding_To_Vanilla_List_Fails", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:sync list1; content=true}"/>
            """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   ObservableList
     *  expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ObservableList_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bidirectional_Binding_To_ObservableList_Fails", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:sync list2}" objectProp="{fx:sync list2}"/>
            """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   ObservableList
     *  expected: target.bindContentBidirectional(source)
     */
    @Test
    public void Bidirectional_ContentBinding_To_ObservableList() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Bidirectional_ContentBinding_To_ObservableList", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:sync list2; content=true}"/>
            """);

        assertNotReferenced("Bidirectional_ContentBinding_To_ObservableList", root, "observableListValue");
        assertNotReferenced("Bidirectional_ContentBinding_To_ObservableList", root, "observableObjectValue");

        assertEquals(3, root.listProp.size());
        boolean[] flag1 = new boolean[1];
        root.listProp.addListener((ListChangeListener<String>)c -> flag1[0] = true);
        root.list2.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.listProp.size());
    }

    /*
     *  source:   ObservableValue<List>
     *  expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ObservableValue_Of_Vanilla_List_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bidirectional_Binding_To_ObservableValue_Of_Vanilla_List_Fails", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:sync list3}"/>
            """));

        assertEquals(ErrorCode.SOURCE_TYPE_MISMATCH, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   ObservableValue<List>
     *  expected: error
     */
    @Test
    public void Bidirectional_ContentBinding_To_ObservableValue_Of_Vanilla_List_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bidirectional_ContentBinding_To_ObservableValue_Of_Vanilla_List_Fails", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:sync list3; content=true}"/>
            """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     * source:   Property<ObservableList>
     * expected: target.bindBidirectional(source)
     */
    @Test
    public void Bidirectional_Binding_To_Property_Of_ObservableList() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Bidirectional_Binding_To_Property_Of_ObservableList", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:sync list4}"/>
            """);

        assertNotReferenced("Bidirectional_Binding_To_Property_Of_ObservableList", root, "observableListValue");
        assertNotReferenced("Bidirectional_Binding_To_Property_Of_ObservableList", root, "observableObjectValue");
    }

    /*
     * source:   Property<ObservableList>
     * expected: target.bindContentBidirectional(FXObservables.observableListValue(source))
     */
    @Test
    public void Bidirectional_ContentBinding_To_Property_Of_ObservableList() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Bidirectional_ContentBinding_To_Property_Of_ObservableList", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:sync list4; content=true}"/>
            """);

        assertReferenced("Bidirectional_ContentBinding_To_Property_Of_ObservableList", root, "observableListValue");
        assertNotReferenced("Bidirectional_ContentBinding_To_Property_Of_ObservableList", root, "observableObjectValue");
    }

    /*
     * source:   ObservableValue<ObservableList>
     * expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ReadOnlyObservableValue_Of_ObservableList_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
            this, "Bidirectional_Binding_To_ObservableValue_Of_ObservableList_Fails", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:sync list4ReadOnly}"/>
            """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     * source:   ObservableValue<ObservableList>
     * expected: target.bindContentBidirectional(FXObservables.observableListValue(source))
     */
    @Test
    public void Bidirectional_ContentBinding_To_ReadOnlyObservableValue_Of_ObservableList() {
        ListTestPane root = TestCompiler.newInstance(
            this, "Bidirectional_ContentBinding_To_ReadOnlyObservableValue_Of_ObservableList", """
                <?import org.jfxcore.compiler.bindings.CollectionBindingTest.ListTestPane?>
                <ListTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                              listProp="{fx:sync list4ReadOnly; content=true}"/>
            """);

        assertReferenced("Bidirectional_ContentBinding_To_ReadOnlyObservableValue_Of_ObservableList", root, "observableListValue");
        assertNotReferenced("Bidirectional_ContentBinding_To_ReadOnlyObservableValue_Of_ObservableList", root, "observableObjectValue");
    }

    @SuppressWarnings("unchecked")
    private <T> T newInstance(T object) throws Exception {
        object = (T)object.getClass().getConstructor().newInstance();
        java.lang.reflect.Method method = object.getClass().getDeclaredMethod("initializeComponent");
        method.setAccessible(true);
        method.invoke(object);
        return object;
    }
    
}

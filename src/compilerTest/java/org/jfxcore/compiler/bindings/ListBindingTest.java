// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
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
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.generate.collections.ListObservableValueWrapperGenerator;
import org.jfxcore.compiler.generate.collections.ListWrapperGenerator;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"HttpUrlsUsage", "DuplicatedCode"})
@ExtendWith(TestExtension.class)
public class ListBindingTest extends CompilerTestBase {

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

        public final ListProperty<String> readOnlyListProp = new SimpleListProperty<>(this, "readOnlyListProp");
        public ReadOnlyListProperty<String> readOnlyListPropProperty() { return readOnlyListProp; }

        public final ListProperty<String> listProp = new SimpleListProperty<>(this, "listProp", FXCollections.observableArrayList());
        public ListProperty<String> listPropProperty() { return listProp; }

        private final ListProperty<String> listPropertyWithJavaGetterNameImpl = new SimpleListProperty<>(list2);
        public ListProperty<String> getListPropertyWithJavaGetterName() { return listPropertyWithJavaGetterNameImpl; }

        public final ObjectProperty<ObservableList<String>> objectProp = new SimpleObjectProperty<>(this, "objectProp");
        public ObjectProperty<ObservableList<String>> objectPropProperty() { return objectProp; }

        private final ObservableList<String> targetObservableList = FXCollections.observableArrayList();
        public Collection<String> getTargetCollection() { return targetObservableList; }
        public List<String> getTargetList() { return targetObservableList; }
        public ObservableList<String> getTargetObservableList() { return targetObservableList; }
    }

    private static String LIST_WRAPPER;
    private static String OBSERVABLE_VALUE_WRAPPER;
    private static String ADD_REFERENCE_METHOD;
    private static String CLEAR_STALE_REFERENCES_METHOD;

    @BeforeAll
    public static void beforeAll() {
        LIST_WRAPPER = ListWrapperGenerator.CLASS_NAME;
        OBSERVABLE_VALUE_WRAPPER = ListObservableValueWrapperGenerator.CLASS_NAME;
        ADD_REFERENCE_METHOD = NameHelper.getMangledMethodName("addReference");
        CLEAR_STALE_REFERENCES_METHOD = NameHelper.getMangledMethodName("clearStaleReferences");
    }

    @Test
    public void Invalid_Content_Expression() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="$...list1"/>
        """));

        assertEquals(ErrorCode.INVALID_EXPRESSION, ex.getDiagnostic().getCode());
    }

    @Test
    public void Once_Binding_To_Vanilla_List() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="$list1" objectProp="$list1"/>
        """);

        assertNotNewExpr(root, LIST_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
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
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="$list2" objectProp="$list2"/>
        """);

        assertNotNewExpr(root, LIST_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
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
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="$list3" objectProp="$list3"/>
        """);

        assertNotNewExpr(root, LIST_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
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
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="$list4" objectProp="$list4"/>
        """);

        assertNotNewExpr(root, LIST_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
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
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="$..list1"/>
        """);

        assertNotNewExpr(root, LIST_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.listProp.size());
        root.list1.clear(); // Change the source list
        assertEquals(3, root.listProp.size()); // Target list is unchanged
    }

    @Test
    public void Once_ContentBinding_To_Vanilla_List_Indirect() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="$..indirect.list1"/>
        """);

        assertNotNewExpr(root, LIST_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.listProp.size());
        root.indirect.get().list1.clear(); // Change the source list
        assertEquals(3, root.listProp.size()); // Target list is unchanged
    }

    @Test
    public void Once_ContentBinding_To_Observable_List() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="$..list2"/>
        """);

        assertNotNewExpr(root, LIST_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.listProp.size());
        root.list1.clear(); // Change the source list
        assertEquals(3, root.listProp.size()); // Target list is unchanged
    }

    @Test
    public void Once_ContentBinding_To_Observable_List_Indirect() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="$..indirect.list2"/>
        """);

        assertNotNewExpr(root, LIST_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.listProp.size());
        root.indirect.get().list1.clear(); // Change the source list
        assertEquals(3, root.listProp.size()); // Target list is unchanged
    }

    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Vanilla_List() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="$..list3"/>
        """);

        assertNotNewExpr(root, LIST_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.listProp.size());
        root.indirect.get().list1.clear(); // Change the source list
        assertEquals(3, root.listProp.size()); // Target list is unchanged
    }

    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Vanilla_List_Indirect() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="$..indirect.list3"/>
        """);

        assertNotNewExpr(root, LIST_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.listProp.size());
        root.indirect.get().list1.clear(); // Change the source list
        assertEquals(3, root.listProp.size()); // Target list is unchanged
    }

    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Observable_List() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="$..list4"/>
        """);

        assertNotNewExpr(root, LIST_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.listProp.size());
        root.indirect.get().list1.clear(); // Change the source list
        assertEquals(3, root.listProp.size()); // Target list is unchanged
    }

    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Observable_List_Indirect() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="$..indirect.list4"/>
        """);

        assertNotNewExpr(root, LIST_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.listProp.size());
        root.indirect.get().list1.clear(); // Change the source list
        assertEquals(3, root.listProp.size()); // Target list is unchanged
    }

    @Test
    public void Once_Binding_Fails_For_ReadOnlyListProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          readOnlyListProp="$list1"/>
        """));

        assertEquals(ErrorCode.CANNOT_MODIFY_READONLY_PROPERTY, ex.getDiagnostic().getCode());
    }

    @Test
    public void Once_Binding_Fails_For_Incompatible_List() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="$incompatibleList1"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   List
     *  expected: target.bind(new ListWrapper(source))
     */
    @Test
    public void Unidirectional_Binding_To_Vanilla_List() {
        ListTestPane root = compileAndRun("""
            <?import org.jfxcore.compiler.bindings.ListBindingTest.ListTestPane?>
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="${list1}" objectProp="${list1}"/>
        """);

        assertNewExpr(root, LIST_WRAPPER);
        assertNotNewExpr(root, "Constant");
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
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
     *  expected: target.bind(new ListObservableValueWrapper(source))
     */
    @Test
    public void Unidirectional_Binding_To_Vanilla_List_Indirect() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="${indirect.list1}" objectProp="${indirect.list1}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotNewExpr(root, LIST_WRAPPER, "Constant");
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
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
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="${..list1}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   List
     *  expected: error
     */
    @Test
    public void Unidirectional_ContentBinding_Fails_For_ObjectProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          objectProp="${..list1}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_TARGET, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   ObservableList
     *  expected: target.bind(new ObjectConstant(source))
     */
    @Test
    public void Unidirectional_Binding_To_ObservableList() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="${list2}" objectProp="${list2}"/>
        """);

        assertNewExpr(root, "ObjectConstant");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER, LIST_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

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
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="${..list2}"/>
        """);

        assertNotNewExpr(root, "Constant", OBSERVABLE_VALUE_WRAPPER, LIST_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.listProp.size());
        boolean[] flag1 = new boolean[1];
        root.listProp.addListener((ListChangeListener<String>)c -> flag1[0] = true);
        root.list2.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.listProp.size());
    }

    /*
     *  source:   ObservableValue<List>
     *  expected: target.bind(new ListObservableValueWrapper(source))
     */
    @Test
    public void Unidirectional_Binding_To_ObservableValue_Of_Vanilla_List() throws Exception {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="${list3}" objectProp="${list3}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotNewExpr(root, "Constant", LIST_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

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
     *  expected: target.bindContent(new ListObservableValueWrapper(source))
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableValue_Of_Vanilla_List() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="${..list3}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotNewExpr(root, "Constant", LIST_WRAPPER);
        assertMethodCall(root, ADD_REFERENCE_METHOD);
        assertMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
    }

    /*
     *  source:   ObservableValue<ObservableList>
     *  expected: target.bind(source)
     */
    @Test
    public void Unidirectional_Binding_To_ObservableValue_Of_ObservableList() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="${list4}" objectProp="${list4}"/>
        """);

        assertNotNewExpr(root, "Constant", OBSERVABLE_VALUE_WRAPPER, LIST_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

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
     *  expected: target.bindContent(new ListObservableValueWrapper(source))
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableValue_Of_ObservableList() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="${..list4}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotNewExpr(root, "Constant", LIST_WRAPPER);
        assertMethodCall(root, ADD_REFERENCE_METHOD);
        assertMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

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
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="#{list1}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());

        ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          objectProp="#{list1}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   List
     *  expected: error
     */
    @Test
    public void Bidirectional_ContentBinding_To_Vanilla_List_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="#{..list1}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   ObservableList
     *  expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ObservableList_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="#{list2}" objectProp="#{list2}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   ObservableList
     *  expected: target.bindContentBidirectional(source)
     */
    @Test
    public void Bidirectional_ContentBinding_To_ObservableList() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="#{..list2}"/>
        """);

        assertNotNewExpr(root, "Constant", OBSERVABLE_VALUE_WRAPPER, LIST_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

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
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="#{list3}"/>
        """));

        assertEquals(ErrorCode.SOURCE_TYPE_MISMATCH, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   ObservableValue<List>
     *  expected: error
     */
    @Test
    public void Bidirectional_ContentBinding_To_ObservableValue_Of_Vanilla_List_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="#{..list3}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     * source:   Property<ObservableList>
     * expected: target.bindBidirectional(source)
     */
    @Test
    public void Bidirectional_Binding_To_Property_Of_ObservableList() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="#{list4}"/>
        """);

        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER, LIST_WRAPPER, "Constant");
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
    }

    /*
     * source:   Property<ObservableList>
     * expected: target.bindContentBidirectional(new ListObservableValueWrapper(source))
     */
    @Test
    public void Bidirectional_ContentBinding_To_Property_Of_ObservableList() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="#{..list4}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotNewExpr(root, LIST_WRAPPER, "Constant");
        assertMethodCall(root, ADD_REFERENCE_METHOD);
        assertMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        gc(); // verify that the generated wrapper is not prematurely collected
        root.list4.set(FXCollections.observableArrayList("123"));
        assertEquals(List.of("123"), root.listProp.get());
    }

    /*
     * source:   ObservableValue<ObservableList>
     * expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ReadOnlyObservableValue_Of_ObservableList_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="#{list4ReadOnly}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     * source:   ObservableValue<ObservableList>
     * expected: target.bindContentBidirectional(new ListObservableValueWrapper(source))
     */
    @Test
    public void Bidirectional_ContentBinding_To_ReadOnlyObservableValue_Of_ObservableList() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="#{..list4ReadOnly}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotNewExpr(root, "Constant", LIST_WRAPPER);
        assertMethodCall(root, ADD_REFERENCE_METHOD);
        assertMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
    }

    @Test
    public void Bidirectional_Binding_To_ListProperty_With_Java_Getter_Name() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="#{listPropertyWithJavaGetterName}"/>
        """);

        assertFalse(root.listProp.isBound());
        assertFalse(root.listPropertyWithJavaGetterNameImpl.isBound());
        assertEquals(root.listProp, root.listPropertyWithJavaGetterNameImpl);
        assertEquals(List.of("foo", "bar", "baz"), root.listProp);
    }

    @Test
    public void Bidirectional_ContentBinding_To_ListProperty_With_Java_Getter_Name() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          listProp="#{..listPropertyWithJavaGetterName}"/>
        """);

        assertFalse(root.listProp.isBound());
        assertFalse(root.listPropertyWithJavaGetterNameImpl.isBound());
        assertEquals(root.listProp, root.listPropertyWithJavaGetterNameImpl);
        assertEquals(List.of("foo", "bar", "baz"), root.listProp);
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

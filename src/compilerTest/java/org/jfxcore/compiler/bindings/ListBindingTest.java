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
        public List<String> list = new ArrayList<>(List.of("foo", "bar", "baz"));
        public ObservableList<String> obsList = FXCollections.observableArrayList("foo", "bar", "baz");
        public ObjectProperty<List<String>> propOfList = new SimpleObjectProperty<>(new ArrayList<>(List.of("foo", "bar", "baz")));
        public ObjectProperty<ObservableList<String>> propOfObsList = new SimpleObjectProperty<>(FXCollections.observableArrayList(List.of("foo", "bar", "baz")));
    }

    @SuppressWarnings("unused")
    public static class ListTestPane extends Pane {
        private final ObjectProperty<IndirectContext> indirect = new SimpleObjectProperty<>(new IndirectContext());
        public Property<IndirectContext> indirectProperty() { return indirect; }

        public List<Double> incompatibleList = new ArrayList<>();

        public List<String> list = new ArrayList<>(List.of("foo", "bar", "baz"));
        public ObservableList<String> obsList = FXCollections.observableArrayList("foo", "bar", "baz");
        public ObjectProperty<List<String>> propOfList = new SimpleObjectProperty<>(new ArrayList<>(List.of("foo", "bar", "baz")));
        public ObjectProperty<ObservableList<String>> propOfObsList = new SimpleObjectProperty<>(FXCollections.observableArrayList(List.of("foo", "bar", "baz")));
        public ObservableValue<ObservableList<String>> propOfObsListReadOnly() { return propOfObsList; }

        public final ListProperty<String> readOnlyListProp = new SimpleListProperty<>(this, "readOnlyListProp");
        public ReadOnlyListProperty<String> readOnlyListPropProperty() { return readOnlyListProp; }

        public final ListProperty<String> targetListProp = new SimpleListProperty<>(this, "targetListProp", FXCollections.observableArrayList());
        public ListProperty<String> targetListPropProperty() { return targetListProp; }

        private final ListProperty<String> listPropertyWithJavaGetterNameImpl = new SimpleListProperty<>(obsList);
        public ListProperty<String> getListPropertyWithJavaGetterName() { return listPropertyWithJavaGetterNameImpl; }

        public final ObjectProperty<ObservableList<String>> targetObjProp = new SimpleObjectProperty<>(this, "targetObjProp");
        public ObjectProperty<ObservableList<String>> targetObjPropProperty() { return targetObjProp; }

        private final ObservableList<String> targetObservableList = FXCollections.observableArrayList();
        public Collection<String> getTargetCollection() { return targetObservableList; }
        public List<String> getTargetList() { return targetObservableList; }
        public ObservableList<String> getTargetObservableList() { return targetObservableList; }
    }

    private static String OBSERVABLE_VALUE_WRAPPER;
    private static String ADD_REFERENCE_METHOD;
    private static String CLEAR_STALE_REFERENCES_METHOD;

    @BeforeAll
    public static void beforeAll() {
        OBSERVABLE_VALUE_WRAPPER = ListObservableValueWrapperGenerator.CLASS_NAME;
        ADD_REFERENCE_METHOD = NameHelper.getMangledMethodName("addReference");
        CLEAR_STALE_REFERENCES_METHOD = NameHelper.getMangledMethodName("clearStaleReferences");
    }

    @Test
    public void Invalid_Content_Expression() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="$...list"/>
        """));

        assertEquals(ErrorCode.INVALID_EXPRESSION, ex.getDiagnostic().getCode());
        assertCodeHighlight("...list", ex);
    }

    /*
     *  source:   List
     *  expected: error
     */
    @Test
    public void Once_Binding_To_Vanilla_List() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="$list"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("list", ex);
    }

    /*
     *  source:   ObservableList
     *  expected: target.setValue(source)
     */
    @Test
    public void Once_Binding_To_ObservableList() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="$obsList" targetObjProp="$obsList"/>
        """);

        assertMethodCall(root, "setValue");
        assertNotMethodCall(root, "bind");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetListProp.size());
        boolean[] flag1 = new boolean[1];
        root.targetListProp.addListener((ListChangeListener<String>)c -> flag1[0] = true);
        root.obsList.clear(); // Change the source list
        assertTrue(flag1[0]); // ListChangeListener was invoked
        assertEquals(0, root.targetListProp.size());

        assertEquals(0, root.targetObjProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.targetObjProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.obsList.add("qux"); // Change the source list
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertEquals("qux", root.targetObjProp.get().get(0));
    }

    /*
     *  source:   ObservableValue<List>
     *  expected: error
     */
    @Test
    public void Once_Binding_To_ObservableValue_Of_Vanilla_List() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="$propOfList"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfList", ex);
    }

    /*
     *  source:   ObservableValue<ObservableList>
     *  expected: target.setValue(source.getValue())
     */
    @Test
    public void Once_Binding_To_ObservableValue_Of_ObservableList() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="$propOfObsList" targetObjProp="$propOfObsList"/>
        """);

        assertMethodCall(root, "setValue", "getValue");
        assertNotMethodCall(root, "bind");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetListProp.size());
        boolean[] flag1 = new boolean[1];
        root.targetListProp.addListener((ListChangeListener<String>)c -> flag1[0] = true);
        root.propOfObsList.getValue().clear(); // Change the source list
        assertTrue(flag1[0]); // ListChangeListener was invoked
        assertEquals(0, root.targetListProp.size());

        assertEquals(0, root.targetObjProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.targetObjProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.propOfObsList.getValue().add("qux"); // Change the source list
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertEquals("qux", root.targetObjProp.get().get(0));

        flag1[0] = flag2[0] = false;
        root.propOfObsList.setValue(FXCollections.observableArrayList("baz")); // Replace the entire source list
        assertFalse(flag1[0]); // ListChangeListener was not invoked
        assertFalse(flag2[0]); // ChangeListener was not invoked
    }

    /*
     *  source:   List
     *  expected: target.addAll(source)
     */
    @Test
    public void Once_ContentBinding_To_Vanilla_List() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="$..list"/>
        """);

        assertMethodCall(root, "addAll");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetListProp.size());
        root.list.clear(); // Change the source list
        assertEquals(3, root.targetListProp.size()); // Target list is unchanged
    }

    /*
     *  source:   List
     *  expected: target.addAll(source)
     */
    @Test
    public void Once_ContentBinding_To_Vanilla_List_Indirect() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="$..indirect.list"/>
        """);

        assertMethodCall(root, "addAll");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetListProp.size());
        root.indirect.get().list.clear(); // Change the source list
        assertEquals(3, root.targetListProp.size()); // Target list is unchanged
    }

    /*
     *  source:   ObservableList
     *  expected: target.addAll(source)
     */
    @Test
    public void Once_ContentBinding_To_Observable_List() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="$..obsList"/>
        """);

        assertMethodCall(root, "addAll");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetListProp.size());
        root.list.clear(); // Change the source list
        assertEquals(3, root.targetListProp.size()); // Target list is unchanged
    }

    /*
     *  source:   ObservableList
     *  expected: target.addAll(source)
     */
    @Test
    public void Once_ContentBinding_To_Observable_List_Indirect() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="$..indirect.obsList"/>
        """);

        assertMethodCall(root, "addAll");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetListProp.size());
        root.indirect.get().list.clear(); // Change the source list
        assertEquals(3, root.targetListProp.size()); // Target list is unchanged
    }

    /*
     *  source:   ObservableValue<List>
     *  expected: target.addAll(source.getValue())
     */
    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Vanilla_List() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="$..propOfList"/>
        """);

        assertMethodCall(root, "addAll", "getValue");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetListProp.size());
        root.indirect.get().list.clear(); // Change the source list
        assertEquals(3, root.targetListProp.size()); // Target list is unchanged
    }

    /*
     *  source:   ObservableValue<List>
     *  expected: target.addAll(source.getValue())
     */
    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Vanilla_List_Indirect() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="$..indirect.propOfList"/>
        """);

        assertMethodCall(root, "addAll", "getValue");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetListProp.size());
        root.indirect.get().list.clear(); // Change the source list
        assertEquals(3, root.targetListProp.size()); // Target list is unchanged
    }

    /*
     *  source:   ObservableValue<ObservableList>
     *  expected: target.addAll(source.getValue())
     */
    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Observable_List() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="$..propOfObsList"/>
        """);

        assertMethodCall(root, "addAll", "getValue");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetListProp.size());
        root.indirect.get().list.clear(); // Change the source list
        assertEquals(3, root.targetListProp.size()); // Target list is unchanged
    }

    /*
     *  source:   ObservableValue<ObservableList>
     *  expected: target.addAll(source.getValue())
     */
    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Observable_List_Indirect() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="$..indirect.propOfObsList"/>
        """);

        assertMethodCall(root, "addAll", "getValue");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetListProp.size());
        root.indirect.get().list.clear(); // Change the source list
        assertEquals(3, root.targetListProp.size()); // Target list is unchanged
    }

    /*
     *  source:   List
     *  expected: error
     */
    @Test
    public void Once_Binding_Fails_For_ReadOnlyListProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          readOnlyListProp="$list"/>
        """));

        assertEquals(ErrorCode.CANNOT_MODIFY_READONLY_PROPERTY, ex.getDiagnostic().getCode());
        assertCodeHighlight("readOnlyListProp=\"$list\"", ex);
    }

    /*
     *  source:   List<Double>
     *  expected: error
     */
    @Test
    public void Once_Binding_Fails_For_Incompatible_List() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="$incompatibleList"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("incompatibleList", ex);
    }

    /*
     *  source:   List
     *  expected: error
     */
    @Test
    public void Unidirectional_Binding_To_Vanilla_List() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="${list}"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("list", ex);
    }

    /*
     *  source:   List
     *  expected: error
     */
    @Test
    public void Unidirectional_Binding_To_Vanilla_List_Indirect() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="${indirect.list}"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("indirect.list", ex);
    }

    /*
     *  source:   List
     *  expected: error
     */
    @Test
    public void Unidirectional_ContentBinding_To_Vanilla_List_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="${..list}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("list", ex);
    }

    /*
     *  source:   List
     *  expected: error
     */
    @Test
    public void Unidirectional_ContentBinding_Fails_For_ObjectProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetObjProp="${..list}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_TARGET, ex.getDiagnostic().getCode());
        assertCodeHighlight("targetObjProp=\"${..list}\"", ex);
    }

    /*
     *  source:   ObservableList
     *  expected: error
     */
    @Test
    public void Unidirectional_Binding_To_ObservableList() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="${obsList}"/>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("obsList", ex);
    }

    /*
     *  source:   ObservableList
     *  expected: target.bindContent(source)
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableList() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="${..obsList}"/>
        """);

        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.targetListProp.size());
        boolean[] flag1 = new boolean[1];
        root.targetListProp.addListener((ListChangeListener<String>)c -> flag1[0] = true);
        root.obsList.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.targetListProp.size());
    }

    /*
     *  source:   ObservableList
     *  expected: target.bindContent(new ListObservableValueWrapper(source))
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableList_Indirect() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="${..indirect.obsList}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertMethodCall(root, ADD_REFERENCE_METHOD);
        assertMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.targetListProp.size());
        boolean[] flag1 = new boolean[1];
        root.targetListProp.addListener((ListChangeListener<String>)c -> flag1[0] = true);
        root.indirect.get().obsList.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.targetListProp.size());
        root.indirect.set(new IndirectContext());
        assertEquals(List.of("foo", "bar", "baz"), root.targetListProp);
    }

    /*
     *  source:   ObservableValue<List>
     *  expected: error
     */
    @Test
    public void Unidirectional_Binding_To_ObservableValue_Of_Vanilla_List() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="${propOfList}"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfList", ex);
    }

    /*
     *  source:   ObservableValue<List>
     *  expected: error
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableValue_Of_Vanilla_List() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="${..propOfList}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfList", ex);
    }

    /*
     *  source:   ObservableValue<ObservableList>
     *  expected: target.bind(source)
     */
    @Test
    public void Unidirectional_Binding_To_ObservableValue_Of_ObservableList() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="${propOfObsList}" targetObjProp="${propOfObsList}"/>
        """);

        assertMethodCall(root, "bind");
        assertNotMethodCall(root, "getValue");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.targetListProp.size());
        boolean[] flag1 = new boolean[1];
        root.targetListProp.addListener((ListChangeListener<String>)c -> flag1[0] = true);
        root.propOfObsList.getValue().clear(); // Change the source list
        assertTrue(flag1[0]); // ListChangeListener was invoked
        assertEquals(0, root.targetListProp.size());

        assertEquals(0, root.targetObjProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.targetObjProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.propOfObsList.getValue().add("qux"); // Change the source list
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertEquals("qux", root.targetObjProp.get().get(0));

        flag1[0] = flag2[0] = false;
        root.propOfObsList.setValue(FXCollections.observableArrayList("baz")); // Replace the entire source list
        assertTrue(flag1[0]); // ListChangeListener was invoked
        assertTrue(flag2[0]); // ChangeListener was invoked
    }

    /*
     *  source:   ObservableValue<ObservableList>
     *  expected: error
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableValue_Of_ObservableList() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="${..propOfObsList}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfObsList", ex);
    }

    /*
     *  source:   List
     *  expected: error
     */
    @Test
    public void Bidirectional_Binding_To_Vanilla_List_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="#{list}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("list", ex);

        ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetObjProp="#{list}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("list", ex);
    }

    /*
     *  source:   List
     *  expected: error
     */
    @Test
    public void Bidirectional_ContentBinding_To_Vanilla_List_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="#{..list}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("list", ex);
    }

    /*
     *  source:   ObservableList
     *  expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ObservableList_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="#{obsList}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("obsList", ex);
    }

    /*
     *  source:   ObservableList
     *  expected: target.bindContentBidirectional(source)
     */
    @Test
    public void Bidirectional_ContentBinding_To_ObservableList() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="#{..obsList}"/>
        """);

        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertMethodCall(root, "bindContentBidirectional");
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.targetListProp.size());
        boolean[] flag1 = new boolean[1];
        root.targetListProp.addListener((ListChangeListener<String>)c -> flag1[0] = true);
        root.obsList.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.targetListProp.size());
    }

    /*
     *  source:   ObservableList
     *  expected: target.bindContentBidirectional(new ListObservableValueWrapper(source))
     */
    @Test
    public void Bidirectional_ContentBinding_To_ObservableList_Indirect() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="#{..indirect.obsList}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertMethodCall(root, "bindContentBidirectional");
        assertMethodCall(root, ADD_REFERENCE_METHOD);
        assertMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.targetListProp.size());
        boolean[] flag1 = new boolean[1];
        root.targetListProp.addListener((ListChangeListener<String>)c -> flag1[0] = !flag1[0]);
        root.indirect.get().obsList.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.targetListProp.size());
        root.indirect.set(new IndirectContext());
        assertFalse(flag1[0]);
        assertEquals(List.of("foo", "bar", "baz"), root.targetListProp);
    }

    /*
     *  source:   ObservableValue<List>
     *  expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ObservableValue_Of_Vanilla_List_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="#{propOfList}"/>
        """));

        assertEquals(ErrorCode.SOURCE_TYPE_MISMATCH, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfList", ex);
    }

    /*
     *  source:   ObservableValue<List>
     *  expected: error
     */
    @Test
    public void Bidirectional_ContentBinding_To_ObservableValue_Of_Vanilla_List_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="#{..propOfList}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfList", ex);
    }

    /*
     * source:   Property<ObservableList>
     * expected: target.bindBidirectional(source)
     */
    @Test
    public void Bidirectional_Binding_To_Property_Of_ObservableList() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="#{propOfObsList}"/>
        """);

        assertMethodCall(root, "bindBidirectional");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
    }

    /*
     * source:   Property<ObservableList>
     * expected: error
     */
    @Test
    public void Bidirectional_ContentBinding_To_Property_Of_ObservableList() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="#{..propOfObsList}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfObsList", ex);
    }

    /*
     * source:   ObservableValue<ObservableList>
     * expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ReadOnlyObservableValue_Of_ObservableList_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="#{propOfObsListReadOnly}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfObsListReadOnly", ex);
    }

    /*
     * source:   ObservableValue<ObservableList>
     * expected: error
     */
    @Test
    public void Bidirectional_ContentBinding_To_ReadOnlyObservableValue_Of_ObservableList() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="#{..propOfObsListReadOnly}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfObsListReadOnly", ex);
    }

    @Test
    public void Bidirectional_Binding_To_ListProperty_With_Java_Getter_Name() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="#{listPropertyWithJavaGetterName}"/>
        """);

        assertFalse(root.targetListProp.isBound());
        assertFalse(root.listPropertyWithJavaGetterNameImpl.isBound());
        assertEquals(root.targetListProp, root.listPropertyWithJavaGetterNameImpl);
        assertEquals(List.of("foo", "bar", "baz"), root.targetListProp);
    }

    @Test
    public void Bidirectional_ContentBinding_To_ListProperty_With_Java_Getter_Name() {
        ListTestPane root = compileAndRun("""
            <ListTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                          targetListProp="#{..listPropertyWithJavaGetterName}"/>
        """);

        assertFalse(root.targetListProp.isBound());
        assertFalse(root.listPropertyWithJavaGetterNameImpl.isBound());
        assertEquals(root.targetListProp, root.listPropertyWithJavaGetterNameImpl);
        assertEquals(List.of("foo", "bar", "baz"), root.targetListProp);
    }
}

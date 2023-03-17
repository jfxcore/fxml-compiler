// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.generate.collections.SetObservableValueWrapperGenerator;
import org.jfxcore.compiler.generate.collections.SetWrapperGenerator;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlySetProperty;
import javafx.beans.property.SetProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleSetProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.scene.layout.Pane;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"HttpUrlsUsage", "DuplicatedCode"})
@ExtendWith(TestExtension.class)
public class SetBindingTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class IndirectContext {
        public Set<String> set1 = new HashSet<>(List.of("foo", "bar", "baz"));
        public ObservableSet<String> set2 = FXCollections.observableSet("foo", "bar", "baz");
        public ObjectProperty<Set<String>> set3 = new SimpleObjectProperty<>(new HashSet<>(List.of("foo", "bar", "baz")));
        public ObjectProperty<ObservableSet<String>> set4 = new SimpleObjectProperty<>(FXCollections.observableSet("foo", "bar", "baz"));
    }

    @SuppressWarnings("unused")
    public static class SetTestPane extends Pane {
        private final ObjectProperty<IndirectContext> indirect = new SimpleObjectProperty<>(new IndirectContext());
        public Property<IndirectContext> indirectProperty() { return indirect; }

        public Set<Double> incompatibleSet1 = new HashSet<>();

        public Set<String> set1 = new HashSet<>(List.of("foo", "bar", "baz"));
        public ObservableSet<String> set2 = FXCollections.observableSet("foo", "bar", "baz");
        public ObjectProperty<Set<String>> set3 = new SimpleObjectProperty<>(new HashSet<>(List.of("foo", "bar", "baz")));
        public ObjectProperty<ObservableSet<String>> set4 = new SimpleObjectProperty<>(FXCollections.observableSet("foo", "bar", "baz"));
        public ObservableValue<ObservableSet<String>> set4ReadOnly() { return set4; }

        public final SetProperty<String> readOnlySetProp = new SimpleSetProperty<>(this, "readOnlySetProp");
        public ReadOnlySetProperty<String> readOnlySetPropProperty() { return readOnlySetProp; }

        public final SetProperty<String> setProp = new SimpleSetProperty<>(this, "setProp", FXCollections.observableSet());
        public SetProperty<String> setPropProperty() { return setProp; }

        public final ObjectProperty<ObservableSet<String>> objectProp = new SimpleObjectProperty<>(this, "objectProp");
        public ObjectProperty<ObservableSet<String>> objectPropProperty() { return objectProp; }

        private final ObservableSet<String> targetObservableSet = FXCollections.observableSet();
        public Collection<String> getTargetCollection() { return targetObservableSet; }
        public Set<String> getTargetSet() { return targetObservableSet; }
        public ObservableSet<String> getTargetObservableSet() { return targetObservableSet; }
    }

    private void assertMethodCall(Object root, String... methodNames) {
        List<String> methodNameList = Arrays.asList(methodNames);
        assertMethodCall(root, list -> list.stream().anyMatch(m -> methodNameList.contains(m.getName())));
    }

    private void assertNotMethodCall(Object root, String... methodNames) {
        List<String> methodNameList = Arrays.asList(methodNames);
        assertMethodCall(root, list -> list.stream().noneMatch(m -> methodNameList.contains(m.getName())));
    }

    private void assertNewExpr(Object root, String... classNames) {
        assertNewExpr(root, ctors -> ctors.stream().anyMatch(
            ctor -> Arrays.stream(classNames).anyMatch(
                cn -> ctor.getDeclaringClass().getSimpleName().endsWith(cn))));
    }

    private void assertNotNewExpr(Object root, String... classNames) {
        assertNewExpr(root, ctors -> ctors.stream().noneMatch(
            ctor -> Arrays.stream(classNames).anyMatch(
                cn -> ctor.getDeclaringClass().getSimpleName().endsWith(cn))));
    }

    private static String SET_WRAPPER;
    private static String OBSERVABLE_VALUE_WRAPPER;
    private static String ADD_REFERENCE_METHOD;

    @BeforeAll
    public static void beforeAll() {
        SET_WRAPPER = SetWrapperGenerator.CLASS_NAME;
        OBSERVABLE_VALUE_WRAPPER = SetObservableValueWrapperGenerator.CLASS_NAME;
        ADD_REFERENCE_METHOD = NameHelper.getMangledMethodName("addReference");
    }

    @Test
    public void Once_Binding_To_Vanilla_Set() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:once set1}" objectProp="{fx:once set1}"/>
        """);

        assertNotNewExpr(root, SET_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertEquals(3, root.setProp.size());

        boolean[] flag = new boolean[1];
        root.setProp.addListener((SetChangeListener<String>) c -> flag[0] = true);

        root.set1.clear(); // Change the source set
        assertFalse(flag[0]); // SetChangeListener was not invoked
        assertEquals(0, root.setProp.size());

        assertEquals(0, root.objectProp.get().size());
        root.set1.add("qux");
        assertTrue(root.objectProp.get().contains("qux"));
    }

    @Test
    public void Once_Binding_To_ObservableSet() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:once set2}" objectProp="{fx:once set2}"/>
        """);

        assertNotNewExpr(root, SET_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertEquals(3, root.setProp.size());

        boolean[] flag1 = new boolean[1];
        root.setProp.addListener((SetChangeListener<String>)c -> flag1[0] = true);
        root.set2.clear(); // Change the source set
        assertTrue(flag1[0]); // SetChangeListener was invoked
        assertEquals(0, root.setProp.size());

        assertEquals(0, root.objectProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.objectProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.set2.add("qux"); // Change the source set
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertTrue(root.objectProp.get().contains("qux"));
    }

    @Test
    public void Once_Binding_To_ObservableValue_Of_Vanilla_Set() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:once set3}" objectProp="{fx:once set3}"/>
        """);

        assertNotNewExpr(root, SET_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertEquals(3, root.setProp.size());

        boolean[] flag1 = new boolean[1];
        root.setProp.addListener((SetChangeListener<String>)c -> flag1[0] = true);
        root.set3.getValue().clear(); // Change the source set
        assertFalse(flag1[0]); // SetChangeListener was not invoked
        assertEquals(0, root.setProp.size());

        assertEquals(0, root.objectProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.objectProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.set3.getValue().add("qux"); // Change the source set
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertTrue(root.objectProp.get().contains("qux"));

        flag1[0] = flag2[0] = false;
        root.set3.setValue(FXCollections.observableSet("baz")); // Replace the entire source set
        assertFalse(flag1[0]); // SetChangeListener was not invoked
        assertFalse(flag2[0]); // ChangeListener was not invoked
    }

    @Test
    public void Once_Binding_To_ObservableValue_Of_ObservableSet() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:once set4}" objectProp="{fx:once set4}"/>
        """);

        assertEquals(3, root.setProp.size());
        boolean[] flag1 = new boolean[1];
        root.setProp.addListener((SetChangeListener<String>)c -> flag1[0] = true);
        root.set4.getValue().clear(); // Change the source set
        assertTrue(flag1[0]); // SetChangeListener was invoked
        assertEquals(0, root.setProp.size());

        assertEquals(0, root.objectProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.objectProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.set4.getValue().add("qux"); // Change the source set
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertTrue(root.objectProp.get().contains("qux"));

        flag1[0] = flag2[0] = false;
        root.set4.setValue(FXCollections.observableSet("baz")); // Replace the entire source set
        assertFalse(flag1[0]); // SetChangeListener was not invoked
        assertFalse(flag2[0]); // ChangeListener was not invoked
    }

    @Test
    public void Once_ContentBinding_To_Vanilla_Set() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:once set1; content=true}"/>
        """);

        assertNotNewExpr(root, SET_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertEquals(3, root.setProp.size());
        root.set1.clear(); // Change the source set
        assertEquals(3, root.setProp.size()); // Target set is unchanged
    }

    @Test
    public void Once_ContentBinding_To_Vanilla_Set_Indirect() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:once indirect.set1; content=true}"/>
        """);

        assertNotNewExpr(root, SET_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertEquals(3, root.setProp.size());
        root.indirect.get().set1.clear(); // Change the source set
        assertEquals(3, root.setProp.size()); // Target set is unchanged
    }

    @Test
    public void Once_ContentBinding_To_Observable_Set() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:once set2; content=true}"/>
        """);

        assertNotNewExpr(root, SET_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertEquals(3, root.setProp.size());
        root.set1.clear(); // Change the source set
        assertEquals(3, root.setProp.size()); // Target set is unchanged
    }

    @Test
    public void Once_ContentBinding_To_Observable_Set_Indirect() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:once indirect.set2; content=true}"/>
        """);

        assertNotNewExpr(root, SET_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertEquals(3, root.setProp.size());
        root.indirect.get().set1.clear(); // Change the source set
        assertEquals(3, root.setProp.size()); // Target set is unchanged
    }

    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Vanilla_Set() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:once set3; content=true}"/>
        """);

        assertNotNewExpr(root, SET_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertEquals(3, root.setProp.size());
        root.indirect.get().set1.clear(); // Change the source set
        assertEquals(3, root.setProp.size()); // Target set is unchanged
    }

    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Vanilla_Set_Indirect() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:once indirect.set3; content=true}"/>
        """);

        assertNotNewExpr(root, SET_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertEquals(3, root.setProp.size());
        root.indirect.get().set1.clear(); // Change the source set
        assertEquals(3, root.setProp.size()); // Target set is unchanged
    }

    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Observable_Set() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:once set4; content=true}"/>
        """);

        assertNotNewExpr(root, SET_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertEquals(3, root.setProp.size());
        root.indirect.get().set1.clear(); // Change the source set
        assertEquals(3, root.setProp.size()); // Target set is unchanged
    }

    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Observable_Set_Indirect() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:once indirect.set4; content=true}"/>
        """);

        assertNotNewExpr(root, SET_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertEquals(3, root.setProp.size());
        root.indirect.get().set1.clear(); // Change the source set
        assertEquals(3, root.setProp.size()); // Target set is unchanged
    }

    @Test
    public void Once_Binding_Fails_For_ReadOnlySetProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         readOnlySetProp="{fx:once set1}"/>
        """));

        assertEquals(ErrorCode.CANNOT_MODIFY_READONLY_PROPERTY, ex.getDiagnostic().getCode());
    }

    @Test
    public void Once_Binding_Fails_For_Incompatible_Set() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:once incompatibleSet1}"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
    }

    @Test
    public void ContentBinding_With_Invalid_ContentParameter_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:sync set2; content=foo}"/>
        """));

        assertEquals(ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   Set
     *  expected: target.bind(new SetWrapper(source))
     */
    @Test
    public void Unidirectional_Binding_To_Vanilla_Set() {
        SetTestPane root = compileAndRun("""
            <?import org.jfxcore.compiler.bindings.SetBindingTest.SetTestPane?>
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:bind set1}" objectProp="{fx:bind set1}"/>
        """);

        assertNewExpr(root, SET_WRAPPER);
        assertNotNewExpr(root, "Constant");
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertEquals(3, root.setProp.size());

        boolean[] flag = new boolean[1];
        root.setProp.addListener((SetChangeListener<String>)c -> flag[0] = true);

        root.set1.clear();
        assertFalse(flag[0]);
        assertEquals(0, root.setProp.size());

        assertEquals(0, root.objectProp.get().size());
        root.set1.add("qux");
        assertTrue(root.objectProp.get().contains("qux"));
    }

    /*
     *  source:   Set
     *  expected: target.bind(new SetObservableValueWrapper(source))
     */
    @Test
    public void Unidirectional_Binding_To_Vanilla_Set_Indirect() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:bind indirect.set1}" objectProp="{fx:bind indirect.set1}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotNewExpr(root, SET_WRAPPER, "Constant");
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertEquals(3, root.setProp.size());

        boolean[] flag = new boolean[1];
        root.setProp.addListener((SetChangeListener<String>)c -> flag[0] = true);

        root.indirect.get().set1.clear();
        assertFalse(flag[0]);
        assertEquals(0, root.setProp.size());
        assertEquals(0, root.objectProp.get().size());

        root.indirect.setValue(new IndirectContext());
        assertTrue(flag[0]);
        assertEquals(3, root.setProp.size());
        assertEquals(3, root.objectProp.get().size());
    }

    /*
     *  source:   Set
     *  expected: error
     */
    @Test
    public void Unidirectional_ContentBinding_To_Vanilla_Set_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:bind set1; content=true}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   Set
     *  expected: error
     */
    @Test
    public void Unidirectional_ContentBinding_Fails_For_ObjectProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         objectProp="{fx:bind set1; content=true}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_TARGET, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   ObservableSet
     *  expected: target.bind(new ObjectConstant(source))
     */
    @Test
    public void Unidirectional_Binding_To_ObservableSet() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:bind set2}" objectProp="{fx:bind set2}"/>
        """);

        assertNewExpr(root, "ObjectConstant");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER, SET_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);

        assertEquals(3, root.setProp.size());
        boolean[] flag1 = new boolean[1];
        root.setProp.addListener((SetChangeListener<String>)c -> flag1[0] = true);
        root.set2.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.setProp.size());

        assertEquals(0, root.objectProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.objectProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.set2.add("qux");
        assertFalse(flag2[0]);
        assertTrue(root.objectProp.get().contains("qux"));
    }

    /*
     *  source:   ObservableSet
     *  expected: target.bindContent(source)
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableSet() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:bind set2; content=true}"/>
        """);

        assertNotNewExpr(root, "Constant", OBSERVABLE_VALUE_WRAPPER, SET_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);

        assertEquals(3, root.setProp.size());
        boolean[] flag1 = new boolean[1];
        root.setProp.addListener((SetChangeListener<String>)c -> flag1[0] = true);
        root.set2.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.setProp.size());
    }

    /*
     *  source:   ObservableValue<Set>
     *  expected: target.bind(new SetObservableValueWrapper(source))
     */
    @Test
    public void Unidirectional_Binding_To_ObservableValue_Of_Vanilla_Set() throws Exception {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:bind set3}" objectProp="{fx:bind set3}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotNewExpr(root, "Constant", SET_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);

        assertEquals(3, root.setProp.size());
        boolean[] flag = new boolean[1];
        root.setProp.addListener((SetChangeListener<String>)c -> flag[0] = true);
        root.set3.getValue().clear(); // Change the source set
        assertFalse(flag[0]); // SetChangeListener was not invoked
        assertEquals(0, root.setProp.size());

        flag[0] = false;
        root.set3.setValue(FXCollections.observableSet("baz")); // Replace the entire source set
        assertEquals(1, root.setProp.size());
        assertTrue(flag[0]); // SetChangeListener was invoked

        // create a new instance to reset all changes
        root = newInstance(root);

        flag[0] = false;
        root.objectProp.addListener((observable, oldValue, newValue) -> flag[0] = true);
        root.set3.getValue().clear(); // Change the source set
        assertFalse(flag[0]); // ChangeListener was not invoked
        assertEquals(0, root.objectProp.getValue().size());

        flag[0] = false;
        root.set3.setValue(FXCollections.observableSet("baz")); // Replace the entire source set
        assertEquals(1, root.objectProp.getValue().size());
        assertTrue(flag[0]); // ChangeListener was invoked
    }

    /*
     *  source:   ObservableValue<Set>
     *  expected: target.bindContent(new SetObservableValueWrapper(source))
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableValue_Of_Vanilla_Set() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:bind set3; content=true}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotNewExpr(root, "Constant", SET_WRAPPER);
        assertMethodCall(root, ADD_REFERENCE_METHOD);
    }

    /*
     *  source:   ObservableValue<ObservableSet>
     *  expected: target.bind(source)
     */
    @Test
    public void Unidirectional_Binding_To_ObservableValue_Of_ObservableList() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:bind set4}" objectProp="{fx:bind set4}"/>
        """);

        assertNotNewExpr(root, "Constant", OBSERVABLE_VALUE_WRAPPER, SET_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);

        assertEquals(3, root.setProp.size());
        boolean[] flag1 = new boolean[1];
        root.setProp.addListener((SetChangeListener<String>)c -> flag1[0] = true);
        root.set4.getValue().clear(); // Change the source set
        assertTrue(flag1[0]); // SetChangeListener was invoked
        assertEquals(0, root.setProp.size());

        assertEquals(0, root.objectProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.objectProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.set4.getValue().add("qux"); // Change the source set
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertTrue(root.objectProp.get().contains("qux"));

        flag1[0] = flag2[0] = false;
        root.set4.setValue(FXCollections.observableSet("baz")); // Replace the entire source set
        assertTrue(flag1[0]); // SetChangeListener was invoked
        assertTrue(flag2[0]); // ChangeListener was invoked
    }

    /*
     *  source:   ObservableValue<ObservableSet>
     *  expected: target.bindContent(new SetObservableValueWrapper(source))
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableValue_Of_ObservableSet() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:bind set4; content=true}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotNewExpr(root, "Constant", SET_WRAPPER);
        assertMethodCall(root, ADD_REFERENCE_METHOD);

        assertEquals(3, root.setProp.size());
        boolean[] flag1 = new boolean[1];
        root.setProp.addListener((SetChangeListener<String>)c -> flag1[0] = true);
        root.set4.getValue().clear(); // Change the source set
        assertTrue(flag1[0]); // SetChangeListener was invoked
        assertEquals(0, root.setProp.size());

        flag1[0] = false;
        root.set4.setValue(FXCollections.observableSet("baz")); // Replace the entire source set
        assertTrue(flag1[0]); // SetChangeListener was invoked
    }

    /*
     *  source:   Set
     *  expected: error
     */
    @Test
    public void Bidirectional_Binding_To_Vanilla_Set_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:sync set1}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());

        ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         objectProp="{fx:sync set1}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   Set
     *  expected: error
     */
    @Test
    public void Bidirectional_ContentBinding_To_Vanilla_Set_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:sync set1; content=true}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   ObservableSet
     *  expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ObservableSet_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:sync set2}" objectProp="{fx:sync set2}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   ObservableSet
     *  expected: target.bindContentBidirectional(source)
     */
    @Test
    public void Bidirectional_ContentBinding_To_ObservableSet() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:sync set2; content=true}"/>
        """);

        assertNotNewExpr(root, "Constant", OBSERVABLE_VALUE_WRAPPER, SET_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);

        assertEquals(3, root.setProp.size());
        boolean[] flag1 = new boolean[1];
        root.setProp.addListener((SetChangeListener<String>)c -> flag1[0] = true);
        root.set2.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.setProp.size());
    }

    /*
     *  source:   ObservableValue<Set>
     *  expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ObservableValue_Of_Vanilla_Set_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:sync set3}"/>
        """));

        assertEquals(ErrorCode.SOURCE_TYPE_MISMATCH, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   ObservableValue<Set>
     *  expected: error
     */
    @Test
    public void Bidirectional_ContentBinding_To_ObservableValue_Of_Vanilla_Set_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:sync set3; content=true}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     * source:   Property<ObservableSet>
     * expected: target.bindBidirectional(source)
     */
    @Test
    public void Bidirectional_Binding_To_Property_Of_ObservableSet() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:sync set4}"/>
        """);

        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER, SET_WRAPPER, "Constant");
        assertEquals(root.set4.get(), root.setProp);
    }

    /*
     * source:   Property<ObservableSet>
     * expected: target.bindContentBidirectional(new SetObservableValueWrapper(source))
     */
    @Test
    public void Bidirectional_ContentBinding_To_Property_Of_ObservableSet() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:sync set4; content=true}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotNewExpr(root, SET_WRAPPER, "Constant");
        assertMethodCall(root, ADD_REFERENCE_METHOD);

        gc(); // verify that the generated wrapper is not prematurely collected
        root.set4.set(FXCollections.observableSet("123"));
        assertEquals(Set.of("123"), root.setProp.get());
    }

    /*
     * source:   ObservableValue<ObservableSet>
     * expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ReadOnlyObservableValue_Of_ObservableSet_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:sync set4ReadOnly}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     * source:   ObservableValue<ObservableSet>
     * expected: target.bindContentBidirectional(new SetObservableValueWrapper(source))
     */
    @Test
    public void Bidirectional_ContentBinding_To_ReadOnlyObservableValue_Of_ObservableSet() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
                         setProp="{fx:sync set4ReadOnly; content=true}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotNewExpr(root, "Constant", SET_WRAPPER);
        assertMethodCall(root, ADD_REFERENCE_METHOD);
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

// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.generate.PushListenerGenerator;
import org.jfxcore.compiler.generate.collections.SetObservableValueWrapperGenerator;
import org.jfxcore.compiler.generate.collections.SetReseatableSourceWrapperGenerator;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"HttpUrlsUsage", "DuplicatedCode"})
@ExtendWith(TestExtension.class)
public class SetBindingTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class IndirectContext {
        public Set<String> set = new HashSet<>(List.of("foo", "bar", "baz"));
        public ObservableSet<String> obsSet = FXCollections.observableSet("foo", "bar", "baz");
        public ObjectProperty<Set<String>> propOfSet = new SimpleObjectProperty<>(new HashSet<>(List.of("foo", "bar", "baz")));
        public ObjectProperty<ObservableSet<String>> propOfObsSet = new SimpleObjectProperty<>(FXCollections.observableSet("foo", "bar", "baz"));
    }

    @SuppressWarnings("unused")
    public static class SetTestPane extends Pane {
        private final ObjectProperty<IndirectContext> indirect = new SimpleObjectProperty<>(new IndirectContext());
        public Property<IndirectContext> indirectProperty() { return indirect; }

        public Set<Double> incompatibleSet1 = new HashSet<>();

        public Set<String> set = new HashSet<>(List.of("foo", "bar", "baz"));
        public ObservableSet<String> obsSet = FXCollections.observableSet("foo", "bar", "baz");
        public ObjectProperty<Set<String>> propOfSet = new SimpleObjectProperty<>(new HashSet<>(List.of("foo", "bar", "baz")));
        public ObjectProperty<ObservableSet<String>> propOfObsSet = new SimpleObjectProperty<>(FXCollections.observableSet("foo", "bar", "baz"));
        public ObservableValue<ObservableSet<String>> propOfObsSetReadOnly() { return propOfObsSet; }

        public final SetProperty<String> readOnlySetProp = new SimpleSetProperty<>(this, "readOnlySetProp");
        public ReadOnlySetProperty<String> readOnlySetPropProperty() { return readOnlySetProp; }

        public final SetProperty<String> targetSetProp = new SimpleSetProperty<>(this, "targetSetProp", FXCollections.observableSet());
        public SetProperty<String> targetSetPropProperty() { return targetSetProp; }

        public final ObjectProperty<ObservableSet<String>> targetObjProp = new SimpleObjectProperty<>(this, "targetObjProp");
        public ObjectProperty<ObservableSet<String>> targetObjPropProperty() { return targetObjProp; }

        private final ObservableSet<String> targetObservableSet = FXCollections.observableSet();
        public Collection<String> getTargetCollection() { return targetObservableSet; }
        public Set<String> getTargetSet() { return targetObservableSet; }
        public ObservableSet<String> getTargetObservableSet() { return targetObservableSet; }
    }

    private static String PUSH_LISTENER;
    private static String OBSERVABLE_VALUE_WRAPPER;
    private static String RESEATABLE_SOURCE_WRAPPER;
    private static String ADD_REFERENCE_METHOD;
    private static String CLEAR_STALE_REFERENCES_METHOD;

    @BeforeAll
    public static void beforeAll() {
        PUSH_LISTENER = PushListenerGenerator.CLASS_NAME;
        OBSERVABLE_VALUE_WRAPPER = SetObservableValueWrapperGenerator.CLASS_NAME;
        RESEATABLE_SOURCE_WRAPPER = SetReseatableSourceWrapperGenerator.CLASS_NAME;
        ADD_REFERENCE_METHOD = NameHelper.getMangledMethodName("addReference");
        CLEAR_STALE_REFERENCES_METHOD = NameHelper.getMangledMethodName("clearStaleReferences");
    }

    /*
     *  source:   Set
     *  expected: error
     */
    @Test
    public void Once_Binding_To_Vanilla_Set() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="$set"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("set", ex);
    }

    /*
     *  source:   ObservableSet
     *  expected: target.setValue(source)
     */
    @Test
    public void Once_Binding_To_ObservableSet() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="$obsSet" targetObjProp="$obsSet"/>
        """);

        assertMethodCall(root, "setValue");
        assertNotMethodCall(root, "getValue");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetSetProp.size());

        boolean[] flag1 = new boolean[1];
        root.targetSetProp.addListener((SetChangeListener<String>)c -> flag1[0] = true);
        root.obsSet.clear(); // Change the source set
        assertTrue(flag1[0]); // SetChangeListener was invoked
        assertEquals(0, root.targetSetProp.size());

        assertEquals(0, root.targetObjProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.targetObjProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.obsSet.add("qux"); // Change the source set
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertTrue(root.targetObjProp.get().contains("qux"));
    }

    /*
     *  source:   ObservableValue<Set>
     *  expected: error
     */
    @Test
    public void Once_Binding_To_ObservableValue_Of_Vanilla_Set() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="$propOfSet"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfSet", ex);
    }

    /*
     *  source:   ObservableValue<ObservableSet>
     *  expected: target.setValue(source.getValue())
     */
    @Test
    public void Once_Binding_To_ObservableValue_Of_ObservableSet() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="$propOfObsSet" targetObjProp="$propOfObsSet"/>
        """);

        assertMethodCall(root, "setValue", "getValue");
        assertNotMethodCall(root, "addAll");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetSetProp.size());
        boolean[] flag1 = new boolean[1];
        root.targetSetProp.addListener((SetChangeListener<String>)c -> flag1[0] = true);
        root.propOfObsSet.getValue().clear(); // Change the source set
        assertTrue(flag1[0]); // SetChangeListener was invoked
        assertEquals(0, root.targetSetProp.size());

        assertEquals(0, root.targetObjProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.targetObjProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.propOfObsSet.getValue().add("qux"); // Change the source set
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertTrue(root.targetObjProp.get().contains("qux"));

        flag1[0] = flag2[0] = false;
        root.propOfObsSet.setValue(FXCollections.observableSet("baz")); // Replace the entire source set
        assertFalse(flag1[0]); // SetChangeListener was not invoked
        assertFalse(flag2[0]); // ChangeListener was not invoked
    }

    /*
     *  source:   Set
     *  expected: target.addAll(source)
     */
    @Test
    public void Once_ContentBinding_To_Vanilla_Set() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="$..set"/>
        """);

        assertMethodCall(root, "addAll");
        assertNotMethodCall(root, "setValue", "getValue");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetSetProp.size());
        root.set.clear(); // Change the source set
        assertEquals(3, root.targetSetProp.size()); // Target set is unchanged
    }

    /*
     *  source:   Set
     *  expected: target.addAll(source)
     */
    @Test
    public void Once_ContentBinding_To_Vanilla_Set_Indirect() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="$..indirect.set"/>
        """);

        assertMethodCall(root, "addAll", "getValue");
        assertNotMethodCall(root, "setValue");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetSetProp.size());
        root.indirect.get().set.clear(); // Change the source set
        assertEquals(3, root.targetSetProp.size()); // Target set is unchanged
    }

    /*
     *  source:   ObservableSet
     *  expected: target.addAll(source)
     */
    @Test
    public void Once_ContentBinding_To_Observable_Set() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="$..obsSet"/>
        """);

        assertMethodCall(root, "addAll");
        assertNotMethodCall(root, "setValue", "getValue");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetSetProp.size());
        root.set.clear(); // Change the source set
        assertEquals(3, root.targetSetProp.size()); // Target set is unchanged
    }

    /*
     *  source:   ObservableSet
     *  expected: target.addAll(source)
     */
    @Test
    public void Once_ContentBinding_To_Observable_Set_Indirect() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="$..indirect.obsSet"/>
        """);

        assertMethodCall(root, "addAll", "getValue");
        assertNotMethodCall(root, "setValue");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetSetProp.size());
        root.indirect.get().set.clear(); // Change the source set
        assertEquals(3, root.targetSetProp.size()); // Target set is unchanged
    }

    /*
     *  source:   ObservableValue<Set>
     *  expected: target.addAll(source.getValue())
     */
    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Vanilla_Set() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="$..propOfSet"/>
        """);

        assertMethodCall(root, "addAll", "getValue");
        assertNotMethodCall(root, "setValue");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetSetProp.size());
        root.indirect.get().set.clear(); // Change the source set
        assertEquals(3, root.targetSetProp.size()); // Target set is unchanged
    }

    /*
     *  source:   ObservableValue<Set>
     *  expected: target.addAll(source.getValue())
     */
    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Vanilla_Set_Indirect() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="$..indirect.propOfSet"/>
        """);

        assertMethodCall(root, "addAll", "getValue");
        assertNotMethodCall(root, "setValue");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetSetProp.size());
        root.indirect.get().set.clear(); // Change the source set
        assertEquals(3, root.targetSetProp.size()); // Target set is unchanged
    }

    /*
     *  source:   ObservableValue<ObservableSet>
     *  expected: target.addAll(source.getValue())
     */
    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Observable_Set() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="$..propOfObsSet"/>
        """);

        assertMethodCall(root, "addAll", "getValue");
        assertNotMethodCall(root, "setValue");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetSetProp.size());
        root.indirect.get().set.clear(); // Change the source set
        assertEquals(3, root.targetSetProp.size()); // Target set is unchanged
    }

    /*
     *  source:   ObservableValue<ObservableSet>
     *  expected: target.addAll(source.getValue())
     */
    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Observable_Set_Indirect() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="$..indirect.propOfObsSet"/>
        """);

        assertMethodCall(root, "addAll", "getValue");
        assertNotMethodCall(root, "setValue");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetSetProp.size());
        root.indirect.get().set.clear(); // Change the source set
        assertEquals(3, root.targetSetProp.size()); // Target set is unchanged
    }

    @Test
    public void Once_Binding_Fails_For_ReadOnlySetProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         readOnlySetProp="$set"/>
        """));

        assertEquals(ErrorCode.CANNOT_MODIFY_READONLY_PROPERTY, ex.getDiagnostic().getCode());
    }

    @Test
    public void Once_Binding_Fails_For_Incompatible_Set() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="$incompatibleSet1"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   Set
     *  expected: error
     */
    @Test
    public void Unidirectional_Binding_To_Vanilla_Set() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="${set}"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("set", ex);
    }

    /*
     *  source:   Set
     *  expected: target.bind(new SetObservableValueWrapper(source))
     */
    @Test
    public void Unidirectional_Binding_To_Vanilla_Set_Indirect() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="${indirect.set}"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("indirect.set", ex);
    }

    /*
     *  source:   Set
     *  expected: error
     */
    @Test
    public void Unidirectional_ContentBinding_To_Vanilla_Set_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="${..set}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertMessageContains("required javafx.collections.ObservableSet<java.lang.String>", ex);
        assertCodeHighlight("set", ex);
    }

    /*
     *  source:   Set
     *  expected: error
     */
    @Test
    public void Unidirectional_ContentBinding_Fails_For_ObjectProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetObjProp="${..set}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_TARGET, ex.getDiagnostic().getCode());
        assertCodeHighlight("targetObjProp=\"${..set}\"", ex);
    }

    /*
     *  source:   ObservableSet
     *  expected: error
     */
    @Test
    public void Unidirectional_Binding_To_ObservableSet() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="${obsSet}"/>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("obsSet", ex);
    }

    /*
     *  source:   ObservableSet
     *  expected: target.bindContent(source)
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableSet() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="${..obsSet}"/>
        """);

        assertMethodCall(root, "bindContent");
        assertNotMethodCall(root, "setValue", "getValue");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.targetSetProp.size());
        boolean[] flag1 = new boolean[1];
        root.targetSetProp.addListener((SetChangeListener<String>)c -> flag1[0] = true);
        root.obsSet.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.targetSetProp.size());
    }

    /*
     *  source:   ObservableSet
     *  expected: target.bindContent(new SetObservableValueWrapper(source))
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableSet_Indirect() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="${..indirect.obsSet}"/>
        """);

        assertMethodCall(root, "bindContent");
        assertNotMethodCall(root, "setValue", "getValue");
        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertMethodCall(root, ADD_REFERENCE_METHOD);
        assertMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.targetSetProp.size());
        boolean[] flag1 = new boolean[1];
        root.targetSetProp.addListener((SetChangeListener<String>)c -> flag1[0] = !flag1[0]);
        root.indirect.get().obsSet.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.targetSetProp.size());
        root.indirect.set(new IndirectContext());
        assertFalse(flag1[0]);
        assertEquals(3, root.targetSetProp.size());
    }

    /*
     *  source:   ObservableValue<Set>
     *  expected: error
     */
    @Test
    public void Unidirectional_Binding_To_ObservableValue_Of_Vanilla_Set() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="${propOfSet}"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfSet", ex);
    }

    /*
     *  source:   ObservableValue<Set>
     *  expected: error
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableValue_Of_Vanilla_Set() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="${..propOfSet}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertMessageContains("required javafx.collections.ObservableSet<java.lang.String>", ex);
        assertCodeHighlight("propOfSet", ex);
    }

    /*
     *  source:   ObservableValue<ObservableSet>
     *  expected: target.bind(source)
     */
    @Test
    public void Unidirectional_Binding_To_ObservableValue_Of_ObservableSet() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="${propOfObsSet}" targetObjProp="${propOfObsSet}"/>
        """);

        assertMethodCall(root, "bind");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.targetSetProp.size());
        boolean[] flag1 = new boolean[1];
        root.targetSetProp.addListener((SetChangeListener<String>)c -> flag1[0] = true);
        root.propOfObsSet.getValue().clear(); // Change the source set
        assertTrue(flag1[0]); // SetChangeListener was invoked
        assertEquals(0, root.targetSetProp.size());

        assertEquals(0, root.targetObjProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.targetObjProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.propOfObsSet.getValue().add("qux"); // Change the source set
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertTrue(root.targetObjProp.get().contains("qux"));

        flag1[0] = flag2[0] = false;
        root.propOfObsSet.setValue(FXCollections.observableSet("baz")); // Replace the entire source set
        assertTrue(flag1[0]); // SetChangeListener was invoked
        assertTrue(flag2[0]); // ChangeListener was invoked
    }

    /*
     *  source:   ObservableValue<ObservableSet>
     *  expected: error
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableValue_Of_ObservableSet() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="${..propOfObsSet}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfObsSet", ex);
    }

    /*
     *  source:   Set
     *  expected: error
     */
    @Test
    public void Reverse_ContentBinding_Fails_For_ObjectProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetObjProp=">{..set}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_TARGET, ex.getDiagnostic().getCode());
        assertCodeHighlight("targetObjProp=\">{..set}\"", ex);
    }

    /*
     *  source:   Set
     *  expected: source.bindContent(target)
     */
    @Test
    public void Reverse_ContentBinding_To_Vanilla_Set() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp=">{..set}"/>
        """);

        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(0, root.targetSetProp.size());
        root.targetSetProp.addAll(Set.of("foo", "bar", "baz"));
        assertEquals(Set.of("foo", "bar", "baz"), root.set);
        root.targetSetProp.clear();
        assertEquals(Set.of(), root.set);
    }

    /*
     *  source:   Set
     *  expected: bindContent(new SetReseatableSourceWrapper(target, source), target)
     */
    @Test
    public void Reverse_ContentBinding_To_Vanilla_Set_Indirect() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp=">{..indirect.set}"/>
        """);

        assertNewExpr(root, RESEATABLE_SOURCE_WRAPPER);
        assertMethodCall(root, ADD_REFERENCE_METHOD);
        assertMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        Set<String> mySet = Set.of("myA", "myB", "myC");
        assertEquals(0, root.targetSetProp.size());
        root.targetSetProp.addAll(mySet);
        assertEquals(mySet, root.indirect.get().set);
        IndirectContext oldContext = root.indirect.get();
        root.indirect.set(new IndirectContext());
        assertEquals(mySet, root.indirect.get().set);
        assertEquals(mySet, root.targetSetProp);
        root.indirect.set(null);
        assertEquals(mySet, root.targetSetProp);
        root.indirect.set(new IndirectContext());
        assertEquals(mySet, root.indirect.get().set);
        assertEquals(mySet, root.targetSetProp);
        root.targetSetProp.clear();
        assertEquals(mySet, oldContext.set);
        assertEquals(Set.of(), root.indirect.get().set);
    }

    /*
     *  source:   ObservableSet
     *  expected: source.bindContent(target)
     */
    @Test
    public void Reverse_ContentBinding_To_ObservableSet() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp=">{..obsSet}"/>
        """);

        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(0, root.targetSetProp.size());
        root.targetSetProp.addAll(Set.of("foo", "bar", "baz"));
        assertEquals(Set.of("foo", "bar", "baz"), root.obsSet);
        root.targetSetProp.clear();
        assertEquals(Set.of(), root.obsSet);
    }

    /*
     *  source:   ObservableSet
     *  expected: bindContent(new SetReseatableSourceWrapper(target, source), target)
     */
    @Test
    public void Reverse_ContentBinding_To_ObservableSet_Indirect() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp=">{..indirect.obsSet}"/>
        """);

        assertNewExpr(root, RESEATABLE_SOURCE_WRAPPER);
        assertMethodCall(root, ADD_REFERENCE_METHOD);
        assertMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        Set<String> mySet = Set.of("myA", "myB", "myC");
        assertEquals(0, root.targetSetProp.size());
        root.targetSetProp.addAll(mySet);
        assertEquals(mySet, root.indirect.get().obsSet);
        IndirectContext oldContext = root.indirect.get();
        root.indirect.set(new IndirectContext());
        assertEquals(mySet, root.indirect.get().obsSet);
        assertEquals(mySet, root.targetSetProp);
        root.indirect.set(null);
        assertEquals(mySet, root.targetSetProp);
        root.indirect.set(new IndirectContext());
        assertEquals(mySet, root.indirect.get().obsSet);
        assertEquals(mySet, root.targetSetProp);
        root.targetSetProp.clear();
        assertEquals(mySet, oldContext.obsSet);
        assertEquals(Set.of(), root.indirect.get().obsSet);
    }

    /*
     *  source:   ObservableValue<Set>
     *  expected: target.addListener(new PushListener(target, source))
     */
    @Test
    public void Reverse_Binding_To_ObservableValue_Of_Vanilla_Set() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp=">{propOfSet}"/>
        """);

        assertNewExpr(root, PUSH_LISTENER);
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertSame(root.targetSetProp.get(), root.propOfSet.get());
        root.targetSetProp.setValue(FXCollections.observableSet(Set.of("foo", "bar", "baz")));
        assertSame(root.targetSetProp.get(), root.propOfSet.get());
    }

    /*
     *  source:   ObservableValue<Set>
     *  expected: error
     */
    @Test
    public void Reverse_ContentBinding_To_ObservableValue_Of_Vanilla_Set() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp=">{..propOfSet}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertMessageContains("required java.util.Set<java.lang.String>", ex);
        assertCodeHighlight("propOfSet", ex);
    }

    /*
     *  source:   ObservableValue<ObservableSet>
     *  expected: error
     */
    @Test
    public void Reverse_ContentBinding_To_ObservableValue_Of_ObservableSet() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp=">{..propOfObsSet}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertMessageContains("required java.util.Set<java.lang.String>", ex);
        assertCodeHighlight("propOfObsSet", ex);
    }

    /*
     *  source:   Set
     *  expected: error
     */
    @Test
    public void Bidirectional_Binding_To_Vanilla_Set_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="#{set}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("set", ex);

        ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetObjProp="#{set}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("set", ex);
    }

    /*
     *  source:   Set
     *  expected: error
     */
    @Test
    public void Bidirectional_ContentBinding_To_Vanilla_Set_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="#{..set}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("set", ex);
    }

    /*
     *  source:   ObservableSet
     *  expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ObservableSet_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="#{obsSet}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("obsSet", ex);

        ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetObjProp="#{obsSet}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("obsSet", ex);
    }

    /*
     *  source:   ObservableSet
     *  expected: target.bindContentBidirectional(source)
     */
    @Test
    public void Bidirectional_ContentBinding_To_ObservableSet() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="#{..obsSet}"/>
        """);

        assertMethodCall(root, "bindContentBidirectional");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.targetSetProp.size());
        boolean[] flag1 = new boolean[1];
        root.targetSetProp.addListener((SetChangeListener<String>)c -> flag1[0] = true);
        root.obsSet.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.targetSetProp.size());
    }

    /*
     *  source:   ObservableValue<Set>
     *  expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ObservableValue_Of_Vanilla_Set_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="#{propOfSet}"/>
        """));

        assertEquals(ErrorCode.SOURCE_TYPE_MISMATCH, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfSet", ex);
    }

    /*
     *  source:   ObservableValue<Set>
     *  expected: error
     */
    @Test
    public void Bidirectional_ContentBinding_To_ObservableValue_Of_Vanilla_Set_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="#{..propOfSet}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfSet", ex);
    }

    /*
     * source:   Property<ObservableSet>
     * expected: target.bindBidirectional(source)
     */
    @Test
    public void Bidirectional_Binding_To_Property_Of_ObservableSet() {
        SetTestPane root = compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="#{propOfObsSet}"/>
        """);

        assertMethodCall(root, "bindBidirectional");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(root.propOfObsSet.get(), root.targetSetProp);
    }

    /*
     * source:   Property<ObservableSet>
     * expected: error
     */
    @Test
    public void Bidirectional_ContentBinding_To_Property_Of_ObservableSet() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="#{..propOfObsSet}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfObsSet", ex);
    }

    /*
     * source:   ObservableValue<ObservableSet>
     * expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ReadOnlyObservableValue_Of_ObservableSet_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="#{propOfObsSetReadOnly}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfObsSetReadOnly", ex);
    }

    /*
     * source:   ObservableValue<ObservableSet>
     * expected: target.bindContentBidirectional(new SetObservableValueWrapper(source))
     */
    @Test
    public void Bidirectional_ContentBinding_To_ReadOnlyObservableValue_Of_ObservableSet() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <SetTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetSetProp="#{..propOfObsSetReadOnly}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfObsSetReadOnly", ex);
    }
}

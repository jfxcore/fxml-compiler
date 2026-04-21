// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import javafx.beans.property.MapProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyMapProperty;
import javafx.beans.property.SimpleMapProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.generate.PushListenerGenerator;
import org.jfxcore.compiler.generate.collections.MapObservableValueWrapperGenerator;
import org.jfxcore.compiler.generate.collections.MapReseatableSourceWrapperGenerator;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.HashMap;
import java.util.Map;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"HttpUrlsUsage", "DuplicatedCode"})
@ExtendWith(TestExtension.class)
public class MapBindingTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class IndirectContext {
        public Map<Integer, String> map = new HashMap<>(Map.of(0, "foo", 1, "bar", 2, "baz"));
        public ObservableMap<Integer, String> obsMap = FXCollections.observableMap(new HashMap<>(Map.of(0, "foo", 1, "bar", 2, "baz")));
        public ObjectProperty<Map<Integer, String>> propOfMap = new SimpleObjectProperty<>(new HashMap<>(Map.of(0, "foo", 1, "bar", 2, "baz")));
        public ObjectProperty<ObservableMap<Integer, String>> propOfObsMap = new SimpleObjectProperty<>(FXCollections.observableMap(new HashMap<>(Map.of(0, "foo", 1, "bar", 2, "baz"))));
    }

    @SuppressWarnings("unused")
    public static class MapTestPane extends Pane {
        private final ObjectProperty<IndirectContext> indirect = new SimpleObjectProperty<>(new IndirectContext());
        public Property<IndirectContext> indirectProperty() { return indirect; }

        public Map<Integer, Double> incompatibleMap1 = new HashMap<>();

        public Map<Integer, String> map = new HashMap<>(Map.of(0, "foo", 1, "bar", 2, "baz"));
        public ObservableMap<Integer, String> obsMap = FXCollections.observableMap(new HashMap<>(Map.of(0, "foo", 1, "bar", 2, "baz")));
        public ObjectProperty<Map<Integer, String>> propOfMap = new SimpleObjectProperty<>(new HashMap<>(Map.of(0, "foo", 1, "bar", 2, "baz")));
        public ObjectProperty<ObservableMap<Integer, String>> propOfObsMap = new SimpleObjectProperty<>(FXCollections.observableMap(new HashMap<>(Map.of(0, "foo", 2, "bar", 3, "baz"))));
        public ObservableValue<ObservableMap<Integer, String>> propOfObsMapReadOnly() { return propOfObsMap; }

        public final MapProperty<Integer, String> readOnlyMapProp = new SimpleMapProperty<>(this, "readOnlyMapProp");
        public ReadOnlyMapProperty<Integer, String> readOnlyMapPropProperty() { return readOnlyMapProp; }

        public final MapProperty<Integer, String> targetMapProp = new SimpleMapProperty<>(this, "targetMapProp", FXCollections.observableMap(new HashMap<>()));
        public MapProperty<Integer, String> targetMapPropProperty() { return targetMapProp; }

        public final ObjectProperty<ObservableMap<Integer, String>> targetObjProp = new SimpleObjectProperty<>(this, "targetObjProp");
        public ObjectProperty<ObservableMap<Integer, String>> targetObjPropProperty() { return targetObjProp; }

        private final ObservableMap<Integer, String> targetObservableMap = FXCollections.observableMap(new HashMap<>());
        public Map<Integer, String> getTargetMap() { return targetObservableMap; }
        public ObservableMap<Integer, String> getTargetObservableMap() { return targetObservableMap; }
    }

    @SuppressWarnings("unused")
    public static class NullObservableMapTestPane extends MapTestPane {
        public NullObservableMapTestPane() {
            obsMap = null;
        }
    }

    private static String PUSH_LISTENER;
    private static String OBSERVABLE_VALUE_WRAPPER;
    private static String RESEATABLE_SOURCE_WRAPPER;
    private static String ADD_REFERENCE_METHOD;
    private static String CLEAR_STALE_REFERENCES_METHOD;

    @BeforeAll
    public static void beforeAll() {
        PUSH_LISTENER = PushListenerGenerator.CLASS_NAME;
        OBSERVABLE_VALUE_WRAPPER = MapObservableValueWrapperGenerator.CLASS_NAME;
        RESEATABLE_SOURCE_WRAPPER = MapReseatableSourceWrapperGenerator.CLASS_NAME;
        ADD_REFERENCE_METHOD = NameHelper.getMangledMethodName("addReference");
        CLEAR_STALE_REFERENCES_METHOD = NameHelper.getMangledMethodName("clearStaleReferences");
    }

    /*
     *  source:   Map
     *  expected: error
     */
    @Test
    public void Once_Binding_To_Vanilla_Map() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="$map"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("map", ex);
    }

    /*
     *  source:   ObservableMap
     *  expected: target.setValue(source)
     */
    @Test
    public void Once_Binding_To_ObservableMap() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="$obsMap" targetObjProp="$obsMap"/>
        """);

        assertMethodCall(root, "setValue");
        assertNotMethodCall(root, "putAll", "bind");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetMapProp.size());

        boolean[] flag1 = new boolean[1];
        root.targetMapProp.addListener((MapChangeListener<Integer, String>)c -> flag1[0] = true);
        root.obsMap.clear(); // Change the source map
        assertTrue(flag1[0]); // MapChangeListener was invoked
        assertEquals(0, root.targetMapProp.size());

        assertEquals(0, root.targetObjProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.targetObjProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.obsMap.put(0, "qux"); // Change the source map
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertTrue(root.targetObjProp.get().containsValue("qux"));
    }

    /*
     *  source:   ObservableValue<Map>
     *  expected: error
     */
    @Test
    public void Once_Binding_To_ObservableValue_Of_Vanilla_Map() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="$propOfMap"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfMap", ex);
    }

    /*
     *  source:   ObservableValue<ObservableMap>
     *  expected: target.setValue(source.getValue())
     */
    @Test
    public void Once_Binding_To_ObservableValue_Of_ObservableMap() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="$propOfObsMap" targetObjProp="$propOfObsMap"/>
        """);

        assertMethodCall(root, "setValue", "getValue");
        assertNotMethodCall(root, "putAll", "bind");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetMapProp.size());
        boolean[] flag1 = new boolean[1];
        root.targetMapProp.addListener((MapChangeListener<Integer, String>)c -> flag1[0] = true);
        root.propOfObsMap.getValue().clear(); // Change the source map
        assertTrue(flag1[0]); // MapChangeListener was invoked
        assertEquals(0, root.targetMapProp.size());

        assertEquals(0, root.targetObjProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.targetObjProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.propOfObsMap.getValue().put(0, "qux"); // Change the source map
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertTrue(root.targetObjProp.get().containsValue("qux"));

        flag1[0] = flag2[0] = false;
        root.propOfObsMap.setValue(FXCollections.observableMap(Map.of(0, "baz"))); // Replace the entire source map
        assertFalse(flag1[0]); // MapChangeListener was not invoked
        assertFalse(flag2[0]); // ChangeListener was not invoked
    }

    /*
     *  source:   Map
     *  expected: target.putAll(source)
     */
    @Test
    public void Once_ContentBinding_To_Vanilla_Map() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="$..map"/>
        """);

        assertMethodCall(root, "putAll");
        assertNotMethodCall(root, "bindContent");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetMapProp.size());
        root.map.clear(); // Change the source map
        assertEquals(3, root.targetMapProp.size()); // Target map is unchanged
    }

    /*
     *  source:   Map
     *  expected: target.putAll(source)
     */
    @Test
    public void Once_ContentBinding_To_Vanilla_Map_Indirect() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="$..indirect.map"/>
        """);

        assertMethodCall(root, "putAll");
        assertNotMethodCall(root, "bindContent");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetMapProp.size());
        root.indirect.get().map.clear(); // Change the source map
        assertEquals(3, root.targetMapProp.size()); // Target map is unchanged
    }

    /*
     *  source:   ObservableMap
     *  expected: target.putAll(source)
     */
    @Test
    public void Once_ContentBinding_To_Observable_Map() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="$..obsMap"/>
        """);

        assertMethodCall(root, "putAll");
        assertNotMethodCall(root, "bindContent");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetMapProp.size());
        root.map.clear(); // Change the source map
        assertEquals(3, root.targetMapProp.size()); // Target map is unchanged
    }

    /*
     *  source:   ObservableMap
     *  expected: target.putAll(source)
     */
    @Test
    public void Once_ContentBinding_To_Observable_Map_Indirect() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="$..indirect.obsMap"/>
        """);

        assertMethodCall(root, "putAll");
        assertNotMethodCall(root, "bindContent");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetMapProp.size());
        root.indirect.get().map.clear(); // Change the source map
        assertEquals(3, root.targetMapProp.size()); // Target map is unchanged
    }

    /*
     *  source:   ObservableValue<Map>
     *  expected: target.putAll(source.getValue())
     */
    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Vanilla_Map() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="$..propOfMap"/>
        """);

        assertMethodCall(root, "putAll", "getValue");
        assertNotMethodCall(root, "bindContent");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetMapProp.size());
        root.indirect.get().map.clear(); // Change the source map
        assertEquals(3, root.targetMapProp.size()); // Target map is unchanged
    }

    /*
     *  source:   ObservableValue<Map>
     *  expected: target.putAll(source.getValue())
     */
    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Vanilla_Map_Indirect() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="$..indirect.propOfMap"/>
        """);

        assertMethodCall(root, "putAll", "getValue");
        assertNotMethodCall(root, "bindContent");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetMapProp.size());
        root.indirect.get().map.clear(); // Change the source map
        assertEquals(3, root.targetMapProp.size()); // Target map is unchanged
    }

    /*
     *  source:   ObservableValue<ObservableMap>
     *  expected: target.putAll(source.getValue())
     */
    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Observable_Map() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="$..propOfObsMap"/>
        """);

        assertMethodCall(root, "putAll", "getValue");
        assertNotMethodCall(root, "bindContent");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetMapProp.size());
        root.indirect.get().map.clear(); // Change the source map
        assertEquals(3, root.targetMapProp.size()); // Target map is unchanged
    }

    /*
     *  source:   ObservableValue<ObservableMap>
     *  expected: target.putAll(source.getValue())
     */
    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Observable_Map_Indirect() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="$..indirect.propOfObsMap"/>
        """);

        assertMethodCall(root, "putAll", "getValue");
        assertNotMethodCall(root, "bindContent");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.targetMapProp.size());
        root.indirect.get().map.clear(); // Change the source map
        assertEquals(3, root.targetMapProp.size()); // Target map is unchanged
    }

    @Test
    public void Once_Binding_Fails_For_ReadOnlyMapProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         readOnlyMapProp="$map"/>
        """));

        assertEquals(ErrorCode.CANNOT_MODIFY_READONLY_PROPERTY, ex.getDiagnostic().getCode());
        assertCodeHighlight("readOnlyMapProp=\"$map\"", ex);
    }

    @Test
    public void Once_Binding_Fails_For_Incompatible_Map() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="$incompatibleMap1"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("incompatibleMap1", ex);
    }

    /*
     *  source:   Map
     *  expected: error
     */
    @Test
    public void Unidirectional_Binding_To_Vanilla_Map() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="${map}"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("map", ex);
    }

    /*
     *  source:   Map
     *  expected: error
     */
    @Test
    public void Unidirectional_Binding_To_Vanilla_Map_Indirect() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="${indirect.map}"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("indirect.map", ex);
    }

    /*
     *  source:   Map
     *  expected: error
     */
    @Test
    public void Unidirectional_ContentBinding_To_Vanilla_Map_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="${..map}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertMessageContains("required javafx.collections.ObservableMap<java.lang.Integer, java.lang.String>", ex);
        assertCodeHighlight("map", ex);
    }

    /*
     *  source:   Map
     *  expected: error
     */
    @Test
    public void Unidirectional_ContentBinding_Fails_For_ObjectProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetObjProp="${..map}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_TARGET, ex.getDiagnostic().getCode());
        assertCodeHighlight("targetObjProp=\"${..map}\"", ex);
    }

    /*
     *  source:   ObservableMap
     *  expected: error
     */
    @Test
    public void Unidirectional_Binding_To_ObservableMap() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="${obsMap}"/>
        """));

        assertEquals(ErrorCode.INVALID_UNIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("obsMap", ex);
    }

    @Test
    public void Unidirectional_Binding_To_ObservableMap_Size_Reevaluates_On_Content_Change() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         prefWidth="${obsMap.size}"/>
        """);

        assertMethodCall(root, "requireNonNull");
        assertEquals(3, root.getPrefWidth(), 0.001);

        root.obsMap.clear();
        assertEquals(0, root.getPrefWidth(), 0.001);

        root.obsMap.put(0, "foo");
        root.obsMap.put(1, "bar");
        assertEquals(2, root.getPrefWidth(), 0.001);
    }

    @Test
    public void Unidirectional_Binding_To_Null_ObservableMap_Size_Throws_NPE() {
        NullPointerException ex = assertThrows(NullPointerException.class, () -> compileAndRun("""
            <NullObservableMapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                                       prefWidth="${obsMap.size}"/>
        """));

        assertEquals("obsMap", ex.getMessage());
    }

    /*
     *  source:   ObservableMap
     *  expected: target.bindContent(source)
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableMap() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="${..obsMap}"/>
        """);

        assertMethodCall(root, "bindContent");
        assertNotMethodCall(root, "putAll", "getValue");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.targetMapProp.size());
        boolean[] flag1 = new boolean[1];
        root.targetMapProp.addListener((MapChangeListener<Integer, String>)c -> flag1[0] = true);
        root.obsMap.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.targetMapProp.size());
    }

    /*
     *  source:   ObservableMap
     *  expected: target.bindContent(new MapObservableValueWrapper(source))
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableMap_Indirect() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="${..indirect.obsMap}"/>
        """);

        assertMethodCall(root, "bindContent");
        assertNotMethodCall(root, "putAll", "getValue");
        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertMethodCall(root, ADD_REFERENCE_METHOD);
        assertMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.targetMapProp.size());
        boolean[] flag1 = new boolean[1];
        root.targetMapProp.addListener((MapChangeListener<Integer, String>)c -> flag1[0] = !flag1[0]);
        root.indirect.get().obsMap.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.targetMapProp.size());
        root.indirect.set(new IndirectContext());
        assertFalse(flag1[0]);
        assertEquals(3, root.targetMapProp.size());
    }

    /*
     *  source:   ObservableValue<Map>
     *  expected: error
     */
    @Test
    public void Unidirectional_Binding_To_ObservableValue_Of_Vanilla_Map() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="${propOfMap}"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfMap", ex);
    }

    /*
     *  source:   ObservableValue<Map>
     *  expected: error
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableValue_Of_Vanilla_Map() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="${..propOfMap}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertMessageContains("required javafx.collections.ObservableMap<java.lang.Integer, java.lang.String>", ex);
        assertCodeHighlight("propOfMap", ex);
    }

    /*
     *  source:   ObservableValue<ObservableMap>
     *  expected: target.bind(source)
     */
    @Test
    public void Unidirectional_Binding_To_ObservableValue_Of_ObservableMap() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="${propOfObsMap}" targetObjProp="${propOfObsMap}"/>
        """);

        assertMethodCall(root, "bind");
        assertNotMethodCall(root, "getValue");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.targetMapProp.size());
        boolean[] flag1 = new boolean[1];
        root.targetMapProp.addListener((MapChangeListener<Integer, String>)c -> flag1[0] = true);
        root.propOfObsMap.getValue().clear(); // Change the source map
        assertTrue(flag1[0]); // MapChangeListener was invoked
        assertEquals(0, root.targetMapProp.size());

        assertEquals(0, root.targetObjProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.targetObjProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.propOfObsMap.getValue().put(0, "qux"); // Change the source map
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertTrue(root.targetObjProp.get().containsValue("qux"));

        flag1[0] = flag2[0] = false;
        root.propOfObsMap.setValue(FXCollections.observableMap(Map.of(0, "baz"))); // Replace the entire source map
        assertTrue(flag1[0]); // MapChangeListener was invoked
        assertTrue(flag2[0]); // ChangeListener was invoked
    }

    /*
     *  source:   ObservableValue<ObservableMap>
     *  expected: error
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableValue_Of_ObservableMap() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="${..propOfObsMap}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfObsMap", ex);
    }

    /*
     *  source:   Map
     *  expected: error
     */
    @Test
    public void Reverse_ContentBinding_Fails_For_ObjectProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetObjProp=">{..map}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_TARGET, ex.getDiagnostic().getCode());
        assertCodeHighlight("targetObjProp=\">{..map}\"", ex);
    }

    /*
     *  source:   Map
     *  expected: source.bindContent(target)
     */
    @Test
    public void Reverse_ContentBinding_To_Vanilla_Map() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp=">{..map}"/>
        """);

        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        Map<Integer, String> myMap = Map.of(5, "myA", 6, "myB", 7, "myC");
        assertEquals(0, root.targetMapProp.size());
        root.targetMapProp.putAll(myMap);
        assertEquals(myMap, root.map);
        root.targetMapProp.clear();
        assertEquals(Map.of(), root.map);
    }

    /*
     *  source:   Map
     *  expected: bindContent(new MapReseatableSourceWrapper(target, source), target)
     */
    @Test
    public void Reverse_ContentBinding_To_Vanilla_Map_Indirect() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp=">{..indirect.map}"/>
        """);

        assertNewExpr(root, RESEATABLE_SOURCE_WRAPPER);
        assertMethodCall(root, ADD_REFERENCE_METHOD);
        assertMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        Map<Integer, String> myMap = Map.of(5, "myA", 6, "myB", 7, "myC");
        assertEquals(0, root.targetMapProp.size());
        root.targetMapProp.putAll(myMap);
        assertEquals(myMap, root.indirect.get().map);
        IndirectContext oldContext = root.indirect.get();
        root.indirect.set(new IndirectContext());
        assertEquals(myMap, root.indirect.get().map);
        assertEquals(myMap, root.targetMapProp);
        root.indirect.set(null);
        assertEquals(myMap, root.targetMapProp);
        root.indirect.set(new IndirectContext());
        assertEquals(myMap, root.indirect.get().map);
        assertEquals(myMap, root.targetMapProp);
        root.targetMapProp.clear();
        assertEquals(myMap, oldContext.map);
        assertEquals(Map.of(), root.indirect.get().map);
    }

    /*
     *  source:   ObservableMap
     *  expected: source.bindContent(target)
     */
    @Test
    public void Reverse_ContentBinding_To_ObservableMap() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp=">{..obsMap}"/>
        """);

        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        Map<Integer, String> myMap = Map.of(5, "myA", 6, "myB", 7, "myC");
        assertEquals(0, root.targetMapProp.size());
        root.targetMapProp.putAll(myMap);
        assertEquals(myMap, root.obsMap);
        root.targetMapProp.clear();
        assertEquals(Map.of(), root.obsMap);
    }

    /*
     *  source:   ObservableMap
     *  expected: bindContent(new MapReseatableSourceWrapper(target, source), target)
     */
    @Test
    public void Reverse_ContentBinding_To_ObservableMap_Indirect() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp=">{..indirect.obsMap}"/>
        """);

        assertNewExpr(root, RESEATABLE_SOURCE_WRAPPER);
        assertMethodCall(root, ADD_REFERENCE_METHOD);
        assertMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        Map<Integer, String> myMap = Map.of(5, "myA", 6, "myB", 7, "myC");
        assertEquals(0, root.targetMapProp.size());
        root.targetMapProp.putAll(myMap);
        assertEquals(myMap, root.indirect.get().obsMap);
        IndirectContext oldContext = root.indirect.get();
        root.indirect.set(new IndirectContext());
        assertEquals(myMap, root.indirect.get().obsMap);
        assertEquals(myMap, root.targetMapProp);
        root.indirect.set(null);
        assertEquals(myMap, root.targetMapProp);
        root.indirect.set(new IndirectContext());
        assertEquals(myMap, root.indirect.get().obsMap);
        assertEquals(myMap, root.targetMapProp);
        root.targetMapProp.clear();
        assertEquals(myMap, oldContext.obsMap);
        assertEquals(Map.of(), root.indirect.get().obsMap);
    }

    /*
     *  source:   ObservableValue<Map>
     *  expected: target.addListener(new PushListener(target, source))
     */
    @Test
    public void Reverse_Binding_To_ObservableValue_Of_Vanilla_Map() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp=">{propOfMap}"/>
        """);

        assertNewExpr(root, PUSH_LISTENER);
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertSame(root.targetMapProp.get(), root.propOfMap.get());
        root.targetMapProp.setValue(FXCollections.observableMap(Map.of(1, "foo", 2, "bar", 3, "baz")));
        assertSame(root.targetMapProp.get(), root.propOfMap.get());
    }

    /*
     *  source:   ObservableValue<Map>
     *  expected: error
     */
    @Test
    public void Reverse_ContentBinding_To_ObservableValue_Of_Vanilla_Map() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp=">{..propOfMap}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertMessageContains("required java.util.Map<java.lang.Integer, java.lang.String>", ex);
        assertCodeHighlight("propOfMap", ex);
    }

    /*
     *  source:   ObservableValue<ObservableMap>
     *  expected: error
     */
    @Test
    public void Reverse_ContentBinding_To_ObservableValue_Of_ObservableMap() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp=">{..propOfObsMap}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertMessageContains("required java.util.Map<java.lang.Integer, java.lang.String>", ex);
        assertCodeHighlight("propOfObsMap", ex);
    }

    /*
     *  source:   Map
     *  expected: error
     */
    @Test
    public void Bidirectional_Binding_To_Vanilla_Map_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="#{map}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("map", ex);

        ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetObjProp="#{map}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("map", ex);
    }

    /*
     *  source:   Map
     *  expected: error
     */
    @Test
    public void Bidirectional_ContentBinding_To_Vanilla_Map_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="#{..map}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("map", ex);
    }

    /*
     *  source:   ObservableMap
     *  expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ObservableMap_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="#{obsMap}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("obsMap", ex);
    }

    /*
     *  source:   ObservableMap
     *  expected: target.bindContentBidirectional(source)
     */
    @Test
    public void Bidirectional_ContentBinding_To_ObservableMap() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="#{..obsMap}"/>
        """);

        assertMethodCall(root, "bindContentBidirectional");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.targetMapProp.size());
        boolean[] flag1 = new boolean[1];
        root.targetMapProp.addListener((MapChangeListener<Integer, String>)c -> flag1[0] = true);
        root.obsMap.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.targetMapProp.size());
    }

    /*
     *  source:   ObservableMap
     *  expected: target.bindContentBidirectional(new MapObservableValueWrapper(source))
     */
    @Test
    public void Bidirectional_ContentBinding_To_ObservableMap_Indirect() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="#{..indirect.obsMap}"/>
        """);

        assertMethodCall(root, "bindContentBidirectional");
        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertMethodCall(root, ADD_REFERENCE_METHOD);
        assertMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.targetMapProp.size());
        boolean[] flag1 = new boolean[1];
        root.targetMapProp.addListener((MapChangeListener<Integer, String>)c -> flag1[0] = !flag1[0]);
        root.indirect.get().obsMap.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.targetMapProp.size());
        root.indirect.set(new IndirectContext());
        assertFalse(flag1[0]);
        assertEquals(3, root.targetMapProp.size());
        root.targetMapProp.clear();
        assertEquals(0, root.indirect.get().obsMap.size());
    }

    /*
     *  source:   ObservableValue<Map>
     *  expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ObservableValue_Of_Vanilla_Map_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="#{propOfMap}"/>
        """));

        assertEquals(ErrorCode.SOURCE_TYPE_MISMATCH, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfMap", ex);
    }

    /*
     *  source:   ObservableValue<Map>
     *  expected: error
     */
    @Test
    public void Bidirectional_ContentBinding_To_ObservableValue_Of_Vanilla_Map_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="#{..propOfMap}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfMap", ex);
    }

    /*
     * source:   Property<ObservableMap>
     * expected: target.bindBidirectional(source)
     */
    @Test
    public void Bidirectional_Binding_To_Property_Of_ObservableMap() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="#{propOfObsMap}"/>
        """);

        assertMethodCall(root, "bindBidirectional");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(root.propOfObsMap.get(), root.targetMapProp);
    }

    /*
     * source:   Property<ObservableMap>
     * expected: error
     */
    @Test
    public void Bidirectional_ContentBinding_To_Property_Of_ObservableMap() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="#{..propOfObsMap}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfObsMap", ex);
    }

    /*
     * source:   ObservableValue<ObservableMap>
     * expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ReadOnlyObservableValue_Of_ObservableMap_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="#{propOfObsMapReadOnly}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfObsMapReadOnly", ex);
    }

    /*
     * source:   ObservableValue<ObservableMap>
     * expected: error
     */
    @Test
    public void Bidirectional_ContentBinding_To_ReadOnlyObservableValue_Of_ObservableMap() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         targetMapProp="#{..propOfObsMapReadOnly}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
        assertCodeHighlight("propOfObsMapReadOnly", ex);
    }
}

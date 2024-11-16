// Copyright (c) 2023, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.generate.collections.MapWrapperGenerator;
import org.jfxcore.compiler.generate.collections.MapObservableValueWrapperGenerator;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"HttpUrlsUsage", "DuplicatedCode"})
@ExtendWith(TestExtension.class)
public class MapBindingTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class IndirectContext {
        public Map<Integer, String> map1 = new HashMap<>(Map.of(0, "foo", 1, "bar", 2, "baz"));
        public ObservableMap<Integer, String> map2 = FXCollections.observableMap(new HashMap<>(Map.of(0, "foo", 1, "bar", 2, "baz")));
        public ObjectProperty<Map<Integer, String>> map3 = new SimpleObjectProperty<>(new HashMap<>(Map.of(0, "foo", 1, "bar", 2, "baz")));
        public ObjectProperty<ObservableMap<Integer, String>> map4 = new SimpleObjectProperty<>(FXCollections.observableMap(new HashMap<>(Map.of(0, "foo", 1, "bar", 2, "baz"))));
    }

    @SuppressWarnings("unused")
    public static class MapTestPane extends Pane {
        private final ObjectProperty<IndirectContext> indirect = new SimpleObjectProperty<>(new IndirectContext());
        public Property<IndirectContext> indirectProperty() { return indirect; }

        public Map<Integer, Double> incompatibleMap1 = new HashMap<>();

        public Map<Integer, String> map1 = new HashMap<>(Map.of(0, "foo", 1, "bar", 2, "baz"));
        public ObservableMap<Integer, String> map2 = FXCollections.observableMap(new HashMap<>(Map.of(0, "foo", 1, "bar", 2, "baz")));
        public ObjectProperty<Map<Integer, String>> map3 = new SimpleObjectProperty<>(new HashMap<>(Map.of(0, "foo", 1, "bar", 2, "baz")));
        public ObjectProperty<ObservableMap<Integer, String>> map4 = new SimpleObjectProperty<>(FXCollections.observableMap(new HashMap<>(Map.of(0, "foo", 2, "bar", 3, "baz"))));
        public ObservableValue<ObservableMap<Integer, String>> map4ReadOnly() { return map4; }

        public final MapProperty<Integer, String> readOnlyMapProp = new SimpleMapProperty<>(this, "readOnlyMapProp");
        public ReadOnlyMapProperty<Integer, String> readOnlyMapPropProperty() { return readOnlyMapProp; }

        public final MapProperty<Integer, String> mapProp = new SimpleMapProperty<>(this, "setProp", FXCollections.observableMap(new HashMap<>()));
        public MapProperty<Integer, String> mapPropProperty() { return mapProp; }

        public final ObjectProperty<ObservableMap<Integer, String>> objectProp = new SimpleObjectProperty<>(this, "objectProp");
        public ObjectProperty<ObservableMap<Integer, String>> objectPropProperty() { return objectProp; }

        private final ObservableMap<Integer, String> targetObservableMap = FXCollections.observableMap(new HashMap<>());
        public Map<Integer, String> getTargetMap() { return targetObservableMap; }
        public ObservableMap<Integer, String> getTargetObservableMap() { return targetObservableMap; }
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

    private static String MAP_WRAPPER;
    private static String OBSERVABLE_VALUE_WRAPPER;
    private static String ADD_REFERENCE_METHOD;
    private static String CLEAR_STALE_REFERENCES_METHOD;

    @BeforeAll
    public static void beforeAll() {
        MAP_WRAPPER = MapWrapperGenerator.CLASS_NAME;
        OBSERVABLE_VALUE_WRAPPER = MapObservableValueWrapperGenerator.CLASS_NAME;
        ADD_REFERENCE_METHOD = NameHelper.getMangledMethodName("addReference");
        CLEAR_STALE_REFERENCES_METHOD = NameHelper.getMangledMethodName("clearStaleReferences");
    }

    @Test
    public void Once_Binding_To_Vanilla_Map() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="$map1" objectProp="$map1"/>
        """);

        assertNotNewExpr(root, MAP_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.mapProp.size());

        boolean[] flag = new boolean[1];
        root.mapProp.addListener((MapChangeListener<Integer, String>) c -> flag[0] = true);

        root.map1.clear(); // Change the source map
        assertFalse(flag[0]); // MapChangeListener was not invoked
        assertEquals(0, root.mapProp.size());

        assertEquals(0, root.objectProp.get().size());
        root.map1.put(0, "qux");
        assertTrue(root.objectProp.get().containsValue("qux"));
    }

    @Test
    public void Once_Binding_To_ObservableMap() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="$map2" objectProp="$map2"/>
        """);

        assertNotNewExpr(root, MAP_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.mapProp.size());

        boolean[] flag1 = new boolean[1];
        root.mapProp.addListener((MapChangeListener<Integer, String>)c -> flag1[0] = true);
        root.map2.clear(); // Change the source map
        assertTrue(flag1[0]); // MapChangeListener was invoked
        assertEquals(0, root.mapProp.size());

        assertEquals(0, root.objectProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.objectProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.map2.put(0, "qux"); // Change the source map
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertTrue(root.objectProp.get().containsValue("qux"));
    }

    @Test
    public void Once_Binding_To_ObservableValue_Of_Vanilla_Map() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="$map3" objectProp="$map3"/>
        """);

        assertNotNewExpr(root, MAP_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.mapProp.size());

        boolean[] flag1 = new boolean[1];
        root.mapProp.addListener((MapChangeListener<Integer, String>)c -> flag1[0] = true);
        root.map3.getValue().clear(); // Change the source map
        assertFalse(flag1[0]); // MapChangeListener was not invoked
        assertEquals(0, root.mapProp.size());

        assertEquals(0, root.objectProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.objectProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.map3.getValue().put(0, "qux"); // Change the source map
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertTrue(root.objectProp.get().containsValue("qux"));

        flag1[0] = flag2[0] = false;
        root.map3.setValue(FXCollections.observableMap(Map.of(0, "baz"))); // Replace the entire source map
        assertFalse(flag1[0]); // MapChangeListener was not invoked
        assertFalse(flag2[0]); // ChangeListener was not invoked
    }

    @Test
    public void Once_Binding_To_ObservableValue_Of_ObservableMap() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="$map4" objectProp="$map4"/>
        """);

        assertNotNewExpr(root, MAP_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.mapProp.size());
        boolean[] flag1 = new boolean[1];
        root.mapProp.addListener((MapChangeListener<Integer, String>)c -> flag1[0] = true);
        root.map4.getValue().clear(); // Change the source map
        assertTrue(flag1[0]); // MapChangeListener was invoked
        assertEquals(0, root.mapProp.size());

        assertEquals(0, root.objectProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.objectProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.map4.getValue().put(0, "qux"); // Change the source map
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertTrue(root.objectProp.get().containsValue("qux"));

        flag1[0] = flag2[0] = false;
        root.map4.setValue(FXCollections.observableMap(Map.of(0, "baz"))); // Replace the entire source map
        assertFalse(flag1[0]); // MapChangeListener was not invoked
        assertFalse(flag2[0]); // ChangeListener was not invoked
    }

    @Test
    public void Once_ContentBinding_To_Vanilla_Map() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="$[..map1]"/>
        """);

        assertNotNewExpr(root, MAP_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.mapProp.size());
        root.map1.clear(); // Change the source map
        assertEquals(3, root.mapProp.size()); // Target map is unchanged
    }

    @Test
    public void Once_ContentBinding_To_Vanilla_Map_Indirect() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="$[..indirect.map1]"/>
        """);

        assertNotNewExpr(root, MAP_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.mapProp.size());
        root.indirect.get().map1.clear(); // Change the source map
        assertEquals(3, root.mapProp.size()); // Target map is unchanged
    }

    @Test
    public void Once_ContentBinding_To_Observable_Map() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="$[..map2]"/>
        """);

        assertNotNewExpr(root, MAP_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.mapProp.size());
        root.map1.clear(); // Change the source map
        assertEquals(3, root.mapProp.size()); // Target map is unchanged
    }

    @Test
    public void Once_ContentBinding_To_Observable_Map_Indirect() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="$[..indirect.map2]"/>
        """);

        assertNotNewExpr(root, MAP_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.mapProp.size());
        root.indirect.get().map1.clear(); // Change the source map
        assertEquals(3, root.mapProp.size()); // Target map is unchanged
    }

    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Vanilla_Map() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="$[..map3]"/>
        """);

        assertNotNewExpr(root, MAP_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.mapProp.size());
        root.indirect.get().map1.clear(); // Change the source map
        assertEquals(3, root.mapProp.size()); // Target map is unchanged
    }

    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Vanilla_Map_Indirect() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="$[..indirect.map3]"/>
        """);

        assertNotNewExpr(root, MAP_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.mapProp.size());
        root.indirect.get().map1.clear(); // Change the source map
        assertEquals(3, root.mapProp.size()); // Target map is unchanged
    }

    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Observable_Map() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="$[..map4]"/>
        """);

        assertNotNewExpr(root, MAP_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.mapProp.size());
        root.indirect.get().map1.clear(); // Change the source map
        assertEquals(3, root.mapProp.size()); // Target map is unchanged
    }

    @Test
    public void Once_ContentBinding_To_ObservableValue_Of_Observable_Map_Indirect() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="$[..indirect.map4]"/>
        """);

        assertNotNewExpr(root, MAP_WRAPPER, OBSERVABLE_VALUE_WRAPPER);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.mapProp.size());
        root.indirect.get().map1.clear(); // Change the source map
        assertEquals(3, root.mapProp.size()); // Target map is unchanged
    }

    @Test
    public void Once_Binding_Fails_For_ReadOnlyMapProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         readOnlyMapProp="$map1"/>
        """));

        assertEquals(ErrorCode.CANNOT_MODIFY_READONLY_PROPERTY, ex.getDiagnostic().getCode());
    }

    @Test
    public void Once_Binding_Fails_For_Incompatible_Map() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="$incompatibleMap1"/>
        """));

        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   Map
     *  expected: target.bind(new MapWrapper(source))
     */
    @Test
    public void Unidirectional_Binding_To_Vanilla_Map() {
        MapTestPane root = compileAndRun("""
            <?import org.jfxcore.compiler.bindings.MapBindingTest.MapTestPane?>
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="${map1}" objectProp="${map1}"/>
        """);

        assertNewExpr(root, MAP_WRAPPER);
        assertNotNewExpr(root, "Constant");
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.mapProp.size());

        boolean[] flag = new boolean[1];
        root.mapProp.addListener((MapChangeListener<Integer, String>)c -> flag[0] = true);

        root.map1.clear();
        assertFalse(flag[0]);
        assertEquals(0, root.mapProp.size());

        assertEquals(0, root.objectProp.get().size());
        root.map1.put(0, "qux");
        assertTrue(root.objectProp.get().containsValue("qux"));
    }

    /*
     *  source:   Map
     *  expected: target.bind(new MapObservableValueWrapper(source))
     */
    @Test
    public void Unidirectional_Binding_To_Vanilla_Map_Indirect() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="${indirect.map1}" objectProp="${indirect.map1}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotNewExpr(root, MAP_WRAPPER, "Constant");
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(3, root.mapProp.size());

        boolean[] flag = new boolean[1];
        root.mapProp.addListener((MapChangeListener<Integer, String>)c -> flag[0] = true);

        root.indirect.get().map1.clear();
        assertFalse(flag[0]);
        assertEquals(0, root.mapProp.size());
        assertEquals(0, root.objectProp.get().size());

        root.indirect.setValue(new IndirectContext());
        assertTrue(flag[0]);
        assertEquals(3, root.mapProp.size());
        assertEquals(3, root.objectProp.get().size());
    }

    /*
     *  source:   Map
     *  expected: error
     */
    @Test
    public void Unidirectional_ContentBinding_To_Vanilla_Map_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="${[..map1]}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   Map
     *  expected: error
     */
    @Test
    public void Unidirectional_ContentBinding_Fails_For_ObjectProperty() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         objectProp="${[..map1]}"/>
        """));

        assertEquals(ErrorCode.INVALID_CONTENT_BINDING_TARGET, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   ObservableMap
     *  expected: target.bind(new ObjectConstant(source))
     */
    @Test
    public void Unidirectional_Binding_To_ObservableMap() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="${map2}" objectProp="${map2}"/>
        """);

        assertNewExpr(root, "ObjectConstant");
        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER, MAP_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.mapProp.size());
        boolean[] flag1 = new boolean[1];
        root.mapProp.addListener((MapChangeListener<Integer, String>)c -> flag1[0] = true);
        root.map2.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.mapProp.size());

        assertEquals(0, root.objectProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.objectProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.map2.put(0, "qux");
        assertFalse(flag2[0]);
        assertTrue(root.objectProp.get().containsValue("qux"));
    }

    /*
     *  source:   ObservableMap
     *  expected: target.bindContent(source)
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableMap() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="${[..map2]}"/>
        """);

        assertNotNewExpr(root, "Constant", OBSERVABLE_VALUE_WRAPPER, MAP_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.mapProp.size());
        boolean[] flag1 = new boolean[1];
        root.mapProp.addListener((MapChangeListener<Integer, String>)c -> flag1[0] = true);
        root.map2.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.mapProp.size());
    }

    /*
     *  source:   ObservableValue<Map>
     *  expected: target.bind(new MapObservableValueWrapper(source))
     */
    @Test
    public void Unidirectional_Binding_To_ObservableValue_Of_Vanilla_Map() throws Exception {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="${map3}" objectProp="${map3}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotNewExpr(root, "Constant", MAP_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.mapProp.size());
        boolean[] flag = new boolean[1];
        root.mapProp.addListener((MapChangeListener<Integer, String>)c -> flag[0] = true);
        root.map3.getValue().clear(); // Change the source map
        assertFalse(flag[0]); // MapChangeListener was not invoked
        assertEquals(0, root.mapProp.size());

        flag[0] = false;
        root.map3.setValue(FXCollections.observableMap(Map.of(0, "baz"))); // Replace the entire source map
        assertEquals(1, root.mapProp.size());
        assertTrue(flag[0]); // MapChangeListener was invoked

        // create a new instance to remap all changes
        root = newInstance(root);

        flag[0] = false;
        root.objectProp.addListener((observable, oldValue, newValue) -> flag[0] = true);
        root.map3.getValue().clear(); // Change the source map
        assertFalse(flag[0]); // ChangeListener was not invoked
        assertEquals(0, root.objectProp.getValue().size());

        flag[0] = false;
        root.map3.setValue(FXCollections.observableMap(Map.of(0, "baz"))); // Replace the entire source map
        assertEquals(1, root.objectProp.getValue().size());
        assertTrue(flag[0]); // ChangeListener was invoked
    }

    /*
     *  source:   ObservableValue<Map>
     *  expected: target.bindContent(new MapObservableValueWrapper(source))
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableValue_Of_Vanilla_Map() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="${[..map3]}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotNewExpr(root, "Constant", MAP_WRAPPER);
        assertMethodCall(root, ADD_REFERENCE_METHOD);
        assertMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
    }

    /*
     *  source:   ObservableValue<ObservableMap>
     *  expected: target.bind(source)
     */
    @Test
    public void Unidirectional_Binding_To_ObservableValue_Of_ObservableList() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="${map4}" objectProp="${map4}"/>
        """);

        assertNotNewExpr(root, "Constant", OBSERVABLE_VALUE_WRAPPER, MAP_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.mapProp.size());
        boolean[] flag1 = new boolean[1];
        root.mapProp.addListener((MapChangeListener<Integer, String>)c -> flag1[0] = true);
        root.map4.getValue().clear(); // Change the source map
        assertTrue(flag1[0]); // MapChangeListener was invoked
        assertEquals(0, root.mapProp.size());

        assertEquals(0, root.objectProp.get().size());
        boolean[] flag2 = new boolean[1];
        root.objectProp.addListener((observable, oldValue, newValue) -> flag2[0] = true);
        root.map4.getValue().put(0, "qux"); // Change the source map
        assertFalse(flag2[0]); // ChangeListener was not invoked
        assertTrue(root.objectProp.get().containsValue("qux"));

        flag1[0] = flag2[0] = false;
        root.map4.setValue(FXCollections.observableMap(Map.of(0, "baz"))); // Replace the entire source map
        assertTrue(flag1[0]); // MapChangeListener was invoked
        assertTrue(flag2[0]); // ChangeListener was invoked
    }

    /*
     *  source:   ObservableValue<ObservableMap>
     *  expected: target.bindContent(new MapObservableValueWrapper(source))
     */
    @Test
    public void Unidirectional_ContentBinding_To_ObservableValue_Of_ObservableMap() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="${[..map4]}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotNewExpr(root, "Constant", MAP_WRAPPER);
        assertMethodCall(root, ADD_REFERENCE_METHOD);
        assertMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.mapProp.size());
        boolean[] flag1 = new boolean[1];
        root.mapProp.addListener((MapChangeListener<Integer, String>)c -> flag1[0] = true);
        root.map4.getValue().clear(); // Change the source map
        assertTrue(flag1[0]); // MapChangeListener was invoked
        assertEquals(0, root.mapProp.size());

        flag1[0] = false;
        root.map4.setValue(FXCollections.observableMap(Map.of(0, "baz"))); // Replace the entire source map
        assertTrue(flag1[0]); // MapChangeListener was invoked
    }

    /*
     *  source:   Map
     *  expected: error
     */
    @Test
    public void Bidirectional_Binding_To_Vanilla_Map_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="#{map1}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());

        ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         objectProp="#{map1}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   Map
     *  expected: error
     */
    @Test
    public void Bidirectional_ContentBinding_To_Vanilla_Map_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="#{[..map1]}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   ObservableMap
     *  expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ObservableMap_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="#{map2}" objectProp="#{map2}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   ObservableMap
     *  expected: target.bindContentBidirectional(source)
     */
    @Test
    public void Bidirectional_ContentBinding_To_ObservableMap() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="#{[..map2]}"/>
        """);

        assertNotNewExpr(root, "Constant", OBSERVABLE_VALUE_WRAPPER, MAP_WRAPPER);
        assertNotMethodCall(root, ADD_REFERENCE_METHOD);
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        assertEquals(3, root.mapProp.size());
        boolean[] flag1 = new boolean[1];
        root.mapProp.addListener((MapChangeListener<Integer, String>)c -> flag1[0] = true);
        root.map2.clear();
        assertTrue(flag1[0]);
        assertEquals(0, root.mapProp.size());
    }

    /*
     *  source:   ObservableValue<Map>
     *  expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ObservableValue_Of_Vanilla_Map_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="#{map3}"/>
        """));

        assertEquals(ErrorCode.SOURCE_TYPE_MISMATCH, ex.getDiagnostic().getCode());
    }

    /*
     *  source:   ObservableValue<Map>
     *  expected: error
     */
    @Test
    public void Bidirectional_ContentBinding_To_ObservableValue_Of_Vanilla_Map_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="#{[..map3]}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     * source:   Property<ObservableMap>
     * expected: target.bindBidirectional(source)
     */
    @Test
    public void Bidirectional_Binding_To_Property_Of_ObservableMap() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="#{map4}"/>
        """);

        assertNotNewExpr(root, OBSERVABLE_VALUE_WRAPPER, MAP_WRAPPER, "Constant");
        assertNotMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
        assertEquals(root.map4.get(), root.mapProp);
    }

    /*
     * source:   Property<ObservableMap>
     * expected: target.bindContentBidirectional(new MapObservableValueWrapper(source))
     */
    @Test
    public void Bidirectional_ContentBinding_To_Property_Of_ObservableMap() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="#{[..map4]}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotNewExpr(root, MAP_WRAPPER, "Constant");
        assertMethodCall(root, ADD_REFERENCE_METHOD);
        assertMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);

        gc(); // verify that the generated wrapper is not prematurely collected
        root.map4.set(FXCollections.observableMap(Map.of(0, "123")));
        assertEquals(Map.of(0, "123"), root.mapProp.get());
    }

    /*
     * source:   ObservableValue<ObservableMap>
     * expected: error
     */
    @Test
    public void Bidirectional_Binding_To_ReadOnlyObservableValue_Of_ObservableMap_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="#{map4ReadOnly}"/>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

    /*
     * source:   ObservableValue<ObservableMap>
     * expected: target.bindContentBidirectional(new MapObservableValueWrapper(source))
     */
    @Test
    public void Bidirectional_ContentBinding_To_ReadOnlyObservableValue_Of_ObservableMap() {
        MapTestPane root = compileAndRun("""
            <MapTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                         mapProp="#{[..map4ReadOnly]}"/>
        """);

        assertNewExpr(root, OBSERVABLE_VALUE_WRAPPER);
        assertNotNewExpr(root, "Constant", MAP_WRAPPER);
        assertMethodCall(root, ADD_REFERENCE_METHOD);
        assertMethodExists(root, ADD_REFERENCE_METHOD, CLEAR_STALE_REFERENCES_METHOD);
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

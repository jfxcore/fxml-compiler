// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.collections;

import javafx.beans.property.ListProperty;
import javafx.beans.property.MapProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyListProperty;
import javafx.beans.property.ReadOnlyMapProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlySetProperty;
import javafx.beans.property.SetProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleMapProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleSetProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.MoreAssertions;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.TestCompiler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public abstract class CollectionBindingTest {

    static final List<String> SOURCE_LIST = List.of("foo", "bar", "baz");
    static final Set<String> SOURCE_SET = Set.of("foo", "bar", "baz");
    static final Map<String, String> SOURCE_MAP = Map.of("key0", "foo", "key1", "bar", "key2", "baz");

    static final String[] ALL_COLLECTION_SOURCES = values("sourceCollection", "sourcePropertyOfCollection");
    static final String[] ALL_LIST_SOURCES = values("sourceList", "sourceObservableList", "sourcePropertyOfList", "sourcePropertyOfObservableList");
    static final String[] LIST_SOURCES = values("sourceList", "sourcePropertyOfList");
    static final String[] LIST_PROPERTY_SOURCES = values("sourcePropertyOfList", "sourcePropertyOfObservableList");
    static final String[] OBSERVABLE_LIST_SOURCES = values("sourceObservableList", "sourcePropertyOfObservableList");
    static final String[] ALL_SET_SOURCES = values("sourceSet", "sourceObservableSet", "sourcePropertyOfSet", "sourcePropertyOfObservableSet");
    static final String[] SET_SOURCES = values("sourceSet", "sourcePropertyOfSet");
    static final String[] SET_PROPERTY_SOURCES = values("sourcePropertyOfSet", "sourcePropertyOfObservableSet");
    static final String[] OBSERVABLE_SET_SOURCES = values("sourceObservableSet", "sourcePropertyOfObservableSet");
    static final String[] ALL_MAP_SOURCES = values("sourceMap", "sourceObservableMap", "sourcePropertyOfMap", "sourcePropertyOfObservableMap");
    static final String[] MAP_SOURCES = values("sourceMap", "sourcePropertyOfMap");
    static final String[] MAP_PROPERTY_SOURCES = values("sourcePropertyOfMap", "sourcePropertyOfObservableMap");
    static final String[] OBSERVABLE_MAP_SOURCES = values("sourceObservableMap", "sourcePropertyOfObservableMap");
    static final String[] ALL_SOURCES;

    static {
        List<String> allSources = new ArrayList<>();
        allSources.addAll(List.of(ALL_COLLECTION_SOURCES));
        allSources.addAll(List.of(ALL_LIST_SOURCES));
        allSources.addAll(List.of(ALL_SET_SOURCES));
        allSources.addAll(List.of(ALL_MAP_SOURCES));
        ALL_SOURCES = allSources.toArray(String[]::new);
    }

    @SuppressWarnings("unused")
    public static class CollectionTestPane extends Pane {
        public Collection<String> sourceCollection = new ArrayList<>(SOURCE_LIST);
        public ObjectProperty<Collection<String>> sourcePropertyOfCollection = new SimpleObjectProperty<>(new ArrayList<>(SOURCE_LIST));

        public List<String> sourceList = new ArrayList<>(SOURCE_LIST);
        public ObservableList<String> sourceObservableList = FXCollections.observableArrayList(SOURCE_LIST);
        public ObjectProperty<List<String>> sourcePropertyOfList = new SimpleObjectProperty<>(new ArrayList<>(SOURCE_LIST));
        public ObjectProperty<ObservableList<String>> sourcePropertyOfObservableList = new SimpleObjectProperty<>(FXCollections.observableArrayList(SOURCE_LIST));

        public Set<String> sourceSet = new HashSet<>(SOURCE_SET);
        public ObservableSet<String> sourceObservableSet = FXCollections.observableSet(SOURCE_SET);
        public ObjectProperty<Set<String>> sourcePropertyOfSet = new SimpleObjectProperty<>(new HashSet<>(SOURCE_SET));
        public ObjectProperty<ObservableSet<String>> sourcePropertyOfObservableSet = new SimpleObjectProperty<>(FXCollections.observableSet(SOURCE_SET));

        public Map<String, String> sourceMap = new HashMap<>(SOURCE_MAP);
        public ObservableMap<String, String> sourceObservableMap = FXCollections.observableMap(SOURCE_MAP);
        public ObjectProperty<Map<String, String>> sourcePropertyOfMap = new SimpleObjectProperty<>(new HashMap<>(SOURCE_MAP));
        public ObjectProperty<ObservableMap<String, String>> sourcePropertyOfObservableMap = new SimpleObjectProperty<>(FXCollections.observableMap(SOURCE_MAP));

        private Collection<String> targetCollection = new ArrayList<>();
        public Collection<String> getReadOnlyTargetCollection() { return targetCollection; }
        public Collection<String> getTargetCollection() { return targetCollection; }
        public void setTargetCollection(Collection<String> value) { targetCollection = value; }

        private List<String> targetList = new ArrayList<>();
        public List<String> getReadOnlyTargetList() { return targetList; }
        public List<String> getTargetList() { return targetList; }
        public void setTargetList(List<String> value) { targetList = value; }

        private Set<String> targetSet = new HashSet<>();
        public Set<String> getReadOnlyTargetSet() { return targetSet; }
        public Set<String> getTargetSet() { return targetSet; }
        public void setTargetSet(Set<String> value) { targetSet = value; }

        private Map<String, String> targetMap = new HashMap<>();
        public Map<String, String> getReadOnlyTargetMap() { return targetMap; }
        public Map<String, String> getTargetMap() { return targetMap; }
        public void setTargetMap(Map<String, String> value) { targetMap = value; }

        private final ObjectProperty<Collection<String>> targetCollectionProp = new SimpleObjectProperty<>(FXCollections.observableArrayList());
        public ReadOnlyObjectProperty<Collection<String>> readOnlyTargetCollectionPropProperty() { return targetCollectionProp; }
        public ObjectProperty<Collection<String>> targetCollectionPropProperty() { return targetCollectionProp; }

        private final ObjectProperty<List<String>> targetObjectListProp = new SimpleObjectProperty<>(FXCollections.observableArrayList());
        public ReadOnlyObjectProperty<List<String>> readOnlyTargetObjectListPropProperty() { return targetObjectListProp; }
        public ObjectProperty<List<String>> targetObjectListPropProperty() { return targetObjectListProp; }

        private final ObjectProperty<Set<String>> targetObjectSetProp = new SimpleObjectProperty<>(FXCollections.observableSet(new HashSet<>()));
        public ReadOnlyObjectProperty<Set<String>> readOnlyTargetObjectSetPropProperty() { return targetObjectSetProp; }
        public ObjectProperty<Set<String>> targetObjectSetPropProperty() { return targetObjectSetProp; }

        private final ObjectProperty<Map<String, String>> targetObjectMapProp = new SimpleObjectProperty<>(FXCollections.observableHashMap());
        public ReadOnlyObjectProperty<Map<String, String>> readOnlyTargetObjectMapPropProperty() { return targetObjectMapProp; }
        public ObjectProperty<Map<String, String>> targetObjectMapPropProperty() { return targetObjectMapProp; }

        private final ObjectProperty<ObservableList<String>> targetObservableListProp = new SimpleObjectProperty<>(FXCollections.observableArrayList());
        public ReadOnlyObjectProperty<ObservableList<String>> readOnlyTargetObservableListPropProperty() { return targetObservableListProp; }
        public ObjectProperty<ObservableList<String>> targetObservableListPropProperty() { return targetObservableListProp; }

        private final ObjectProperty<ObservableSet<String>> targetObservableSetProp = new SimpleObjectProperty<>(FXCollections.observableSet(new HashSet<>()));
        public ReadOnlyObjectProperty<ObservableSet<String>> readOnlyTargetObservableSetPropProperty() { return targetObservableSetProp; }
        public ObjectProperty<ObservableSet<String>> targetObservableSetPropProperty() { return targetObservableSetProp; }

        private final ObjectProperty<ObservableMap<String, String>> targetObservableMapProp = new SimpleObjectProperty<>(FXCollections.observableHashMap());
        public ReadOnlyObjectProperty<ObservableMap<String, String>> readOnlyTargetObservableMapPropProperty() { return targetObservableMapProp; }
        public ObjectProperty<ObservableMap<String, String>> targetObservableMapPropProperty() { return targetObservableMapProp; }

        private final ListProperty<String> targetListProp = new SimpleListProperty<>(FXCollections.observableArrayList());
        public ReadOnlyListProperty<String> readOnlyTargetListPropProperty() { return targetListProp; }
        public ListProperty<String> targetListPropProperty() { return targetListProp; }

        private final SetProperty<String> targetSetProp = new SimpleSetProperty<>(FXCollections.observableSet(new HashSet<>()));
        public ReadOnlySetProperty<String> readOnlyTargetSetPropProperty() { return targetSetProp; }
        public SetProperty<String> targetSetPropProperty() { return targetSetProp; }

        private final MapProperty<String, String> targetMapProp = new SimpleMapProperty<>(FXCollections.observableHashMap());
        public ReadOnlyMapProperty<String, String> readOnlyTargetMapPropProperty() { return targetMapProp; }
        public MapProperty<String, String> targetMapPropProperty() { return targetMapProp; }
    }

    static Execution[] variants(String target, String sourceTemplate, String[] sourceParams, Object expected) {
        return Arrays.stream(sourceParams)
            .map(p -> String.format(sourceTemplate, p))
            .map(s -> new Execution(target, s, expected))
            .toArray(Execution[]::new);
    }

    static String[] difference(String[] a, String... b) {
        var list = Arrays.asList(b);
        return Arrays.stream(a).filter(x -> !list.contains(x)).toArray(String[]::new);
    }

    static String[] values(String... values) {
        return values;
    }

    @SuppressWarnings("ClassCanBeRecord")
    static class Execution {
        final String target;
        final String source;
        final Object expected;

        Execution(String target, String source, Object expected) {
            this.target = target;
            this.source = source;
            this.expected = expected;
        }

        @Override
        public String toString() {
            return target + "_" + source;
        }
    }

    @SuppressWarnings("unchecked")
    static void runTest(Execution execution) throws Exception {
        String target = execution.target, source = execution.source;
        String name;

        if (source.startsWith("${..")) name = "_bindContent_" + source.substring(4, source.length() - 1);
        else if (source.startsWith("#{..")) name = "_bindContentBidirectional_" + source.substring(4, source.length() - 1);
        else if (source.startsWith("$..")) name = "_content_" + source.substring(3);
        else if (source.startsWith("${")) name = "_bind_" + source.substring(2, source.length() - 1);
        else if (source.startsWith("#{")) name = "_bindBidirectional_" + source.substring(2, source.length() - 1);
        else if (source.startsWith("$")) name = "_once_" + source.substring(1);
        else throw new AssertionError();

        String fileName = "CollectionBindingTest_" + target + name;

        var expectedError = execution.expected instanceof ErrorCode ? (ErrorCode)execution.expected : null;
        var expectedResult = !(execution.expected instanceof ErrorCode) ? execution.expected : null;

        if (expectedError != null) {
            MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(fileName, """
                    <?import org.jfxcore.compiler.collections.CollectionBindingTest.CollectionTestPane?>
                    <CollectionTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" %s="%s"/>
                """.formatted(target, source)));

            assertEquals(expectedError, ex.getDiagnostic().getCode());
        } else {
            CollectionTestPane root = TestCompiler.newInstance(fileName, """
                    <?import org.jfxcore.compiler.collections.CollectionBindingTest.CollectionTestPane?>
                    <CollectionTestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0" %s="%s"/>
                """.formatted(target, source));

            Method getter;
            try {
                getter = CollectionTestPane.class.getMethod(NameHelper.getGetterName(target, false));
            } catch (NoSuchMethodException ex) {
                getter = CollectionTestPane.class.getMethod(target + "Property");
            }

            Object targetObj = getter.invoke(root);

            if (targetObj instanceof ObservableValue) {
                targetObj = ((ObservableValue<?>)targetObj).getValue();
            }

            if (targetObj instanceof Map) {
                MoreAssertions.assertContentEquals((Map<String, String>)targetObj, (Map<String, String>)expectedResult);
            } else {
                MoreAssertions.assertContentEquals((Collection<String>)targetObj, (Collection<String>)expectedResult);
            }
        }
    }
}

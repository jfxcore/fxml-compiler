// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.bindings;

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
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.jfxcore.compiler.diagnostic.ErrorCode.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"HttpUrlsUsage", "unused"})
@ExtendWith(TestExtension.class)
public class CollectionBindingMatrixTest {

    public static final List<String> SOURCE_LIST = List.of("foo", "bar", "baz");
    public static final Set<String> SOURCE_SET = Set.of("foo", "bar", "baz");
    public static final Map<String, String> SOURCE_MAP = Map.of("key0", "foo", "key1", "bar", "key2", "baz");

    private static final String[] COLLECTION_SOURCES = new String[] {"sourceCollection", "sourcePropertyOfCollection"};
    private static final String[] LIST_SOURCES = new String[] {"sourceList", "sourceObservableList", "sourcePropertyOfList", "sourcePropertyOfObservableList"};
    private static final String[] OBSERVABLE_LIST_SOURCES = new String[] {"sourceObservableList", "sourcePropertyOfObservableList"};
    private static final String[] SET_SOURCES = new String[] {"sourceSet", "sourceObservableSet", "sourcePropertyOfSet", "sourcePropertyOfObservableSet"};
    private static final String[] OBSERVABLE_SET_SOURCES = new String[] {"sourceObservableSet", "sourcePropertyOfObservableSet"};
    private static final String[] MAP_SOURCES = new String[] {"sourceMap", "sourceObservableMap", "sourcePropertyOfMap", "sourcePropertyOfObservableMap"};
    private static final String[] OBSERVABLE_MAP_SOURCES = new String[] {"sourceObservableMap", "sourcePropertyOfObservableMap"};
    private static final String[] ALL_SOURCES;

    static {
        List<String> allSources = new ArrayList<>();
        allSources.addAll(List.of(COLLECTION_SOURCES));
        allSources.addAll(List.of(LIST_SOURCES));
        allSources.addAll(List.of(SET_SOURCES));
        allSources.addAll(List.of(MAP_SOURCES));
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

    private static Execution[] variants(String target, String sourceTemplate, String[] sourceParams, Object expected) {
        return Arrays.stream(sourceParams)
            .map(p -> String.format(sourceTemplate, p))
            .map(s -> new Execution(target, s, expected))
            .toArray(Execution[]::new);
    }

    private static Collection<?> collectionOf(Object... items) {
        List<Object[]> result = new ArrayList<>();

        for (Object item : items) {
            if (item instanceof Execution[]) {
                for (Execution e : (Execution[])item) {
                    result.add(new Object[] {e.target, e.source, e.expected});
                }
            } else if (item instanceof Object[]) {
                result.add((Object[])item);
            }
        }

        return result;
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static class Execution {
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

    static Stream<?> params() {
        return Stream.of(
            /*
                target:                            source:
                Collection<T> (read-only)          Collection/List/Set/Map<T>
                                                    |   ObservableList/Set/Map<T>
                                                    |          |   ObservableValue<Collection/List/Set/Map<T>>
                                                    |          |          |  ObservableValue<ObservableList/Set/Map<T>>
                ------------------------------------|----------|----------|----------|---------------------------------
                fx:once                             1          1          1          1
                fx:content                          +/+/+/2    +/+/2      +/+/+/2    +/+/2
                fx:bind                             1          1          1          1
                fx:bindBidirectional                1          1          1          1
                fx:bindContent                      3          3          3          3
                fx:bindContentBidirectional         4          4          4          4

                1 = CANNOT_MODIFY_READONLY_PROPERTY
                2 = INVALID_CONTENT_ASSIGNMENT_SOURCE
                3 = INVALID_CONTENT_BINDING_TARGET
                4 = INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET
            */
            variants("readOnlyTargetCollection", "{fx:once %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetCollection", "{fx:once %s; content=true}", COLLECTION_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetCollection", "{fx:once %s; content=true}", LIST_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetCollection", "{fx:once %s; content=true}", SET_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetCollection", "{fx:once %s; content=true}", MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetCollection", "{fx:bind %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetCollection", "{fx:sync %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetCollection", "{fx:bind %s; content=true}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("readOnlyTargetCollection", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            /*
                target:                            source:
                Collection<T> (writable)           Collection/List/Set/Map<T>
                                                    |   ObservableList/Set/Map<T>
                                                    |          |   ObservableValue<Collection/List/Set/Map<T>>
                                                    |          |          |  ObservableValue<ObservableList/Set/Map<T>>
                ------------------------------------|----------|----------|----------|---------------------------------
                fx:once                             +/+/+/4    +/+/4      +/+/+/4    +/+/4
                fx:content                          +/+/+/5    +/+/5      +/+/+/5    +/+/5
                fx:bind                             1          1          1          1
                fx:bindBidirectional                1          1          1          1
                fx:bindContent                      2          2          2          2
                fx:bindContentBidirectional         3          3          3          3

                1 = INVALID_BINDING_TARGET
                2 = INVALID_CONTENT_BINDING_TARGET
                3 = CANNOT_CONTENT_BIND__BIDIRECTIONAL_PROPERTY
                4 = CANNOT_CONVERT_SOURCE_TYPE
                5 = INVALID_CONTENT_ASSIGNMENT_SOURCE
            */
            variants("targetCollection", "{fx:once %s}", COLLECTION_SOURCES, SOURCE_LIST),
            variants("targetCollection", "{fx:once %s}", LIST_SOURCES, SOURCE_LIST),
            variants("targetCollection", "{fx:once %s}", SET_SOURCES, SOURCE_LIST),
            variants("targetCollection", "{fx:once %s}", MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetCollection", "{fx:once %s; content=true}", COLLECTION_SOURCES, SOURCE_LIST),
            variants("targetCollection", "{fx:once %s; content=true}", LIST_SOURCES, SOURCE_LIST),
            variants("targetCollection", "{fx:once %s; content=true}", SET_SOURCES, SOURCE_LIST),
            variants("targetCollection", "{fx:once %s; content=true}", MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetCollection", "{fx:bind %s}", ALL_SOURCES, INVALID_BINDING_TARGET),
            variants("targetCollection", "{fx:sync %s}", ALL_SOURCES, INVALID_BINDING_TARGET),
            variants("targetCollection", "{fx:bind %s; content=true}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("targetCollection", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            /*
                target:                            source:
                List<T> (read-only)                Collection/List/Set/Map<T>
                                                    |   ObservableList/Set/Map<T>
                                                    |          |   ObservableValue<Collection/List/Set/Map<T>>
                                                    |          |          |  ObservableValue<ObservableList/Set/Map<T>>
                ------------------------------------|----------|----------|----------|---------------------------------
                fx:once                             1          1          1          1
                fx:content                          +/+/+/2    +/+/2      +/+/+/2    +/+/2
                fx:bind                             1          1          1          1
                fx:bindBidirectional                1          1          1          1
                fx:bindContent                      3          +/3/3/3    3          +/3/3/3
                fx:bindContentBidirectional         4          4          4          4

                1 = CANNOT_MODIFY_READONLY_PROPERTY
                2 = INVALID_CONTENT_ASSIGNMENT_SOURCE
                3 = INVALID_CONTENT_BINDING_SOURCE
                4 = INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET
            */
            variants("readOnlyTargetList", "{fx:once %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetList", "{fx:once %s; content=true}", COLLECTION_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetList", "{fx:once %s; content=true}", LIST_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetList", "{fx:once %s; content=true}", SET_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetList", "{fx:once %s; content=true}", MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetList", "{fx:bind %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetList", "{fx:sync %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetList", "{fx:bind %s; content=true}", COLLECTION_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetList", "{fx:bind %s; content=true}", new String[] {"sourceList"}, INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetList", "{fx:bind %s; content=true}", new String[] {"sourcePropertyOfList"}, SOURCE_LIST),
            variants("readOnlyTargetList", "{fx:bind %s; content=true}", OBSERVABLE_LIST_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetList", "{fx:bind %s; content=true}", SET_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetList", "{fx:bind %s; content=true}", MAP_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetList", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            /*
                target:                            source:
                List<T> (writable)                 Collection/List/Set/Map<T>
                                                    |   ObservableList/Set/Map<T>
                                                    |          |   ObservableValue<Collection/List/Set/Map<T>>
                                                    |          |          |  ObservableValue<ObservableList/Set/Map<T>>
                ------------------------------------|----------|----------|----------|---------------------------------
                fx:once                             1/+/1/1    +/1/1      1/+/1/1    +/1/1
                fx:content                          +/+/+/2    +/+/2      +/+/+/2    +/+/2
                fx:bind                             3          3          3          3
                fx:bindBidirectional                3          3          3          3
                fx:bindContent                      4          +/4/4      4/+/4/4    +/4/4
                fx:bindContentBidirectional         5          5          5          5

                1 = CANNOT_CONVERT_SOURCE_TYPE
                2 = INVALID_CONTENT_ASSIGNMENT_SOURCE
                3 = INVALID_BINDING_TARGET
                4 = INVALID_CONTENT_BINDING_SOURCE
                5 = INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET
            */
            variants("targetList", "{fx:once %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetList", "{fx:once %s}", LIST_SOURCES, SOURCE_LIST),
            variants("targetList", "{fx:once %s}", SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetList", "{fx:once %s}", MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetList", "{fx:once %s; content=true}", COLLECTION_SOURCES, SOURCE_LIST),
            variants("targetList", "{fx:once %s; content=true}", LIST_SOURCES, SOURCE_LIST),
            variants("targetList", "{fx:once %s; content=true}", SET_SOURCES, SOURCE_LIST),
            variants("targetList", "{fx:once %s; content=true}", MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetList", "{fx:bind %s}", ALL_SOURCES, INVALID_BINDING_TARGET),
            variants("targetList", "{fx:sync %s}", ALL_SOURCES, INVALID_BINDING_TARGET),
            variants("targetList", "{fx:bind %s; content=true}", COLLECTION_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("targetList", "{fx:bind %s; content=true}", new String[] {"sourceList"}, INVALID_CONTENT_BINDING_SOURCE),
            variants("targetList", "{fx:bind %s; content=true}", new String[] {"sourcePropertyOfList"}, SOURCE_LIST),
            variants("targetList", "{fx:bind %s; content=true}", OBSERVABLE_LIST_SOURCES, SOURCE_LIST),
            variants("targetList", "{fx:bind %s; content=true}", SET_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("targetList", "{fx:bind %s; content=true}", MAP_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("targetList", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            /*
                target:                            source:
                Set<T> (read-only)                 Collection/List/Set/Map<T>
                                                    |   ObservableList/Set/Map<T>
                                                    |          |   ObservableValue<Collection/List/Set/Map<T>>
                                                    |          |          |  ObservableValue<ObservableList/Set/Map<T>>
                ------------------------------------|----------|----------|----------|---------------------------------
                fx:once                             1          1          1          1
                fx:content                          +/+/+/2    +/+/2      +/+/+/2    +/+/2
                fx:bind                             1          1          1          1
                fx:bindBidirectional                1          1          1          1
                fx:bindContent                      3          3/+/3      3          3/+/3
                fx:bindContentBidirectional         4          4          4          4

                1 = CANNOT_MODIFY_READONLY_PROPERTY
                2 = INVALID_CONTENT_ASSIGNMENT_SOURCE
                3 = INVALID_CONTENT_BINDING_SOURCE
                4 = INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET
            */
            variants("readOnlyTargetSet", "{fx:once %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetSet", "{fx:once %s; content=true}", COLLECTION_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetSet", "{fx:once %s; content=true}", LIST_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetSet", "{fx:once %s; content=true}", SET_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetSet", "{fx:once %s; content=true}", MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetSet", "{fx:bind %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetSet", "{fx:sync %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetSet", "{fx:bind %s; content=true}", COLLECTION_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetSet", "{fx:bind %s; content=true}", LIST_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetSet", "{fx:bind %s; content=true}", new String[] {"sourceSet"}, INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetSet", "{fx:bind %s; content=true}", new String[] {"sourcePropertyOfSet"}, SOURCE_SET),
            variants("readOnlyTargetSet", "{fx:bind %s; content=true}", OBSERVABLE_SET_SOURCES, SOURCE_SET),
            variants("readOnlyTargetSet", "{fx:bind %s; content=true}", MAP_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetSet", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            /*
                target:                            source:
                Set<T> (writable)                  Collection/List/Set/Map<T>
                                                    |   ObservableList/Set/Map<T>
                                                    |          |   ObservableValue<Collection/List/Set/Map<T>>
                                                    |          |          |  ObservableValue<ObservableList/Set/Map<T>>
                ------------------------------------|----------|----------|----------|---------------------------------
                fx:once                             1/1/+/1    1/+/1      1/1/+/1    1/+/1
                fx:content                          +/+/+/2    +/+/2      +/+/+/2    +/+/2
                fx:bind                             3          3          3          3
                fx:bindBidirectional                3          3          3          3
                fx:bindContent                      4          4/+/4      4/4/+/4    4/+/4
                fx:bindContentBidirectional         5          5          5          5

                1 = CANNOT_CONVERT_SOURCE_TYPE
                2 = INVALID_CONTENT_ASSIGNMENT_SOURCE
                3 = INVALID_BINDING_TARGET
                4 = INVALID_CONTENT_BINDING_SOURCE
                5 = INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET
            */
            variants("targetSet", "{fx:once %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetSet", "{fx:once %s}", LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetSet", "{fx:once %s}", SET_SOURCES, SOURCE_SET),
            variants("targetSet", "{fx:once %s}", MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetSet", "{fx:once %s; content=true}", COLLECTION_SOURCES, SOURCE_LIST),
            variants("targetSet", "{fx:once %s; content=true}", LIST_SOURCES, SOURCE_LIST),
            variants("targetSet", "{fx:once %s; content=true}", SET_SOURCES, SOURCE_LIST),
            variants("targetSet", "{fx:once %s; content=true}", MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetSet", "{fx:bind %s}", ALL_SOURCES, INVALID_BINDING_TARGET),
            variants("targetSet", "{fx:sync %s}", ALL_SOURCES, INVALID_BINDING_TARGET),
            variants("targetSet", "{fx:bind %s; content=true}", COLLECTION_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("targetSet", "{fx:bind %s; content=true}", LIST_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("targetSet", "{fx:bind %s; content=true}", new String[] {"sourceSet"}, INVALID_CONTENT_BINDING_SOURCE),
            variants("targetSet", "{fx:bind %s; content=true}", new String[] {"sourcePropertyOfSet"}, SOURCE_SET),
            variants("targetSet", "{fx:bind %s; content=true}", OBSERVABLE_SET_SOURCES, SOURCE_SET),
            variants("targetSet", "{fx:bind %s; content=true}", MAP_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("targetSet", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            /*
                target:                            source:
                Map<T> (read-only)                 Collection/List/Set/Map<T>
                                                    |   ObservableList/Set/Map<T>
                                                    |          |   ObservableValue<Collection/List/Set/Map<T>>
                                                    |          |          |  ObservableValue<ObservableList/Set/Map<T>>
                ------------------------------------|----------|----------|----------|---------------------------------
                fx:once                             1          1          1          1
                fx:content                          2/2/2/+    2/2/+      2/2/2/+    2/2/+
                fx:bind                             1          1          1          1
                fx:bindBidirectional                1          1          1          1
                fx:bindContent                      3          3/3/+      3          3/3/+
                fx:bindContentBidirectional         4          4          4          4

                1 = CANNOT_MODIFY_READONLY_PROPERTY
                2 = INVALID_CONTENT_ASSIGNMENT_SOURCE
                3 = INVALID_CONTENT_BINDING_SOURCE
                4 = INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET
            */
            variants("readOnlyTargetMap", "{fx:once %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetMap", "{fx:once %s; content=true}", COLLECTION_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetMap", "{fx:once %s; content=true}", LIST_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetMap", "{fx:once %s; content=true}", SET_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetMap", "{fx:once %s; content=true}", MAP_SOURCES, SOURCE_MAP),
            variants("readOnlyTargetMap", "{fx:bind %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetMap", "{fx:sync %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetMap", "{fx:bind %s; content=true}", COLLECTION_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetMap", "{fx:bind %s; content=true}", LIST_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetMap", "{fx:bind %s; content=true}", SET_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetMap", "{fx:bind %s; content=true}", new String[] {"sourceMap"}, INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetMap", "{fx:bind %s; content=true}", new String[] {"sourcePropertyOfMap"}, SOURCE_MAP),
            variants("readOnlyTargetMap", "{fx:bind %s; content=true}", OBSERVABLE_MAP_SOURCES, SOURCE_MAP),
            variants("readOnlyTargetMap", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            /*
                target:                            source:
                Map<T> (writable)                  Collection/List/Set/Map<T>
                                                    |   ObservableList/Set/Map<T>
                                                    |          |   ObservableValue<Collection/List/Set/Map<T>>
                                                    |          |          |  ObservableValue<ObservableList/Set/Map<T>>
                ------------------------------------|----------|----------|----------|---------------------------------
                fx:once                             1/1/1/+    1/1/+      1/1/1/+    1/1/+
                fx:content                          2/2/2/+    2/2/+      2/2/2/+    2/2/+
                fx:bind                             3          3          3          3
                fx:bindBidirectional                3          3          3          3
                fx:bindContent                      4          4/4/+      4/4/4/+    4/4/+
                fx:bindContentBidirectional         5          5          5          5

                1 = CANNOT_CONVERT_SOURCE_TYPE
                2 = INVALID_CONTENT_ASSIGNMENT_SOURCE
                3 = INVALID_BINDING_TARGET
                4 = INVALID_CONTENT_BINDING_SOURCE
                5 = INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET
            */
            variants("targetMap", "{fx:once %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetMap", "{fx:once %s}", LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetMap", "{fx:once %s}", SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetMap", "{fx:once %s}", MAP_SOURCES, SOURCE_MAP),
            variants("targetMap", "{fx:once %s; content=true}", COLLECTION_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetMap", "{fx:once %s; content=true}", LIST_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetMap", "{fx:once %s; content=true}", SET_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetMap", "{fx:once %s; content=true}", MAP_SOURCES, SOURCE_MAP),
            variants("targetMap", "{fx:bind %s}", ALL_SOURCES, INVALID_BINDING_TARGET),
            variants("targetMap", "{fx:sync %s}", ALL_SOURCES, INVALID_BINDING_TARGET),
            variants("targetMap", "{fx:bind %s; content=true}", COLLECTION_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("targetMap", "{fx:bind %s; content=true}", LIST_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("targetMap", "{fx:bind %s; content=true}", SET_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("targetMap", "{fx:bind %s; content=true}", new String[] {"sourceMap"}, INVALID_CONTENT_BINDING_SOURCE),
            variants("targetMap", "{fx:bind %s; content=true}", new String[] {"sourcePropertyOfMap"}, SOURCE_MAP),
            variants("targetMap", "{fx:bind %s; content=true}", OBSERVABLE_MAP_SOURCES, SOURCE_MAP),
            variants("targetMap", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            /*
                target:                            source:
                ObservableValue<Collection<T>>     Collection/List/Set/Map<T>
                ObservableValue<List<T>>            |   ObservableList/Set/Map<T>
                ObservableValue<Set<T>>             |          |   ObservableValue<Collection/List/Set/Map<T>>
                ObservableValue<ObservableList<T>>  |          |          |  ObservableValue<ObservableList/Set/Map<T>>
                ObservableValue<ObservableSet<T>>   |          |          |          |
                ------------------------------------|----------|----------|----------|---------------------------------
                fx:once                             1          1          1          1
                fx:content                          +/+/+/2    +/+/2      +/+/+/2    +/+/2
                fx:bind                             1          1          1          1
                fx:bindBidirectional                1          1          1          1
                fx:bindContent                      3          3          3          3
                fx:bindContentBidirectional         4          4          4          4

                1 = CANNOT_MODIFY_READONLY_PROPERTY
                2 = INVALID_CONTENT_ASSIGNMENT_SOURCE
                3 = INVALID_CONTENT_BINDING_TARGET
                4 = INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET
            */
            variants("readOnlyTargetCollectionProp", "{fx:once %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetCollectionProp", "{fx:once %s; content=true}", COLLECTION_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetCollectionProp", "{fx:once %s; content=true}", LIST_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetCollectionProp", "{fx:once %s; content=true}", SET_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetCollectionProp", "{fx:once %s; content=true}", MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetCollectionProp", "{fx:bind %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetCollectionProp", "{fx:sync %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetCollectionProp", "{fx:bind %s; content=true}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("readOnlyTargetCollectionProp", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            variants("readOnlyTargetObjectListProp", "{fx:once %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObjectListProp", "{fx:once %s; content=true}", COLLECTION_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObjectListProp", "{fx:once %s; content=true}", LIST_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObjectListProp", "{fx:once %s; content=true}", SET_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObjectListProp", "{fx:once %s; content=true}", MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetObjectListProp", "{fx:bind %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObjectListProp", "{fx:sync %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObjectListProp", "{fx:bind %s; content=true}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("readOnlyTargetObjectListProp", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            variants("readOnlyTargetObjectSetProp", "{fx:once %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObjectSetProp", "{fx:once %s; content=true}", COLLECTION_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObjectSetProp", "{fx:once %s; content=true}", LIST_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObjectSetProp", "{fx:once %s; content=true}", SET_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObjectSetProp", "{fx:once %s; content=true}", MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetObjectSetProp", "{fx:bind %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObjectSetProp", "{fx:sync %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObjectSetProp", "{fx:bind %s; content=true}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("readOnlyTargetObjectSetProp", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            variants("readOnlyTargetObservableListProp", "{fx:once %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObservableListProp", "{fx:once %s; content=true}", COLLECTION_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObservableListProp", "{fx:once %s; content=true}", LIST_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObservableListProp", "{fx:once %s; content=true}", SET_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObservableListProp", "{fx:once %s; content=true}", MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetObservableListProp", "{fx:bind %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObservableListProp", "{fx:sync %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObservableListProp", "{fx:bind %s; content=true}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("readOnlyTargetObservableListProp", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            variants("readOnlyTargetObservableSetProp", "{fx:once %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObservableSetProp", "{fx:once %s; content=true}", COLLECTION_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObservableSetProp", "{fx:once %s; content=true}", LIST_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObservableSetProp", "{fx:once %s; content=true}", SET_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObservableSetProp", "{fx:once %s; content=true}", MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetObservableSetProp", "{fx:bind %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObservableSetProp", "{fx:sync %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObservableSetProp", "{fx:bind %s; content=true}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("readOnlyTargetObservableSetProp", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            /*
                target:                            source:
                ObservableValue<Map<T>>            Collection/List/Set/Map<T>
                ObservableValue<ObservableMap<T>>   |   ObservableList/Set/Map<T>
                                                    |          |   ObservableValue<Collection/List/Set/Map<T>>
                                                    |          |          |  ObservableValue<ObservableList/Set/Map<T>>
                ------------------------------------|----------|----------|----------|---------------------------------
                fx:once                             1          1          1          1
                fx:content                          2/2/2/+    2/2/+      2/2/2/+    2/2/+
                fx:bind                             1          1          1          1
                fx:bindBidirectional                1          1          1          1
                fx:bindContent                      3          3          3          3
                fx:bindContentBidirectional         4          4          4          4

                1 = CANNOT_MODIFY_READONLY_PROPERTY
                2 = INVALID_CONTENT_ASSIGNMENT_SOURCE
                3 = INVALID_CONTENT_BINDING_TARGET
                4 = INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET
            */
            variants("readOnlyTargetObjectMapProp", "{fx:once %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObjectMapProp", "{fx:once %s; content=true}", COLLECTION_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetObjectMapProp", "{fx:once %s; content=true}", LIST_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetObjectMapProp", "{fx:once %s; content=true}", SET_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetObjectMapProp", "{fx:once %s; content=true}", MAP_SOURCES, SOURCE_MAP),
            variants("readOnlyTargetObjectMapProp", "{fx:bind %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObjectMapProp", "{fx:sync %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObjectMapProp", "{fx:bind %s; content=true}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("readOnlyTargetObjectMapProp", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            variants("readOnlyTargetObservableMapProp", "{fx:once %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObservableMapProp", "{fx:once %s; content=true}", COLLECTION_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetObservableMapProp", "{fx:once %s; content=true}", LIST_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetObservableMapProp", "{fx:once %s; content=true}", SET_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetObservableMapProp", "{fx:once %s; content=true}", MAP_SOURCES, SOURCE_MAP),
            variants("readOnlyTargetObservableMapProp", "{fx:bind %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObservableMapProp", "{fx:sync %s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObservableMapProp", "{fx:bind %s; content=true}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("readOnlyTargetObservableMapProp", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            /*
                target:                            source:
                Property<List<T>>                  Collection/List/Set/Map<T>
                                                    |   ObservableList/Set/Map<T>
                                                    |          |   Property<Collection/List/Set/Map<T>>
                                                    |          |          |  Property<ObservableList/Set/Map<T>>
                ------------------------------------|----------|----------|----------|--------------------------
                fx:once                             1/+/1/1    +/1/1      1/+/1/1    +/1/1
                fx:content                          +/+/+/2    +/+/2      +/+/+/2    +/+/2
                fx:bind                             1/+/1/1    +/1/1      1/+/1/1    +/1/1
                fx:bindBidirectional                3          3          4/+/4/4    +/4/4
                fx:bindContent                      5          5          5          5
                fx:bindContentBidirectional         6          6          6          6

                1 = CANNOT_CONVERT_SOURCE_TYPE
                2 = INVALID_CONTENT_ASSIGNMENT_SOURCE
                3 = INVALID_BIDIRECTIONAL_BINDING_SOURCE
                4 = SOURCE_TYPE_MISMATCH
                5 = INVALID_CONTENT_BINDING_TARGET
                6 = INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET
            */
            variants("targetObjectListProp", "{fx:once %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectListProp", "{fx:once %s}", LIST_SOURCES, SOURCE_LIST),
            variants("targetObjectListProp", "{fx:once %s}", SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectListProp", "{fx:once %s}", MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectListProp", "{fx:once %s; content=true}", COLLECTION_SOURCES, SOURCE_LIST),
            variants("targetObjectListProp", "{fx:once %s; content=true}", LIST_SOURCES, SOURCE_LIST),
            variants("targetObjectListProp", "{fx:once %s; content=true}", SET_SOURCES, SOURCE_LIST),
            variants("targetObjectListProp", "{fx:once %s; content=true}", MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObjectListProp", "{fx:bind %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectListProp", "{fx:bind %s}", LIST_SOURCES, SOURCE_LIST),
            variants("targetObjectListProp", "{fx:bind %s}", SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectListProp", "{fx:bind %s}", MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectListProp", "{fx:sync %s}", new String[] {"sourcePropertyOfList"}, SOURCE_LIST),
            variants("targetObjectListProp", "{fx:sync %s}", new String[] {"sourcePropertyOfObservableList"}, SOURCE_TYPE_MISMATCH),
            variants("targetObjectListProp", "{fx:sync %s}", Arrays.stream(LIST_SOURCES).filter(s -> !List.of("sourcePropertyOfList", "sourcePropertyOfObservableList").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectListProp", "{fx:sync %s}", new String[] {"sourcePropertyOfSet", "sourcePropertyOfObservableSet"}, SOURCE_TYPE_MISMATCH),
            variants("targetObjectListProp", "{fx:sync %s}", Arrays.stream(SET_SOURCES).filter(s -> !List.of("sourcePropertyOfSet", "sourcePropertyOfObservableSet").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectListProp", "{fx:sync %s}", new String[] {"sourcePropertyOfMap", "sourcePropertyOfObservableMap"}, SOURCE_TYPE_MISMATCH),
            variants("targetObjectListProp", "{fx:sync %s}", Arrays.stream(MAP_SOURCES).filter(s -> !List.of("sourcePropertyOfMap", "sourcePropertyOfObservableMap").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectListProp", "{fx:bind %s; content=true}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("targetObjectListProp", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            /*
                target:                            source:
                Property<Set<T>>                   Collection/List/Set/Map<T>
                                                    |   ObservableList/Set/Map<T>
                                                    |          |   Property<Collection/List/Set/Map<T>>
                                                    |          |          |  Property<ObservableList/Set/Map<T>>
                ------------------------------------|----------|----------|----------|--------------------------
                fx:once                             1/1/+/1    1/+/1      1/1/+/1    1/+/1
                fx:content                          +/+/+/2    +/+/2      +/+/+/2    +/+/2
                fx:bind                             1/1/+/1    1/+/1      1/1/+/1    1/+/1
                fx:bindBidirectional                3          3          4/4/+/4    4/+/4
                fx:bindContent                      5          5          5          5
                fx:bindContentBidirectional         6          6          6          6

                1 = CANNOT_CONVERT_SOURCE_TYPE
                2 = INVALID_CONTENT_ASSIGNMENT_SOURCE
                3 = INVALID_BIDIRECTIONAL_BINDING_SOURCE
                4 = SOURCE_TYPE_MISMATCH
                5 = INVALID_CONTENT_BINDING_TARGET
                6 = INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET
            */
            variants("targetObjectSetProp", "{fx:once %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectSetProp", "{fx:once %s}", LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectSetProp", "{fx:once %s}", SET_SOURCES, SOURCE_SET),
            variants("targetObjectSetProp", "{fx:once %s}", MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectSetProp", "{fx:once %s; content=true}", COLLECTION_SOURCES, SOURCE_LIST),
            variants("targetObjectSetProp", "{fx:once %s; content=true}", LIST_SOURCES, SOURCE_LIST),
            variants("targetObjectSetProp", "{fx:once %s; content=true}", SET_SOURCES, SOURCE_LIST),
            variants("targetObjectSetProp", "{fx:once %s; content=true}", MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObjectSetProp", "{fx:bind %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectSetProp", "{fx:bind %s}", LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectSetProp", "{fx:bind %s}", SET_SOURCES, SOURCE_SET),
            variants("targetObjectSetProp", "{fx:bind %s}", MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectSetProp", "{fx:sync %s}", new String[] {"sourcePropertyOfSet"}, SOURCE_SET),
            variants("targetObjectSetProp", "{fx:sync %s}", new String[] {"sourcePropertyOfObservableSet"}, SOURCE_TYPE_MISMATCH),
            variants("targetObjectSetProp", "{fx:sync %s}", Arrays.stream(SET_SOURCES).filter(s -> !List.of("sourcePropertyOfSet", "sourcePropertyOfObservableSet").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectSetProp", "{fx:sync %s}", new String[] {"sourcePropertyOfList", "sourcePropertyOfObservableList"}, SOURCE_TYPE_MISMATCH),
            variants("targetObjectSetProp", "{fx:sync %s}", Arrays.stream(LIST_SOURCES).filter(s -> !List.of("sourcePropertyOfList", "sourcePropertyOfObservableList").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectSetProp", "{fx:sync %s}", new String[] {"sourcePropertyOfMap", "sourcePropertyOfObservableMap"}, SOURCE_TYPE_MISMATCH),
            variants("targetObjectSetProp", "{fx:sync %s}", Arrays.stream(MAP_SOURCES).filter(s -> !List.of("sourcePropertyOfMap", "sourcePropertyOfObservableMap").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectSetProp", "{fx:bind %s; content=true}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("targetObjectSetProp", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            /*
                target:                            source:
                Property<Map<T>>                   Collection/List/Set/Map<T>
                                                    |   ObservableList/Set/Map<T>
                                                    |          |   Property<Collection/List/Set/Map<T>>
                                                    |          |          |  Property<ObservableList/Set/Map<T>>
                ------------------------------------|----------|----------|----------|--------------------------
                fx:once                             1/1/1/+    1/1/+      1/1/1/+    1/1/+
                fx:content                          2/2/2/+    2/2/+      2/2/2/+    2/2/+
                fx:bind                             1/1/1/+    1/1/+      1/1/1/+    1/1/+
                fx:bindBidirectional                3          3          4/4/4/+    4/4/+
                fx:bindContent                      5          5          5          5
                fx:bindContentBidirectional         6          6          6          6

                1 = CANNOT_CONVERT_SOURCE_TYPE
                2 = INVALID_CONTENT_ASSIGNMENT_SOURCE
                3 = INVALID_BIDIRECTIONAL_BINDING_SOURCE
                4 = SOURCE_TYPE_MISMATCH
                5 = INVALID_CONTENT_BINDING_TARGET
                6 = INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET
            */
            variants("targetObjectMapProp", "{fx:once %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectMapProp", "{fx:once %s}", LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectMapProp", "{fx:once %s}", SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectMapProp", "{fx:once %s}", MAP_SOURCES, SOURCE_MAP),
            variants("targetObjectMapProp", "{fx:once %s; content=true}", COLLECTION_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObjectMapProp", "{fx:once %s; content=true}", LIST_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObjectMapProp", "{fx:once %s; content=true}", SET_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObjectMapProp", "{fx:once %s; content=true}", MAP_SOURCES, SOURCE_MAP),
            variants("targetObjectMapProp", "{fx:bind %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectMapProp", "{fx:bind %s}", LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectMapProp", "{fx:bind %s}", SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectMapProp", "{fx:bind %s}", MAP_SOURCES, SOURCE_MAP),
            variants("targetObjectMapProp", "{fx:sync %s}", new String[] {"sourcePropertyOfMap"}, SOURCE_MAP),
            variants("targetObjectMapProp", "{fx:sync %s}", new String[] {"sourcePropertyOfObservableMap"}, SOURCE_TYPE_MISMATCH),
            variants("targetObjectMapProp", "{fx:sync %s}", Arrays.stream(MAP_SOURCES).filter(s -> !List.of("sourcePropertyOfMap", "sourcePropertyOfObservableMap").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectMapProp", "{fx:sync %s}", new String[] {"sourcePropertyOfList", "sourcePropertyOfObservableList"}, SOURCE_TYPE_MISMATCH),
            variants("targetObjectMapProp", "{fx:sync %s}", Arrays.stream(LIST_SOURCES).filter(s -> !List.of("sourcePropertyOfList", "sourcePropertyOfObservableList").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectMapProp", "{fx:sync %s}", new String[] {"sourcePropertyOfSet", "sourcePropertyOfObservableSet"}, SOURCE_TYPE_MISMATCH),
            variants("targetObjectMapProp", "{fx:sync %s}", Arrays.stream(SET_SOURCES).filter(s -> !List.of("sourcePropertyOfSet", "sourcePropertyOfObservableSet").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectMapProp", "{fx:bind %s; content=true}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("targetObjectMapProp", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            /*
                target:                            source:
                Property<ObservableList<T>>        Collection/List/Set/Map<T>
                                                    |   ObservableList/Set/Map<T>
                                                    |          |   Property<Collection/List/Set/Map<T>>
                                                    |          |          |  Property<ObservableList/Set/Map<T>>
                ------------------------------------|----------|----------|----------|--------------------------
                fx:once                             1/+/1/1    +/1/1      1/+/1/1    +/1/1
                fx:content                          +/+/+/2    +/+/2      +/+/+/2    +/+/2
                fx:bind                             1/+/1/1    +/1/1      1/+/1/1    +/1/1
                fx:bindBidirectional                3          3          4          +/4/4
                fx:bindContent                      5          5          5          5
                fx:bindContentBidirectional         6          6          6          6

                1 = CANNOT_CONVERT_SOURCE_TYPE
                2 = INVALID_CONTENT_ASSIGNMENT_SOURCE
                3 = INVALID_BIDIRECTIONAL_BINDING_SOURCE
                4 = SOURCE_TYPE_MISMATCH
                5 = INVALID_CONTENT_BINDING_TARGET
                6 = INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET
            */
            variants("targetObservableListProp", "{fx:once %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableListProp", "{fx:once %s}", LIST_SOURCES, SOURCE_LIST),
            variants("targetObservableListProp", "{fx:once %s}", SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableListProp", "{fx:once %s}", MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableListProp", "{fx:once %s; content=true}", COLLECTION_SOURCES, SOURCE_LIST),
            variants("targetObservableListProp", "{fx:once %s; content=true}", LIST_SOURCES, SOURCE_LIST),
            variants("targetObservableListProp", "{fx:once %s; content=true}", SET_SOURCES, SOURCE_LIST),
            variants("targetObservableListProp", "{fx:once %s; content=true}", MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObservableListProp", "{fx:bind %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableListProp", "{fx:bind %s}", LIST_SOURCES, SOURCE_LIST),
            variants("targetObservableListProp", "{fx:bind %s}", SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableListProp", "{fx:bind %s}", MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableListProp", "{fx:sync %s}", new String[] {"sourcePropertyOfList"}, SOURCE_TYPE_MISMATCH),
            variants("targetObservableListProp", "{fx:sync %s}", new String[] {"sourcePropertyOfObservableList"}, SOURCE_LIST),
            variants("targetObservableListProp", "{fx:sync %s}", Arrays.stream(LIST_SOURCES).filter(s -> !List.of("sourcePropertyOfList", "sourcePropertyOfObservableList").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableListProp", "{fx:sync %s}", new String[] {"sourcePropertyOfSet", "sourcePropertyOfObservableSet"}, SOURCE_TYPE_MISMATCH),
            variants("targetObservableListProp", "{fx:sync %s}", Arrays.stream(SET_SOURCES).filter(s -> !List.of("sourcePropertyOfSet", "sourcePropertyOfObservableSet").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableListProp", "{fx:sync %s}", new String[] {"sourcePropertyOfMap", "sourcePropertyOfObservableMap"}, SOURCE_TYPE_MISMATCH),
            variants("targetObservableListProp", "{fx:sync %s}", Arrays.stream(MAP_SOURCES).filter(s -> !List.of("sourcePropertyOfMap", "sourcePropertyOfObservableMap").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableListProp", "{fx:bind %s; content=true}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("targetObservableListProp", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            /*
                target:                            source:
                Property<ObservableSet<T>>         Collection/List/Set/Map<T>
                                                    |   ObservableList/Set/Map<T>
                                                    |          |   Property<Collection/List/Set/Map<T>>
                                                    |          |          |  Property<ObservableList/Set/Map<T>>
                ------------------------------------|----------|----------|----------|--------------------------
                fx:once                             1/1/+/1    1/+/1      1/1/+/1    1/+/1
                fx:content                          +/+/+/2    +/+/2      +/+/+/2    +/+/2
                fx:bind                             1/1/+/1    1/+/1      1/1/+/1    1/+/1
                fx:bindBidirectional                3          3          4          4/+/4
                fx:bindContent                      5          5          5          5
                fx:bindContentBidirectional         6          6          6          6

                1 = CANNOT_CONVERT_SOURCE_TYPE
                2 = INVALID_CONTENT_ASSIGNMENT_SOURCE
                3 = INVALID_BIDIRECTIONAL_BINDING_SOURCE
                4 = SOURCE_TYPE_MISMATCH
                5 = INVALID_CONTENT_BINDING_TARGET
                6 = INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET
            */
            variants("targetObservableSetProp", "{fx:once %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableSetProp", "{fx:once %s}", LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableSetProp", "{fx:once %s}", SET_SOURCES, SOURCE_SET),
            variants("targetObservableSetProp", "{fx:once %s}", MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableSetProp", "{fx:once %s; content=true}", COLLECTION_SOURCES, SOURCE_LIST),
            variants("targetObservableSetProp", "{fx:once %s; content=true}", LIST_SOURCES, SOURCE_LIST),
            variants("targetObservableSetProp", "{fx:once %s; content=true}", SET_SOURCES, SOURCE_LIST),
            variants("targetObservableSetProp", "{fx:once %s; content=true}", MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObservableSetProp", "{fx:bind %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableSetProp", "{fx:bind %s}", LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableSetProp", "{fx:bind %s}", SET_SOURCES, SOURCE_SET),
            variants("targetObservableSetProp", "{fx:bind %s}", MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableSetProp", "{fx:sync %s}", new String[] {"sourcePropertyOfSet"}, SOURCE_TYPE_MISMATCH),
            variants("targetObservableSetProp", "{fx:sync %s}", new String[] {"sourcePropertyOfObservableSet"}, SOURCE_SET),
            variants("targetObservableSetProp", "{fx:sync %s}", Arrays.stream(SET_SOURCES).filter(s -> !List.of("sourcePropertyOfSet", "sourcePropertyOfObservableSet").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableSetProp", "{fx:sync %s}", new String[] {"sourcePropertyOfList", "sourcePropertyOfObservableList"}, SOURCE_TYPE_MISMATCH),
            variants("targetObservableSetProp", "{fx:sync %s}", Arrays.stream(LIST_SOURCES).filter(s -> !List.of("sourcePropertyOfList", "sourcePropertyOfObservableList").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableSetProp", "{fx:sync %s}", new String[] {"sourcePropertyOfMap", "sourcePropertyOfObservableMap"}, SOURCE_TYPE_MISMATCH),
            variants("targetObservableSetProp", "{fx:sync %s}", Arrays.stream(MAP_SOURCES).filter(s -> !List.of("sourcePropertyOfMap", "sourcePropertyOfObservableMap").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableSetProp", "{fx:bind %s; content=true}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("targetObservableSetProp", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            /*
                target:                            source:
                Property<ObservableMap<T>>         Collection/List/Set/Map<T>
                                                    |   ObservableList/Set/Map<T>
                                                    |          |   Property<Collection/List/Set/Map<T>>
                                                    |          |          |  Property<ObservableList/Set/Map<T>>
                ------------------------------------|----------|----------|----------|--------------------------
                fx:once                             1/1/1/+    1/1/+      1/1/1/+    1/1/+
                fx:content                          2/2/2/+    2/2/+      2/2/2/+    2/2/+
                fx:bind                             1/1/1/+    1/1/+      1/1/1/+    1/1/+
                fx:bindBidirectional                3          3          4          4/4/+
                fx:bindContent                      5          5          5          5
                fx:bindContentBidirectional         6          6          6          6

                1 = CANNOT_CONVERT_SOURCE_TYPE
                2 = INVALID_CONTENT_ASSIGNMENT_SOURCE
                3 = INVALID_BIDIRECTIONAL_BINDING_SOURCE
                4 = SOURCE_TYPE_MISMATCH
                5 = INVALID_CONTENT_BINDING_TARGET
                6 = INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET
            */
            variants("targetObservableMapProp", "{fx:once %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableMapProp", "{fx:once %s}", LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableMapProp", "{fx:once %s}", SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableMapProp", "{fx:once %s}", MAP_SOURCES, SOURCE_MAP),
            variants("targetObservableMapProp", "{fx:once %s; content=true}", COLLECTION_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObservableMapProp", "{fx:once %s; content=true}", LIST_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObservableMapProp", "{fx:once %s; content=true}", SET_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObservableMapProp", "{fx:once %s; content=true}", MAP_SOURCES, SOURCE_MAP),
            variants("targetObservableMapProp", "{fx:bind %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableMapProp", "{fx:bind %s}", LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableMapProp", "{fx:bind %s}", SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableMapProp", "{fx:bind %s}", MAP_SOURCES, SOURCE_MAP),
            variants("targetObservableMapProp", "{fx:sync %s}", new String[] {"sourcePropertyOfMap"}, SOURCE_TYPE_MISMATCH),
            variants("targetObservableMapProp", "{fx:sync %s}", new String[] {"sourcePropertyOfObservableMap"}, SOURCE_MAP),
            variants("targetObservableMapProp", "{fx:sync %s}", Arrays.stream(MAP_SOURCES).filter(s -> !List.of("sourcePropertyOfMap", "sourcePropertyOfObservableMap").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableMapProp", "{fx:sync %s}", new String[] {"sourcePropertyOfList", "sourcePropertyOfObservableList"}, SOURCE_TYPE_MISMATCH),
            variants("targetObservableMapProp", "{fx:sync %s}", Arrays.stream(LIST_SOURCES).filter(s -> !List.of("sourcePropertyOfList", "sourcePropertyOfObservableList").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableMapProp", "{fx:sync %s}", new String[] {"sourcePropertyOfSet", "sourcePropertyOfObservableSet"}, SOURCE_TYPE_MISMATCH),
            variants("targetObservableMapProp", "{fx:sync %s}", Arrays.stream(SET_SOURCES).filter(s -> !List.of("sourcePropertyOfSet", "sourcePropertyOfObservableSet").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableMapProp", "{fx:bind %s; content=true}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("targetObservableMapProp", "{fx:sync %s; content=true}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            /*
                target:                            source:
                ListProperty<T>                    Collection/List/Set/Map<T>
                                                    |   ObservableList/Set/Map<T>
                                                    |          |   Property<Collection/List/Set/Map<T>>
                                                    |          |          |  Property<ObservableList/Set/Map<T>>
                ------------------------------------|----------|----------|----------|--------------------------
                fx:once                             1/+/1/1    +/1/1      1/+/1/1    +/1/1
                fx:content                          +/+/+/2    +/+/2      +/+/+/2    +/+/2
                fx:bind                             1/+/1/1    +/1/1      1/+/1/1    +/1/1
                fx:bindBidirectional                3          3          4          +/4/4
                fx:bindContent                      5          5          5          5
                fx:bindContentBidirectional         6          +/6/6      6          +/6/6

                1 = CANNOT_CONVERT_SOURCE_TYPE
                2 = INVALID_CONTENT_ASSIGNMENT_SOURCE
                3 = INVALID_BIDIRECTIONAL_BINDING_SOURCE
                4 = SOURCE_TYPE_MISMATCH
                5 = INVALID_CONTENT_BINDING_TARGET
                6 = INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET
            */
            variants("targetListProp", "{fx:once %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetListProp", "{fx:once %s}", LIST_SOURCES, SOURCE_LIST),
            variants("targetListProp", "{fx:once %s}", SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetListProp", "{fx:once %s}", MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetListProp", "{fx:once %s; content=true}", COLLECTION_SOURCES, SOURCE_LIST),
            variants("targetListProp", "{fx:once %s; content=true}", LIST_SOURCES, SOURCE_LIST),
            variants("targetListProp", "{fx:once %s; content=true}", SET_SOURCES, SOURCE_LIST),
            variants("targetListProp", "{fx:once %s; content=true}", MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetListProp", "{fx:bind %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetListProp", "{fx:bind %s}", LIST_SOURCES, SOURCE_LIST),
            variants("targetListProp", "{fx:bind %s}", SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetListProp", "{fx:bind %s}", MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetListProp", "{fx:sync %s}", new String[] {"sourcePropertyOfList"}, SOURCE_TYPE_MISMATCH),
            variants("targetListProp", "{fx:sync %s}", new String[] {"sourcePropertyOfObservableList"}, SOURCE_LIST),
            variants("targetListProp", "{fx:sync %s}", Arrays.stream(LIST_SOURCES).filter(s -> !List.of("sourcePropertyOfList", "sourcePropertyOfObservableList").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetListProp", "{fx:sync %s}", new String[] {"sourcePropertyOfSet", "sourcePropertyOfObservableSet"}, SOURCE_TYPE_MISMATCH),
            variants("targetListProp", "{fx:sync %s}", Arrays.stream(SET_SOURCES).filter(s -> !List.of("sourcePropertyOfSet", "sourcePropertyOfObservableSet").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetListProp", "{fx:sync %s}", new String[] {"sourcePropertyOfMap", "sourcePropertyOfObservableMap"}, SOURCE_TYPE_MISMATCH),
            variants("targetListProp", "{fx:sync %s}", Arrays.stream(MAP_SOURCES).filter(s -> !List.of("sourcePropertyOfMap", "sourcePropertyOfObservableMap").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetListProp", "{fx:bind %s; content=true}", new String[] {"sourceObservableList", "sourcePropertyOfList", "sourcePropertyOfObservableList"}, SOURCE_LIST),
            variants("targetListProp", "{fx:bind %s; content=true}", Arrays.stream(ALL_SOURCES).filter(s -> !List.of("sourceObservableList", "sourcePropertyOfList", "sourcePropertyOfObservableList").contains(s)).toArray(String[]::new), INVALID_CONTENT_BINDING_SOURCE),
            variants("targetListProp", "{fx:sync %s; content=true}", new String[] {"sourceObservableList", "sourcePropertyOfObservableList"}, SOURCE_LIST),
            variants("targetListProp", "{fx:sync %s; content=true}", Arrays.stream(ALL_SOURCES).filter(s -> !List.of("sourceObservableList", "sourcePropertyOfObservableList").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE),

            /*
                target:                            source:
                SetProperty<T>                     Collection/List/Set/Map<T>
                                                    |   ObservableList/Set/Map<T>
                                                    |          |   Property<Collection/List/Set/Map<T>>
                                                    |          |          |  Property<ObservableList/Set/Map<T>>
                ------------------------------------|----------|----------|----------|--------------------------
                fx:once                             1/1/+/1    1/+/1      1/1/+/1    1/+/1
                fx:content                          +/+/+/2    +/+/2      +/+/+/2    +/+/2
                fx:bind                             1/1/+/1    1/+/1      1/1/+/1    1/+/1
                fx:bindBidirectional                3          3          4          4/+/4
                fx:bindContent                      5          5          5          5
                fx:bindContentBidirectional         6          6/+/6      6          6/+/6

                1 = CANNOT_CONVERT_SOURCE_TYPE
                2 = INVALID_CONTENT_ASSIGNMENT_SOURCE
                3 = INVALID_BIDIRECTIONAL_BINDING_SOURCE
                4 = SOURCE_TYPE_MISMATCH
                5 = INVALID_CONTENT_BINDING_TARGET
                6 = INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET
            */
            variants("targetSetProp", "{fx:once %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetSetProp", "{fx:once %s}", LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetSetProp", "{fx:once %s}", SET_SOURCES, SOURCE_SET),
            variants("targetSetProp", "{fx:once %s}", MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetSetProp", "{fx:once %s; content=true}", COLLECTION_SOURCES, SOURCE_LIST),
            variants("targetSetProp", "{fx:once %s; content=true}", LIST_SOURCES, SOURCE_LIST),
            variants("targetSetProp", "{fx:once %s; content=true}", SET_SOURCES, SOURCE_LIST),
            variants("targetSetProp", "{fx:once %s; content=true}", MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetSetProp", "{fx:bind %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetSetProp", "{fx:bind %s}", LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetSetProp", "{fx:bind %s}", SET_SOURCES, SOURCE_SET),
            variants("targetSetProp", "{fx:bind %s}", MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetSetProp", "{fx:sync %s}", new String[] {"sourcePropertyOfSet"}, SOURCE_TYPE_MISMATCH),
            variants("targetSetProp", "{fx:sync %s}", new String[] {"sourcePropertyOfObservableSet"}, SOURCE_SET),
            variants("targetSetProp", "{fx:sync %s}", Arrays.stream(SET_SOURCES).filter(s -> !List.of("sourcePropertyOfSet", "sourcePropertyOfObservableSet").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetSetProp", "{fx:sync %s}", new String[] {"sourcePropertyOfList", "sourcePropertyOfObservableList"}, SOURCE_TYPE_MISMATCH),
            variants("targetSetProp", "{fx:sync %s}", Arrays.stream(LIST_SOURCES).filter(s -> !List.of("sourcePropertyOfList", "sourcePropertyOfObservableList").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetSetProp", "{fx:sync %s}", new String[] {"sourcePropertyOfMap", "sourcePropertyOfObservableMap"}, SOURCE_TYPE_MISMATCH),
            variants("targetSetProp", "{fx:sync %s}", Arrays.stream(MAP_SOURCES).filter(s -> !List.of("sourcePropertyOfMap", "sourcePropertyOfObservableMap").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetSetProp", "{fx:bind %s; content=true}", new String[] {"sourceObservableSet", "sourcePropertyOfSet", "sourcePropertyOfObservableSet"}, SOURCE_SET),
            variants("targetSetProp", "{fx:bind %s; content=true}", Arrays.stream(ALL_SOURCES).filter(s -> !List.of("sourceObservableSet", "sourcePropertyOfSet", "sourcePropertyOfObservableSet").contains(s)).toArray(String[]::new), INVALID_CONTENT_BINDING_SOURCE),
            variants("targetSetProp", "{fx:sync %s; content=true}", new String[] {"sourceObservableSet", "sourcePropertyOfObservableSet"}, SOURCE_SET),
            variants("targetSetProp", "{fx:sync %s; content=true}", Arrays.stream(ALL_SOURCES).filter(s -> !List.of("sourceObservableSet", "sourcePropertyOfObservableSet").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE),

            /*
                target:                            source:
                MapProperty<T>                     Collection/List/Set/Map<T>
                                                    |   ObservableList/Set/Map<T>
                                                    |          |   Property<Collection/List/Set/Map<T>>
                                                    |          |          |  Property<ObservableList/Set/Map<T>>
                ------------------------------------|----------|----------|----------|--------------------------
                fx:once                             1/1/1/+    1/1/+      1/1/1/+    1/1/+
                fx:content                          2/2/2/+    2/2/+      2/2/2/+    2/2/+
                fx:bind                             1/1/1/+    1/1/+      1/1/1/+    1/1/+
                fx:bindBidirectional                3          3          4          4/4/+
                fx:bindContent                      5          5          5          5
                fx:bindContentBidirectional         6          6/6/+      6          6/6/+

                1 = CANNOT_CONVERT_SOURCE_TYPE
                2 = INVALID_CONTENT_ASSIGNMENT_SOURCE
                3 = INVALID_BIDIRECTIONAL_BINDING_SOURCE
                4 = SOURCE_TYPE_MISMATCH
                5 = INVALID_CONTENT_BINDING_TARGET
                6 = INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET
            */
            variants("targetMapProp", "{fx:once %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetMapProp", "{fx:once %s}", LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetMapProp", "{fx:once %s}", SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetMapProp", "{fx:once %s}", MAP_SOURCES, SOURCE_MAP),
            variants("targetMapProp", "{fx:once %s; content=true}", COLLECTION_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetMapProp", "{fx:once %s; content=true}", LIST_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetMapProp", "{fx:once %s; content=true}", SET_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetMapProp", "{fx:once %s; content=true}", MAP_SOURCES, SOURCE_MAP),
            variants("targetMapProp", "{fx:bind %s}", COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetMapProp", "{fx:bind %s}", LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetMapProp", "{fx:bind %s}", SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetMapProp", "{fx:bind %s}", MAP_SOURCES, SOURCE_MAP),
            variants("targetMapProp", "{fx:sync %s}", new String[] {"sourcePropertyOfMap"}, SOURCE_TYPE_MISMATCH),
            variants("targetMapProp", "{fx:sync %s}", new String[] {"sourcePropertyOfObservableMap"}, SOURCE_MAP),
            variants("targetMapProp", "{fx:sync %s}", Arrays.stream(MAP_SOURCES).filter(s -> !List.of("sourcePropertyOfMap", "sourcePropertyOfObservableMap").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetMapProp", "{fx:sync %s}", new String[] {"sourcePropertyOfList", "sourcePropertyOfObservableList"}, SOURCE_TYPE_MISMATCH),
            variants("targetMapProp", "{fx:sync %s}", Arrays.stream(LIST_SOURCES).filter(s -> !List.of("sourcePropertyOfList", "sourcePropertyOfObservableList").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetMapProp", "{fx:sync %s}", new String[] {"sourcePropertyOfSet", "sourcePropertyOfObservableSet"}, SOURCE_TYPE_MISMATCH),
            variants("targetMapProp", "{fx:sync %s}", Arrays.stream(SET_SOURCES).filter(s -> !List.of("sourcePropertyOfSet", "sourcePropertyOfObservableSet").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetMapProp", "{fx:bind %s; content=true}", new String[] {"sourceObservableMap", "sourcePropertyOfMap", "sourcePropertyOfObservableMap"}, SOURCE_MAP),
            variants("targetMapProp", "{fx:bind %s; content=true}", Arrays.stream(ALL_SOURCES).filter(s -> !List.of("sourceObservableMap", "sourcePropertyOfMap", "sourcePropertyOfObservableMap").contains(s)).toArray(String[]::new), INVALID_CONTENT_BINDING_SOURCE),
            variants("targetMapProp", "{fx:sync %s; content=true}", new String[] {"sourceObservableMap", "sourcePropertyOfObservableMap"}, SOURCE_MAP),
            variants("targetMapProp", "{fx:sync %s; content=true}", Arrays.stream(ALL_SOURCES).filter(s -> !List.of("sourceObservableMap", "sourcePropertyOfObservableMap").contains(s)).toArray(String[]::new), INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE)
        );
    }

    @ParameterizedTest
    @MethodSource("params")
    @SuppressWarnings("unchecked")
    public void ParameterizedTest(Execution execution) throws Exception {
        String target = execution.target, source = execution.source;
        String fileName = "ParameterizedTest_" + target + "_" + source.replaceAll("fx:|\\{|_|\s|;|=", "");
        fileName = fileName.substring(0, fileName.length() - 1);

        var expectedError = execution.expected instanceof ErrorCode ? (ErrorCode)execution.expected : null;
        var expectedResult = !(execution.expected instanceof ErrorCode) ? execution.expected : null;

        if (expectedError != null) {
            MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
                this, "ParameterizedTest", """
                        <?import org.jfxcore.compiler.bindings.CollectionBindingMatrixTest.CollectionTestPane?>
                        <CollectionTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml" %s="%s"/>
                    """.formatted(target, source)));

            assertEquals(expectedError, ex.getDiagnostic().getCode());
        } else {
            CollectionTestPane root = TestCompiler.newInstance(
                this, fileName, """
                        <?import org.jfxcore.compiler.bindings.CollectionBindingMatrixTest.CollectionTestPane?>
                        <CollectionTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml" %s="%s"/>
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

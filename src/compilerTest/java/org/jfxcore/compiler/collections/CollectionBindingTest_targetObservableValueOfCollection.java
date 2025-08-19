// Copyright (c) 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.collections;

import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.jfxcore.compiler.diagnostic.ErrorCode.*;

@ExtendWith(TestExtension.class)
@SuppressWarnings("NewClassNamingConvention")
public class CollectionBindingTest_targetObservableValueOfCollection extends CollectionBindingTest {

    static Stream<?> params() {
        return Stream.of(
            // target: ObservableValue<Collection<T>>
            variants("readOnlyTargetCollectionProp", "$%s", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetCollectionProp", "$..%s", ALL_COLLECTION_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetCollectionProp", "$..%s", ALL_LIST_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetCollectionProp", "$..%s", ALL_SET_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetCollectionProp", "$..%s", ALL_MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetCollectionProp", "${%s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetCollectionProp", "#{%s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetCollectionProp", "${..%s}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("readOnlyTargetCollectionProp", "#{..%s}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            // target: ObservableValue<List<T>>
            variants("readOnlyTargetObjectListProp", "$%s", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObjectListProp", "$..%s", ALL_COLLECTION_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObjectListProp", "$..%s", ALL_LIST_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObjectListProp", "$..%s", ALL_SET_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObjectListProp", "$..%s", ALL_MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetObjectListProp", "${%s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObjectListProp", "#{%s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObjectListProp", "${..%s}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("readOnlyTargetObjectListProp", "#{..%s}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            // target: ObservableValue<Set<T>>
            variants("readOnlyTargetObjectSetProp", "$%s", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObjectSetProp", "$..%s", ALL_COLLECTION_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObjectSetProp", "$..%s", ALL_LIST_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObjectSetProp", "$..%s", ALL_SET_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObjectSetProp", "$..%s", ALL_MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetObjectSetProp", "${%s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObjectSetProp", "#{%s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObjectSetProp", "${..%s}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("readOnlyTargetObjectSetProp", "#{..%s}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            // target: ObservableValue<ObservableList<T>>
            variants("readOnlyTargetObservableListProp", "$%s", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObservableListProp", "$..%s", ALL_COLLECTION_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObservableListProp", "$..%s", ALL_LIST_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObservableListProp", "$..%s", ALL_SET_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObservableListProp", "$..%s", ALL_MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetObservableListProp", "${%s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObservableListProp", "#{%s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObservableListProp", "${..%s}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("readOnlyTargetObservableListProp", "#{..%s}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            // target: ObservableValue<ObservableSet<T>>
            variants("readOnlyTargetObservableSetProp", "$%s", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObservableSetProp", "$..%s", ALL_COLLECTION_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObservableSetProp", "$..%s", ALL_LIST_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObservableSetProp", "$..%s", ALL_SET_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetObservableSetProp", "$..%s", ALL_MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetObservableSetProp", "${%s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObservableSetProp", "#{%s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetObservableSetProp", "${..%s}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("readOnlyTargetObservableSetProp", "#{..%s}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET)
        ).flatMap(Arrays::stream);
    }

    @ParameterizedTest
    @MethodSource("params")
    void test(Execution execution) throws Exception {
        runTest(execution);
    }
}

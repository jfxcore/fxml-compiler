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
public class CollectionBindingTest_targetPropertyOfList extends CollectionBindingTest {

    static Stream<?> params() {
        return Stream.of(
            // target: Property<List<T>>
            variants("targetObjectListProp", "$%s", ALL_COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectListProp", "$%s", ALL_LIST_SOURCES, SOURCE_LIST),
            variants("targetObjectListProp", "$%s", ALL_SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectListProp", "$%s", ALL_MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectListProp", "$..%s", ALL_COLLECTION_SOURCES, SOURCE_LIST),
            variants("targetObjectListProp", "$..%s", ALL_LIST_SOURCES, SOURCE_LIST),
            variants("targetObjectListProp", "$..%s", ALL_SET_SOURCES, SOURCE_LIST),
            variants("targetObjectListProp", "$..%s", ALL_MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObjectListProp", "${%s}", ALL_COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectListProp", "${%s}", LIST_PROPERTY_SOURCES, SOURCE_LIST),
            variants("targetObjectListProp", "${%s}", difference(ALL_LIST_SOURCES, LIST_PROPERTY_SOURCES), INVALID_UNIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectListProp", "${%s}", ALL_SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectListProp", "${%s}", ALL_MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectListProp", "#{%s}", values("sourcePropertyOfList"), SOURCE_LIST),
            variants("targetObjectListProp", "#{%s}", values("sourcePropertyOfObservableList"), SOURCE_TYPE_MISMATCH),
            variants("targetObjectListProp", "#{%s}", difference(ALL_LIST_SOURCES, LIST_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectListProp", "#{%s}", difference(ALL_SET_SOURCES, SET_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectListProp", "#{%s}", difference(ALL_MAP_SOURCES, MAP_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectListProp", "#{%s}", SET_PROPERTY_SOURCES, SOURCE_TYPE_MISMATCH),
            variants("targetObjectListProp", "#{%s}", MAP_PROPERTY_SOURCES, SOURCE_TYPE_MISMATCH),
            variants("targetObjectListProp", "${..%s}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("targetObjectListProp", "#{..%s}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            // target: Property<ObservableList<T>>
            variants("targetObservableListProp", "$%s", ALL_COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableListProp", "$%s", OBSERVABLE_LIST_SOURCES, SOURCE_LIST),
            variants("targetObservableListProp", "$%s", LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableListProp", "$%s", ALL_SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableListProp", "$%s", ALL_MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableListProp", "$..%s", ALL_COLLECTION_SOURCES, SOURCE_LIST),
            variants("targetObservableListProp", "$..%s", ALL_LIST_SOURCES, SOURCE_LIST),
            variants("targetObservableListProp", "$..%s", ALL_SET_SOURCES, SOURCE_LIST),
            variants("targetObservableListProp", "$..%s", ALL_MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObservableListProp", "${%s}", ALL_COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableListProp", "${%s}", values("sourcePropertyOfObservableList"), SOURCE_LIST),
            variants("targetObservableListProp", "${%s}", values("sourceObservableList"), INVALID_UNIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableListProp", "${%s}", LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableListProp", "${%s}", ALL_SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableListProp", "${%s}", ALL_MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableListProp", "#{%s}", values("sourcePropertyOfList"), SOURCE_TYPE_MISMATCH),
            variants("targetObservableListProp", "#{%s}", values("sourcePropertyOfObservableList"), SOURCE_LIST),
            variants("targetObservableListProp", "#{%s}", difference(ALL_LIST_SOURCES, LIST_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableListProp", "#{%s}", difference(ALL_SET_SOURCES, SET_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableListProp", "#{%s}", difference(ALL_MAP_SOURCES, MAP_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableListProp", "#{%s}", SET_PROPERTY_SOURCES, SOURCE_TYPE_MISMATCH),
            variants("targetObservableListProp", "#{%s}", MAP_PROPERTY_SOURCES, SOURCE_TYPE_MISMATCH),
            variants("targetObservableListProp", "${..%s}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("targetObservableListProp", "#{..%s}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET)
        ).flatMap(Arrays::stream);
    }

    @ParameterizedTest
    @MethodSource("params")
    void test(Execution execution) throws Exception {
        runTest(execution);
    }
}

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
public class CollectionBindingTest_targetPropertyOfMap extends CollectionBindingTest {

    static Stream<?> params() {
        return Stream.of(
            // target: Property<Map<T, T>>
            variants("targetObjectMapProp", "$%s", ALL_COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectMapProp", "$%s", ALL_LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectMapProp", "$%s", ALL_SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectMapProp", "$%s", ALL_MAP_SOURCES, SOURCE_MAP),
            variants("targetObjectMapProp", "$..%s", ALL_COLLECTION_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObjectMapProp", "$..%s", ALL_LIST_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObjectMapProp", "$..%s", ALL_SET_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObjectMapProp", "$..%s", ALL_MAP_SOURCES, SOURCE_MAP),
            variants("targetObjectMapProp", "${%s}", ALL_COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectMapProp", "${%s}", MAP_PROPERTY_SOURCES, SOURCE_MAP),
            variants("targetObjectMapProp", "${%s}", difference(ALL_MAP_SOURCES, MAP_PROPERTY_SOURCES), INVALID_UNIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectMapProp", "${%s}", ALL_LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectMapProp", "${%s}", ALL_SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectMapProp", "#{%s}", values("sourcePropertyOfMap"), SOURCE_MAP),
            variants("targetObjectMapProp", "#{%s}", values("sourcePropertyOfObservableMap"), SOURCE_TYPE_MISMATCH),
            variants("targetObjectMapProp", "#{%s}", difference(ALL_LIST_SOURCES, LIST_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectMapProp", "#{%s}", difference(ALL_SET_SOURCES, SET_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectMapProp", "#{%s}", difference(ALL_MAP_SOURCES, MAP_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectMapProp", "#{%s}", LIST_PROPERTY_SOURCES, SOURCE_TYPE_MISMATCH),
            variants("targetObjectMapProp", "#{%s}", SET_PROPERTY_SOURCES, SOURCE_TYPE_MISMATCH),
            variants("targetObjectMapProp", "${..%s}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("targetObjectMapProp", "#{..%s}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            // target: Property<ObservableMap<T, T>>
            variants("targetObservableMapProp", "$%s", ALL_COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableMapProp", "$%s", OBSERVABLE_MAP_SOURCES, SOURCE_MAP),
            variants("targetObservableMapProp", "$%s", MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableMapProp", "$%s", ALL_LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableMapProp", "$%s", ALL_SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableMapProp", "$..%s", ALL_COLLECTION_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObservableMapProp", "$..%s", ALL_LIST_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObservableMapProp", "$..%s", ALL_SET_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObservableMapProp", "$..%s", ALL_MAP_SOURCES, SOURCE_MAP),
            variants("targetObservableMapProp", "${%s}", ALL_COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableMapProp", "${%s}", values("sourcePropertyOfObservableMap"), SOURCE_MAP),
            variants("targetObservableMapProp", "${%s}", values("sourceObservableMap"), INVALID_UNIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableMapProp", "${%s}", MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableMapProp", "${%s}", ALL_LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableMapProp", "${%s}", ALL_SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableMapProp", "#{%s}", values("sourcePropertyOfMap"), SOURCE_TYPE_MISMATCH),
            variants("targetObservableMapProp", "#{%s}", values("sourcePropertyOfObservableMap"), SOURCE_MAP),
            variants("targetObservableMapProp", "#{%s}", difference(ALL_LIST_SOURCES, LIST_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableMapProp", "#{%s}", difference(ALL_SET_SOURCES, SET_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableMapProp", "#{%s}", difference(ALL_MAP_SOURCES, MAP_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableMapProp", "#{%s}", LIST_PROPERTY_SOURCES, SOURCE_TYPE_MISMATCH),
            variants("targetObservableMapProp", "#{%s}", SET_PROPERTY_SOURCES, SOURCE_TYPE_MISMATCH),
            variants("targetObservableMapProp", "${..%s}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("targetObservableMapProp", "#{..%s}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET)
        ).flatMap(Arrays::stream);
    }

    @ParameterizedTest
    @MethodSource("params")
    void test(Execution execution) throws Exception {
        runTest(execution);
    }
}

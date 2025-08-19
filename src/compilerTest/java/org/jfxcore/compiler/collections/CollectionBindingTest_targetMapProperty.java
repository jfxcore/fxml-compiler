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
public class CollectionBindingTest_targetMapProperty extends CollectionBindingTest {

    // target: MapProperty<T, T>
    static Stream<?> params() {
        return Stream.of(
            variants("targetMapProp", "$%s", ALL_COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetMapProp", "$%s", ALL_LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetMapProp", "$%s", ALL_SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetMapProp", "$%s", MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetMapProp", "$%s", OBSERVABLE_MAP_SOURCES, SOURCE_MAP),

            variants("targetMapProp", "$..%s", ALL_COLLECTION_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetMapProp", "$..%s", ALL_LIST_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetMapProp", "$..%s", ALL_SET_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetMapProp", "$..%s", ALL_MAP_SOURCES, SOURCE_MAP),

            variants("targetMapProp", "${%s}", ALL_COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetMapProp", "${%s}", values("sourcePropertyOfObservableMap"), SOURCE_MAP),
            variants("targetMapProp", "${%s}", values("sourceObservableMap"), INVALID_UNIDIRECTIONAL_BINDING_SOURCE),
            variants("targetMapProp", "${%s}", MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetMapProp", "${%s}", ALL_LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetMapProp", "${%s}", ALL_SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),

            variants("targetMapProp", "#{%s}", values("sourcePropertyOfMap"), SOURCE_TYPE_MISMATCH),
            variants("targetMapProp", "#{%s}", values("sourcePropertyOfObservableMap"), SOURCE_MAP),
            variants("targetMapProp", "#{%s}", difference(ALL_MAP_SOURCES, MAP_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetMapProp", "#{%s}", LIST_PROPERTY_SOURCES, SOURCE_TYPE_MISMATCH),
            variants("targetMapProp", "#{%s}", difference(ALL_LIST_SOURCES, LIST_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetMapProp", "#{%s}", SET_PROPERTY_SOURCES, SOURCE_TYPE_MISMATCH),
            variants("targetMapProp", "#{%s}", difference(ALL_SET_SOURCES, SET_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),

            variants("targetMapProp", "${..%s}", values("sourceObservableMap"), SOURCE_MAP),
            variants("targetMapProp", "${..%s}", difference(ALL_SOURCES, "sourceObservableMap"), INVALID_CONTENT_BINDING_SOURCE),

            variants("targetMapProp", "#{..%s}", values("sourceObservableMap"), SOURCE_MAP),
            variants("targetMapProp", "#{..%s}", difference(ALL_SOURCES, "sourceObservableMap"), INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE)
        ).flatMap(Arrays::stream);
    }

    @ParameterizedTest
    @MethodSource("params")
    void test(Execution execution) throws Exception {
        runTest(execution);
    }
}

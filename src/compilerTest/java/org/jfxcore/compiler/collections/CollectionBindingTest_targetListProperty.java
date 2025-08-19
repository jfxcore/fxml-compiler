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
public class CollectionBindingTest_targetListProperty extends CollectionBindingTest {

    // target: ListProperty<T>
    static Stream<?> params() {
        return Stream.of(
            variants("targetListProp", "$%s", ALL_COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetListProp", "$%s", LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetListProp", "$%s", OBSERVABLE_LIST_SOURCES, SOURCE_LIST),
            variants("targetListProp", "$%s", ALL_SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetListProp", "$%s", ALL_MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),

            variants("targetListProp", "$..%s", ALL_COLLECTION_SOURCES, SOURCE_LIST),
            variants("targetListProp", "$..%s", ALL_LIST_SOURCES, SOURCE_LIST),
            variants("targetListProp", "$..%s", ALL_SET_SOURCES, SOURCE_LIST),
            variants("targetListProp", "$..%s", ALL_MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),

            variants("targetListProp", "${%s}", ALL_COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetListProp", "${%s}", values("sourcePropertyOfObservableList"), SOURCE_LIST),
            variants("targetListProp", "${%s}", values("sourceObservableList"), INVALID_UNIDIRECTIONAL_BINDING_SOURCE),
            variants("targetListProp", "${%s}", LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetListProp", "${%s}", ALL_SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetListProp", "${%s}", ALL_MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),

            variants("targetListProp", "#{%s}", values("sourcePropertyOfList"), SOURCE_TYPE_MISMATCH),
            variants("targetListProp", "#{%s}", values("sourcePropertyOfObservableList"), SOURCE_LIST),
            variants("targetListProp", "#{%s}", difference(ALL_LIST_SOURCES, LIST_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetListProp", "#{%s}", SET_PROPERTY_SOURCES, SOURCE_TYPE_MISMATCH),
            variants("targetListProp", "#{%s}", difference(ALL_SET_SOURCES, SET_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetListProp", "#{%s}", MAP_PROPERTY_SOURCES, SOURCE_TYPE_MISMATCH),
            variants("targetListProp", "#{%s}", difference(ALL_MAP_SOURCES, MAP_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),

            variants("targetListProp", "${..%s}", values("sourceObservableList"), SOURCE_LIST),
            variants("targetListProp", "${..%s}", difference(ALL_SOURCES, "sourceObservableList"), INVALID_CONTENT_BINDING_SOURCE),

            variants("targetListProp", "#{..%s}", values("sourceObservableList"), SOURCE_LIST),
            variants("targetListProp", "#{..%s}", difference(ALL_SOURCES, "sourceObservableList"), INVALID_BIDIRECTIONAL_CONTENT_BINDING_SOURCE)
        ).flatMap(Arrays::stream);
    }

    @ParameterizedTest
    @MethodSource("params")
    void test(Execution execution) throws Exception {
        runTest(execution);
    }
}

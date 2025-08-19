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
public class CollectionBindingTest_targetPropertyOfSet extends CollectionBindingTest {

    static Stream<?> params() {
        return Stream.of(
            // target: Property<Set<T>>
            variants("targetObjectSetProp", "$%s", ALL_COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectSetProp", "$%s", ALL_LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectSetProp", "$%s", ALL_SET_SOURCES, SOURCE_SET),
            variants("targetObjectSetProp", "$%s", ALL_MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectSetProp", "$..%s", ALL_COLLECTION_SOURCES, SOURCE_SET),
            variants("targetObjectSetProp", "$..%s", ALL_LIST_SOURCES, SOURCE_SET),
            variants("targetObjectSetProp", "$..%s", ALL_SET_SOURCES, SOURCE_SET),
            variants("targetObjectSetProp", "$..%s", ALL_MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObjectSetProp", "${%s}", ALL_COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectSetProp", "${%s}", SET_PROPERTY_SOURCES, SOURCE_SET),
            variants("targetObjectSetProp", "${%s}", difference(ALL_SET_SOURCES, SET_PROPERTY_SOURCES), INVALID_UNIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectSetProp", "${%s}", ALL_LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectSetProp", "${%s}", ALL_MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObjectSetProp", "#{%s}", values("sourcePropertyOfSet"), SOURCE_SET),
            variants("targetObjectSetProp", "#{%s}", values("sourcePropertyOfObservableSet"), SOURCE_TYPE_MISMATCH),
            variants("targetObjectSetProp", "#{%s}", difference(ALL_LIST_SOURCES, LIST_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectSetProp", "#{%s}", difference(ALL_SET_SOURCES, SET_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectSetProp", "#{%s}", difference(ALL_MAP_SOURCES, MAP_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObjectSetProp", "#{%s}", LIST_PROPERTY_SOURCES, SOURCE_TYPE_MISMATCH),
            variants("targetObjectSetProp", "#{%s}", MAP_PROPERTY_SOURCES, SOURCE_TYPE_MISMATCH),
            variants("targetObjectSetProp", "${..%s}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("targetObjectSetProp", "#{..%s}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET),

            // target: Property<ObservableSet<T>>
            variants("targetObservableSetProp", "$%s", ALL_COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableSetProp", "$%s", OBSERVABLE_SET_SOURCES, SOURCE_SET),
            variants("targetObservableSetProp", "$%s", SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableSetProp", "$%s", ALL_LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableSetProp", "$%s", ALL_MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableSetProp", "$..%s", ALL_COLLECTION_SOURCES, SOURCE_SET),
            variants("targetObservableSetProp", "$..%s", ALL_LIST_SOURCES, SOURCE_SET),
            variants("targetObservableSetProp", "$..%s", ALL_SET_SOURCES, SOURCE_SET),
            variants("targetObservableSetProp", "$..%s", ALL_MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetObservableSetProp", "${%s}", ALL_COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableSetProp", "${%s}", values("sourcePropertyOfObservableSet"), SOURCE_SET),
            variants("targetObservableSetProp", "${%s}", values("sourceObservableSet"), INVALID_UNIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableSetProp", "${%s}", SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableSetProp", "${%s}", ALL_LIST_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableSetProp", "${%s}", ALL_MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetObservableSetProp", "#{%s}", values("sourcePropertyOfSet"), SOURCE_TYPE_MISMATCH),
            variants("targetObservableSetProp", "#{%s}", values("sourcePropertyOfObservableSet"), SOURCE_SET),
            variants("targetObservableSetProp", "#{%s}", difference(ALL_LIST_SOURCES, LIST_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableSetProp", "#{%s}", difference(ALL_SET_SOURCES, SET_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableSetProp", "#{%s}", difference(ALL_MAP_SOURCES, MAP_PROPERTY_SOURCES), INVALID_BIDIRECTIONAL_BINDING_SOURCE),
            variants("targetObservableSetProp", "#{%s}", LIST_PROPERTY_SOURCES, SOURCE_TYPE_MISMATCH),
            variants("targetObservableSetProp", "#{%s}", MAP_PROPERTY_SOURCES, SOURCE_TYPE_MISMATCH),
            variants("targetObservableSetProp", "${..%s}", ALL_SOURCES, INVALID_CONTENT_BINDING_TARGET),
            variants("targetObservableSetProp", "#{..%s}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET)
        ).flatMap(Arrays::stream);
    }

    @ParameterizedTest
    @MethodSource("params")
    void test(Execution execution) throws Exception {
        runTest(execution);
    }
}

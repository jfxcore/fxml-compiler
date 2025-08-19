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
public class CollectionBindingTest_targetList extends CollectionBindingTest {

    // target: List<T> (writable)
    static Stream<?> params() {
        return Stream.of(
            variants("targetList", "$%s", ALL_COLLECTION_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetList", "$%s", ALL_LIST_SOURCES, SOURCE_LIST),
            variants("targetList", "$%s", ALL_SET_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetList", "$%s", ALL_MAP_SOURCES, CANNOT_CONVERT_SOURCE_TYPE),
            variants("targetList", "$..%s", ALL_COLLECTION_SOURCES, SOURCE_LIST),
            variants("targetList", "$..%s", ALL_LIST_SOURCES, SOURCE_LIST),
            variants("targetList", "$..%s", ALL_SET_SOURCES, SOURCE_LIST),
            variants("targetList", "$..%s", ALL_MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("targetList", "${%s}", ALL_SOURCES, INVALID_BINDING_TARGET),
            variants("targetList", "#{%s}", ALL_SOURCES, INVALID_BINDING_TARGET),
            variants("targetList", "${..%s}", ALL_COLLECTION_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("targetList", "${..%s}", values("sourceObservableList"), SOURCE_LIST),
            variants("targetList", "${..%s}", difference(ALL_LIST_SOURCES, "sourceObservableList"), INVALID_CONTENT_BINDING_SOURCE),
            variants("targetList", "${..%s}", ALL_SET_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("targetList", "${..%s}", ALL_MAP_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("targetList", "#{..%s}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET)
        ).flatMap(Arrays::stream);
    }

    @ParameterizedTest
    @MethodSource("params")
    void test(Execution execution) throws Exception {
        runTest(execution);
    }
}

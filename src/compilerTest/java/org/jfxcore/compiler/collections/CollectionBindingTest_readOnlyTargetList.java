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
public class CollectionBindingTest_readOnlyTargetList extends CollectionBindingTest {

    // target: List<T> (read-only)
    static Stream<?> params() {
        return Stream.of(
            variants("readOnlyTargetList", "$%s", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetList", "$..%s", ALL_COLLECTION_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetList", "$..%s", ALL_LIST_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetList", "$..%s", ALL_SET_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetList", "$..%s", ALL_MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetList", "${%s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetList", "#{%s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetList", "${..%s}", ALL_COLLECTION_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetList", "${..%s}", values("sourceObservableList"), SOURCE_LIST),
            variants("readOnlyTargetList", "${..%s}", difference(ALL_LIST_SOURCES, "sourceObservableList"), INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetList", "${..%s}", ALL_SET_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetList", "${..%s}", ALL_MAP_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetList", "#{..%s}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET)
        ).flatMap(Arrays::stream);
    }

    @ParameterizedTest
    @MethodSource("params")
    void test(Execution execution) throws Exception {
        runTest(execution);
    }
}

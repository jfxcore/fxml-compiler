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
public class CollectionBindingTest_readOnlyTargetSet extends CollectionBindingTest {

    // target: Set<T> (read-only)
    static Stream<?> params() {
        return Stream.of(
            variants("readOnlyTargetSet", "$%s", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetSet", "$..%s", ALL_COLLECTION_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetSet", "$..%s", ALL_LIST_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetSet", "$..%s", ALL_SET_SOURCES, SOURCE_LIST),
            variants("readOnlyTargetSet", "$..%s", ALL_MAP_SOURCES, INVALID_CONTENT_ASSIGNMENT_SOURCE),
            variants("readOnlyTargetSet", "${%s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetSet", "#{%s}", ALL_SOURCES, CANNOT_MODIFY_READONLY_PROPERTY),
            variants("readOnlyTargetSet", "${..%s}", ALL_COLLECTION_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetSet", "${..%s}", ALL_LIST_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetSet", "${..%s}", values("sourceObservableSet"), SOURCE_SET),
            variants("readOnlyTargetSet", "${..%s}", difference(ALL_SET_SOURCES, "sourceObservableSet"), INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetSet", "${..%s}", ALL_MAP_SOURCES, INVALID_CONTENT_BINDING_SOURCE),
            variants("readOnlyTargetSet", "#{..%s}", ALL_SOURCES, INVALID_BIDIRECTIONAL_CONTENT_BINDING_TARGET)
        ).flatMap(Arrays::stream);
    }

    @ParameterizedTest
    @MethodSource("params")
    void test(Execution execution) throws Exception {
        runTest(execution);
    }
}

// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.generate.HeadSegmentGenerator;
import org.jfxcore.compiler.generate.TailSegmentGenerator;
import org.jfxcore.compiler.generate.IntermediateSegmentGenerator;
import org.jfxcore.compiler.generate.Generator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Represents a property path in which non-observable segments (fields or getters) are folded into
 * observable segments. Each of the resulting folded groups correspond to one generated class.
 *
 * <p>For example, given the following path: foo.bar.baz.qux.quux.quuz
 * <p>where foo, qux are observable segments, the folded path will be [foo.bar.baz.qux].[qux.quux.quuz]
 *
 * <p>Note that the first group ends with the first segment of the next group.
 */
public class FoldedPath {

    private final SourceInfo sourceInfo;
    private final FoldedGroup[] groups;

    public FoldedPath(SourceInfo sourceInfo, Collection<FoldedGroup> groups) {
        this.sourceInfo = sourceInfo;
        this.groups = groups.toArray(new FoldedGroup[0]);
    }

    public FoldedGroup[] getGroups() {
        return groups;
    }

    public List<Generator> toGenerators() {
        List<Generator> list = new ArrayList<>();

        if (groups.length == 0) {
            throw new IllegalArgumentException();
        } else if (groups.length == 1) {
            list.add(new TailSegmentGenerator(sourceInfo, groups));
        } else {
            list.add(new HeadSegmentGenerator(sourceInfo, groups));

            for (int i = 1; i < groups.length - 1; ++i) {
                list.add(new IntermediateSegmentGenerator(sourceInfo, groups, i));
            }

            list.add(new TailSegmentGenerator(sourceInfo, groups));
        }

        return list;
    }

}

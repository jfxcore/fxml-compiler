// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.intrinsic;

public class Usage {

    public static final Usage ELEMENT = new Usage(true, false, false);
    public static final Usage ATTRIBUTE = new Usage(false, true, true);
    public static final Usage ROOT_ATTRIBUTE = new Usage(false, true, false);
    public static final Usage CHILD_ATTRIBUTE = new Usage(false, false, true);

    private final boolean element;
    private final boolean rootAttribute;
    private final boolean childAttribute;

    public Usage(boolean element, boolean rootAttribute, boolean childAttribute) {
        this.element = element;
        this.rootAttribute = rootAttribute;
        this.childAttribute = childAttribute;
    }

    /**
     * Indicates whether the intrinsic can be used as an element.
     */
    public boolean isElement() {
        return element;
    }

    /**
     * Indicates whether the intrinsic can be used as an attribute.
     */
    public boolean isAttribute() {
        return rootAttribute || childAttribute;
    }

    /**
     * Indicates whether the intrinsic can only be used as an attribute of the root element.
     */
    public boolean isRootAttribute() {
        return rootAttribute;
    }

    /**
     * Indicates whether the intrinsic can only be used as an attribute of child elements of the root element.
     */
    public boolean isChildAttribute() {
        return childAttribute;
    }

}

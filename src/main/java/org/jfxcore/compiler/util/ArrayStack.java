// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import java.util.ArrayList;
import java.util.Collection;

public class ArrayStack<E> extends ArrayList<E> {

    public ArrayStack() {}

    public ArrayStack(int capacity) {
        super(capacity);
    }

    public ArrayStack(Collection<? extends E> c) {
        super(c);
    }

    public void push(E item) {
        add(item);
    }

    public E pop() {
        return remove(size() - 1);
    }

    public E peek() {
        if (isEmpty()) {
            return null;
        }

        return get(size() - 1);
    }

}

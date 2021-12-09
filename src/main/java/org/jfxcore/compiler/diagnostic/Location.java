// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic;

/**
 * Identifies a location in a document.
 */
public class Location {

    private final int line;
    private final int column;

    public Location(int line, int column) {
        this.line = line;
        this.column = column;
    }

    /**
     * The zero-based line number of this location.
     */
    public int getLine() {
        return line;
    }

    /**
     * The zero-based column number of this location.
     */
    public int getColumn() {
        return column;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Location)) {
            return false;
        }

        Location location = (Location)o;
        return line == location.line && column == location.column;
    }

    @Override
    public int hashCode() {
        int result = line;
        result = 31 * result + column;
        return result;
    }

    @Override
    public String toString() {
        return line + ":" + column;
    }

}

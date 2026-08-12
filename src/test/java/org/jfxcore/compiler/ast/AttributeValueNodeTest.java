// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AttributeValueNodeTest {

    @Test
    public void Literal_Form_Rejects_An_Incompatible_Visitor_Replacement() {
        SourceInfo sourceInfo = SourceInfo.none();
        AttributeValueNode value = AttributeValueNode.literal(
            new LiteralValueNode("text", sourceInfo), sourceInfo);

        MarkupException exception = assertThrows(MarkupException.class, () ->
            Visitor.visit(value, new Visitor() {
                @Override
                protected Node onVisited(Node node) {
                    return node instanceof LiteralValueNode
                        ? new IdentifierNode("replacement", sourceInfo)
                        : node;
                }
            }));

        assertEquals(ErrorCode.UNEXPECTED_EXPRESSION, exception.getDiagnostic().getCode());
    }

    @Test
    public void Sequence_Payload_Cannot_Be_Mutated_Outside_A_Visitor() {
        SourceInfo sourceInfo = SourceInfo.none();
        AttributeValueNode value = AttributeValueNode.sequence(
            List.of(new LiteralValueNode("text", sourceInfo)), sourceInfo);

        assertThrows(UnsupportedOperationException.class, () ->
            value.getItems().add(new LiteralValueNode("other", sourceInfo)));
    }
}

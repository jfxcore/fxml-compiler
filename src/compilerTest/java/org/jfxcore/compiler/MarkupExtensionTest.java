// Copyright (c) 2021, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class MarkupExtensionTest extends CompilerTestBase {

    @Test
    public void Instantiation_With_MarkupExtension_Syntax() {
        GridPane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <fx:define>
                    <Background fx:id="a" fills="{BackgroundFill fill=#ff0000; radii={fx:null}; insets=1,2,3.5,.4}"/>
                    <Background fx:id="b" fills="{BackgroundFill fill=red; radii=EMPTY; insets=1,2,3.5,.4}"/>
                    <Background fx:id="c" fills="{BackgroundFill fill=RED; radii=$CornerRadii.EMPTY; insets=1,2,3.5,.4}"/>
                </fx:define>
            </GridPane>
        """);

        class Test {
            static void test(Background background) {
                BackgroundFill fill = background.getFills().get(0);
                assertEquals(Color.RED, fill.getFill());
                assertEquals(CornerRadii.EMPTY, fill.getRadii());
                assertEquals(1, fill.getInsets().getTop(), 0.001);
                assertEquals(2, fill.getInsets().getRight(), 0.001);
                assertEquals(3.5, fill.getInsets().getBottom(), 0.001);
                assertEquals(.4, fill.getInsets().getLeft(), 0.001);
            }
        }

        Test.test((Background)root.getProperties().get("a"));
        Test.test((Background)root.getProperties().get("b"));
        Test.test((Background)root.getProperties().get("c"));
    }

    @Test
    public void Missing_CloseCurly_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                   text="{fx:foo bar"/>
        """));

        assertEquals(ErrorCode.EXPECTED_TOKEN, ex.getDiagnostic().getCode());
        assertTrue(ex.getDiagnostic().getMessage().contains("}"));
        assertCodeHighlight("\"", ex);
    }

}

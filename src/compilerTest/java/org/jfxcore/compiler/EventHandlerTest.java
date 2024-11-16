// Copyright (c) 2022, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
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
public class EventHandlerTest extends CompilerTestBase {

    @SuppressWarnings("unused")
    public static class TestPane extends Pane {
        boolean flag;

        // this is not invoked
        public void actionHandler() {}

        public void actionHandler(ActionEvent event) {
            flag = true;
        }

        public void mouseHandler(MouseEvent event) {}

        public void parameterlessHandler() {
            flag = true;
        }

        public EventHandler<ActionEvent> actionHandlerProp = new EventHandler<>() {
            @Override
            public void handle(ActionEvent event) {
                flag = true;
            }
        };

        private void inaccessibleHandler() {}

        void packagePrivateHandler() {
            flag = true;
        }
    }

    @Test
    public void EventHandler_Fails_If_Method_Is_Not_Found() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Button onAction="#actionHandlerNotFound"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
        assertCodeHighlight("#actionHandlerNotFound", ex);
    }

    @Test
    public void EventHandler_Fails_With_Incompatible_EventType() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Button onAction="#mouseHandler"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.UNSUITABLE_EVENT_HANDLER, ex.getDiagnostic().getCode());
        assertCodeHighlight("#mouseHandler", ex);
    }

    @Test
    public void EventHandler_Method_Is_Invoked() {
        TestPane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Button onAction="#actionHandler"/>
            </TestPane>
        """);

        Button button = (Button)root.getChildren().get(0);
        button.getOnAction().handle(null);
        assertTrue(root.flag);
    }

    @Test
    public void Parameterless_EventHandler_Method_Is_Invoked() {
        TestPane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Button onAction="#parameterlessHandler"/>
            </TestPane>
        """);

        Button button = (Button)root.getChildren().get(0);
        button.getOnAction().handle(null);
        assertTrue(root.flag);
    }

    @Test
    public void EventHandler_Property_Is_Invoked() {
        TestPane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Button onAction="$actionHandlerProp"/>
            </TestPane>
        """);

        Button button = (Button)root.getChildren().get(0);
        button.getOnAction().handle(null);
        assertTrue(root.flag);
    }

    @Test
    public void EventHandler_Bound_Property_Is_Invoked() {
        TestPane root = compileAndRun("""
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Button onAction="${actionHandlerProp}"/>
            </TestPane>
        """);

        Button button = (Button)root.getChildren().get(0);
        button.getOnAction().handle(null);
        assertTrue(root.flag);
    }

    @Test
    public void Private_EventHandler_Is_Not_Accessible() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Button onAction="#inaccessibleHandler"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void PackagePrivate_EventHandler_Is_Not_Accessible() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.control.*?>
            <TestPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                <Button onAction="#packagePrivateHandler"/>
            </TestPane>
        """));

        assertEquals(ErrorCode.MEMBER_NOT_ACCESSIBLE, ex.getDiagnostic().getCode());
        assertCodeHighlight("#packagePrivateHandler", ex);
    }

}

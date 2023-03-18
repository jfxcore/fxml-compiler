// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.Pane;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"unused", "HttpUrlsUsage", "DuplicatedCode"})
@ExtendWith(TestExtension.class)
@Disabled
public class TemplatesTest extends CompilerTestBase {

    @Test
    public void Unnamed_Template_In_Resources_Has_Class_Key() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <?import javafx.scene.control.template.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <fx:define>
                    <Template fx:typeArguments="java.lang.Double">
                        <VBox/>
                    </Template>
                </fx:define>
            </Pane>
        """);

        Map.Entry<Object, Object> entry = root.getProperties().entrySet().iterator().next();
        assertEquals(Double.class, entry.getKey());
    }

    @Test
    @SuppressWarnings("unused")
    public void This_Context_Can_Be_Used_As_Path() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <?import javafx.scene.control.template.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <ListView fx:typeArguments="java.lang.Double">
                    <cellFactory>
                        <TemplatedListCellFactory fx:typeArguments="java.lang.Double">
                            <Template fx:typeArguments="java.lang.Double">
                                <Label text="{fx:once this.toString}"/>
                            </Template>
                        </TemplatedListCellFactory>
                    </cellFactory>
                    <java.lang.Double>123.0</java.lang.Double>
                </ListView>
            </Pane>
        """);

        Scene scene = new Scene(root);
        root.applyCss();
        root.layout();
        Parent listView = (Parent)root.getChildren().get(0);
        Parent virtualFlow = (Parent)listView.getChildrenUnmodifiable().get(0);
        Parent clippedContainer = (Parent)virtualFlow.getChildrenUnmodifiable().get(0);
        Parent group = (Parent)clippedContainer.getChildrenUnmodifiable().get(0);
        Parent listViewSkin = (Parent)group.getChildrenUnmodifiable().get(0);
        Label label = (Label)listViewSkin.getChildrenUnmodifiable().get(0);
        assertEquals("123.0", label.getText());
    }

    @Test
    @SuppressWarnings("unused")
    public void This_Context_Can_Be_Used_In_Function_Expression() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <?import javafx.scene.control.template.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <ListView fx:typeArguments="java.lang.Double">
                    <cellFactory>
                        <TemplatedListCellFactory fx:typeArguments="java.lang.Double">
                            <Template fx:typeArguments="java.lang.Double">
                                <Label text="{fx:once java.lang.String.format('%s', this)}"/>
                            </Template>
                        </TemplatedListCellFactory>
                    </cellFactory>
                    <java.lang.Double>123.0</java.lang.Double>
                </ListView>
            </Pane>
        """);

        Scene scene = new Scene(root);
        root.applyCss();
        root.layout();
        Parent listView = (Parent)root.getChildren().get(0);
        Parent virtualFlow = (Parent)listView.getChildrenUnmodifiable().get(0);
        Parent clippedContainer = (Parent)virtualFlow.getChildrenUnmodifiable().get(0);
        Parent group = (Parent)clippedContainer.getChildrenUnmodifiable().get(0);
        Parent listViewSkin = (Parent)group.getChildrenUnmodifiable().get(0);
        Label label = (Label)listViewSkin.getChildrenUnmodifiable().get(0);
        assertTrue(label.getText().startsWith("TemplatesTest_This_Context_Can_Be_Used_In_Function_Expression"));
    }

    @Test
    @SuppressWarnings({"unchecked", "unused"})
    public void Nested_Template_Works_Correctly() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <?import javafx.scene.control.template.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <fx:define>
                    <TemplatedListCellFactory fx:id="templ" fx:typeArguments="String">
                        <Template fx:typeArguments="java.lang.String">
                            <ListView fx:typeArguments="java.lang.String">
                                <cellFactory>
                                    <TemplatedListCellFactory fx:typeArguments="String">
                                        <Template fx:typeArguments="String">
                                            <Label text="{fx:once this}"/>
                                        </Template>
                                    </TemplatedListCellFactory>
                                </cellFactory>
                            </ListView>
                        </Template>
                    </TemplatedListCellFactory>
                </fx:define>
                <ListView cellFactory="{fx:once templ}" />
            </Pane>
        """);

        Scene scene = new Scene(root);
        root.applyCss();
        root.layout();

        ListView<String> outer = ((ListView<String>)root.getChildren().get(0));
        Parent virtualFlow = (Parent)outer.getChildrenUnmodifiable().get(0);
        Parent clippedContainer = (Parent)virtualFlow.getChildrenUnmodifiable().get(0);
        Parent group = (Parent)clippedContainer.getChildrenUnmodifiable().get(0);
        assertEquals(0, group.getChildrenUnmodifiable().size());

        outer.getItems().add("foo");
        root.layout();

        Parent listViewSkin = (Parent)group.getChildrenUnmodifiable().get(0);
        assertEquals(1, listViewSkin.getChildrenUnmodifiable().size());

        ListView<String> inner = (ListView<String>)listViewSkin.getChildrenUnmodifiable().get(0);
        inner.getItems().add("bar");
        root.layout();

        virtualFlow = (Parent)inner.getChildrenUnmodifiable().get(0);
        clippedContainer = (Parent)virtualFlow.getChildrenUnmodifiable().get(0);
        group = (Parent)clippedContainer.getChildrenUnmodifiable().get(0);
        listViewSkin = (Parent)group.getChildrenUnmodifiable().get(0);
        Label label = (Label)listViewSkin.getChildrenUnmodifiable().get(0);
        assertEquals("bar", label.getText());
    }

    @Test
    public void Template_As_TemplateContent_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <?import javafx.scene.control.template.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <fx:define>
                    <Template fx:typeArguments="java.lang.String" fx:id="templ">
                        <Template fx:typeArguments="java.lang.String">
                            <ScrollPane fx:id="pane"/>
                        </Template>
                    </Template>
                </fx:define>
            </Pane>
        """));

        assertEquals(ErrorCode.INCOMPATIBLE_PROPERTY_TYPE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Cannot_Set_FxId_On_Template_Root_Node() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <?import javafx.scene.control.template.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <fx:define>
                    <Template fx:typeArguments="java.lang.String">
                        <ScrollPane fx:id="pane"/>
                    </Template>
                </fx:define>
            </Pane>
        """));

        assertEquals(ErrorCode.UNEXPECTED_INTRINSIC, ex.getDiagnostic().getCode());
    }

    @Test
    public void ParentScope_Cannot_Select_Beyond_Template_By_Index() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <?import javafx.scene.control.template.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <fx:define>
                    <Template fx:typeArguments="java.lang.String">
                        <ScrollPane>
                            <Button prefWidth="{fx:bind parent[2]/prefHeight}"/>
                        </ScrollPane>
                    </Template>
                </fx:define>
            </Pane>
        """));

        assertEquals(ErrorCode.PARENT_INDEX_OUT_OF_BOUNDS, ex.getDiagnostic().getCode());
    }

    @Test
    public void ParentScope_Cannot_Select_Beyond_Template_By_Type() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <?import javafx.scene.control.template.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <fx:define>
                    <Template fx:typeArguments="java.lang.String">
                        <ScrollPane>
                            <Button prefWidth="{fx:bind parent[Pane]/prefHeight}"/>
                        </ScrollPane>
                    </Template>
                </fx:define>
            </Pane>
        """));

        assertEquals(ErrorCode.PARENT_TYPE_NOT_FOUND, ex.getDiagnostic().getCode());
    }

    public static final class FinalLabel extends Label {}

    @Test
    public void Root_Class_Cannot_Be_Final() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.template.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <fx:define>
                    <Template fx:typeArguments="java.lang.String">
                        <FinalLabel/>
                    </Template>
                </fx:define>
            </Pane>
        """));

        assertEquals(ErrorCode.ROOT_CLASS_CANNOT_BE_FINAL, ex.getDiagnostic().getCode());
    }

    @SuppressWarnings("unused")
    public static class DerivedListView<T> extends ListView<T> {}

    @Test
    @SuppressWarnings({"unchecked", "unused"})
    public void DerivedListView_Works_Correctly() {
        Pane root = compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <?import javafx.scene.control.template.*?>
            <?import org.jfxcore.compiler.TemplatesTest.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <DerivedListView fx:typeArguments="java.lang.String">
                    <cellFactory>
                        <TemplatedListCellFactory fx:typeArguments="String">
                            <Template fx:typeArguments="java.lang.String">
                                <Label text="{fx:once this}"/>
                            </Template>
                        </TemplatedListCellFactory>
                    </cellFactory>
                    <String>Item 1</String>
                </DerivedListView>
            </Pane>
        """);

        Scene scene = new Scene(root);
        root.applyCss();
        root.layout();

        DerivedListView<String> outer = ((DerivedListView<String>)root.getChildren().get(0));
        Parent virtualFlow = (Parent)outer.getChildrenUnmodifiable().get(0);
        Parent clippedContainer = (Parent)virtualFlow.getChildrenUnmodifiable().get(0);
        Parent group = (Parent)clippedContainer.getChildrenUnmodifiable().get(0);
        Parent listViewSkin = (Parent)group.getChildrenUnmodifiable().get(0);
        Label label = (Label)listViewSkin.getChildrenUnmodifiable().get(0);
        assertEquals("Item 1", label.getText());
    }

    @Test
    public void TemplatedItem_Cannot_Be_Bound_Bidirectionally() {
        MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
            <?import javafx.scene.layout.*?>
            <?import javafx.scene.control.*?>
            <?import javafx.scene.control.template.*?>
            <Pane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                <ListView fx:typeArguments="java.lang.String">
                    <cellFactory>
                        <TemplatedListCellFactory fx:typeArguments="java.lang.String">
                            <Template fx:typeArguments="java.lang.String">
                                <Label text="{fx:bindBidirectional this}"/>
                            </Template>
                        </TemplatedListCellFactory>
                    </cellFactory>
                    <String>Item 1</String>
                </ListView>
            </Pane>
        """));

        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
    }

}

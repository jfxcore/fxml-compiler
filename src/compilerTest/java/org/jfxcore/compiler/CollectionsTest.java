// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilerTestBase;
import org.jfxcore.compiler.util.TestExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jfxcore.compiler.util.MoreAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
@ExtendWith(TestExtension.class)
public class CollectionsTest {

    @Nested
    public class InstantiationTest extends CompilerTestBase {
        @Test
        public void Objects_Are_Added_To_List() {
            GridPane root = compileAndRun("""
                <?import java.util.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <properties>
                        <ArrayList fx:typeArguments="Object" fx:id="list">
                            <String fx:id="str0">foo</String>
                            <Double fx:id="val0" fx:value="123.5"/>
                            <String>baz</String>
                            <GridPane fx:id="pane"/>
                        </ArrayList>
                    </properties>
                </GridPane>
            """);

            List<?> list = (List<?>)root.getProperties().get("list");
            assertEquals(4, list.size());
            assertEquals("foo", list.get(0));
            assertEquals(123.5, list.get(1));
            assertEquals("baz", list.get(2));
            assertTrue(list.get(3) instanceof GridPane);
        }

        @Test
        public void Objects_Are_Added_To_Incompatible_List_ItemType_Fails() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import java.util.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <properties>
                        <ArrayList fx:typeArguments="Integer" fx:id="list">
                            <String>foo</String>
                        </ArrayList>
                    </properties>
                </GridPane>
            """));

            assertEquals(ErrorCode.CANNOT_ADD_ITEM_INCOMPATIBLE_TYPE, ex.getDiagnostic().getCode());
            assertCodeHighlight("<String>foo</String>", ex);
        }

        @Test
        public void Objects_Are_Added_To_Set() {
            GridPane root = compileAndRun("""
                <?import java.util.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <properties>
                        <HashSet fx:typeArguments="Object" fx:id="set">
                            <String fx:id="str0">foo</String>
                            <Double fx:id="val0" fx:value="123.5"/>
                            <String>baz</String>
                        </HashSet>
                    </properties>
                </GridPane>
            """);

            Set<?> set = (Set<?>)root.getProperties().get("set");
            assertEquals(3, set.size());
            assertTrue(set.contains("foo"));
            assertTrue(set.contains(123.5));
            assertTrue(set.contains("baz"));
        }

        @Test
        public void Objects_Are_Added_To_Incompatible_Set_ItemType_Fails() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import java.util.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <properties>
                        <HashSet fx:typeArguments="Integer" fx:id="list">
                            <String>foo</String>
                        </HashSet>
                    </properties>
                </GridPane>
            """));

            assertEquals(ErrorCode.CANNOT_ADD_ITEM_INCOMPATIBLE_TYPE, ex.getDiagnostic().getCode());
            assertCodeHighlight("<String>foo</String>", ex);
        }

        @Test
        public void Objects_Are_Added_To_Map() {
            GridPane root = compileAndRun("""
                <?import java.util.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <properties>
                        <HashMap fx:id="map0">
                            <String>foo</String>
                            <Double fx:value="123.5"/>
                        </HashMap>
                    
                        <HashMap fx:typeArguments="String,Object" fx:id="map1">
                            <String fx:id="str0">foo</String>
                            <Double fx:id="val0" fx:value="123.5"/>
                            <String>baz</String>
                        </HashMap>
                    </properties>
                </GridPane>
            """);

            //noinspection unchecked
            Map<Object, Object> map0 = (Map<Object, Object>)root.getProperties().get("map0");
            assertEquals(2, map0.size());
            assertTrue(map0.containsValue("foo"));
            assertTrue(map0.containsValue(123.5));

            //noinspection unchecked
            Map<Object, Object> map1 = (Map<Object, Object>)root.getProperties().get("map1");
            assertEquals("foo", map1.get("str0"));
            assertEquals(123.5, (Double)map1.get("val0"), 0.001);

            //noinspection OptionalGetWithoutIsPresent
            Map.Entry<Object, Object> entry = map1.entrySet().stream()
                .filter(o -> o.getValue() instanceof String && o.getValue().equals("baz")).findFirst().get();
            assertTrue(entry.getKey() instanceof String);
        }

        @Test
        public void Object_Is_Added_To_Incompatible_Map_KeyType_Fails() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import java.util.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <properties>
                        <HashMap fx:typeArguments="Integer,String">
                            <String>foo</String>
                        </HashMap>
                    </properties>
                </GridPane>
            """));

            assertEquals(ErrorCode.UNSUPPORTED_MAP_KEY_TYPE, ex.getDiagnostic().getCode());
            assertCodeHighlight("""
                <HashMap fx:typeArguments="Integer,String">
            """.trim(), ex);
        }

        @Test
        public void Object_Is_Added_To_Incompatible_Map_ValueType_Fails() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import java.util.*?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <properties>
                        <HashMap fx:typeArguments="String,Integer">
                            <String>foo</String>
                        </HashMap>
                    </properties>
                </GridPane>
            """));

            assertEquals(ErrorCode.CANNOT_ADD_ITEM_INCOMPATIBLE_TYPE, ex.getDiagnostic().getCode());
            assertCodeHighlight("<String>foo</String>", ex);
        }
    }

    @Nested
    public class PropertyAssignmentTest extends CompilerTestBase {
        @Test
        public void Literal_Is_Added_To_MapProperty_Fails() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <properties>
                        Hello123
                    </properties>
                </GridPane>
            """));

            assertEquals(ErrorCode.CANNOT_ADD_ITEM_INCOMPATIBLE_VALUE, ex.getDiagnostic().getCode());
        }

        @SuppressWarnings("unused")
        public static class UnsupportedKeyTestPane extends Pane {
            private final Map<Double, Object> unsupportedKeyMap = new HashMap<>();

            @SuppressWarnings("unused")
            public Map<Double, Object> getUnsupportedKeyMap() {
                return unsupportedKeyMap;
            }
        }

        @Test
        public void Object_Is_Added_To_Unsupported_MapProperty_Fails() {
            MarkupException ex = assertThrows(MarkupException.class, () -> compileAndRun("""
                <?import javafx.scene.layout.*?>
                <UnsupportedKeyTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <unsupportedKeyMap>
                        <String>foo</String>
                    </unsupportedKeyMap>
                </UnsupportedKeyTestPane>
            """));

            assertEquals(ErrorCode.UNSUPPORTED_MAP_KEY_TYPE, ex.getDiagnostic().getCode());
            assertCodeHighlight("<String>foo</String>", ex);
        }

        @Test
        public void Objects_Are_Added_To_MapProperty() {
            GridPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <?import javafx.scene.paint.*?>
                <GridPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <properties>
                        <String fx:id="str0">foo</String>
                        <Double fx:id="val0" fx:value="123.5"/>
                        <String>baz</String>
                        <Color fx:id="col0" fx:constant="RED"/>
                        <GridPane fx:id="pane"/>
                    </properties>
                </GridPane>
            """);

            assertEquals("foo", root.getProperties().get("str0"));
            assertEquals(123.5, (Double)root.getProperties().get("val0"), 0.001);
            assertEquals(Color.RED, root.getProperties().get("col0"));
            assertTrue(root.getProperties().get("pane") instanceof GridPane);

            //noinspection OptionalGetWithoutIsPresent
            Map.Entry<Object, Object> entry = root.getProperties().entrySet().stream()
                .filter(o -> o.getValue() instanceof String && o.getValue().equals("baz")).findFirst().get();
            assertFalse(entry.getKey() instanceof String);
        }

        public static class StringKeyMapTestPane extends Pane {
            private final Map<String, Object> stringKeyMap = new HashMap<>();

            @SuppressWarnings("unused")
            public Map<String, Object> getStringKeyMap() {
                return stringKeyMap;
            }
        }

        @Test
        public void Object_Are_Added_To_StringKey_MapProperty() {
            StringKeyMapTestPane root = compileAndRun("""
                <?import javafx.scene.layout.*?>
                <StringKeyMapTestPane xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml">
                    <stringKeyMap>
                        <String fx:id="str0">foo</String>
                        <Double fx:id="val0" fx:value="123.5"/>
                        <String>baz</String>
                    </stringKeyMap>
                </StringKeyMapTestPane>
            """);

            assertEquals("foo", root.stringKeyMap.get("str0"));
            assertEquals(123.5, (Double)root.stringKeyMap.get("val0"), 0.001);
            assertEquals(3, root.stringKeyMap.size());
        }
    }

}

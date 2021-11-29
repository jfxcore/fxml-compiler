//package org.jfxcore.compiler.bindings;
//
//import javafx.scene.layout.Pane;
//import org.jfxcore.compiler.diagnostic.ErrorCode;
//import org.jfxcore.compiler.diagnostic.MarkupException;
//import org.jfxcore.compiler.util.TestCompiler;
//import org.jfxcore.compiler.util.TestExtension;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//@SuppressWarnings("HttpUrlsUsage")
//@ExtendWith(TestExtension.class)
//public class KtBindingPathTest {
//
//    public static class TestPane0 extends Pane {
//        public final KtTestContext0 context = new KtTestContext0();
//    }
//
//    @SuppressWarnings("unused")
//    public static class TestPane1 extends Pane {
//        public final KtTestContext1 context = new KtTestContext1();
//    }
//
//    @SuppressWarnings("unused")
//    public static class TestPane2 extends Pane {
//        public final KtTestContext2 context = new KtTestContext2();
//    }
//
//    @SuppressWarnings("unused")
//    public static class TestPane3 extends Pane {
//        public final KtTestContext3 context = new KtTestContext3();
//    }
//
//    @SuppressWarnings("unused")
//    public static class TestPane4 extends Pane {
//        public final KtTestContext4 context = new KtTestContext4();
//    }
//
//    @SuppressWarnings("unused")
//    public static class TestPane5 extends Pane {
//        public final KtTestContext5 context = new KtTestContext5();
//    }
//
//    @Test
//    public void Bind_Once_To_Delegated_Property() {
//        TestPane0 root = TestCompiler.newInstance(
//            this, "Bind_Once_To_Delegated_Property", """
//                    <?import org.jfxcore.compiler.bindings.KtBindingPathTest.TestPane0?>
//                    <TestPane0 xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
//                               visible="{fx:once context.boolProp}"/>
//                """);
//
//        assertFalse(root.isVisible());
//        root.context.setBoolProp(true);
//        assertFalse(root.isVisible());
//    }
//
//    @Test
//    public void Bind_Unidirectional_To_Delegated_Property() {
//        TestPane0 root = TestCompiler.newInstance(
//            this, "Bind_Unidirectional_To_Delegated_Property", """
//                    <?import org.jfxcore.compiler.bindings.KtBindingPathTest.TestPane0?>
//                    <TestPane0 xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
//                               visible="{fx:bind context.boolProp}"/>
//                """);
//
//        assertTrue(root.visibleProperty().isBound());
//        assertFalse(root.isVisible());
//        root.context.setBoolProp(true);
//        assertTrue(root.isVisible());
//    }
//
//    @Test
//    public void Bind_Bidirectional_To_Delegated_Property() {
//        TestPane0 root = TestCompiler.newInstance(
//            this, "Bind_Bidirectional_To_Delegated_Property", """
//                <?import org.jfxcore.compiler.bindings.KtBindingPathTest.TestPane0?>
//                <TestPane0 xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
//                           visible="{fx:sync context.boolProp}"/>
//            """);
//
//        assertFalse(root.isVisible());
//        root.context.setBoolProp(true);
//        assertTrue(root.isVisible());
//        root.setVisible(false);
//        assertFalse(root.context.getBoolProp());
//    }
//
//    @Test
//    public void Bind_Once_To_ReadOnly_Delegated_Property() {
//        TestPane1 root = TestCompiler.newInstance(
//            this, "Bind_Once_To_ReadOnly_Delegated_Property", """
//                <?import org.jfxcore.compiler.bindings.KtBindingPathTest.TestPane1?>
//                <TestPane1 xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
//                           prefWidth="{fx:once context.readOnlyDoubleProp}"/>
//            """);
//
//        assertFalse(root.prefWidthProperty().isBound());
//        assertEquals(123.0, root.getPrefWidth(), 0.001);
//        root.context.changeValue(0.0);
//        assertEquals(123.0, root.getPrefWidth(), 0.001);
//    }
//
//    @Test
//    public void Bind_Unidirectional_To_ReadOnly_Delegated_Property() {
//        TestPane1 root = TestCompiler.newInstance(
//            this, "Bind_Unidirectional_To_ReadOnly_Delegated_Property", """
//                <?import org.jfxcore.compiler.bindings.KtBindingPathTest.TestPane1?>
//                <TestPane1 xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
//                           prefWidth="{fx:bind context.readOnlyDoubleProp}"/>
//            """);
//
//        assertTrue(root.prefWidthProperty().isBound());
//        assertEquals(123.0, root.getPrefWidth(), 0.001);
//        root.context.changeValue(0.0);
//        assertEquals(0.0, root.getPrefWidth(), 0.001);
//    }
//
//    @Test
//    public void Bind_Bidirectional_To_ReadOnly_Delegated_Property_Fails() {
//        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
//            this, "Bind_Bidirectional_To_ReadOnly_Delegated_Property_Fails", """
//                <?import org.jfxcore.compiler.bindings.KtBindingPathTest.TestPane1?>
//                <TestPane1 xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
//                           prefWidth="{fx:sync context.readOnlyDoubleProp}"/>
//            """));
//
//        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
//    }
//
//    @Test
//    public void Bind_Unidirectional_To_Inaccessible_Delegated_Property_Fails() {
//        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
//            this, "Bind_Unidirectional_To_Inaccessible_Delegated_Property_Fails", """
//                <?import org.jfxcore.compiler.bindings.KtBindingPathTest.TestPane2?>
//                <TestPane2 xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
//                           visible="{fx:bind context.privateBoolProp}"/>
//            """));
//
//        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.getDiagnostic().getCode());
//    }
//
//    @Test
//    public void Bind_Once_To_Val_Delegated_Property() {
//        TestPane3 root = TestCompiler.newInstance(
//            this, "Bind_Once_To_Val_Delegated_Property", """
//                <?import org.jfxcore.compiler.bindings.KtBindingPathTest.TestPane3?>
//                <TestPane3 xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
//                           visible="{fx:once context.readOnlyBoolProp}"/>
//            """);
//
//        assertFalse(root.visibleProperty().isBound());
//        assertTrue(root.isVisible());
//    }
//
//    @Test
//    public void Bind_Unidirectional_To_Val_Delegated_Property() {
//        TestPane3 root = TestCompiler.newInstance(
//            this, "Bind_Unidirectional_To_Val_Delegated_Property", """
//                <?import org.jfxcore.compiler.bindings.KtBindingPathTest.TestPane3?>
//                <TestPane3 xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
//                           visible="{fx:bind context.readOnlyBoolProp}"/>
//            """);
//
//        assertTrue(root.visibleProperty().isBound());
//        assertTrue(root.isVisible());
//    }
//
//    @Test
//    public void Bind_Bidirectional_To_Val_Delegated_Property_Fails() {
//        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
//            this, "Bind_Bidirectional_To_Val_Delegated_Property_Fails", """
//                <?import org.jfxcore.compiler.bindings.KtBindingPathTest.TestPane3?>
//                <TestPane3 xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
//                           visible="{fx:sync context.readOnlyBoolProp}"/>
//            """));
//
//        assertEquals(ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE, ex.getDiagnostic().getCode());
//    }
//
//    @Test
//    public void Bind_Unidirectional_To_ObjectProperty_Of_String() {
//        TestPane4 root = TestCompiler.newInstance(
//            this, "Bind_Unidirectional_To_ObjectProperty_Of_String", """
//                <?import org.jfxcore.compiler.bindings.KtBindingPathTest.TestPane4?>
//                <TestPane4 xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
//                           id="{fx:bind context.data}"/>
//            """);
//
//        assertTrue(root.idProperty().isBound());
//        assertEquals("data", root.getId());
//    }
//
//    @Test
//    public void Bind_Unidirectional_To_ObjectProperty_Of_GenericObject() {
//        TestPane5 root = TestCompiler.newInstance(
//            this, "Bind_Unidirectional_To_ObjectProperty_Of_GenericObject", """
//                <?import org.jfxcore.compiler.bindings.KtBindingPathTest.TestPane5?>
//                <TestPane5 xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
//                           id="{fx:bind context.data.value}"/>
//            """);
//
//        assertTrue(root.idProperty().isBound());
//        assertEquals("data", root.getId());
//    }
//
//    @Test
//    public void Bind_Unidirectional_To_ObjectProperty_Of_GenericObject_With_InconvertibleType_Fails() {
//        MarkupException ex = assertThrows(MarkupException.class, () -> TestCompiler.newInstance(
//            this, "Bind_Unidirectional_To_ObjectProperty_Of_GenericObject_With_InconvertibleType_Fails", """
//                <?import org.jfxcore.compiler.bindings.KtBindingPathTest.TestPane5?>
//                <TestPane5 xmlns="http://jfxcore.org/javafx" xmlns:fx="http://jfxcore.org/fxml"
//                           prefWidth="{fx:bind context.data.value}"/>
//            """));
//
//        assertEquals(ErrorCode.CANNOT_CONVERT_SOURCE_TYPE, ex.getDiagnostic().getCode());
//    }
//
//}
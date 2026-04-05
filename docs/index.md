---
layout: default
title: Overview
nav_order: 1
---

# FXML 2.0 overview

FXML 2.0 is a declarative markup language for JavaFX based on the [FXML 1.0](https://openjfx.io/javadoc/24/javafx.fxml/javafx/fxml/doc-files/introduction_to_fxml.html) markup language, adding powerful new features:

## Compile markup directly to bytecode
FXML 2.0 markup is compiled directly to bytecode, and doesn't require `FXMLLoader` to load the document at runtime. No parsing is required, and no reflection is necessary to instantiate the JavaFX object graph. This dramatically improves the loading performance of FXML documents.

Here's how an FXML 2.0 file is compiled to a Java class:
<div class="filename">com/sample/NumberDialog.fxml</div>
```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.layout.*?>
<?import javafx.scene.control.*?>

<VBox xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
    <Label text="Enter a number:"/>
    <TextField fx:id="textField"/>
    <Label text="The value cannot be empty" visible="${textField.text.empty}"/>
</VBox>
```

The resulting class is named `com.sample.NumberDialog`, corresponding to the name of the FXML file. It extends the `javafx.scene.layout.VBox` class and its content is similar to the following decompiled code:
<details markdown="block">
<summary><code>com.sample.NumberDialog</code></summary>
```java
// Reconstructed from bytecode, details omitted
public class NumberDialog extends VBox {
    protected TextField textField;

    public NumberDialog() {
        __FX$RuntimeContext var1 = new __FX$RuntimeContext(this, 2);
        TextField var2 = this.textField = new TextField();
        var1.push(this);
        ObservableList var3 = this.getChildren();
        Label var10002 = new Label();
        var10002.setText("Enter a number:");
        var3.add(var10002);
        var2.setId("textField");
        var3.add(var2);
        var10002 = new Label();
        var1.push(var10002);
        var10002.setText("The value cannot be empty");
        StringProperty var4 = (StringProperty)
            ((TextField)((MainView)var1.parents[0]).textField).textProperty();
        Object var10003;
        if (var4 == null) {
            boolean var5 = false;
            var10003 = new __FX$BooleanConstant(var5);
        } else {
            var10003 = new __FX$textField$textProperty$isEmpty(var4);
        }

        Object var6 = var10003;
        var10002.visibleProperty().bind((ObservableValue)var6);
        var1.pop();
        var3.add(var10002);
        var1.pop();
    }
}
```
</details>

In many cases, custom controls or user interfaces require imperative code for additional functionality. FXML 2.0 supports this with an optional [code-behind](code-behind.html) class to combine FXML markup and Java code.

## Compile-time type safety
All symbols referenced in FXML 2.0 markup are resolved at compile time. Errors are surfaced early in the build, instead of later at runtime. Compiler diagnostics make it easy to see what went wrong:

```
NumberDialog.fxml:8: 'textFiel' in NumberDialog cannot be resolved

    <Label text="The value cannot be empty" visible="${textFiel.text.empty}"/>
                                                       ^^^^^^^^
```

## Embedded markup in Java or Kotlin files

FXML 2.0 also supports embedding markup directly into Java or Kotlin source files with the `@ComponentView` annotation, keeping markup and imperative code in the same file.
With `@ComponentView`, the annotated class supplies the FXML source text, and the compiler treats it like a regular FXML view associated with that class:

```java
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import org.jfxcore.markup.ComponentView;

@ComponentView("""
    <StackPane>
        <Button fx:id="myButton"/>
    </StackPane>
""")
public class MyControl extends MyControlBase {

    public MyControl() {
        initializeComponent();
        myButton.setText("Click me");
    }
}
```

This makes it easy to build components in a single source file while still getting the benefits of compiled FXML, including type-safe symbol resolution and seamless integration with imperative code.

## Bring your own pattern
FXML 2.0 does not use the markup/controller pattern as featured in FXML 1.0 with `FXMLLoader`. Instead, FXML 2.0 markup compiles down to scene graph nodes, optionally including a [code-behind](code-behind.html) class to combine it with imperative code.

Application developers are free to implement their preferred patterns like [MVC](https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93controller) or [MVVM](https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93viewmodel), but this is not a design choice that is imposed by the FXML 2.0 markup language.

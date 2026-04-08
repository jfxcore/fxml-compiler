---
layout: default
title: Embedded markup
parent: Getting started
nav_order: 2
---

# Embedded markup
FXML 2.0 markup can be embedded directly into Java or Kotlin source code with the `@ComponentView` annotation.
This can be useful for custom controls and reusable components that benefit from keeping markup and imperative
logic in the same file.

{: .note }
The `@ComponentView` annotation is available in the [markup](https://github.com/jfxcore/markup) library.

The annotated class supplies the markup source text, and the compiler treats it like a regular FXML view
associated with that component:

```java
package com.sample;

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
        myButton.setText("Hello!");
    }
}
```

## How embedded markup differs from standalone files
Embedded markup follows the same language rules as standalone FXML, but some information is provided by the
surrounding Java or Kotlin source:

- The annotated class is already known to be the [code-behind](../code-behind.html) class, so the `fx:class` attribute
  is neither required nor supported.
- Import declarations from the Java or Kotlin source file are also available to the embedded FXML document,
  they do not need to be redeclared as `<?import?>` processing instructions.
- The `xmlns="http://javafx.com/javafx"` and `xmlns:fx="http://jfxcore.org/fxml/2.0"` namespaces are implied
  and do not need to be explicitly declared.

The generated base class still follows the usual naming convention, so `MyControl` extends `MyControlBase`.

{: .note }
In Kotlin projects, the embedded FXML document cannot reference type aliases.

## Enable annotation processing
The `@ComponentView` annotation is processed at compile time by an annotation processor, it is not retained in
the compiled class file. The [FXML 2.0 Gradle plugin](https://plugins.gradle.org/plugin/org.jfxcore.fxmlplugin)
does not enable annotation processing by default, you need to opt in explicitly in your Gradle build script:

```kotlin
fxml {
    annotationProcessing = true
}
```

In Kotlin projects, the KSP plugin also needs to be explicitly applied:
```kotlin
plugins {
    kotlin("jvm") version "2.3.20"
    id("com.google.devtools.ksp") version "2.3.6"
    id("org.jfxcore.fxmlplugin") version "0.13.0"
}
```

---
layout: default
title: FXML source file
parent: Getting started
nav_order: 1
---

# FXML source file
A dedicated `.fxml` source file is the most direct migration path from classic FXML projects, except that the markup is compiled during the build instead of being parsed with `FXMLLoader` at runtime.

The basic structure of a FXML 2.0 source file consists of any number of `import` instructions, and exactly one root element:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.layout.StackPane?>

<StackPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
</StackPane>
```

There are two XML namespaces involved:
- `xmlns="http://javafx.com/javafx"` is the default namespace for all elements declared in the FXML file. It <b>must</b> include the `javafx.com/javafx` part, and <b>may</b> include any number of additional path segments for informational purposes.
- `xmlns:fx="http://jfxcore.org/fxml/2.0"` contains intrinsic types of the FXML 2.0 markup language.

{: .note }
When the default namespace `xmlns="http://javafx.com/javafx"` is omitted or does not contain the `javafx.com/javafx` part, the FXML 2.0 compiler will skip the file.

## FXML file without code-behind class
An FXML 2.0 source file without [code-behind](../code-behind.html) class is compiled to a Java class with the same name.
For example, if you place a `MyButton.fxml` file in the `com/sample/control` package, the compiler will create the Java class `com.sample.control.MyButton`.

## Deployment
While FXML 1.0 files are resource files that are loaded at runtime using `FXMLLoader`, FXML 2.0 files are compiled to class files.
It is neither required nor useful to deploy FXML 2.0 source files along with the application.

{: .note}
It is advisable to place FXML 2.0 files in a source directory like `src/main/java`, and not in a resource directory like `src/main/resources`.
If a FXML 2.0 file is paired with a [code-behind](../code-behind.html) class, the FXML file and the code-behind file should be placed in the same directory.

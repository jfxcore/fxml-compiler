---
layout: default
title: Markup & deployment
nav_order: 3
---

# Markup
The basic structure of an FXML 2.0 file consists of any number of `import` instructions, and exactly one root element:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.layout.StackPane?>

<StackPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
</StackPane>
```

There are two XML namespaces involved:
- `xmlns="http://javafx.com/javafx"` is the default namespace for all elements declared in the FXML file. It <b>must</b> include the `javafx.com/javafx` part, and <b>may</b> include any number of additional path segments for informational purposes.
- `xmlns:fx="http://jfxcore.org/fxml/2.0"` contains intrinsic types of the FMXL 2.0 markup language.

{: .note }
When the default namespace `xmlns="http://javafx.com/javafx"` is omitted or does not contain the `javafx.com/javafx` part, the FXML 2.0 compiler will skip the file.

A standalone FXML file is compiled to a Java class with the same name. For example, if you place a `MyButton.fxml` file in the `com/sample/control` package, the compiler will create the Java class `com.sample.control.MyButton`.

## Deployment model
While FXML 1.0 files are resource files that are loaded at runtime using `FXMLLoader`, FXML 2.0 files are compiled to class files. It is neither required nor useful to deploy the FXML files along with the application.

{: .note}
It is advisable to place FXML 2.0 files in a source directory like `src/main/java`, and not in a resource directory like `src/main/resources`. If an FXML 2.0 file is paired with a code-behind class, the FXML file and the code-behind file should be placed in the same directory.
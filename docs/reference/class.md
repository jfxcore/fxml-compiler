---
layout: default
title: fx:class
parent: FXML 2.0 language reference
nav_order: 5
---

# fx:class attribute
The `fx:class` attribute identifies the fully-qualified name of the code-behind class. The FXML compiler generates a base class which must be extended by the code-behind class.

The generated base class contains an `initializeComponent()` method, which must be called in the constructor of the code-behind class to initialize the scene graph.

{: .highlight }
The `fx:class` attribute can only be set on the root element.

## FXML documents without code-behind class
If the `fx:class` attribute is omitted, the FXML file is compiled down to a class with the same name. For example, the document `com/sample/MyControl.fxml` will yield the class `com.sample.MyControl`. This is a supported scenario for FXML documents that don't require a code-behind class with custom code.

## Usage

<div class="filename">com/sample/MyControl.fxml</div>
```xml
<BorderPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
            fx:class="com.sample.MyControl">
</BorderPane>
```

<div class="filename">com/sample/MyControl.java</div>
```java
public class MyControl extends MyControlBase {
    public MyControl() {
        // Code before initialization
        initializeComponent();
        // Code after initialization
    }
}
```
---
layout: default
title: fx:markupClassName
parent: FXML 2.0 language reference
nav_order: 14
---

# fx:markupClassName attribute
The `fx:markupClassName` attribute controls the name of the compiled markup class. If this attribute is not specified, the name of the markup class defaults to the standalone `.fxml` file name or the annotated Java class name, plus the `Base` suffix. For example, the default markup class name for `MyControl.fxml` will be `MyControlBase`.

{: .highlight }
In `.fxml` files, the `fx:markupClassName` attribute can only be used when the `fx:class` attribute is also specified.

## Usage

<div class="filename">com/sample/MyControl.fxml</div>
```xml
<BorderPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
            fx:class="com.sample.MyControl"
            fx:markupClassName="MyCustomBaseClass">
<BorderPane/>
```

<div class="filename">com/sample/MyControl.java</div>
```java
public class MyControl extends MyCustomBaseClass {
    MyControl() {
        initializeComponent();
    }
}
```

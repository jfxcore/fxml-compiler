---
layout: default
title: fx:className
parent: FXML 2.0 language reference
---

# fx:className attribute
The `fx:className` attribute controls the name of the generated class. If this attribute is not specified,
the name of the generated class defaults to the standalone `.fxml` file name or the annotated Java class name,
plus the `Base` suffix. For example, the default generated class name for `MyControl.fxml` will be `MyControlBase`.

{: .highlight }
In `.fxml` files, the `fx:className` attribute can only be used when the `fx:subclass` attribute is also specified.

## Usage

<div class="filename">com/sample/MyControl.fxml</div>
```xml
<BorderPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
            fx:subclass="com.sample.MyControl"
            fx:className="MyCustomBaseClass">
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

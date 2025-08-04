---
layout: default
title: Compiled bindings
nav_order: 9
has_children: true
---

# Compiled bindings
FXML 2.0 supports all binding types afforded by JavaFX, and extends binding expressions with operators and functions. Binding paths are compiled down to specialized code by the FXML compiler, which can increase the performance of the resulting code.

Here's how a simple binding is specified in FXML 2.0, using different (but equivalent) notations:

<div class="filename">com/sample/MyControl.fxml</div>
```xml
<VBox xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
      fx:class="com.sample.MyControl">
    <!-- fx:bind element with binding source path -->
    <Button text="{fx:bind path=caption}"/>

    <!-- 'path' is the default property of the fx:bind element, so it can be omitted -->
    <Button text="{fx:bind caption}"/>

    <!-- Short notation, similar to FXML 1.0 -->
    <Button text="${caption}"/>
</VBox>
```

<div class="filename">com/sample/MyControl.java</div>
```java
public class MyControl extends MyControlBase {
    private final StringProperty caption = new SimpleStringProperty("Click me");

    public StringProperty captionProperty() {
        return caption;
    }

    public MyControl() {
        initializeComponent();
    }
}
```

`fx:bind` is an intrinsic element of the FMXL 2.0 language, which corresponds to calling the `Property.bind(ObservableValue)` method with the `caption` argument, which is the binding source path.
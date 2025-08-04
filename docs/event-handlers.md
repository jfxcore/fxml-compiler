---
layout: default
title: Event handlers
nav_order: 8
---

# Event handlers
Event handlers are implementations of the `javafx.event.EventHandler` interface, and can be set on event handler properties with a [binding expression](binding/binding-path.html):

<div class="filename">com/sample/MyControl.fxml</div>
```xml
<StackPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
           fx:class="com.sample.MyControl">
    <Button onAction="$myActionHandler"/>
<StackPane/>
```

<div class="filename">com/sample/MyControl.java</div>
```java
public class MyControl extends StackPane {
    final EventHandler<ActionEvent> myActionHandler = (event) -> {
        ...
    };

    public MyControl() {
        initializeComponent();
    }
}
```

{: .note }
Usually, event handlers don't change dynamically. It is therefore advisable to use a [one-time assignment](reference/once.html) instead of a [unidirectional binding](reference/bind.html).

## Method event handlers
Event handlers can also be implemented as methods on the code-behind class that have a signature compatible with the `javafx.event.EventHandler` interface. The method name must be prefixed with `#` in the FXML attribute:

<div class="filename">com/sample/MyControl.fxml</div>
```xml
<StackPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
           fx:class="com.sample.MyControl">
    <!-- Note that # identifies a method handler -->
    <Button onAction="#myActionHandler"/>
<StackPane/>
```

<div class="filename">com/sample/MyControl.java</div>
```java
public class MyControl extends StackPane {
    public MyControl() {
        initializeComponent();
    }
    
    void handleActionEvent(ActionEvent event) {
        ...
    }
}
```

{: .highlight }
The `event` parameter is optional in the method signature and can be omitted if not required.
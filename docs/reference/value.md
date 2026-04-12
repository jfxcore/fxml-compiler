---
layout: default
title: fx:value
parent: FXML 2.0 language reference
---

# fx:value directive
The `fx:value` directive initializes an element by invoking a static `valueOf(...)` method to create an instance
of the element. The `valueOf(...)` method may be declared on the type itself or on one of its superclasses.

## Usage
```xml
<Button xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
    <textFill>
        <Color fx:value="red"/>
    </textFill>
</Button>
```

{: .note }
`fx:value` is evaluated once during object initialization.<br>
[Evaluation](evaluate.html) expressions are supported, but [observable](observe.html) expressions are not.

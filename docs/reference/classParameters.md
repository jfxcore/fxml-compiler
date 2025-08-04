---
layout: default
title: fx:classParameters
parent: FXML 2.0 language reference
nav_order: 7
---

# fx:classParameters attribute
The `fx:classParameters` attribute specifies the constructor parameters of the generated class. If omitted, the generated class has a parameterless constructor.

{: .highlight }
The `fx:classParameters` attribute can only be set on the root element.

## Usage

```xml
<BorderPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
            fx:classParameters="String, MyClass<Double>">
</BorderPane>
```

{: .note }
In XML files, the < character can only be used as a markup delimiter, and must be escaped using &lt; in attribute text. However, the FXML 2.0 compiler accepts the non-standard literal form for better code readability.
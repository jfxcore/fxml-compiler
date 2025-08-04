---
layout: default
title: fx:classModifier
parent: FXML 2.0 language reference
nav_order: 6
---

# fx:classModifier attribute
The `fx:classModifier` attribute specifies the access modifier of the generated class. When omitted, the generated class has a `public` access modifier.

{: .highlight }
The `fx:classModifier` attribute can only be set on the root element.

## Values

| Value | Java class modifier |
|:-|:-|
| (not specified) | `public` |
| protected | `protected` |
| package | package-private (no modifier) |

## Usage

```xml
<BorderPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
            fx:classModifier="protected">
</BorderPane>
```
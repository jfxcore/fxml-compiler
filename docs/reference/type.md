---
layout: default
title: fx:Type
parent: FXML 2.0 language reference
---

# fx:Type markup extension
The `fx:Type` markup extension resolves a name to a class literal.

## Properties

| Property | Description |
|:-|:-|
| `name` | A string that specifies the class name. This is the [default property](../property-notation.html#default-property). |

## Usage

```xml
<!-- Element notation -->
<object>
    <property>
        <fx:Type name="MyClass"/>
    </property>
<object>

<!-- Attribute notation -->
<object property="{fx:Type name=MyClass}"/>

<!-- Attribute notation with omitted "name" -->
<object property="{fx:Type MyClass}"/>
```

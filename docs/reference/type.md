---
layout: default
title: fx:type
parent: FXML 2.0 language reference
nav_order: 18
---

# fx:type markup extension
The `fx:type` markup extension resolves a name to a class literal.

## Properties

| Property | Description |
|:-|:-|
| `name` | A string that specifies the class name. This is the [default property](../property-notation.html#default-property). |

## Usage

```xml
<!-- Element notation -->
<object>
    <property>
        <fx:type name="MyClass"/>
    </property>
<object>

<!-- Attribute notation -->
<object property="{fx:type name=MyClass}"/>

<!-- Attribute notation with omitted "name" -->
<object property="{fx:type MyClass}"/>
```
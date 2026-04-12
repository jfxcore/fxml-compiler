---
layout: default
title: fx:Class
parent: FXML 2.0 language reference
---

# fx:Class markup extension
The `fx:Class` markup extension resolves a name to a class literal.

## Properties

| Property | Description |
|:-|:-|
| `name` | A string that specifies the class name. This is the [default property](../property-notation.html#default-property). |

## Usage

```xml
<!-- Element notation -->
<object>
    <property>
        <fx:Class name="MyClass"/>
    </property>
<object>

<!-- Attribute notation -->
<object property="{fx:Class name=MyClass}"/>

<!-- Attribute notation with omitted "name" -->
<object property="{fx:Class MyClass}"/>
```

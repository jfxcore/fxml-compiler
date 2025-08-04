---
layout: default
title: fx:bind, <span class="nav-inline-code">${x}</span>
parent: FXML 2.0 language reference
nav_order: 1
---

# fx:bind element, ${x}
The `fx:bind` element establishes a unidirectional binding. It can be set on any `javafx.beans.property.Property` instance, and is equivalent to invoking the `Property.bind(ObservableValue)` API in Java code.

Its short notation is `${x}`, where <span class="inline-code">x</span> is the [binding path](../binding/binding-path.html).

## Properties

| Property | Description |
|:-|:-|
| `path` | A string that specifies the [binding path](../binding/binding-path.html). This is the [default property](../compact-notation.html#default-property). |

## Usage

```xml
<!-- Element notation -->
<object>
    <property>
        <fx:bind path="myPath"/>
    </property>
<object>

<!-- Attribute notation -->
<object property="{fx:bind path=myPath}"/>

<!-- Attribute notation with omitted "path" -->
<object property="{fx:bind myPath}"/>

<!-- Short notation -->
<object property="${myPath}"/>
```
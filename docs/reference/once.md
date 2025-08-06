---
layout: default
title: fx:once, <span class="nav-inline-code">$x</span>
parent: FXML 2.0 language reference
nav_order: 16
---

# fx:once markup extension, $x
The `fx:once` markup extension resolves a value and assigns it to a property. Even if the source is an `ObservableValue`, no binding is established and the value is not updated after the initial assignment.

Its compact notation is `$x`, where <span class="inline-code">x</span> is the [binding path](../binding/binding-path.html).

## Properties

| Property | Description |
|:-|:-|
| `path` | A string that specifies the [binding path](../binding/binding-path.html). This is the [default property](../property-notation.html#default-property). |

## Usage

```xml
<!-- Element notation -->
<object>
    <property>
        <fx:once path="myPath"/>
    </property>
<object>

<!-- Attribute notation -->
<object property="{fx:once path=myPath}"/>

<!-- Attribute notation with omitted "path" -->
<object property="{fx:once myPath}"/>

<!-- Compact notation -->
<object property="$myPath"/>
```
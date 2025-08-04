---
layout: default
title: fx:once
parent: FXML 2.0 language reference
nav_order: 16
---

# fx:once element
The `fx:once` element resolves a value and assigns it to a property. Even if the source is an `ObservableValue`, no binding is established and the value is not updated after the initial assignment.

## Properties

| Property | Description |
|:-|:-|
| `path` | A string that specifies the [binding path](../binding/binding-path.html). This is the [default property](../compact-notation.html#default-property). |

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

<!-- Short notation -->
<object property="$myPath"/>
```
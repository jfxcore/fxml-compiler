---
layout: default
title: fx:bindContent
parent: FXML 2.0 language reference
nav_order: 3
---

# fx:bindContent element
The `fx:bindContent` element establishes a content binding. It can be set on any `ReadOnlyListProperty`, `ReadOnlySetProperty`, or `ReadOnlyMapProperty` instance, and is equivalent to invoking the `ReadOnly{List/Set/Map}Property.bindContent(Observable{List/Set/Map})` API in Java code.

## Properties

| Property | Description |
|:-|:-|
| `path` | A string that specifies the [binding path](../binding/binding-path.html). This is the [default property](../compact-notation.html#default-property). |

## Usage

```xml
<!-- Element notation -->
<object>
    <property>
        <fx:bindContent path="myPath"/>
    </property>
<object>

<!-- Attribute notation -->
<object property="{fx:bindContent path=myPath}"/>

<!-- Attribute notation with omitted "path" -->
<object property="{fx:bindContent myPath}"/>

<!-- Short notation -->
<object property="${..myPath}"/>
```
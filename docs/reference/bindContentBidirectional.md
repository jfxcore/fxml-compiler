---
layout: default
title: fx:bindContentBidirectional, <span class="nav-inline-code">#{..x}</span>
parent: FXML 2.0 language reference
nav_order: 4
---

# fx:bindContentBidirectional markup extension, #{..x}
The `fx:bindContentBidirectional` markup extension establishes a bidirectional content binding. It can be set on any `ReadOnlyListProperty`, `ReadOnlySetProperty`, or `ReadOnlyMapProperty` instance, and is equivalent to invoking the `ReadOnly{List/Set/Map}Property.bindContentBidirectional(Observable{List/Set/Map})` API in Java code.

Its compact notation is `#{..x}`, where <span class="inline-code">x</span> is the [binding path](../binding/binding-path.html).

## Properties

| Property | Description |
|:-|:-|
| `path` | A string that specifies the [binding path](../binding/binding-path.html). This is the [default property](../property-notation.html#default-property). |

## Usage

```xml
<!-- Element notation -->
<object>
    <property>
        <fx:bindContentBidirectional path="myPath"/>
    </property>
<object>

<!-- Attribute notation -->
<object property="{fx:bindContentBidirectional path=myPath}"/>

<!-- Attribute notation with omitted "path" -->
<object property="{fx:bindContentBidirectional myPath}"/>

<!-- Compact notation -->
<object property="#{..myPath}"/>
```
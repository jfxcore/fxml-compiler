---
layout: default
title: fx:content, <span class="nav-inline-code">$..x</span>
parent: FXML 2.0 language reference
nav_order: 9
---

# fx:content element, $..x
The `fx:content` element assigns the content of a collection. It can be set on any `List`, `Set`, or `Map` property, and is equivalent to invoking the `List.addAll(Collection)`, `Set.addAll(Collection)`, or `Map.putAll(Map)` API in Java code.

Its short notation is `$..x`, where <span class="inline-code">x</span> is the [binding path](../binding/binding-path.html).

## Properties

| Property | Description |
|:-|:-|
| `path` | A string that specifies the [binding path](../binding/binding-path.html). This is the [default property](../compact-notation.html#default-property). |

## Usage

```xml
<!-- Element notation -->
<object>
    <property>
        <fx:content path="myPath"/>
    </property>
<object>

<!-- Attribute notation -->
<object property="{fx:content path=myPath}"/>

<!-- Attribute notation with omitted "path" -->
<object property="{fx:content myPath}"/>

<!-- Short notation -->
<object property="$..myPath"/>
```
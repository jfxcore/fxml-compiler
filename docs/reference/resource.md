---
layout: default
title: fx:resource
parent: FXML 2.0 language reference
nav_order: 17
---

# fx:resource element
The `fx:resource` element resolves a resource as if by calling `Class.getResource(String)` on the root class. 

Its short notation is the resource resolution operator `@`.

## Applicability
The `fx:resource` element is applicable to properties of type `URL`, `URI`, and `String`.

| Target type | Equivalent code |
|:-|:-|
| `URL` | `Class.getResource(String)` |
| `URI` | `Class.getResource(String).toURI()` |
| `String` | `Class.getResource(String).toExternalForm()` |

## Properties

| Property | Description |
|:-|:-|
| `name` | A string that specifies the resource name, which will be passed to the `Class.getResource(String)` method. This is the [default property](../compact-notation.html#default-property). |

## Usage

```xml
<!-- Element notation -->
<object>
    <property>
        <fx:resource name="/path/to/myResource"/>
    </property>
<object>

<!-- Attribute notation -->
<object property="{fx:resource name=/path/to/myResource}"/>

<!-- Attribute notation with omitted "name" -->
<object property="{fx:resource /path/to/myResource}"/>

<!-- Short notation -->
<object property="@/path/to/myResource"/>
```

{: .note }
If the resource name includes spaces, it must be enclosed in single quotes.
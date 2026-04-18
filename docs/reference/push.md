---
layout: default
title: fx:Push, <span class="nav-inline-code">$>{x}</span>
parent: FXML 2.0 language reference
---

# fx:Push markup extension, $>{x}
The `fx:Push` markup extension establishes a reverse binding that pushes values from the binding target to the source.
It is only valid in [property-consumer](../markup-extension.html#where-markup-extensions-can-be-used) position and cannot
be used where a value or [`ObservableValue`](https://openjfx.io/javadoc/17/javafx.base/javafx/beans/value/ObservableValue.html)
is expected.

A leading `..` in the source path selects the [content](../markup-extension/expression/path.html#content-selection-operator-)
of the target and source collection. In that form, `fx:Push` establishes a reverse content binding between
the source and target collections.

Its prefix notation is `$>{x}`, where <span class="inline-code">x</span> is the [source path](../markup-extension/expression/path.html).

## Properties

| Property | Description |
|:-|:-|
| `source` | A string that specifies the [source path](../markup-extension/expression/path.html). This is the [default property](../property-notation.html#default-property). |

## Usage

```xml
<!-- Element notation -->
<object>
    <property>
        <fx:Push source="mySourcePath"/>
    </property>
</object>

<!-- Attribute notation -->
<object property="{fx:Push source=mySourcePath}"/>

<!-- Attribute notation with omitted "source=" -->
<object property="{fx:Push mySourcePath}"/>

<!-- Prefix notation -->
<object property="$>{mySourcePath}"/>

<!-- Reverse content binding -->
<object property="$>{..mySourcePath}"/>
```

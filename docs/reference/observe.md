---
layout: default
title: fx:Observe, <span class="nav-inline-code">${x}</span>
parent: FXML 2.0 language reference
---

# fx:Observe markup extension, ${x}
The `fx:Observe` markup extension observes its [source path](../markup-extension/expression/path.html).
It can be used in [value supplier](../markup-extension.html#where-markup-extensions-can-be-used) and property consumer position:

1. **Value supplier**: resolves the source path as an
   [`ObservableValue`](https://openjfx.io/javadoc/17/javafx.base/javafx/beans/value/ObservableValue.html)
   and supplies it to the enclosing construct.
2. **Property consumer**: binds the target [`Property`](https://openjfx.io/javadoc/17/javafx.base/javafx/beans/property/Property.html)
   to the `ObservableValue` identified by the source path.

A leading `..` in the source path selects the [content](../markup-extension/expression/path.html#content-selection-operator-)
of the source collection. In that form, `fx:Observe` establishes a content binding to the target collection.

Its prefix notation is `${x}`, where <span class="inline-code">x</span> is the source path.

## Properties

| Property | Description |
|:-|:-|
| `source` | A string that specifies the [source path](../markup-extension/expression/path.html). This is the [default property](../property-notation.html#default-property). |

## Usage

```xml
<!-- Element notation -->
<object>
    <property>
        <fx:Observe source="myPath"/>
    </property>
</object>

<!-- Attribute notation -->
<object property="{fx:Observe source=myPath}"/>

<!-- Attribute notation with omitted "source" -->
<object property="{fx:Observe myPath}"/>

<!-- Prefix notation -->
<object property="${myPath}"/>
```

### Usage as a value supplier
When `fx:Observe` is nested inside another markup extension, it supplies the resulting
[`ObservableValue`](https://openjfx.io/javadoc/17/javafx.base/javafx/beans/value/ObservableValue.html)
to the enclosing construct:

```xml
<Label text="{DynamicResource message; formatArguments=${amount}}"/>
```

In this example, `${amount}` resolves the `amount` expression as an `ObservableValue`, and passes it to the
`formatArguments` parameter of [`DynamicResource`](../markup-extension/dynamic-resource.html).

### Usage as a property consumer
When `fx:Observe` is applied directly to a property target, it establishes a unidirectional binding on the target property:

```xml
<Label text="${title}"/>
```

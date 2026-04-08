---
layout: default
title: fx:observe, <span class="nav-inline-code">${x}</span>
parent: FXML 2.0 language reference
nav_order: 12
---

# fx:observe markup extension, ${x}
The `fx:observe` markup extension observes its `path`.
It can be used in [value supplier](../markup-extension.html#where-markup-extensions-can-be-used) and property consumer position:

1. **Value supplier**: resolves the `path` as an
   [`ObservableValue`](https://openjfx.io/javadoc/17/javafx.base/javafx/beans/value/ObservableValue.html)
   and supplies it to the enclosing construct.
2. **Property consumer**: binds the target [`Property`](https://openjfx.io/javadoc/17/javafx.base/javafx/beans/property/Property.html)
   to the observable value identified by `path`.

A leading `..` in the `path` value selects the [content](../markup-extension/expression/path.html#content-selection-operator-)
of the source collection. In that form, `fx:observe` establishes a content binding to the target collection.

Its prefix notation is `${x}`, where <span class="inline-code">x</span> is the [source path](../markup-extension/expression/path.html).

## Properties

| Property | Description |
|:-|:-|
| `path` | A string that specifies the [source path](../markup-extension/expression/path.html). This is the [default property](../property-notation.html#default-property). |

## Usage

```xml
<!-- Element notation -->
<object>
	<property>
		<fx:observe path="myPath"/>
	</property>
</object>

<!-- Attribute notation -->
<object property="{fx:observe path=myPath}"/>

<!-- Attribute notation with omitted "path" -->
<object property="{fx:observe myPath}"/>

<!-- Prefix notation -->
<object property="${myPath}"/>
```

### Usage as a value supplier
When `fx:observe` is nested inside another markup extension, it supplies the resulting
[`ObservableValue`](https://openjfx.io/javadoc/17/javafx.base/javafx/beans/value/ObservableValue.html)
to the enclosing construct:

```xml
<Label text="{DynamicResource message; formatArguments=${amount}}"/>
```

In this example, `${amount}` resolves the `amount` expression as an `ObservableValue`, and passes it to the
`formatArguments` parameter of [`DynamicResource`](../markup-extension/dynamic-resource.html).

### Usage as a property consumer
When `fx:observe` is applied directly to a property target, it establishes a unidirectional binding on the target property:

```xml
<Label text="${title}"/>
```

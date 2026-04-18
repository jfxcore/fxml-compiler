---
layout: default
title: fx:Evaluate, <span class="nav-inline-code">$x</span>
parent: FXML 2.0 language reference
---

# fx:Evaluate markup extension, $x
The `fx:Evaluate` markup extension evaluates its [source path](../markup-extension/expression/path.html) once.
It can be used in [value-supplier](../markup-extension.html#where-markup-extensions-can-be-used) and property-consumer position:

1. **Value supplier**: evaluates the source path once and supplies the resulting value to the enclosing construct.
2. **Property consumer**: evaluates the source path once and assigns the resulting value to the target property.

A leading `..` in the source path selects the [content](../markup-extension/expression/path.html#content-selection-operator-)
of the source collection. In that form, `fx:Evaluate` assigns the collection content to the target collection.

Its prefix notation is `$x`, where <span class="inline-code">x</span> is the source path.

## Properties

| Property | Description |
|:-|:-|
| `source` | A string that specifies the [source path](../markup-extension/expression/path.html). This is the [default property](../property-notation.html#default-property). |

## Usage

```xml
<!-- Element notation -->
<object>
    <property>
        <fx:Evaluate source="mySourcePath"/>
    </property>
</object>

<!-- Attribute notation -->
<object property="{fx:Evaluate source=mySourcePath}"/>

<!-- Attribute notation with omitted "source=" -->
<object property="{fx:Evaluate mySourcePath}"/>

<!-- Prefix notation -->
<object property="$myPath"/>
```

### Usage as a value supplier
When `fx:Evaluate` is nested inside another markup extension, it supplies the value of the evaluated expression
to the enclosing construct:

```xml
<Label text="{StaticResource message; formatArguments=$amount}"/>
```

In this example, `$amount` evaluates the `amount` path once and passes the resulting value to the `formatArguments`
parameter of [`StaticResource`](../markup-extension/static-resource.html).

### Usage as a property consumer
When `fx:Evaluate` is applied directly to a property, it assigns the evaluated value once and does not keep the
property synchronized afterward.

```xml
<Label text="$title"/>
```

---
layout: default
title: fx:evaluate, <span class="nav-inline-code">$x</span>
parent: FXML 2.0 language reference
nav_order: 7
---

# fx:evaluate markup extension, $x
The `fx:evaluate` markup extension evaluates its `path` once.
It can be used in [value-supplier](../markup-extension.html#where-markup-extensions-can-be-used) and property-consumer position:

1. **Value supplier**: evaluates the `path` once and supplies the resulting value to the enclosing construct.
2. **Property consumer**: evaluates the `path` once and assigns the resulting value to the target property.

A leading `..` in the `path` value selects the [content](../markup-extension/expression/path.html#content-selection-operator-)
of the source collection. In that form, `fx:evaluate` assigns the collection content to the target collection.

Its prefix notation is `$x`, where <span class="inline-code">x</span> is the [source path](../markup-extension/expression/path.html).

## Properties

| Property | Description |
|:-|:-|
| `path` | A string that specifies the [source path](../markup-extension/expression/path.html). This is the [default property](../property-notation.html#default-property). |

## Usage

```xml
<!-- Element notation -->
<object>
    <property>
        <fx:evaluate path="myPath"/>
    </property>
</object>

<!-- Attribute notation -->
<object property="{fx:evaluate path=myPath}"/>

<!-- Attribute notation with omitted "path" -->
<object property="{fx:evaluate myPath}"/>

<!-- Prefix notation -->
<object property="$myPath"/>
```

### Usage as a value supplier
When `fx:evaluate` is nested inside another markup extension, it supplies the value of the evaluated expression
to the enclosing construct:

```xml
<Label text="{StaticResource message; formatArguments=$amount}"/>
```

In this example, `$amount` evaluates the `amount` path once and passes the resulting value to the `formatArguments`
parameter of [`StaticResource`](../markup-extension/static-resource.html).

### Usage as a property consumer
When `fx:evaluate` is applied directly to a property, it assigns the evaluated value once and does not keep the
property synchronized afterward.

```xml
<Label text="$title"/>
```

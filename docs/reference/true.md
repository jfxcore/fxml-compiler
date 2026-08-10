---
layout: default
title: fx:True
parent: FXML/2 language reference
---

# fx:True markup extension
The `fx:True` markup extension specifies the boolean `true` value.

In many cases, using the `fx:True` markup extension is not necessary. In a boolean assignment context,
the text `"true"` is automatically [converted](../type-coercion.html) to a boolean value.

Text assignment is target-typed, so the text `"true"` has no intrinsic boolean type. In particular, assigning
`value="true"` to an `Object` property produces the string `"true"`. Use `{fx:True}` when the value must be Boolean
regardless of the assignment target.

As a method argument, the unquoted keyword `true` is a boolean literal. Quote it as `'true'` to pass a
string, or qualify it as a path such as `:element.true` to refer to a property named `true`.

## Usage

```xml
<!-- Using the fx:True markup extension -->
<Button visible="{fx:True}"/>

<!-- Using type coercion -->
<Button visible="true"/>

<!-- The Object-valued property receives Boolean.TRUE, not the string "true" -->
<Button userData="{fx:True}"/>

<!-- Boolean and string literals as method arguments -->
<Button text="$String.format('%s / %s', true, 'true')"/>
```

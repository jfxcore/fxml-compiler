---
layout: default
title: fx:False
parent: FXML/2 language reference
---

# fx:False markup extension
The `fx:False` markup extension specifies the boolean `false` value.

In many cases, using the `fx:False` markup extension is not necessary. In a boolean assignment context,
the text `"false"` is automatically [converted](../type-coercion.html) to a boolean value.

Text assignment is target-typed, so the text `"false"` has no intrinsic boolean type. In particular, assigning
`value="false"` to an `Object` property produces the string `"false"`. Use `{fx:False}` when the value must be Boolean
regardless of the assignment target.

As a method argument, the unquoted keyword `false` is a boolean literal. Quote it as `'false'` to pass a
string, or qualify it as a path such as `:self.false` to refer to a property named `false`.

## Usage

```xml
<!-- Using the fx:False markup extension -->
<Button visible="{fx:False}"/>

<!-- Using type coercion -->
<Button visible="false"/>

<!-- The Object-valued property receives Boolean.FALSE, not the string "false" -->
<Button userData="{fx:False}"/>

<!-- Boolean and string literals as method arguments -->
<Button text="$String.format('%s / %s', false, 'false')"/>
```

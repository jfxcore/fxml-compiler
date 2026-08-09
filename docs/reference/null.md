---
layout: default
title: fx:Null
parent: FXML/2 language reference
---

# fx:Null markup extension
The `fx:Null` markup extension specifies the `null` value.

The text `"null"` has no intrinsic null value in an ordinary assignment. In particular, assigning `value="null"` to
an `Object` property produces the string `"null"`; use `{fx:Null}` when the assigned value must be null.

As a method argument, the unquoted keyword `null` is the null literal. Quote it as `'null'` to pass a string,
or qualify it as a path such as `:self.null` to refer to a property named `null`.

## Usage

```xml
<!-- Using the fx:Null markup extension -->
<Button graphic="{fx:Null}"/>

<!-- The Object-valued property receives null, not the string "null" -->
<Button userData="{fx:Null}"/>

<!-- Null and string literals as method arguments -->
<Button text="$String.format('%s / %s', null, 'null')"/>
```

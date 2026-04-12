---
layout: default
title: fx:False
parent: FXML 2.0 language reference
---

# fx:False markup extension
The `fx:False` markup extension specifies the boolean `false` value.

In many cases, using the `fx:False` markup extension is not necessary. In a boolean assignment context,
the text `"false"` is automatically converted to a boolean value by [coercion](../value-coercion.html).

## Usage

```xml
<!-- Using the fx:False markup extension -->
<Button visible="{fx:False}"/>

<!-- Using value coercion -->
<Button visible="false"/>
```

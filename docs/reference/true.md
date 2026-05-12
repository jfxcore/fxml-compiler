---
layout: default
title: fx:True
parent: FXML 2.0 language reference
---

# fx:True markup extension
The `fx:True` markup extension specifies the boolean `true` value.

In many cases, using the `fx:True` markup extension is not necessary. In a boolean assignment context,
the text `"true"` is automatically [converted](../type-coercion.html) to a boolean value.

## Usage

```xml
<!-- Using the fx:True markup extension -->
<Button visible="{fx:True}"/>

<!-- Using type coercion -->
<Button visible="true"/>
```

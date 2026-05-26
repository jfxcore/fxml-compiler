---
layout: default
title: fx:False
parent: FXML/2 language reference
---

# fx:False markup extension
The `fx:False` markup extension specifies the boolean `false` value.

In many cases, using the `fx:False` markup extension is not necessary. In a boolean assignment context,
the text `"false"` is automatically [converted](../type-coercion.html) to a boolean value.

## Usage

```xml
<!-- Using the fx:False markup extension -->
<Button visible="{fx:False}"/>

<!-- Using type coercion -->
<Button visible="false"/>
```

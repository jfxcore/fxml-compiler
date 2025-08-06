---
layout: default
title: Boolean operators
parent: Compiled bindings
nav_order: 6
---

# Boolean operators
A binding path expression can be prefixed with a boolean operator, which causes the expression to evaluate to a boolean value.

| Operator | Description |
|:-|:-|
| `!` | inverts the boolean value; converts `0` or `null` to `true` |
| `!!` | inverts the boolean value twice; converts `0` or `null` to `false` |

In the following example, the controls are disabled or hidden when the bound list is empty:

```xml
<!-- disabled=true when size=0 -->
<MyAddressControl disabled="${!user::addresses.size}"/>

<!-- visible=false when size=0 -->
<MyAddressControl visible="${!!user::addresses.size}"/>
```

## Applicability
A boolean operator is applicable to any expression type, not just boolean expressions. However, restrictions apply for some binding modes.

| Binding mode | Applicable |
|:-|:-|
| [`fx:once`](../reference/once.html) | yes, all expression types |
| [`fx:bind`](../reference/bind.html) | yes, all expression types |
| [`fx:bindBidirectional`](../reference/bindBidirectional.html) | only if the binding source implements `WritableValue<Boolean>` (e.g. `DoubleProperty`) |
| [`fx:content`](../reference/content.html) | no |
| [`fx:bindContent`](../reference/bindContent.html) | no |
| [`fx:bindContentBidirectional`](../reference/bindContentBidirectional.html) | no |

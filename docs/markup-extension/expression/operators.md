---
layout: default
title: Boolean operators
parent: Compiled expressions
nav_order: 5
---

# Boolean operators
An expression can be prefixed with a boolean operator, which causes the expression to evaluate to a boolean value.

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
A boolean operator is applicable to any expression type, not just boolean expressions.
However, restrictions apply depending on which expression type is used.

| Markup extension | Applicable |
|:-|:-|
| [`fx:evaluate`](../../reference/evaluate.html) | all non-content expressions |
| [`fx:observe`](../../reference/observe.html) | all non-content expressions |
| [`fx:synchronize`](../../reference/synchronize.html) | only if the binding source implements `WritableValue<Boolean>` |

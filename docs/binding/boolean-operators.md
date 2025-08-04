---
layout: default
title: Boolean operators
parent: Compiled bindings
nav_order: 6
---

# Boolean operators
A binding path expression can be prefixed with a boolean operator, which causes the expression to evaluate to a boolean value. Boolean operators are applicable to any expression type, not just boolean expressions.

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
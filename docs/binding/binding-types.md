---
layout: default
title: Types of bindings
parent: Compiled bindings
nav_order: 1
---

# Types of bindings
Binding expressions in FXML 2.0 are implemented using intrinsic elements that can be used in either element notation or [attribute notation](../compact-notation.html). An additional short notation is supported for brevity.

The following table lists all binding types, as well as the corresponding JavaFX methods:

| Intrinsic element | Short notation | JavaFX method |
|:-|:-|:-|
| `{fx:once path=source}` | `$source` | `WritableValue.setValue(source)` |
| `{fx:bind path=source}` | `${source}` | `Property.bind(source)` |
| `{fx:bindBidirectional path=source}` | `#{source}` | `Property.bindBidirectional(source)` |
| `{fx:content path=source}` | `$..source` | `Collection.addAll(source)`<br>`Map.putAll(source)` |
| `{fx:bindContent path=source}` | `${..source}` | `ListProperty.bindContent(source)`<br>`SetProperty.bindContent(source)`<br>`MapProperty.bindContent(source` |
| `{fx:bindContentBidirectional path=source}` | `#{..source}` | `ListProperty.bindContentBidirectional(source)`<br>`SetProperty.bindContentBidirectional(source)`<br>`MapProperty.bindContentBidirectional(source)` |

{: .note }
Since `path` is the [default property](../compact-notation.html#default-property) of all binding intrinsics, it doesn't need to be explicitly specified. For example, `{fx:bind path=source}` and `{fx:bind source}` are equivalent.

{: .note }
This documentation will use the short notation in most code samples.

## Runtime overhead of bindings
[`fx:once`](../reference/once.html) and [`fx:content`](../reference/content.html) have the lowest run-time overhead, since no listener maintentance is required to keep the binding target in sync with the binding source. All other bindings may require setting up change listeners or additional code generation, and should only be used when the source value is expected to change.
---
layout: default
title: Types of bindings
parent: Compiled bindings
nav_order: 1
---

# Types of bindings
Binding expressions in FXML 2.0 are implemented as markup extensions that can be used in either element notation or attribute notation. An additional compact notation is supported for brevity.

The following table lists all binding types, as well as the corresponding JavaFX methods:

| Markup extension | Compact notation | JavaFX method |
|:-|:-|:-|
| `{fx:once path=source}` | `$source` | `WritableValue.setValue(source)` |
| `{fx:bind path=source}` | `${source}` | `Property.bind(source)` |
| `{fx:bindBidirectional path=source}` | `#{source}` | `Property.bindBidirectional(source)` |
| `{fx:content path=source}` | `$..source` | `Collection.addAll(source)`<br>`Map.putAll(source)` |
| `{fx:bindContent path=source}` | `${..source}` | `ListProperty.bindContent(source)`<br>`SetProperty.bindContent(source)`<br>`MapProperty.bindContent(source` |
| `{fx:bindContentBidirectional path=source}` | `#{..source}` | `ListProperty.bindContentBidirectional(source)`<br>`SetProperty.bindContentBidirectional(source)`<br>`MapProperty.bindContentBidirectional(source)` |

{: .note }
Since `path` is the [default property](../property-notation.html#default-property) of all binding markup extensions, it doesn't need to be explicitly specified. For example, `{fx:bind path=source}` and `{fx:bind source}` are equivalent.

{: .note }
This documentation will use the compact notation in most code samples.

## Runtime overhead of bindings
[`fx:once`](../reference/once.html) and [`fx:content`](../reference/content.html) have the lowest run-time overhead, since no listener maintentance is required to keep the binding target in sync with the binding source. All other bindings may require setting up change listeners or additional code generation, and should only be used when the source value is expected to change.
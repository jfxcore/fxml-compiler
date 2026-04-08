---
layout: default
title: Compiled expressions
parent: Markup extensions
nav_order: 1
has_children: true
---

# Compiled expressions
FXML 2.0 supports compiled expressions for one-time evaluation, observation, and bindings.
These expressions are implemented as intrinsic markup extensions and compiled to specialized code by the FXML compiler.

| Markup extension | [Prefix notation](../markup-extension.html#prefix-shorthand-in-attribute-notation) | [Usage](../markup-extension.html#where-markup-extensions-can-be-used) |
|:-|:-|:-|
| [`fx:evaluate`](../reference/evaluate.html) | `$source` | value supplier, property consumer |
| [`fx:observe`](../reference/observe.html) | `${source}` | value supplier, property consumer |
| [`fx:synchronize`](../reference/synchronize.html) | `#{source}` | property consumer |

`{fx:evaluate}` has the lowest runtime overhead, since no listener maintenance is required after the initial assignment.
`{fx:observe}` and `{fx:synchronize}` may require listeners or additional generated code to keep the target synchronized with the source.

## Setting up a binding
Here's how a simple binding is specified in FXML 2.0, using different but equivalent notations:

<div class="filename">com/sample/MyControl.fxml</div>
```xml
<VBox xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
      fx:class="com.sample.MyControl">
    <!-- fx:observe markup extension with source path -->
    <Button text="{fx:observe path=caption}"/>

    <!-- 'path' is the default property of the fx:observe markup extension, so it can be omitted -->
    <Button text="{fx:observe caption}"/>

    <!-- Prefix notation, similar to FXML 1.0 -->
    <Button text="${caption}"/>
</VBox>
```

<div class="filename">com/sample/MyControl.java</div>
```java
public class MyControl extends MyControlBase {
    private final StringProperty caption = new SimpleStringProperty("Click me");

    public StringProperty captionProperty() {
        return caption;
    }

    public MyControl() {
        initializeComponent();
    }
}
```

## Applying expressions to properties
When an intrinsic expression is assigned to a property (so it acts as a
[property consumer](../markup-extension.html#where-markup-extensions-can-be-used)),
the following operations are performed on the target property:

| Markup extension | Prefix notation | Operation |
|:-|:-|:-|
| `{fx:evaluate source}` | `$source` | assign the resolved value once |
| `{fx:observe source}` | `${source}` | `Property.bind(source)` |
| `{fx:synchronize source}` | `#{source}` | `Property.bindBidirectional(source)` |
| `{fx:evaluate ..source}` | `$..source` | `Collection.addAll(source)`<br>`Map.putAll(source)` |
| `{fx:observe ..source}` | `${..source}` | `ListProperty.bindContent(source)`<br>`SetProperty.bindContent(source)`<br>`MapProperty.bindContent(source)` |
| `{fx:synchronize ..source}` | `#{..source}` | `ListProperty.bindContentBidirectional(source)`<br>`SetProperty.bindContentBidirectional(source)`<br>`MapProperty.bindContentBidirectional(source)` |

{: .note }
Since `path` is the [default property](../property-notation.html#default-property) of all intrinsic expression extensions, `{fx:observe path=source}` and `{fx:observe source}` are equivalent.

{: .note }
This documentation will use the prefix notation in most code samples.

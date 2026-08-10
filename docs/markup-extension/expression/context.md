---
layout: default
title: Evaluation context
parent: Compiled expressions
nav_order: 2
---

# Evaluation context
By default, expressions are evaluated against the root element of the FXML document.
This can be used to bind properties of controls to custom properties defined in the [code-behind class](../../code-behind.html).
For other use cases, alternative evaluation contexts can be specified:

| Selector | Evaluates against |
|:-|:-|
| (no notation) | root element, or [`fx:context`](../../reference/context.html) if set |
| `:context` | explicit selector for the no-notation default context |
| `:root` | root element, regardless of `fx:context` |
| `:element` | current element |
| `:parent(0)` | current element, equivalent to `:element` |
| `:parent` or `:parent(1)` | immediate parent of the current element |
| `:parent(N)` | element `N` levels above the current element |
| `:parent<MyType>` | nearest ancestor assignable to `MyType` |
| `:parent<MyType>(0)` | current element, provided it is assignable to `MyType` |
| `:parent<MyType>(N)` | `N`th matching ancestor, where the nearest matching ancestor is `1` |

Context selectors are specified as part of the expression path:
```xml
<Rectangle height="${:element.width}"/>
```

A context selector is a complete expression primary. For example:
* `:context` returns the default evaluation context object
* `:element` returns the current element
* `:parent` returns the immediate parent element
* `:parent === owner` is an identity comparison with another object

The type qualifier and depth can be used independently or together: `:parent<Pane>` selects the nearest `Pane`
ancestor, while `:parent<Pane>(2)` selects the second matching `Pane` ancestor. A depth of `0` always denotes the
current element and succeeds for a typed selector only when the current element has the requested type.

{: .warning }
> Using [`fx:Evaluate`](../../reference/evaluate.html) with `:element` or `:parent` selectors may lead to unexpected
> results, since the evaluated value may depend on the order of element initialization.
>
> Consider the following example:
>
> ```xml
> <Pane prefWidth="123">
>     <Label prefWidth="$:parent.prefWidth"/>
> </Pane>
> ```
>
> Perhaps surprisingly, `Label.prefWidth` will be `-1.0` instead of `123.0`. The reason for this behavior is that
> child elements are initialized before parent elements, which means that when the `fx:Evaluate` expression is
> evaluated, `Pane.prefWidth` still has its default value of `-1.0`.
>
> In cases like these, an observable binding expression should be preferred.

## Changing the default evaluation context with `fx:context`
The default evaluation context is the root element of the FXML document. This can be changed with the
[`fx:context`](../../reference/context.html) attribute, which can be bound to an arbitrary object:

<div class="filename">com/sample/MyControl.java</div>
```java
public class MyControl extends MyControlBase {
    final MyBindingContext myContext;

    MyControl() {
        myContext = new MyBindingContext();
        initializeComponent();
    }
}

class MyBindingContext {
    ObjectProperty<User> userProperty();
}
```

<div class="filename">com/sample/MyControl.fxml</div>
```xml
<StackPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
           fx:subclass="com.sample.MyControl"
           fx:context="$myContext">
    <!-- "user.name" will be evaluated against "myContext" -->
    <Label text="${user.name}"/>
</StackPane>
```

{: .note }
`fx:context` can be set not only to a specific object, but also be bound to an
[`ObservableValue`](https://openjfx.io/javadoc/17/javafx.base/javafx/beans/value/ObservableValue.html)
if the evaluation context is expected to change. Note that this will incur listener management overhead.

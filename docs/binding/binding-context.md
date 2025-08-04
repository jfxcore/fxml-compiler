---
layout: default
title: Binding context
parent: Compiled bindings
nav_order: 3
---

# Binding context
By default, binding paths are evaluated against the root element of the FXML document. This can be used to bind properties of controls to custom properties defined in the [code-behind class](../code-behind.html). For other use cases, alternative binding contexts can be specified:

| Selector | Evaluates against |
|:-|:-|
| (no notation) | root element, or [`fx:context`](../reference/context.html) if set |
| `self` | current element |
| `parent` | parent of the current element, equivalent to `parent[0]` |
| `parent[N]` | N-th parent of the current element |
| `parent[MyType]` | first `MyType` parent of the current element |
| `parent[MyType:N]` | N-th `MyType` parent of the current element |

Binding context selectors are specified as part of the binding path, separated by a forward slash:
```xml
<Rectangle height="${self/width}"/>
```

{: .warning }
> Using `fx:once` or `fx:content` expressions with `self` or `parent` selectors may lead to unexpected results, since the evaluated value may depend on the order of element initialization.
>
> Consider the following example:
>
> ```xml
> <Pane prefWidth="123">
>     <Label prefWidth="$parent/prefWidth"/>
> </Pane>
> ```
>
> Perhaps surprisingly, `Label.prefWidth` will be `-1.0` instead of `123.0`. The reason for this behavior is that child elements are initialized before parent elements, which means that when the `fx:once` expression is evaluated, `Pane.prefWidth` still has its default value of `-1.0`.
>
> In cases like these, an observable binding expression should be preferred.

## Changing the default binding context with `fx:context`
The default binding context is the root element of the FXML document. This can be changed with the [`fx:context`](../reference/context.html) attribute, which can be bound to an arbitrary object:

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
           fx:class="com.sample.MyControl"
           fx:context="$myContext">
    <!-- "user.name" will be evaluated against "myContext" -->
    <Label text="${user.name}"/>
<StackPane/>
```

{: .note }
`fx:context` can not only be set to a specific object, but also bound to an observable value if the binding context is expected to change. Note that this will incur listener management overhead.
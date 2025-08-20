---
layout: default
title: Binding path
parent: Compiled bindings
nav_order: 2
---

# Binding path
The binding source in the expression `<Label text="${path.to.userName}"/>` may be any of the following:
* a plain field or method with the name `userName`, returning a `String`
* a Java Beans-style method with the name `getUserName`, returning a `String` (optionally with the `is` prefix if the type is `boolean`)
* a JavaFX Beans-style method with the name `userNameProperty`, returning an `ObservableValue<String>` (or `Property` in case of bidirectional bindings)
* an attached property like `BorderPane.getAlignment(Node)` or `VBox.getMargin(Node)`

{: .note }
The binding path is resolved by evaluating it against the [binding context](binding-context.html).
Alternatively, it can also be a statically reachable path, beginning with the name of a class.

## Member selection operator `.`
A binding path can select members of a type using the member selection operator:

```xml
<Label text="${user.address.streetName}"/>
```

The member selection operator is null-tolerant and short-circuiting, which means that if one of the members is `null`, the rest of the expression evaluates to the default value of its last member. For example, if the last member in the expression `${user.address.postalCode}` is of type `int`, the binding expression evalutes to zero if `user` or `address` is `null`.

If the path contains `ObservableValue` members, the binding expression is automatically re-evaluated when any of the observable values are changed. This does not apply to `fx:once` and `fx:content` expressions, which are only assigned once and are never re-evaluated.

{: .note }
The member selection operator looks just like the dot operator in Java, but its semantics are slightly different. In particular, the member selection operator "hides" the difference between a member of type `Address` and a member of type `ObservableValue<Address>`. In both cases, a value of type `Address` is selected, but the second case allows the binding expression to be automatically re-evaluated when the value is changed.

## Observable selection operator `::`
In some cases, it can be necessary to select the `ObservableValue` instance itself, and not the value contained within. Consider the following binding expression:

```xml
<MyAddressControl count="${user.addresses.size}"/>
```

In this example, `adresses` is of type `ListProperty<Address>`. Since `ListProperty<Address>` implements `ObservableValue<ObservableList<Address>>`, the expression `user.addresses` would select the contained `ObservableList<Address>` value. Consequently, `.size` would select the `ObservableList.size()` method, returning an `int`. Since `ObservableList.size()` is not an observable property, the binding will not work as expected when the number of addresses in the list changes.

In order to solve this problem, the observable selection operator can be used to select the `ListProperty<Address>` instance itself, instead of the contained `ObservableList<Address>`:

```xml
<MyAddressControl count="${user::addresses.size}"/>
```

In this case, `.size` will select the `ListProperty.sizeProperty()` method, which returns an observable value. Now the binding is correctly re-evaluated when the number of addresses in the list changes.

{: .note }
Unlike the member selection operator, the observable selection operator can also be placed in front of the first path segment.
For example, `${::addresses.size}` is a valid expression if `addresses` is an `ObservableValue`.

## Attached properties

An attached property can be selected by wrapping the qualified attached property name in parentheses. The qualified attached property name consists of the name of the declaring class and the name of the property:

```xml
<VBox>
    <Label VBox.margin="10" fx:id="myLabel"/>
    
    <!-- Selects VBox.getMargin(myLabel) -->
    <Label VBox.margin="$myLabel.(VBox.margin)"/>
</VBox>
```

## Generic type witness
When a generic method is selected, it can sometimes be necessary to specify a type witness in order to preserve type information. A generic type witness is specified in angle brackets after the method name:

```xml
<MyControl value="${path.to.genericGetter<String>.value}"/>
```

{: .note }
In XML files, the `<` character can only be used as a markup delimiter, and must be escaped using `&lt;` in attribute text. However, the FXML compiler accepts the non-standard literal form for better code readability.

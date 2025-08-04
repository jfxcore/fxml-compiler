---
layout: default
title: String conversion
parent: Compiled bindings
nav_order: 5
---

# String conversion binding for `StringProperty`
A `StringProperty` that is bidirectionally bound to a property of another type can use a `java.text.Format` or `javafx.util.StringConverter` to convert between the string representation and the bound type.

## Using `java.text.Format`
The `format` parameter of a bidirectional binding expression specifies the [path](binding-path.html) to a `java.text.Format` instance:
```xml
<TextField text="#{path.to.value; format=path.to.format}"/>
```
This corresponds to a bidirectional binding that is established with the `StringProperty.bindBidirectional(Property<?>, Format)` method.

## Using `javafx.util.StringConverter`
The `converter` parameter of a bidirectional binding expression specifies the [path](binding-path.html) to a `javafx.util.StringConverter` instance:
```xml
<TextField text="#{path.to.value; converter=path.to.converter}"/>
```
This corresponds to a bidirectional binding that is established with the `StringProperty.bindBidirectional(Property<T>, StringConverter<T>converter)` method.
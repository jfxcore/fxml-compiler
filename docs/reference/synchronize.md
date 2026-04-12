---
layout: default
title: fx:Synchronize, <span class="nav-inline-code">#{x}</span>
parent: FXML 2.0 language reference
---

# fx:Synchronize markup extension, #{x}
The `fx:Synchronize` markup extension establishes a bidirectional binding.  It is only valid in
[property-consumer](../markup-extension.html#where-markup-extensions-can-be-used) position and cannot be used
where a value or [`ObservableValue`](https://openjfx.io/javadoc/17/javafx.base/javafx/beans/value/ObservableValue.html)
is expected.

`fx:Synchronize` establishes a bidirectional binding between the target and source
[`Property`](https://openjfx.io/javadoc/17/javafx.base/javafx/beans/property/Property.html).

A leading `..` in the source path selects the [content](../markup-extension/expression/path.html#content-selection-operator-)
of the target and source collection. In that form, `fx:Synchronize` establishes a bidirectional content binding between
the source and target collections.

Its prefix notation is `#{x}`, where <span class="inline-code">x</span> is the [source path](../markup-extension/expression/path.html).

## Properties

| Property | Description |
|:-|:-|
| `path` | A string that specifies the [source path](../markup-extension/expression/path.html). This is the [default property](../property-notation.html#default-property). |
| `format` | The path to a `java.text.Format` instance passed to `StringProperty.bindBidirectional(Property<?>, Format)`. The `format` path is evaluated once when the synchronization is set up. This property is only applicable to `StringProperty` targets. |
| `converter` | The path to a `javafx.util.StringConverter` instance passed to `StringProperty.bindBidirectional(Property<T>, StringConverter<T>)`. The `converter` path is evaluated once when the synchronization is set up. This property is only applicable to `StringProperty` targets. |
| `inverseMethod` | The path to an inverse method for the method referenced in `path`. This can also be the name of a constructor. |

## Usage

```xml
<!-- Element notation -->
<object>
    <property>
        <fx:Synchronize path="myPath"/>
    </property>
</object>

<!-- Attribute notation -->
<object property="{fx:Synchronize path=myPath}"/>

<!-- Attribute notation with omitted "path" -->
<object property="{fx:Synchronize myPath}"/>

<!-- Prefix notation -->
<object property="#{myPath}"/>

<!-- Bidirectional binding with javafx.util.StringConverter -->
<object property="#{myPath; converter=myConverterPath}"/>

<!-- Bidirectional binding with java.text.Format -->
<object property="#{myPath; format=myFormatPath}"/>
```

## Inverse method in a bidirectional method binding

{: .note }
The `@InverseMethod` annotation is available in the [markup](https://github.com/jfxcore/markup) runtime library.

If a `fx:Synchronize` expression converts the source property with a [method call](../markup-extension/expression/function.html),
the compiler needs a way to translate changes on the target property back to the source property.

For a method <em>m(B)→A</em>, the inverse method must have the corresponding shape <em>n(A)→B</em>.
In other words, both methods must accept exactly one argument, and the parameter and return types must be reversed.

The inverse method can be supplied explicitly with the `inverseMethod` parameter:

```xml
<TextField text="#{stringToDouble(stringProp); inverseMethod=doubleToString}"/>
```

When the method declaration is annotated with `@InverseMethod`, the inverse method can be inferred automatically
and does not need to be repeated in markup:

```java
@InverseMethod("stringToDouble")
public String doubleToString(double value) {
    return Double.toString(value);
}

@InverseMethod("doubleToString")
public double stringToDouble(String value) {
    return value != null ? Double.parseDouble(value) : 0;
}
```

```xml
<TextField text="#{doubleToString(doubleProp)}"/>
```

The `InverseMethod` annotation names another method on the same class. It is legal for a method to name itself
as its own inverse when its parameter type and return type are the same.

An explicit `inverseMethod=...` in markup is still useful when the annotation is not present or when a different
inverse should be chosen than the one declared on the method.

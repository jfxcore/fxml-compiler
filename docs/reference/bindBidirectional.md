---
layout: default
title: fx:bindBidirectional, <span class="nav-inline-code">#{x}</span>
parent: FXML 2.0 language reference
nav_order: 2
---

# fx:bindBidirectional markup extension, #{x}
The `fx:bindBidirectional` markup extension establishes a bidirectional binding. It can be set on any `javafx.beans.property.Property` instance, and is equivalent to invoking the `Property.bindBidirectional(Property)` API in Java code.

Its compact notation is `#{x}`, where <span class="inline-code">x</span> is the [binding path](../binding/binding-path.html).

## Properties

| Property | Description |
|:-|:-|
| `path` | A string that specifies the [binding path](../binding/binding-path.html). This is the [default property](../property-notation.html#default-property). |
| `format` | The path to a `java.text.Format` instance that is passed to the `StringProperty.bindBidirectional(Property<?>, Format)` method.<br>Note that this path will only be evaluated once when the binding is set up.<br>The `format` property is only applicable to `StringProperty` bindings. |
| `converter` | The path to a `javafx.util.StringConverter` instance that is passed to the `StringProperty.bindBidirectional(Property<T>, StringConverter<T>)` method.<br>Note that this path will only be evaluated once when the binding is set up.<br>The `converter` property is only applicable to `StringProperty` bindings. |
| `inverseMethod` | The path to an inverse method for the method referenced in `path`.<br>This can also be the name of a constructor. |

## Usage

```xml
<!-- Element notation -->
<object>
    <property>
        <fx:bindBidirectional path="myPath"/>
    </property>
<object>

<!-- Attribute notation -->
<object property="{fx:bindBidirectional path=myPath}"/>

<!-- Attribute notation with omitted "path" -->
<object property="{fx:bindBidirectional myPath}"/>

<!-- Compact notation -->
<object property="#{myPath}"/>

<!-- Bidirectional binding with StringConverter -->
<object property="{fx:bindBidirectional myPath; converter=myConverterPath}"/>

<!-- Bidirectional binding with Format -->
<object property="{fx:bindBidirectional myPath; format=myFormatPath}"/>
```
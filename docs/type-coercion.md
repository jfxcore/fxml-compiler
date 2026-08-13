---
layout: default
title: Type coercion
nav_order: 6
---

# Type coercion
When a value is assigned to a property in an FXML document, the textual value is automatically converted
to the property type. This process, called type coercion, is supported in the following scenarios:
1. Primitive types and primitive boxes, for example:
    ```xml
    <!-- Converting "true" to a boolean value -->
    <Button visible="true"/>
    
    <!-- Converting "10" to a double value -->
    <Button lineSpacing="10"/>
    ```
2. Class literals, for example:
   ```xml
   <!-- Converting "Double" to a java.lang.Class instance -->
   <MyControl type="Double"/>
   ```
3. Enum constants, for example:
    ```xml
    <!-- Converting "LEFT" to the enum constant ContentDisplay.LEFT -->
    <Button contentDisplay="LEFT"/>
    ```
4. Static fields on the declaring class, for example:
    ```xml
    <!-- Converting "UNCONSTRAINED_RESIZE_POLICY" to the value of
         the static field TableView.UNCONSTRAINED_RESIZE_POLICY -->
    <TableView columnResizePolicy="UNCONSTRAINED_RESIZE_POLICY"/>
    ```
5. Method [event handlers](event-handlers.html), for example:
    ```xml
    <Button onAction="handleActionEvent"/>
    ```
    In this case, `handleActionEvent` is resolved to a compatible method on the [code-behind](code-behind.html) class.
6. Color values, for example:
    ```xml
    <Button textFill="RED"/>
    <Button textFill="#FF0000"/>
    ```
    Any color literal that is accepted by the `Color.web` method is also accepted by FXML.

{: .note }
Values that use [markup extension](markup-extension.html) syntax are not treated as literals and therefore do not
participate in type coercion. This includes both the standard form such as `{StaticResource greeting}`
and a prefix shorthand such as `%greeting`. A markup extension is instead resolved against the type of
its target property, collection item, array component, or constructor parameter.

## Comma-separated lists
When a property has a collection or array type, its values can be written as a comma-separated list:
```xml
<Polygon points="0, 0, 50, 100, 100, 50"/>
```

List items can be literal values or value-producing [markup extensions](markup-extension.html).
Each list item is converted independently to the required element type.

If the property does not have a collection or array type, a comma-separated list has no special meaning and
is interpreted as a literal value. In the following example, the string `"hello, world"` is therefore assigned
to the `text` property:

```xml
<Label text="hello, world"/>
```

### Greedy parsing of markup extensions in prefix notation
A markup extension written in [prefix notation](markup-extension.html#prefix-shorthand-in-attribute-notation) can itself
contain a comma-separated list. Because prefix syntax has no closing delimiter, this inner list is greedy: it consumes
all subsequent comma-separated values. In the following example, `Jane`, `Doe`, and `@fallback.txt` all belong to
`formatArguments`: the `@fallback.txt` expression is not a second item in the outer `values` list:

```xml
<MessageList values="%greeting; formatArguments=Jane, Doe, @fallback.txt"/>
```

To continue the outer list instead, use brace-style notation to delimit the extension explicitly:

```xml
<MessageList values="{StaticResource greeting; formatArguments=Jane, Doe}, @fallback.txt"/>
```

## Implicit construction
An object instance can be created implicitly from a literal value or a value-producing [markup extension](markup-extension.html),
provided that the literal or expression type is compatible with the type of the constructor argument.
This conversion only works for constructors where the parameter is annotated with `@NamedArg`.

For example, the `javafx.geometry.Insets` class declares a constructor that accepts a double value:
```java
public class Insets {
    public Insets(@NamedArg("topRightBottomLeft") double topRightBottomLeft);
}
```

An `Insets` instance would normally be created like this:
```xml
<Button>
    <padding>
        <Insets topRightBottomLeft="10"/>
    </padding>
</Button>
```

However, since the literal `10` can be coerced to the type of the named constructor argument `topRightBottomLeft`,
the `Insets` object can also be created implicitly:
```xml
<Button padding="10"/>
```

A markup extension can supply the constructor argument in the same way. For example, if `model.uniformInset`
resolves to a number, the following attribute also creates an `Insets` instance:
```xml
<Button padding="$model.uniformInset"/>
```

## Implicit construction with multiple arguments
Implicit construction also works for constructors with multiple parameters, provided that all parameters
are annotated with `@NamedArg`. For example, we can create an instance of `Insets` with multiple arguments:

```xml
<Button>
    <padding>
        <Insets top="10" left="20" bottom="10" right="20"/>
    </padding>
</Button>
```

This also works with implicit construction using a comma-separated list:

```xml
<Button padding="10, 20, 10, 20"/>
<Button padding="10, $model.rightInset, 10, $model.leftInset"/>
```

In both examples, the four values correspond to the `top`, `right`, `bottom`, and `left` parameters of the `Insets`
constructor. Each argument is resolved against its parameter type.

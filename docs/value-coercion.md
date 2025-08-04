---
layout: default
title: Value coercion
nav_order: 7
---

# Value coercion
If a literal value is assigned to a property in an FXML 2.0 document, the literal value is automatically converted to the type of its property. This process, called value coercion, is supported in the following scenarios:
1. Primitive types and primitive boxes, for example:
    ```xml
    <!-- Converting "true" to a boolean value -->
    <Button visible="true"/>
    
    <!-- Converting "10" to a double value -->
    <Button lineSpacing="10"/>
    ```
2. Enum constants, for example:
    ```xml
    <!-- Converting "LEFT" to the enum constant ContentDisplay.LEFT -->
    <Button contentDisplay="LEFT"/>
    ```
3. Static fields on the declaring class, for example:
    ```xml
    <!-- Converting "UNCONSTRAINED_RESIZE_POLICY" to the value of
         the static field TableView.UNCONSTRAINED_RESIZE_POLICY -->
    <TableView columnResizePolicy="UNCONSTRAINED_RESIZE_POLICY"/>
    ```
4. Color values, for example:
    ```xml
    <Button textFill="RED"/>
    <Button textFill="#FF0000"/>
    ```
    Any color literal that is accepted by the `Color.web` method is also accepted by FXML.

## Implicit constructor coercion
An object instance can also be created implicitly from a literal value, provided that the literal value can be coerced to the type of the constructor argument. This conversion only works for constructors where the parameter is annotated with `@NamedArg`.

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
        <Insets topLeftBottomRight="10"/>
    </padding>
</Button>
```

However, since the literal `10` can be coerced to the named constructor argument `topLeftBottomRight`, the `Insets` object can also be created implicitly:
```xml
<Button padding="10"/>
```

## Implicit constructor coercion with multiple arguments
Implicit constructor coercion also works for constructors with multiple parameters, provided that all parameters are annotated with `@NamedArg`. For example, we can create an instance of `Insets` with multiple arguments:
```xml
<Button>
    <padding>
        <Insets top="10" left="20" bottom="10" right="20"/>
    </padding>
</Button>
```

This also works with implicit coercion by separating the arguments with commas:
```xml
<Button padding="10,20,10,20"/>
```

{: .note }
A comma-separated list can only contain literal values, it cannot contain other types of expressions.
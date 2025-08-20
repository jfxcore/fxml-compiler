---
layout: default
title: Markup extensions
nav_order: 8
---

# Markup extensions
Attribute values in an FXML document are [coerced](value-coercion.html) to the type of their property with a range of built-in conversions. This process can be customized with markup extensions to support advanced scenarios that the FXML 2.0 language does not offer out of the box.

A markup extension can be used in attribute notation or element notation. When used in attribute notation, it has a special syntax where the name of the markup extension is surrounded by curly braces:

```xml
<Label text="{MyExtension}"/>
```

In order to have `{MyExtension}` be interpreted as a literal string instead, it must be prefixed with the following escape sequence: `{}{MyExtension}`

In element notation, the markup extension looks like a regular XML element:

```xml
<Label>
    <text>
        <MyExtension/>
    </text>
</Label>
```

## Types of markup extensions
Markup extensions have two general forms: property consumers and value suppliers. A property consumer extension can only be applied to a JavaFX property (i.e. a property implementing `Property` or `ReadOnlyProperty`). A value supplier extension can also be applied to method or constructor arguments.

All markup extensions implement one of the following interfaces in the `org.jfxcore.markup` package:

| Property consumer extensions | Supplier extensions |
|:-|:-|
| `MarkupExtension.PropertyConsumer<T>` | `MarkupExtension.Supplier<T>` |
| `MarkupExtension.ReadOnlyPropertyConsumer<T>` | `MarkupExtension.BooleanSupplier` |
| | `MarkupExtension.IntSupplier` |
| | `MarkupExtension.LongSupplier` |
| | `MarkupExtension.FloatSupplier` |
| | `MarkupExtension.DoubleSupplier` |

## Configuring a markup extension
Markup extensions can expose configuration parameters via constructor arguments annotated with `@NamedArg`, JavaFX properties, or getter/setter pairs:

```java
public final class MyExtension implements MarkupExtension.PropertyConsumer<String> {

    private final int param1;
    private final IntegerProperty param2 = new SimpleIntegerProperty(this, "param2");
    private int param3;

    // Named constructor parameter
    public MyExtension(@NamedArg("param1") int param1) {
        this.param1 = param1;
    }

    // JavaFX property
    public IntegerProperty param2Property() { return param2; }

    // Getter/setter pair
    public int getParam3() { return param3; }
    public void setParam3(int value) { param3 = value; }

    @Override
    public void accept(Property<String> property, MarkupContext context) throws Exception {
       // ...
    }
}
```

## Implementing a markup extension
The following example implements a markup extension that converts the name of an application resource to a `String`, `URL`, or `URI` representation, depending on the target type:

<div class="filename">FXML</div>
```xml
<Image url="{Resource /path/to/image.jpg}"/>
```

<div class="filename">Java code</div>
```java
@DefaultProperty("name")
public final class Resource implements MarkupExtension.Supplier<Object> {

    private final String name;

    public Resource(@NamedArg("name") String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null").trim();
    }

    @Override
    @ReturnType({String.class, URI.class, URL.class})
    public Object get(MarkupContext context) throws Exception {
        URL url = name.startsWith("/") ?
            Thread.currentThread().getContextClassLoader().getResource(name.substring(1)) :
            context.getRoot().getClass().getResource(name);

        if (url == null) {
            throw new RuntimeException("Resource not found: " + name);
        }

        Class<?> targetType = context.getTargetType();

        if (targetType.isAssignableFrom(String.class)) {
            return url.toExternalForm();
        }

        if (targetType.isAssignableFrom(URI.class)) {
            return url.toURI();
        }

        return url;
    }
}
```

The markup extension in this example uses several FXML features:
1. It defines a `@DefaultProperty`, which allows users to omit the `name` property in the markup extension invocation. If not for the default property, users would have to explicitly spell out the name of the constructor parameter: `{Resource name=/path/to/image.jpg}`.
2. It implements `MarkupExtension.Supplier<Object>` to make the extension compatible with `String`, `URL`, and `URI` target types, as there is no other common base class other than `Object`. However, it restricts the set of target types with the `@ReturnType` annotation. This allows the FXML compiler to type-check the markup extension usage at compile time, instead of potentially running into `ClassCastException` later at runtime.
3. It queries the `MarkupContext` at runtime to decide which type of object to return based on the type of the property or argument targeted by the markup extension.

{: .warning }
Note that the `MarkupContext` is only valid during the invocation of the `get(MarkupContext)` method, any attempt to access the markup context after the method has completed is undefined behavior and can lead to unpredictable results.
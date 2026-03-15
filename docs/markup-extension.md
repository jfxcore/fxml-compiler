---
layout: default
title: Markup extensions
nav_order: 8
has_children: true
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

## Built-in resource markup extensions
The FXML 2.0 runtime library includes markup extensions for resolving classpath resources and localizable resources from a `ResourceContext`:

| Markup extension | Description |
|:-|:-|
| [`ClassPathResource`](markup-extension/class-path-resource.html) | Resolves a classpath resource to a `String`, `URL`, or `URI`, depending on the target type |
| [`StaticResource`](markup-extension/static-resource.html) | Resolves a localizable resource once and supplies the result to the target |
| [`DynamicResource`](markup-extension/dynamic-resource.html) | Resolves a localizable resource for a property and updates that property when the resource changes |

## Prefix shorthand in attribute notation
Markup extensions that use attribute notation can also be written with a single-character prefix. The `%` and `@` prefixes are available implicitly and expand to `StaticResource` and `ClassPathResource`, even if the FXML file does not import these markup extensions and does not declare any prefixes:

```xml
<?import javafx.scene.control.Label?>

<Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
       text="%greeting"
       graphic="@icons/app.png"/>
```

{: .note }
If the [markup](https://github.com/jfxcore/markup) runtime library is not on the compile classpath, compilation will fail when the built-in markup extensions or their implicit prefixes are used.

These examples are equivalent to the following long form:

```xml
<?import javafx.scene.control.Label?>

<Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
       text="{StaticResource greeting}"
       graphic="{ClassPathResource icons/app.png}"/>
```

If the markup extension has named arguments, they can also be specified in the prefix form:
```xml
<?import javafx.scene.control.Label?>

<Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
       text="%greeting; formatArguments=Jane, Doe, 1234.5"
       graphic="@icons/app.png"/>
```

The prefix form has the same semantics as the corresponding markup extension. In this example, `%...` follows the rules documented for [`StaticResource`](markup-extension/static-resource.html), and `@...` follows the rules documented for [`ClassPathResource`](markup-extension/class-path-resource.html).

{: .note }
Generic type arguments are not allowed in prefix form, so `%greeting` is valid, but `%<String>greeting` is not. If type arguments are required, use the regular markup extension syntax instead; for example `{StaticResource<String> greeting}`.

### Prefix declarations
Additional prefixes can be declared explicitly with a `<?prefix?>` processing instruction before the root element. Explicit declarations can also override the built-in defaults for `%` and `@`:

```xml
<?import javafx.scene.control.Label?>
<?import org.example.MyStaticResource?>

<?prefix % = MyStaticResource?>
<?prefix @ = org.example.MyClassPathResource?>

<Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
       text="%greeting"
       graphic="@icons/app.png"/>
```

A declared prefix must satisfy the following rules:

- It must be exactly one character.
- It must not be a letter or digit.
- It must not be one of the following reserved characters: `{ } ( ) [ ] < > , ; : = * / . # & " ' ? _`

In practice, punctuation characters such as `%` and `@` are good choices.

{: .note }
The `%` and `@` prefixes are reserved as built-in defaults for `StaticResource` and `ClassPathResource`. They can still be declared explicitly, in which case the explicit declaration overrides the built-in mapping for the current document.

### Escaping declared prefixes

Prefix shorthand uses the same escape mechanism as other attribute-form markup extensions. To treat a prefixed value as a literal string, prefix it with `{}`:

```xml
<Label text="{}%greeting"/>
```

The value assigned to `text` in this example is the literal string `%greeting`, and not a markup extension.

## Types of markup extensions
Markup extensions have two general forms: property consumers and value suppliers. A property consumer extension can only be applied to a JavaFX property (i.e. a property implementing `Property` or `ReadOnlyProperty`). A value supplier extension can also be applied to method or constructor arguments.

All markup extensions implement one of the following interfaces in the [markup](https://github.com/jfxcore/markup) runtime library:

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
<Image url="{ClassPathResource /path/to/image.jpg}"/>
```

<div class="filename">Java code</div>
```java
@DefaultProperty("value")
public final class ClassPathResource implements MarkupExtension.Supplier<Object> {

    private final String value;

    public ClassPathResource(@NamedArg("value") String value) {
        this.value = Objects.requireNonNull(value, "value cannot be null").trim();
    }

    @Override
    @ReturnType({String.class, URI.class, URL.class})
    public Object get(MarkupContext context) throws Exception {
        URL url = value.startsWith("/") ?
            Thread.currentThread().getContextClassLoader().getResource(value.substring(1)) :
            context.getRoot().getClass().getResource(value);

        if (url == null) {
            throw new RuntimeException("Resource not found: " + value);
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
1. It defines a `@DefaultProperty`, which allows users to omit the `value` property in the markup extension invocation. If not for the default property, users would have to explicitly spell out the name of the constructor parameter: `{ClassPathResource value=/path/to/image.jpg}`.
2. It implements `MarkupExtension.Supplier<Object>` to make the extension compatible with `String`, `URL`, and `URI` target types, as there is no other common base class other than `Object`. However, it restricts the set of target types with the `@ReturnType` annotation. This allows the FXML compiler to type-check the markup extension usage at compile time, instead of potentially running into `ClassCastException` later at runtime.
3. It queries the `MarkupContext` at runtime to decide which type of object to return based on the type of the property or argument targeted by the markup extension.

{: .warning }
Note that the `MarkupContext` is only valid during the invocation of the `get(MarkupContext)` method, any attempt to access the markup context after the method has completed is undefined behavior and can lead to unpredictable results.
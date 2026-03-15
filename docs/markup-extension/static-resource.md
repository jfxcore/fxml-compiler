---
layout: default
title: StaticResource
parent: Markup extensions
nav_order: 3
---

# StaticResource markup extension

{: .note }
This markup extension is available in the [markup](https://github.com/jfxcore/markup) runtime library.

The `StaticResource` markup extension resolves a localizable resource once and supplies the result to the assignment target. It is intended for values that are read during object construction and do not need to be updated afterward.

`StaticResource` obtains its values from a `ResourceContext`, which is supplied by the root element of the FXML document through `ResourceContextProvider`.

## Properties

| Property | Description |
|:-|:-|
| `key` | The resource key. This is the [default property](../property-notation.html#default-property). |
| `formatArguments` | Optional format arguments used when the target type is `String` or a compatible supertype. |

## Usage

`StaticResource` is a generic resource extension that can be written in raw form or in generic form. The raw form is usually sufficient, as the target type is reified at runtime:

```xml
<!-- Raw form -->
<Label text="{StaticResource greeting}"/>

<!-- Generic form -->
<Label text="{StaticResource<String> greeting}"/>
```

String resources can be formatted with arguments:

```xml
<!-- Literal arguments -->
<Label text="{StaticResource greetingWithNameAndNumber; formatArguments=Jane, Doe, 1234.5}"/>

<!-- One-time expression argument -->
<Label text="{StaticResource greetingWithNameAndNumber; formatArguments=Jane, Doe, $amount}"/>
```

`$amount` is evaluated once before formatting. Observable expressions such as `${amount}` (see [fx:bind](../reference/bind.html)) are not supported and will result in an exception when the markup extension is applied.

Non-string resources are converted to the target type:

```xml
<!-- javafx.geometry.Orientation -->
<Separator orientation="{StaticResource separatorOrientation}"/>

<!-- boolean -->
<MyPane active="{StaticResource active}"/>
```

`StaticResource` can also be used for constructor arguments and collection items.
In this example, the `count` and `active` attributes are named constructor arguments of `StatusBadge`:

```xml
<MyPane>
    <graphic>
        <StatusBadge count="{StaticResource count}" active="{StaticResource active}"/>
    </graphic>
</MyPane>
```

## Supplying the ResourceContext

The root object of the FXML document must implement `ResourceContextProvider` to provide the `ResourceContext` that is used by `StaticResource` to resolve the named resource.

A `ResourceContext` provides two kinds of lookup:

| Method | Used for |
|:-|:-|
| `getString(String, Object...)` | string targets and formatted string values |
| `getObject(String, Class<?>)` | non-string targets such as enums, numbers, and booleans |

The returned `ResourceContext` must be reusable for repeated lookups. When the FXML root is a JavaFX `Node`, the `ResourceContextProvider.getResourceContext()` method is only called once, and the returned `ResourceContext` is cached for subsequent lookups.

The most common way to create a resource context is with one of the `ResourceContext.ofResourceBundle(...)` factory methods. The following example uses a `ResourceBundle` directly:

```java
public class MyPane extends MyPaneBase implements ResourceContextProvider {

    private final ResourceContext resourceContext =
        ResourceContext.ofResourceBundle(ResourceBundle.getBundle("com.example.messages"));

    @Override
    public ResourceContext getResourceContext() {
        return resourceContext;
    }
}
```

`ResourceContext.ofResourceBundle(ResourceBundle)` uses the locale reported by the `ResourceBundle` for formatted string values. `ResourceContext.ofResourceBundle(ResourceBundle, Locale)` can be used when a specific formatting locale is required:

```java
public class MyPane extends MyPaneBase implements ResourceContextProvider {

    private final ResourceContext resourceContext =
        ResourceContext.ofResourceBundle(ResourceBundle.getBundle("com.example.messages"), Locale.US);

    @Override
    public ResourceContext getResourceContext() {
        return resourceContext;
    }
}
```

Because `StaticResource` performs a one-time lookup during initialization, later changes to the locale or to the underlying resource source do not affect values that have already been assigned.

## Formatting and conversion

`StaticResource` delegates resource lookup to the active `ResourceContext`. If the target type is `String` or a compatible supertype, the value is obtained from `ResourceContext.getString(...)`. For all other target types, the value is obtained from `ResourceContext.getObject(...)`.

The `ResourceContext` implementation defines how the resource string is interpolated with its `formatArguments`. A custom `ResourceContext` may therefore apply formatting semantics that are specific to the application.

Observable format arguments are not supported. If a format argument is an `ObservableValue`, an exception is thrown when the markup extension is constructed. For reactive formatted values, `DynamicResource` should be used instead.

### ResourceBundle-backed resource contexts

When one of the `ResourceContext.ofResourceBundle(...)` factory methods is used, string values are read from the `ResourceBundle` and formatted with `MessageFormat` when arguments are supplied. Non-string values are read from the bundle directly. Compatible values are returned as-is, while string-valued resources are converted to common primitive, wrapper, character, and enum target types.

For example, consider the following bundle entry and markup:

```properties
welcomeMessage=Hello {0} {1}, total = {2,number,#,##0.0}
```
```xml
<Label text="{StaticResource welcomeMessage; formatArguments=Jane, Doe, 1234.5}"/>
```

Applying the markup extension produces the string `Hello Jane Doe, total = 1,234.5` when the bundle-backed `ResourceContext` uses a locale that formats numbers in that form.

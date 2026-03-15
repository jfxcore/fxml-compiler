---
layout: default
title: DynamicResource
parent: Markup extensions
nav_order: 2
---

# DynamicResource markup extension

{: .note }
This markup extension is available in the [org.jfxcore.markup](https://github.com/jfxcore/markup) package.

The `DynamicResource` markup extension resolves a localizable resource for a JavaFX property and updates that property when the resource context or a format argument changes.

`DynamicResource` obtains its values from a `ResourceContext`, which is supplied by the root element of the FXML document through `ResourceContextProvider`.

## Properties

| Property | Description |
|:-|:-|
| `key` | The resource key. This is the [default property](../property-notation.html#default-property). |
| `formatArguments` | Optional format arguments used when the target type is `String` or a compatible supertype. Observable arguments are supported. |

## Usage

`DynamicResource` is a generic resource extension that can be written in raw form or in generic form. The raw form is usually sufficient, as the target type is reified at runtime:

```xml
<!-- Raw form -->
<Label text="{DynamicResource greeting}"/>

<!-- Generic form -->
<Label text="{DynamicResource<String> greeting}"/>
```

String resources can be formatted with literal arguments or expression arguments:

```xml
<!-- Literal arguments -->
<Label text="{DynamicResource greetingWithNameAndNumber; formatArguments=Jane, Doe, 1234.5}"/>

<!-- One-time expression argument -->
<Label text="{DynamicResource greetingWithNameAndMessage; formatArguments=Jane, Doe, $message}"/>

<!-- Observable expression argument -->
<Label text="{DynamicResource greetingWithNameAndMessage; formatArguments=Jane, Doe, ${message}}"/>
```

`$message` is evaluated once before formatting, while `${message}` remains observable and causes the formatted string to be recomputed when its value changes.

Non-string resources are converted to the target type:

```xml
<!-- boolean -->
<MyPane active="{DynamicResource active}"/>

<!-- javafx.geometry.Orientation -->
<Separator orientation="{DynamicResource separatorOrientation}"/>
```

`DynamicResource` can only be applied to JavaFX properties, but not to constructor or method arguments.

## Dynamic updates

If `formatArguments` contain `ObservableValue` instances, `DynamicResource` observes those values and recomputes the formatted string when they change.

If the `ResourceContext` implements `Observable`, the target property is updated when the resource context is invalidated. This is useful for locale-sensitive strings and other values that can change after initialization.

## Supplying the ResourceContext

The root object of the FXML document must implement `ResourceContextProvider` to provide the `ResourceContext` that is used by `DynamicResource` to resolve the named resource.

A `ResourceContext` provides two kinds of lookup:

| Method | Used for |
|:-|:-|
| `getString(String, Object...)` | string targets and formatted string values |
| `getObject(String, Class<?>)` | non-string targets such as enums, numbers, and booleans |

The returned `ResourceContext` must be reusable for repeated lookups. When the FXML root is a JavaFX `Node`, the `ResourceContextProvider.getResourceContext()` method is only called once, and the returned `ResourceContext` is cached for subsequent lookups.

The most common way to create a resource context is with one of the `ResourceContext.ofResourceBundle(...)` factory methods. The following example uses a `ResourceBundle` together with an observable locale:

```java
public class MyPane extends MyPaneBase implements ResourceContextProvider {

    private final ObjectProperty<Locale> locale = new SimpleObjectProperty<>(Locale.US);
    private final ResourceContext resourceContext =
        ResourceContext.ofResourceBundle(ResourceBundle.getBundle("com.example.messages"), locale);

    @Override
    public ResourceContext getResourceContext() {
        return resourceContext;
    }
}
```

`ResourceContext.ofResourceBundle(ResourceBundle)` and `ResourceContext.ofResourceBundle(ResourceBundle, Locale)` are also available when the locale is fixed. `ResourceContext.ofResourceBundle(ResourceBundle, ObservableValue<Locale>)` is useful for `DynamicResource`, because the returned `ResourceContext` becomes observable and can notify resource bindings when locale-dependent values should be refreshed.

## Formatting and conversion

`DynamicResource` delegates resource lookup to the active `ResourceContext`. If the target type is `String` or a compatible supertype, the value is obtained from `ResourceContext.getString(...)`. For all other target types, the value is obtained from `ResourceContext.getObject(...)`.

The `ResourceContext` implementation defines how the resource string is interpolated with its `formatArguments`. A custom `ResourceContext` may therefore apply formatting semantics that are specific to the application.

### ResourceBundle-backed resource contexts

When one of the `ResourceContext.ofResourceBundle(...)` factory methods is used, string values are read from the `ResourceBundle` and formatted with `MessageFormat` when arguments are supplied. Non-string values are read from the bundle directly. Compatible values are returned as-is, while string-valued resources are converted to common primitive, wrapper, character, and enum target types.

For example, consider the following bundle entry and markup:

```properties
welcomeMessage=Hello {0} {1}, total = {2,number,#,##0.0}
```
```xml
<Label text="{DynamicResource welcomeMessage; formatArguments=Jane, Doe, ${amount}}"/>
```

Applying the markup extension produces the string `Hello Jane Doe, total = 1,234.5` when the bundle-backed `ResourceContext` uses a locale that formats numbers in that form.

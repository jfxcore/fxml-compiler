---
layout: default
title: ClassPathResource, <span class="nav-inline-code">@x</span>
parent: Markup extensions
nav_order: 2
---

# ClassPathResource markup extension, @x

{: .note }
This markup extension is available in the [markup](https://github.com/jfxcore/markup) runtime library.

The `ClassPathResource` markup extension resolves a classpath resource and converts it to a `String`, `URL`,
or `URI`, depending on the type of the target property or argument.

By default, resources are resolved in the context of the FXML document's root class, with embedded resources
taking precedence. The optional `classLoader` property selects a different class loader for resource lookup.

Its default [prefix](../markup-extension.html#prefix-shorthand-in-attribute-notation) notation is `@x`, where <span class="inline-code">x</span> is the resource name.

## Properties

| Property | Description |
|:-|:-|
| `value` | The classpath resource name. This is the [default property](../property-notation.html#default-property). |
| `classLoader` | Optional `ClassLoader` for resource lookup. Defaults to `null`. |

## Usage

```xml
<ImageView>
    <image>
        <Image url="{ClassPathResource path/to/image.jpg}"/>
    </image>
</ImageView>
```

Quotes must be used when the resource name contains spaces:

```xml
<ImageView>
    <image>
        <Image url="{ClassPathResource 'path/to/image with spaces.jpg'}"/>
    </image>
</ImageView>
```

## Applicability

`ClassPathResource` is applicable to properties, constructor arguments, method arguments, and collection items.

The type of the assignment target determines the returned value:

| Assignment target | Result |
|:-|:-|
| `String` | `URL.toExternalForm()` |
| `URI` | `URL.toURI()` |
| `URL` | the resolved `URL` |

Using `ClassPathResource` with an incompatible assignment target is rejected by the FXML compiler.

## Resource resolution

When `classLoader` is omitted or `null`, resource lookup first checks for a matching
[embedded resource](../embedded-resource.html) declared in the FXML document.
An embedded resource takes precedence over an external resource with the same name.

If no embedded resource matches, resource lookup uses the document's root class as determined at compile time,
following the rules of `Class.getResource(String)`.

A leading slash makes the resource name absolute, so the root class's package is not prepended to the path:

```xml
<Image url="{ClassPathResource /com/sample/images/logo.png}"/>
```

A relative name is resolved against the root class's package. For example, for a root class in `com.sample`,
the following name resolves to `com/sample/images/background.png`:

```xml
<MyPane backgroundImage="{ClassPathResource images/background.png}"/>
```

If the resource cannot be found, `ClassPathResource` throws an exception at runtime.

## Custom class loader

Set `classLoader` to resolve a resource through a specific class loader:

```xml
<Image url="{ClassPathResource /images/logo.png;
             classLoader=$Thread.currentThread.contextClassLoader}"/>
```

The name is resolved from the supplied class loader's resource root. A leading `/`, if present, is removed before
calling `ClassLoader.getResource(String)`, so `images/logo.png` and `/images/logo.png` request the same resource.

Supplying a custom class loader skips lookup of embedded resources. If the class loader cannot find the resource,
an exception is thrown; resource lookup does not fall back to the document's root class.

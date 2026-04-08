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

If the resource name starts with `/`, the path is resolved with the context class loader of the current thread.
Otherwise, the path is resolved relative to the class of the FXML document's root element.

Its default [prefix](../markup-extension.html#prefix-shorthand-in-attribute-notation) notation is `@x`, where <span class="inline-code">x</span> is the resource name.

## Properties

| Property | Description |
|:-|:-|
| `value` | The classpath resource name. This is the [default property](../property-notation.html#default-property). |

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
A leading slash resolves the path against the context class loader of the current thread:

```xml
<Image url="{ClassPathResource /com/sample/images/logo.png}"/>
```

A relative path is resolved against the class of the FXML document's root element:

```xml
<MyPane backgroundImage="{ClassPathResource images/background.png}"/>
```

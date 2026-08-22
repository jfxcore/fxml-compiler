---
layout: default
title: Embedded resources
nav_order: 7
---

# Embedded resources
An embedded resource is a text file declared directly in an FXML/2 document. The resource is processed alongside
the document and made available as a classpath resource at runtime.

Embedded resources are useful for content that belongs to a single view and is most conveniently maintained together
with that view. A common example is a view-local stylesheet, but the same mechanism can be used for other textual
resources such as templates or configuration snippets.

In the following example, a stylesheet is embedded into an FXML/2 document:

<div class="filename">com/sample/MyView.fxml</div>
```xml
<?import javafx.scene.control.Button?>
<?import javafx.scene.layout.AnchorPane?>

<?resource styles.css text/css:
    .root {
        -fx-font-size: 1.1em;
    }
?>

<AnchorPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
            stylesheets="@styles.css">
    <Button text="Hello"/>
</AnchorPane>
```

## Declaring a resource
An embedded resource is declared with a `resource` processing instruction:

```text
<?resource <name> [<media-type>]:<content>?>
```

| Part | Description |
|:-|:-|
| `name` | The name of the resource. It must be a single file name; subdirectories are not accepted. |
| `media-type` | Optional media type. If omitted, defaults to `text/plain`. |
| `content` | The textual resource content.<br/>The colon is required and separates the declaration from the content. |

A resource name can be quoted when it contains spaces:

```xml
<?resource "dark theme.css" text/css:.root { -fx-base: black; }?>
```

Resource names cannot contain path separators or other characters that are not portable in file names.
Names that differ only in letter case are considered duplicates within the same document.

Resource declarations are scoped to the entire FXML document. They may appear before, inside, or after the root
element wherever XML permits a processing instruction; the declaration order and position is not significant.

## Loading and resource resolution
Embedded resources are loaded with [`ClassPathResource`](markup-extension/class-path-resource.html), using either its
regular markup-extension form or its prefix notation:

```xml
<AnchorPane stylesheets="@styles.css"/>
```

```xml
<AnchorPane stylesheets="{ClassPathResource styles.css}"/>
```

For a simple relative name such as `styles.css`, `ClassPathResource` resolves the resource in this order:

1. Look for a `styles.css` embedded resource.
2. If no embedded resource is found, look for an external resource with the same name.
3. If neither resource exists, an exception is thrown at runtime.

A name beginning with `/` does not perform embedded-resource lookup. A relative name that contains a path separator,
such as `theme/styles.css`, can only refer to external resources. Embedded resource declarations have
single-component names, so only simple relative names participate in embedded lookup.

## Whitespace normalization
A same-line resource payload is preserved exactly. In particular, spaces immediately after the colon or immediately
before `?>` are part of the resource:

```xml
<?resource message.txt: Hello ?>
```

Multiline declarations are normalized so that the FXML indentation used to lay out the declaration does not become
part of the resource. The compiler applies these rules:

1. If the colon is followed only by spaces or tabs and then a line break, that indentation and line break are removed.
2. If the final line break is followed only by spaces or tabs before `?>`, that line break and indentation are removed.
3. The longest identical leading sequence of spaces and tabs shared by every non-blank content line is removed from
   those lines. Up to the same sequence is removed from blank lines, while any additional whitespace is preserved.

For example:

```xml
<?resource message.txt:
        first line
          indented line

        last line
?>
```

produces:

```text
first line
  indented line

last line
```

Additional indentation, internal blank lines, and an intentional trailing blank line remain part of the content.
XML line endings are normalized to `\n` before the indentation rules are applied.

## Character encoding
The encoding of the containing FXML document and the encoding of an embedded resource are separate concerns. The FXML
source is first decoded according to the normal XML encoding rules. The normalized resource text is then encoded using
the charset selected by the resource declaration.

The default resource charset is UTF-8. A different charset can be selected with a `charset` media-type parameter:

```xml
<?resource message.txt text/plain;charset=UTF-16LE:Hello?>
```

Consequently, a UTF-16 FXML source file still produces UTF-8 resource bytes when no resource charset is specified.

## Media type
The media type is optional and defaults to `text/plain`. Apart from the charset, the media type is only informational
and does not change how the compiler processes the resource declaration.

{: .note }
The media type can still be useful for development tools. For example, an IDE can use `text/css` to provide CSS
syntax highlighting, completion, or validation for a stylesheet payload, even though the FXML compiler treats the
payload as ordinary text.

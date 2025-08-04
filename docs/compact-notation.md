---
layout: default
title: Compact notation
nav_order: 6
---

# Compact notation
FXML 2.0 introduces a new compact element syntax that can be used in FXML attributes. Any string that is enclosed by curly braces is interpreted as if it was specified using element notation:
```xml
<!-- Compact notation -->
<Button padding="{Insets topLeftBottomRight=10}"/>

<!-- Corresponding element notation -->
<Button>
    <padding>
        <Insets topLeftBottomRight="10"/>
    </padding>
</Button>
```
Multiple properties or named arguments are separated by semicolons:
```xml
<Button graphic="{Rectangle width=100; height=20; fill=RED}"/>
```
Compact notations can also be nested:
```xml
<Tab content="{Button graphic={Rectangle width=100; height=20; fill=RED}}"/>
```

## Quoted strings
Strings don't need to be enclosed in quotes, provided the string does not contain quotes, braces, or semicolons. If it does, the string must be enclosed in single quotes:
```xml
<Tab content="{Button text='Inside a quoted string, I can write {Hello};'}"/>
```

## Default property
Like in element notation, the default property name can be omitted in compact notation:
```xml
<!-- Omitted default property "text" -->
<Tab content="{Button Hello}"/>

<!-- This corresponds to -->
<Tab content="{Button text=Hello}"/>
```

## Escaping
If an attribute value should not be interpreted as a compact element expression, it can be escaped by prefixing it with `{}`:
```xml
<!-- The "text" property contains the string "{Plain text}" -->
<Button text="{}{Plain text}"/>
```
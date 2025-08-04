---
layout: default
title: Property notation
nav_order: 5
---

# Properties
The FXML 2.0 property syntax is similar to FXML 1.0, and most of the time the notations look identical. However, FXML 2.0 relaxes the restriction that property names must be lower-cased, and allows a qualified notation to resolve ambiguities.
## Instance property notation
Properties of scene graph elements can either be specified using attribute notation or element notation:
```xml
<!-- 1. Attribute notation -->
<Button text="My button 1"/>

<!-- 2. Qualified element notation -->
<Button>
    <Button.text>My button 2</Button.text>
</Button>

<!-- 3. Short element notation -->
<Button>
    <text>My button 3</text>
</Button>
```
## Static property notation
Static properties can be specified using the following notation, where the static property name is prefixed with the declaring class:
```xml
<!-- 1. Attribute notation -->
<Button VBox.margin="10"/>

<!-- 2. Element notation -->
<Button>
    <VBox.margin>10</VBox.margin>
</Button>
```
## Resolving ambiguities
In the short element notation example, the `text` element can be unambiguously resolved to the `Button.text` property. 
However, if a class named `text` was imported into the document, the meaning would change: a `text` element would then create a new instance of this class.

This can be resolved by qualifying the property name with the declaring class, as seen in the qualified element notation example:
```xml
<Button.text>My button 2</Button.text>
```

It is also possible to fully qualify the property name:
```xml
<javafx.scene.control.Button.text>My button 2</javafx.scene.control.Button.text>
```
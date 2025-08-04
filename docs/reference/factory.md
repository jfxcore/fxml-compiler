---
layout: default
title: fx:factory
parent: FXML 2.0 language reference
nav_order: 12
---

# fx:factory attribute
The `fx:factory` attribute can be used to initialize an element with a factory method instead of a constructor. The factory method must be accessible and parameterless.

## Usage

```xml
<StackPane>
    <fx:define>
        <FXCollections fx:factory="observableArrayList" fx:id="list1">
            <String>foo</String>
            <String>bar</String>
            <String>baz</String>
        </FXCollections>
    </fx:define>

    <ListView items="$list1"/>
</StackPane>
```
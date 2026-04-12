---
layout: default
title: fx:define
parent: FXML 2.0 language reference
---

# fx:define element
The `fx:define` element allows FXML documents to store arbitrary objects outside of the scene graph.
The objects can be named with the [`fx:id`](id.html) directive and referenced in expressions.

## Usage

```xml
<StackPane>
    <fx:define>
        <Insets fx:id="margins1" topLeftBottomRight="2"/>
    </fx:define>

    <Button BorderPane.margin="$margins1"/>
</StackPane>
```

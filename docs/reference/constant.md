---
layout: default
title: fx:constant
parent: FXML 2.0 language reference
nav_order: 8
---

# fx:constant attribute
The `fx:constant` attribute identifies a constant value. It is specified on an element that contains a static field with the specified name, and causes the element to resolve to the type of the referenced field.

## Usage

```xml
<TableView fx:typeArguments="String">
    <columnResizePolicy>
        <TableView fx:constant="CONSTRAINED_RESIZE_POLICY"/>
    </columnResizePolicy>
</TableView>
```

{: .note }
> A more concise way to achieve the same result is to use an [assignment expression](once.html). Alternatively, if the constant is declared on the same class, its name can be used as a literal:
> ```xml
> <!-- Converting "UNCONSTRAINED_RESIZE_POLICY" to the value of
>      the static field TableView.UNCONSTRAINED_RESIZE_POLICY -->
> <TableView fx:typeArguments="String"
>            columnResizePolicy="UNCONSTRAINED_RESIZE_POLICY"/>
> ```
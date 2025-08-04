---
layout: default
title: fx:typeArguments
parent: FXML 2.0 language reference
nav_order: 19
---

# fx:typeArguments attribute
FXML 2.0 documents are strongly typed and support generic types. When a generic class is instantiated, the type arguments are specified with the `fx:typeArguments` attribute.

If this attribute is omitted on a generic class, it is used as a raw type. Note that using raw types affects compile-time type safety and should be avoided.

{: .note }
Generic type inference is not supported. Type arguments must always be specified explicitly.

## Usage

```xml
<!-- Instantiates the generic type TableView<String> -->
<TableView fx:typeArguments="String"/>

<!-- Instantiates the generic type MyGeneicClass<Integer, Double> -->
<MyGenericClass fx:typeArguments="Integer, Double"/>

<!-- Instantiates the generic type MyGenericClass2<MyGenericClass<String, Number>> -->
<MyGenericClass2 fx:typeArguments="MyGenericClass<String, Number>"/>
```

{: .note }
In XML files, the `<` character can only be used as a markup delimiter, and must be escaped using `&lt;` in attribute text. However, the FXML 2.0 compiler accepts the non-standard literal form for better code readability.
---
layout: default
title: fx:id
parent: FXML 2.0 language reference
nav_order: 13
---

# fx:id attribute
The `fx:id` attribute is used to name elements in an FXML document. If the document has a [code-behind](../code-behind.html) class, the FXML compiler generates a protected field with the same name to make the named element accessible from Java code.

{: .highlight }
The `fx:id` attribute also sets the `Node.id` property to the same value.

## Usage

<div class="filename">com/sample/MyControl.fxml</div>
```xml
<StackPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
           fx:class="com.sample.MyControl">
    <Button fx:id="myButton1"/>
</StackPane>
```

<div class="filename">com/sample/MyControl.java</div>
```java
public class MyControl extends MyControlBase {
    MyControl() {
        initializeComponent();

        // The named element "myButton1" can be referenced in the code-behind class.
        // Note that the value of "myButton1" is set by initializeComponent(), so it is
        // only available after the method was invoked.
        myButton1.setText("My Button");
    }
}
```
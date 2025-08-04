---
layout: default
title: fx:context
parent: FXML 2.0 language reference
nav_order: 10
---

# fx:context attribute
The default [binding context](../binding/binding-context.html) is the root element of the FXML document. This can be changed with the `fx:context` attribute, which can be set to an arbitrary object. Binding expressions will then be resolved against the object referenced by `fx:context`.

{: .highlight }
The `fx:context` attribute can only be set on the root node of the FXML document.

## Static object in element notation
The value of `fx:context` can be a static object that is instantiated in the FXML document with element notation. The object exists outside of the scene graph and is internally stored by the root node.

```xml
<StackPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
    <fx:context>
        <!-- Instantiate MyBindingContext and set it as the control's binding context -->
        <MyBindingContext/>
    </fx:context>

    <!-- "user.name" will be evaluated against MyBindingContext -->
    <Label text="${user.name}"/>
<StackPane/>
```

```java
class MyBindingContext {
    ObjectProperty<User> userProperty();
    ...
}

class User {
    StringProperty nameProperty();
    ...
}
```

## Binding path to an object
The binding context may also be exposed with a field, getter, or `ObservableValue` on the [code-behind](../code-behind.html) class. In this case, the value of `fx:context` is a [binding path](../binding/binding-path.html).

<div class="filename">com/sample/MyControl.fxml</div>
```xml
<StackPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
           fx:class="com.sample.MyControl"
           fx:context="$myContext">
    <!-- "user.name" will be resolved against "myContext" -->
    <Label text="${user.name}"/>
<StackPane/>
```

<div class="filename">com/sample/MyControl.java</div>
```java
public class MyControl extends MyControlBase {
    final MyBindingContext myContext;

    MyControl() {
        myContext = new MyBindingContext();
        initializeComponent();
    }
}

class MyBindingContext {
    ObjectProperty<User> userProperty();
    ...
}
```

{: .note }
If the binding context object can change and is exposed as an `ObservableValue`, the `fx:context` attribute can be bound with a [unidirectional binding](bind.html). Note that this will incur listener management overhead. If possible, a one-time assignment should be preferred.
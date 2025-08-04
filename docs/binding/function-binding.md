---
layout: default
title: Function bindings
parent: Compiled bindings
nav_order: 4
---

# Function bindings
Methods and constructors can be used in binding expressions to process a value, combine values, or convert a value to a different type. In the following example, the `String.format` method is used to convert the width of a button to text:

```xml
<Button text="${String.format('Width: %.0f', self/width)}"/>
```

If the method or constructor is bound using `{fx:bind}`, and it has observable arguments, then the method will be re-evaluated whenever an observable argument is changed.

## Method path
The method path is resolved against the [binding context](binding-context.html) like other binding paths, optionally including a binding context selector separated with a forward slash. Both static and instance methods can be selected.

{: .note }
The method path can also be a statically reachable path, beginning with the name of a class.

After resolving the method path, a method is selected with the following rules:
* A method must be accessible (package/protected/public) to the FXML markup class to be applicable.
* The return type of the method must be assignable to the target type of the binding.
* If multiple methods are applicable, overload selection follows the Java Language rules.

A method path can also be the name of a constructor (i.e. the name of the type). In this case, the return type is simply the type of the constructed object:

```xml
<Button textFill="${Color(path.to.red, path.to.green, path.to.blue, 1)}"/>
```

## Method arguments
Method arguments can be any of the following expressions:
* Paths resolved against the [binding context](binding-context.html), optionally including a binding context selector separated with a forward slash, for example: `parent[Label]/text`
* Method or constructor invocations (note: constructor invocations don't use the `new` keyword)
* String literals: `'text'`
* Number literals: `1` (int), `1L` (long), `1F` (float), `1D` (double)
* Class literal: `{fx:type MyClass}`
* [Constants](../reference/constant.html): `{Double fx:constant=POSITIVE_INFINITY}` or `Double.POSITIVE_INFINITY`
* Null: `{fx:null}`

{: .note }
Method arguments can be method or constructor invocations, allowing method calls to be nested.

## Bidirectional function binding with inverse method
A method that is used in a bidirectional binding expression must have exactly one argument, and an inverse method must be specified in the binding expression:

```xml
<TextField text="#{path.to.method(path.to.value); inverseMethod=path.to.inverseMethod}"/>
```

Note that the inverse method is only referenced with a path expression; it has no argument list. The inverse method must have exactly one argument, where the argument type corresponds to the return type of the other method, and the return type corresponds to the argument type of the other method.

The inverse method can also be a constructor invocation with a single argument.
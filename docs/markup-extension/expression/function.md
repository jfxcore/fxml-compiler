---
layout: default
title: Function expressions
parent: Compiled expressions
nav_order: 3
---

# Function expressions
Methods and constructors can be used in binding expressions to process a value, combine values, or convert a value to a
different type. In the following example, the `String.format` method is used to convert the width of a button to text:

```xml
<Button text="${String.format('Width: %.0f', self/width)}"/>
```

If the method or constructor is used in a [`{fx:Observe}`](../../reference/observe.html) or
[`{fx:Synchronize}`](../../reference/synchronize.html) expression, and it has observable arguments,
then the method will be re-evaluated whenever an observable argument changes.

## Method path
The method path is resolved against the [evaluation context](context.html) like other expressions, optionally including
a context selector separated with a forward slash. Both static and instance methods can be selected.

{: .note }
The method path can also be a statically reachable path, beginning with the name of a class.

After resolving the method path, a method is selected with the following rules:
* A method must be accessible (package/protected/public) to the FXML markup class to be applicable.
* The return type of the method must be assignable to the target type of the binding.
* If multiple methods are applicable, overload selection follows the Java Language rules.

A method path can also be the name of a constructor (i.e. the name of the type). In this case, the return type is
simply the type of the constructed object:

```xml
<Button textFill="${Color(path.to.red, path.to.green, path.to.blue, 1)}"/>
```

## Method arguments
Method arguments can be any of the following:
* Expressions resolved against the [evaluation context](context.html), including method or constructor invocations;
  optionally also including a context selector separated with a forward slash, for example: `parent[Label]/text` 
* String literals: `'text'`
* Number literals: `1` (int), `1L` (long), `1F` (float), `1D` (double)
* Class literal: `{fx:Type MyClass}`
* [Constants](../../reference/constant.html): `{Double fx:constant=POSITIVE_INFINITY}` or `Double.POSITIVE_INFINITY`
* Null: `{fx:Null}`

{: .note }
Constructor invocations have the same syntax as method invocations, they do not use the `new` keyword.

## Bidirectional function binding with inverse method
A method that is used in a [`{fx:Synchronize}`](../../reference/synchronize.html) expression must have exactly
one argument, and an inverse method must be available; either by annotating the referenced method with
[`@InverseMethod`](../../reference/synchronize.html#inverse-method-in-a-bidirectional-method-binding)
or by specifying the inverse method name in the binding expression:

```xml
<TextField text="#{path.to.method(path.to.value); inverseMethod=path.to.inverseMethod}"/>
```

Note that the inverse method is only referenced with a path expression; it has no argument list.
The inverse method must have exactly one argument, where the argument type corresponds to the return type
of the other method, and the return type corresponds to the argument type of the other method.

The inverse method can also be a constructor invocation with a single argument.
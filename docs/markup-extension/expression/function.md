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
<Button text="${String.format('Width: %.0f', :element.width)}"/>
```

If the method or constructor is used in a [`{fx:Observe}`](../../reference/observe.html) or
[`{fx:Synchronize}`](../../reference/synchronize.html) expression, and it has an observable receiver
or observable arguments, then the method will be re-evaluated whenever an observable argument changes.

## Applicability
Methods and constructors in binding expressions can be used with the following markup extensions:

| Markup extension | Applicable |
|:-|:-|
| [`fx:Evaluate`](../../reference/evaluate.html) | yes |
| [`fx:Observe`](../../reference/observe.html) | yes, if the expression depends on at least one observable receiver or argument |
| [`fx:Push`](../../reference/push.html) | no |
| [`fx:Synchronize`](../../reference/synchronize.html) | yes, if the expression is invertible |

## Method invocation

A method path is resolved against the [evaluation context](context.html) like other expressions. Both static and
instance methods can be selected. Generic type arguments are specified after the method name:

```xml
<MyControl value="${convert<String>(value)}"/>
<MyControl value="${:parent.compute(value)}"/>
```

{: .note }
The method path can also be a statically reachable path, beginning with the name of a class.

After resolving the method path, a method is selected with the following rules:
* A method must be accessible (package/protected/public) to the FXML markup class to be applicable.
* The return type of the method must be assignable to the target type of the binding.
* If multiple methods are applicable, overload selection follows the Java Language rules.

## Method arguments
Method arguments can be any of the following:
* Paths, method invocations, constructors, groups, and operator expressions, for example
  `:parent<Label>.text`, `width * 0.7`, or `Box(value)`
* String literals: `'text'`
* Number literals: `1` (int), `1L` (long), `1F` (float), `1D`/`1.0` (double)
* Boolean literals: `true`, `false`
* Null literal: `null`
* Class literal: `{fx:Class MyClass}`
* [Constants](../../reference/constant.html): `{Double fx:constant=POSITIVE_INFINITY}` or `Double.POSITIVE_INFINITY`
* [Value-supplier markup extensions](../../markup-extension.html#where-markup-extensions-can-be-used)

The unquoted words `true`, `false`, and `null` are literals only when they occur as expression primaries.
Quoted forms such as `'true'` are strings. A qualified form such as `:element.true`, `:context.true`, or `model.true`
is a path, which allows a property with the same name as a literal keyword to be referenced explicitly.

## Constructor invocation
A constructor uses the same invocation syntax as a method. Top-level and static nested classes are named directly:

```xml
<Button textFill="$Color(red, green, blue, 1)"/>
<MyControl value="$Box<String>('value')"/>
<MyControl value="$Outer.Nested<String>('value')"/>
```

A non-static member class requires an enclosing-instance qualifier:

```xml
<MyControl value="$outer.Inner<String>('value')"/>
<MyControl value="$:context.Inner<String>('value')"/>
```

Constructor type arguments are specified after class type arguments. If `Box<T>` declares a constructor
`<W extends Number> Box(T value, W witness)`, its invocation is:

```xml
<MyControl value="$Box<String, Long>('value', 1L)"/>
```

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

The inverse method can also be the name of a constructor with a single argument.

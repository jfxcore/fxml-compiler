---
layout: default
title: Operators
parent: Compiled expressions
nav_order: 5
---

# Operators

Operators can be used in expressions to perform calculations or evaluate conditions.
FXML/2 supports the following operators, ordered from highest to lowest precedence:

| Category | Operators |
|:-|:-|
| Unary | `+value`, `-value`, `!value`, `!!value` |
| Multiplicative | `*`, `/` |
| Additive | `+`, `-` |
| Relational | `<`, `<=`, `>`, `>=` |
| Equality | `==`, `!=`, `===`, `!==` |
| Conditional AND | `&&` |
| Conditional OR | `||` |

Binary operators associate from left to right, and parentheses override precedence. A relational chain such as
`a < b < c` is therefore parsed as `(a < b) < c` and rejected because the first relation produces a boolean value.

## Arithmetic operators
Arithmetic operators support numeric primitives and their boxed counterparts. Java unary and binary numeric promotion
is applied at each operator, preserving integral overflow, floating-point rounding, integer division, and division by
zero. String concatenation and general number-like declarations such as `Number`, `BigInteger`, and `BigDecimal` are
not valid arithmetic operands.

```xml
<MyControl value="${a + b * c}"/>
<MyControl value="${(a + b) * c}"/>
<MyControl value="${Math.max(width * 0.7, minWidth)}"/>
```

When a boxed numeric operand is null, it is converted to the primitive zero value. A null integral divisor
therefore throws `ArithmeticException`, while a null floating-point divisor becomes positive zero and follows
IEEE-754 behavior.

## Relational operators
The relational operators `<`, `<=`, `>`, and `>=` choose their strategy from the static types of their operands:
- Numeric primitive or box pairs use Java binary numeric promotion.
- `Comparable<T>` values use the left operand's type, a raw `Comparable` value is rejected.

This permits natural ordering for values such as `String`, `BigDecimal`, dates, enums, and user-defined comparable
types. Dispatch is directional: `left < right` requires the right value to be compatible with the left value's
`Comparable<T>` parameter.

For numeric operands, NaN is unordered: every `<`, `<=`, `>`, and `>=` relation involving NaN returns `false`.

A `null` literal is never a valid relational operand. After a relation has been validated from its static types, a
runtime `null` on either side returns `false`.

```xml
<Label visible="${name < 'N'}"/>
<Label visible="${:parent<Pane>.selectedItem != null && width < maxWidth}"/>
```

## Equality operators

| Operator | Semantics |
|:-|:-|
| `==` | compares numeric and boolean values, otherwise uses null-safe `equals` |
| `!=` | the logical negation of `==` |
| `===` | compares reference equality |
| `!==` | the logical negation of `===` |

The equality operators `==` and `!=` choose their strategy from the static types of their operands:
- Numeric primitive or box pairs use binary numeric promotion. NaN is unequal to every value, including itself,
  so `==` returns `false` and `!=` returns `true`; positive and negative zero compare equal.
- `boolean`/`Boolean` pairs compare boolean values with null-safe evaluation.
- Every other combination boxes primitives as needed and uses null-safe `equals` semantics.

The identity-equality operators `===` and `!==` compare reference equality without calling `equals`, they reject
primitive operands and statically incompatible reference types.

## Boolean operators

| Operator | Semantics |
|:-|:-|
| `!value` | converts `value` to its type-directed truthiness and negates the result |
| `!!value` | converts `value` to its type-directed truthiness |
| `left && right` | logical AND |
| `left || right` | logical OR |

The logical operators `&&` and `||` accept only `boolean` or `Boolean` operands, a null `Boolean` is false.
Other values require explicit `!` or `!!` truthiness conversion. These unary operators accept any expression
and return a boolean value.

Truthiness is selected from the operand's static type:

| Static type | False value |
|:-|:-|
| `boolean` or `Boolean` | `false`, a null box is also false |
| Numeric primitive or box, or `char`/`Character` | zero or floating-point NaN, a null wrapper is also false |
| Any other subtype of `Number` | `null`, or `doubleValue()` is zero or NaN |
| Any other reference type | `null` only |

NaN and floating-point zero are false, positive and negative infinity are true. An `Object` containing numeric zero
or NaN remains true because truthiness does not depend on the runtime class, but on the static type.

## Applicability

| Markup extension | Operator support |
|:-|:-|
| [`fx:Evaluate`](../../reference/evaluate.html) | all operators are supported |
| [`fx:Observe`](../../reference/observe.html) | all operators are supported if the expression contains at least one observable value |
| [`fx:Push`](../../reference/push.html) | not supported |
| [`fx:Synchronize`](../../reference/synchronize.html) | only a direct `!` or `!!` if the binding source implements `WritableValue<Boolean>` |
| content variants | not supported |

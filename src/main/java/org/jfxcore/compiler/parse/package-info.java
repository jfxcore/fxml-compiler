// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

/// # Inline value and expression grammar
///
/// This file describes the grammar implemented by [org.jfxcore.compiler.parse.InlineParser].
///
/// [org.jfxcore.compiler.parse.FxmlParser] sends every XML attribute value to `InlineParser`. Inline markup
/// nested in braces or introduced by a compact prefix is parsed there as well. The surrounding attribute or
/// markup-extension property selects generic-value, expression, or path-reference mode.
///
/// Compact binding forms select expression mode before the expression itself is parsed:
///
/// ```text
///     $width + 1                 expression: width + 1
///     ${width + 1}               expression: width + 1
///     #{left == right}           expression: left == right
///     >{selectedItem}            expression: selectedItem
/// ```
///
/// The long forms of `fx:Evaluate`, `fx:Observe`, `fx:Synchronize`, and `fx:Push` use the same expression grammar
/// for their `source` value.
///
/// Expression and active inline-markup syntax run through the tokenizer. It removes ordinary whitespace and
/// comments, joins tokens that continue across some newlines, and keeps source locations for diagnostics. Generic
/// XML attributes are first scanned for outer commas and active prefixes so that their remaining literal text can
/// be preserved. The parser then creates syntax and value nodes in `ast` and `ast/text`. Name lookup, type checking,
/// target-type selection, and deciding whether a call names a method or a constructor happen later.
///
/// For example, expression mode gives both of these the same kind of call node:
///
/// ```text
///     format(value)
///     Widget(value)
/// ```
///
/// Only a later compiler phase decides that `format` is a method and `Widget` is a constructor.
///
/// ## Parsing modes
///
/// ### Expression mode
///
/// Compiled bindings and the `source` properties of the expression intrinsics use expression mode.
/// Operators, keywords, paths, and calls are all parsed as syntax:
///
/// ```text
///     ${foo-bar}                 subtraction: foo - bar
///     ${-1}                      unary minus applied to the number 1
///     ${true}                    the boolean literal true
/// ```
///
/// ### Generic-value mode
///
/// Ordinary attributes and markup-extension properties use generic-value mode. An unprefixed item is literal
/// text, even when it resembles expression syntax. A brace-style markup extension or a recognized compact prefix
/// starts active syntax:
///
/// ```text
///     {Ext value=foo-bar}        LiteralValueNode containing "foo-bar"
///     {Ext value=-1}             LiteralValueNode containing "-1"
///     {Ext value=true}           LiteralValueNode containing "true"
///     {Ext value=Type<T>(x)}     LiteralValueNode containing "Type<T>(x)"
///     {Ext value=$Type<T>(x)}    Evaluate object whose source contains a call
/// ```
///
/// This distinction lets text such as filenames and punctuation-heavy labels remain literal without quoting.
///
/// ### Path-reference mode
///
/// Path-reference mode accepts path-shaped syntax without accepting general operators or literal expressions.
/// It is used by path-valued intrinsic properties such as `fx:Synchronize.format`, `converter`, and
/// `inverseMethod`.
///
/// ## Comma-separated value sequences
///
/// Comma separation belongs to generic-value mode; it is not an expression operator.
/// Conceptually, inline markup uses this outer grammar:
///
/// ```text
///     generic-value-sequence
///         ::= generic-value-item ("," generic-value-item)*
///
///     generic-value-item
///         ::= literal-value
///           | object-expression
/// ```
///
/// For example, this property contains a literal, a compact expression object, and a brace-style object:
///
/// ```text
///     {Ext values=1, $amount, {Other value=x}}
/// ```
///
/// Commas inside a quoted string, a nested object, a call, or a type-argument list belong to that nested construct.
/// A comma at the outer generic-value level separates items. Inline markup represents multiple items with an
/// [org.jfxcore.compiler.ast.InlineArgumentSequenceNode].
///
/// Generic XML attributes are target-sensitive. An attribute containing only literal text stays a single
/// [org.jfxcore.compiler.ast.LiteralValueNode] with optional coercion parts:
///
/// ```xml
///     <Label text="hello, world"/>
///     <Polygon points="0, 0, 50, 100, 100, 50"/>
/// ```
///
/// This lets later resolution use the whole text for a scalar target such as `Label.text`, or use the parts for a
/// collection, array, or implicit constructor. If any outer item uses active markup syntax, the attribute is an
/// explicit sequence instead:
///
/// ```xml
///     <Stylesheets values="plain.css, @theme.css"/>
/// ```
///
/// Prefix notation has no closing delimiter. A comma-separated value assigned to one of its named properties is
/// therefore greedy and consumes the remaining comma-separated items. Brace-style notation provides an explicit
/// boundary when an outer sequence needs to continue.
///
/// ## Expression specification
///
/// ```text
///     expression
///         ::= logical-or-expression
///
///     logical-or-expression
///         ::= logical-and-expression ("||" logical-and-expression)*
///
///     logical-and-expression
///         ::= equality-expression ("&&" equality-expression)*
///
///     equality-expression
///         ::= relational-expression
///             (("==" | "!=" | "===" | "!==") relational-expression)*
///
///     relational-expression
///         ::= additive-expression
///             (("<" | "<=" | ">" | ">=") additive-expression)*
///
///     additive-expression
///         ::= multiplicative-expression (("+" | "-") multiplicative-expression)*
///
///     multiplicative-expression
///         ::= unary-expression (("*" | "/") unary-expression)*
///
///     unary-expression
///         ::= ("+" | "-" | "!" | "!!") unary-expression
///           | postfix-expression
///
///     postfix-expression
///         ::= path-expression [invocation-arguments] selected-member-suffix*
///           | non-path-primary selected-member-suffix*
///
///     non-path-primary
///         ::= number
///           | string
///           | "true"
///           | "false"
///           | "null"
///           | "(" expression ")"
///
///     path-expression
///         ::= path-head (selection path-segment)*
///           | "::" path-segment (selection path-segment)*
///
///     path-head
///         ::= named-path-segment
///           | context-selector
///
///     path-segment
///         ::= named-path-segment
///           | attached-segment
///
///     named-path-segment
///         ::= identifier [type-argument-list]
///
///     attached-segment
///         ::= "(" declaring-type "." identifier ")"
///
///     selected-member-suffix
///         ::= selection identifier [type-argument-list] [invocation-arguments]
///
///     selection
///         ::= "."
///           | "::"
///
///     context-selector
///         ::= ":context"
///           | ":element"
///           | ":root"
///           | parent-selector
///
///     parent-selector
///         ::= ":parent" ["<" qualified-identifier ">"] ["(" signed-integer ")"]
///
///     signed-integer
///         ::= ["+" | "-"] digits
///
///     type-argument-list
///         ::= "<" type ("," type)* ">"
///
///     type
///         ::= primitive-type
///           | qualified-identifier [type-argument-list]
///
///     primitive-type
///         ::= "boolean" | "byte" | "char" | "short" | "int"
///           | "long" | "float" | "double" | "void"
///
///     invocation-arguments
///         ::= "(" [invocation-argument ("," invocation-argument)*] ")"
///
///     invocation-argument
///         ::= expression
///           | object-expression
/// ```
///
/// An `identifier` is a Java identifier. A `qualified-identifier` is one or more identifiers separated by dots,
/// such as `Pane` or `javafx.scene.layout.Pane`.
///
/// `declaring-type` is a qualified identifier. In `(GridPane.rowIndex)`, `GridPane` is the declaring type and
/// `rowIndex` is the attached property. In `(javafx.scene.layout.GridPane.rowIndex)`, everything before the
/// final dot is the declaring type.
///
/// `object-expression` means one complete markup extension such as `{StaticResource key}`. The object must
/// occupy the whole argument; it cannot be one side of an operator:
///
/// ```text
///     f(a + b, {Ext value=x}, Type(y))     valid
///     f({Ext} + value)                     invalid
/// ```
///
/// ## Operator order
///
/// Operators higher in this table are read before operators lower in the table:
///
/// | Order | Operators | Example |
/// |---|---|---|
/// | 1 | calls and member selection | `factory().value` |
/// | 2 | `+`, `-`, `!`, `!!` as unary operators | `!-value` |
/// | 3 | `*`, `/` | `a + b * c` means `a + (b * c)` |
/// | 4 | `+`, `-` as binary operators | `a * b + c` means `(a * b) + c` |
/// | 5 | `<`, `<=`, `>`, `>=` | `a + b < c` means `(a + b) < c` |
/// | 6 | `==`, `!=`, `===`, `!==` | `a < b == ready` means `(a < b) == ready` |
/// | 7 | `&&` | `a == b && ready` means `(a == b) && ready` |
/// | 8 | `\|\|` | `ready \|\| valid && visible` means `ready \|\| (valid && visible)` |
///
/// All binary operators group from left to right:
///
/// ```text
///     a - b - c                 means (a - b) - c
///     a / b / c                 means (a / b) / c
///     a < b <= c                means (a < b) <= c
/// ```
///
/// Unary operators group from right to left because each unary operator reads another unary expression:
///
/// ```text
/// !-!!value                 means !( -(!!value) )
/// ```
///
/// Parentheses can be used anywhere a primary value is allowed:
///
/// ```text
///     -(a + b) * c
///     !(left < right)
///     (factory()).value
/// ```
///
/// ## Paths and selectors
///
/// An unqualified path starts at the default context. A selector can also name a context explicitly.
///
/// | Source | What it selects |
/// |---|---|
/// | `width` | `width` from the default context. |
/// | `::width` | The observable `width` member from the default context. |
/// | `:context` | The default context object itself. |
/// | `:element` | The object represented by the current FXML element. |
/// | `:root` | The root object. |
/// | `:parent` | The immediate parent. |
/// | `:parent(2)` | The parent at depth 2. |
/// | `:parent<Pane>` | The nearest parent whose type matches `Pane`. |
/// | `:parent<Pane>(2)` | The second matching `Pane` parent. |
/// | `model.value` | An ordinary member selection. |
/// | `model::value` | An observable member selection. |
///
/// The parser checks for a leading `::` before it checks for a single-colon context selector.
/// This makes `::width` one observable selection; it is not an empty context selector followed by `:width`.
///
/// The four context selectors can stand on their own:
///
/// ```text
///     :context
///     :element
///     :root
///     :parent
/// ```
///
/// They can also be followed by `.` or `::`:
///
/// ```text
///     :context.name
///     :element::visible
///     :parent<Pane>(1).width
/// ```
///
/// Only `:parent` accepts a type or depth. These forms are invalid:
///
/// ```text
///     :context(1)
///     :element<Pane>
///     :parent(Pane)
///     :parent<Pane>()
/// ```
///
/// The parent depth grammar accepts a signed integer so that the parser can point at the complete argument.
/// Rules such as that the depth must not be negative are checked later:
///
/// ```text
///     :parent(-1)               parsed successfully, rejected during compilation
///     :parent(1.5)              rejected by the parser
/// ```
///
/// An opening `<` immediately after `:parent` always begins the parent type, spaces do not change that choice.
/// To compare the parent object with another value, group it:
///
/// ```text
///     (:parent) < owner         comparison
///     :parent<Pane> < owner     typed parent, then comparison
/// ```
///
/// Names without a colon have no special meaning. For example, `this`, `context`, `element`, `root`,
/// and `parent` are ordinary identifiers:
///
/// ```text
///     this.value
///     context.value
///     parent / width
/// ```
///
/// ### Attached properties
///
/// An attached property is written as a parenthesized path segment:
///
/// ```text
///     pane.(GridPane.rowIndex)
///     pane::(GridPane.rowIndex)
///     :context.(GridPane.rowIndex)
/// ```
///
/// The final name is the property. All preceding names form the declaring type:
///
/// ```text
///     pane.(javafx.scene.layout.GridPane.rowIndex)
/// ```
///
/// An attached property may appear in a path before its first call:
///
/// ```text
///     pane.(Owner.value).method()
/// ```
///
/// A leading attached property is allowed only with the observable `::` shorthand.
/// Use an explicit context for an ordinary attached property:
///
/// ```text
///     ::(Owner.value)                    valid
///     :context.(Owner.value)             valid
///     .(Owner.value)                     invalid
/// ```
///
/// An attached property is not a callable name, so this is invalid:
///
/// ```text
///     pane.(Owner.value)()
/// ```
///
/// ## Calls and type arguments
///
/// Parentheses turn a named path target into a call:
///
/// ```text
///     model.value<T>             a path with a type argument on `value`
///     model.value<T>()           a call whose target is `model.value<T>`
/// ```
///
/// The parser does not decide whether a call is a method call or construction.
/// These all produce an [org.jfxcore.compiler.ast.text.InvocationNode]:
///
/// ```text
///     method(value)
///     model.method<T>(value)
///     Type<T>(value)
///     outer.Inner<T, W>(value)
/// ```
///
/// The resolver later considers the candidates that are legal for each receiver.
/// Capitalization and imports do not change the parse.
///
/// Type arguments follow the name to which they belong:
///
/// ```text
///     model.child<T>.create<U>()
///                 ^        ^
///                 T is for child
///                          U is for create
/// ```
///
/// There is one type-argument list at each named target. For a constructor, the same list can later be
/// split into class arguments and constructor arguments. The parser keeps it as one list.
///
/// Type lists must be nonempty and cannot have a trailing comma:
///
/// ```text
///     Type<T>(value)             valid
///     Type<T, U>(value)          valid
///     Type<>(value)              invalid
///     Type<T,>(value)            invalid
/// ```
///
/// Qualified and nested types are accepted:
///
/// ```text
///     method<java.lang.String>()
///     method<Comparable<java.lang.String>>()
/// ```
///
/// Primitive names are accepted by the parser so that the type checker can issue the appropriate diagnostic later:
///
/// ```text
///     Type<int>(value)           parsed as a call with `int` in its type list
/// ```
///
/// Calls can be followed by more named selections and calls:
///
/// ```text
///     factory().value
///     factory().Inner().Nested(value)
///     foo(a).bar<T>.baz(c).qux
///     (outer).Inner()
/// ```
///
/// A suffix after a completed call or grouped expression must start with `.` or `::` and a name.
/// Calling a returned value directly is not supported:
///
/// ```text
///     foo()()                    invalid
///     (factory())()              invalid
///     foo().bar()                valid
/// ```
///
/// Only a path ending in a named segment can be called.
/// A context object or attached property cannot be called directly:
///
/// ```text
///     ::method()                         valid
///     :context.Type()                    valid
///     :context()                         invalid
///     pane.(Owner.value)()               invalid
/// ```
///
/// Each call argument is a complete expression, so operators and nested calls work normally:
///
/// ```text
///     f(a * 2, b + 1)
///     f(a || b && c, Type(value))
/// ```
///
/// A trailing comma is not accepted:
///
/// ```text
///     f(a, b)                    valid
///     f(a, b,)                   invalid
/// ```
///
/// ## How `<` is chosen
///
/// After a named path segment, `<` might begin a type-argument list, or it might be the less-than operator.
/// Spaces cannot make that decision because the tokenizer removes them:
///
/// ```text
///     value<T>
///     value < T >
/// ```
///
/// Both token sequences are identical by the time they reach the parser.
///
/// The parser uses this rule:
///
/// 1. Save its current position.
/// 2. Try to read a nonempty type list through the matching `>`.
/// 3. Look at the token after `>`. Keep the type list only when that token can follow a finished path.
/// 4. Otherwise, return to the saved position and let the expression parser read `<` and `>` as relational operators.
///
/// A finished path may be followed by a call, another selection, an operator, a comma or closing delimiter,
/// a line or item separator, or the end of the input. The parser only looks at tokens here. It does not look
/// up types or methods.
///
/// These examples show the result:
///
/// | Source | Parse |
/// |---|---|
/// | `a < b` | `a < b` because there is no closing `>`. |
/// | `a < b >` | The path `a<b>` because a complete type list reaches the end. |
/// | `a < b > c` | `(a < b) > c` because an identifier cannot directly follow the tentative path `a<b>`. |
/// | `a < b > +c` | `a<b> + c` because `+` can follow a path. |
/// | `a < b > (c)` | The call `a<b>(c)` because `(` can follow a callable path. |
/// | `a < b + c > (d)` | `(a < (b + c)) > (d)` because `b + c` is not a type list. |
///
/// A comma can also belong to a type list. This expression has one argument:
///
/// ```text
///     f(a < b, c > +d)          argument: a<b,c> + d
/// ```
///
/// Use grouping when two comparisons are intended:
///
/// ```text
///     f((a < b), (c > +d))      two arguments
/// ```
///
/// If a closing `>` and a legal following token make the intended type-list shape clear, the parser
/// reports a local type-list error instead of trying a different relational parse:
///
/// ```text
///     a<>(c)                    error: the type list is empty
///     a<T,>(c)                  error: a type is missing after the comma
/// ```
///
/// An incomplete list does not establish that shape. For example, `a<T` remains the comparison `a < T`.
///
/// ## Whitespace, newlines, and comments
///
/// Horizontal whitespace does not affect the grammar; it also does not resolve the `<` ambiguity described above.
///
/// Both comment forms are removed by the tokenizer:
///
/// ```text
///     value /* explanation */ + offset
///     value + offset // explanation
/// ```
///
/// A newline is ignored when nearby punctuation clearly continues the same expression.
/// For example, these are each one expression:
///
/// ```text
///     value +
///         offset
///
///     model
///         .value
///
///     !
///         ready
/// ```
///
/// In generic inline markup, a comma groups several items into one value sequence, while a newline or semicolon can
/// separate properties and child values. A newline next to a comma is treated as continuation layout. These
/// decisions are made before the expression grammar runs.
///
/// ## Syntax tree produced by the parser
///
/// The parser records the source shape; it does not yet record the method, constructor, field, or property that a
/// name will resolve to.
///
/// Every XML attribute starts with an [org.jfxcore.compiler.ast.AttributeValueNode]. Its form records which
/// attribute grammar was selected:
///
/// | Attribute source | Form | Child nodes |
/// |---|---|---|
/// | generic `text="hello, world"` | `LITERAL` | `LiteralValueNode`, optionally with coercion parts |
/// | generic `values="plain, @theme.css"` | `SEQUENCE` | `LiteralValueNode` and an `ObjectNode` |
/// | expression `source="a + b"` | `SYNTAX` | `BinaryOperatorNode` |
/// | path reference `converter="converters.number"` | `SYNTAX` | `PathNode` |
///
/// A comma-separated value inside inline markup uses an
/// [org.jfxcore.compiler.ast.InlineArgumentSequenceNode], whose children can be literals or objects.
///
/// Expression syntax uses the following nodes:
///
/// | Source form | Main syntax node |
/// |---|---|
/// | `a + b` | `BinaryOperatorNode` |
/// | `-a` | `UnaryOperatorNode` |
/// | `(a + b)` | `ParenthesizedNode` |
/// | `42`, `'text'`, `true`, `null` | `NumberNode`, `StringLiteralNode`, or `LiteralKeywordNode` |
/// | `model.value` | `PathNode` containing `TextSegmentNode` entries |
/// | `:parent<Pane>(1).value` | `PathNode` containing a `ContextSelectorNode` |
/// | `pane.(GridPane.rowIndex)` | `PathNode` containing an `AttachedSegmentNode` |
/// | `method(value)` | `InvocationNode` wrapped around its target path |
/// | `factory().value` | `SelectedMemberNode` whose receiver is an `InvocationNode` |
///
/// A named chain before its first call stays in one `PathNode`:
///
/// ```text
///     model.child<T>.create<U>()
/// ```
///
/// Here the invocation target is a `PathNode` with three named segments. After a call or grouped expression,
/// another member is stored in a `SelectedMemberNode`:
///
/// ```text
///     factory().value
///     (model).value
/// ```
///
/// Syntax nodes retain source ranges. Operators, selectors, type-list brackets, and call parentheses also have
/// their own ranges so diagnostics can point to the relevant punctuation. When an expression came from XML,
/// [org.jfxcore.compiler.parse.SourceMappedText] keeps the connection between decoded text such as `&lt;`
/// and its original source range.
package org.jfxcore.compiler.parse;

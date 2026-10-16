---
description: How an expression a deployment writes is offered, named, answered and refused — the
  function set of each kind of rule, the name of a function, and what an expression evaluates to.
paths:
  - "server/src/main/kotlin/com/sympauthy/expression/**"
  - "server/src/test/kotlin/com/sympauthy/expression/**"
---

# Expression standard

How the language a deployment writes its rules in is extended. A rule expression is configuration:
an operator writes it in a file, the server refuses it at startup if it cannot mean anything, and
every request after that evaluates it. Where the language lives and what it may import is [the
general code standard](general-code-standard.md#dependency-rules); which engine it is, and why, is
[the technology choices](../design/technology.md).

## Where a function is offered

**A kind of rule declares its function set once, in an object named `<RuleKind>Expressions`.**
Both of its entry points read that one declaration, so an expression accepted at startup never
calls a function the evaluation does not provide.

**A kind of rule has two entry points: one validating an expression, one configuring its
evaluation.** The validating one binds every function to nothing; the configuring one takes the
values a request carries as non-null parameters. Never merge them into one entry point taking
nullable values.

**A function is offered to a kind of rule only where its subject can exist there.** Leave it out
of the set elsewhere, so writing it is refused at startup as an unknown function rather than
answered at request time.

```kotlin
fun validateClientExpression(expressionString: String) // CLIENT bound to nothing
fun clientConfiguration(client: Client): ExpressionConfiguration // CLIENT bound to the client
```

## Naming a function

**A function is named for its subject, then for what is asked of it.** The subject comes first so
a reader scanning an expression groups what is asked of the same thing.

| Shape | Name | Answers | Example |
| --- | --- | --- | --- |
| Accessor | `<SUBJECT>("<property>")` | its property | `CLIENT("audience")` |
| Predicate | `<SUBJECT>_IS_<STATE>("<arg>")` | whether it holds | `CLAIM_IS_VERIFIED("email")` |

**The subject is a noun `docs/` uses for it** — the claim, the client, the scope, the token, the
authentication. Name the thing asked about rather than the party it belongs to, so a function about
a scope the person agreed to is `SCOPE_IS_CONSENTED`, not `PERSON_HAS_CONSENTED`.

**A scope is named by how it reached the credential, never by who holds it.**
[Security](../design/security.md#scopes) splits a scope the person agreed to from one a rule
decided, and the functions split the same way: `SCOPE_IS_CONSENTED`, `SCOPE_IS_GRANTED`.

**A function is a class named `<Name>Function`, and its name in an expression is its
`FUNCTION_NAME` constant, in upper snake case.** Register it under that constant, never under a
literal.

## What a function answers

**The values a function answers about are passed to its constructor, and a function constructed
with none answers as though there were nothing to find.** That constructor is the one the
validating entry point uses.

**An accessor answers the property, or `NULL` where the subject has none.** Answer the type the
public documentation states for that property — a string, or a boolean where the property is one.

**A predicate answers a boolean, and never `NULL`.** Answer `false` where the subject is absent, so
an expression combining predicates is always a boolean.

**An expression evaluates to a boolean.** One answering anything else is refused like one that does
not parse.

## A refused expression

**An expression is refused when the configuration is read, through the validating entry point of
its kind of rule.** A parse failure is reported as `config.expression.invalid` and any answer
but a boolean as `config.expression.invalid_return`, naming the key the expression was written
under.

**A failure is thrown as `InvalidRuleExpressionException`, carrying both of its codes.** The
validator reports the configuration code; a manager evaluating an expression that passed validation
and then failed reports the business one, `expression.evaluate.failed` or
`expression.evaluate.invalid_return`.

## Documenting a function

**A function is documented on [the rule expressions
page](https://sympauthy.github.io/technical/rule_expressions) of the public documentation.** The
issue filed on the documentation's tracker for the change names the function, what it answers, and
the row it adds to the table of which kinds of rule offer it.

## What this standard does not cover

**An argument naming nothing, and this is accepted.** `CLAIM("nmae")` and `CLIENT("adience")`
answer `NULL` at every request rather than being refused at startup, because an argument may be any
expression and not only a literal, so what it names is known only once it is evaluated.

**What a refused rule tells the caller.** Whether an expression a request fails is answered with a
challenge naming what would satisfy it is the decision of the surface that evaluates it, and
[#565](https://github.com/sympauthy/sympauthy/issues/565) settles it for a claim's ACL.

**The cost of an evaluation.** An expression is parsed every time it is evaluated, and nothing
caches one.

---

← [How the code is written](index.md)

---
description: What a paged collection answers with and what a caller may ask of one — the response,
  the paging, the filter, order and search grammar, and what each collection publishes about itself.
paths:
  - "server/src/main/kotlin/com/sympauthy/business/model/collection/**"
  - "server/src/main/kotlin/com/sympauthy/business/manager/collection/**"
  - "server/src/main/kotlin/com/sympauthy/api/controller/admin/**"
  - "server/src/main/kotlin/com/sympauthy/api/controller/client/**"
---

# Collection standard

What a paged collection answers with and what a caller may ask of one: the response, the page they
ask for, the criteria that narrow it, and the document each collection publishes saying what it
accepts. Every collection on every surface answers the same way; the Kotlin a surface is made of is
[the `api` layer standard](api-layer-code-standard.md).

## The response

**A collection response is an object.** The items sit under the plural name of what they are, with
the paging beside them:

```json
{ "users": [ … ], "page": 0, "size": 20, "total": 413 }
```

## Paging

**`page` is 0-based, and an omitted `size` is the one the deployment configured.** Both arrive as
ordinary query parameters and both are optional.

**A page or a size outside its bounds is refused rather than clamped.** A caller handed the nearest
page they could have had would be reading one they did not ask for and could not tell apart.

**`size` has a ceiling, and a deployment sets it.** The default size and the ceiling are
configuration, and the shipped values are in the default configuration file.

**The bounds are checked where the two numbers are resolved**, so every paged endpoint answers the
same way and a new collection inherits the answer.

## Filtering

**A bare `field=value` is an exact match, and every other operator is a dotted suffix on the field
name.** The suffix is the operator's name, and the field name is the wire name the collection
publishes it under.

```
GET /api/v1/admin/users
      ?status=enabled&email.contains=ana&created_at.gte=2026-01-01T00:00:00
```

| Operator | Written | Means |
| --- | --- | --- |
| equals | `field=` or `field.eq=` | the value is exactly this |
| differs | `field.ne=` | the value is anything but this |
| before / after | `field.lt=` `field.lte=` `field.gt=` `field.gte=` | ordered comparison |
| one of | `field.in=a,b,c` | the value is one of a comma-separated list |
| holds | `field.contains=` | a partial, case-insensitive match |
| begins | `field.starts_with=` | a case-insensitive prefix match |
| absent | `field.is_null=true` | the row carries no value for this field |

**The set is closed and none of its words is localized.** What renders a filter bar holds them, so a
collection publishes which of them a field admits rather than what any of them means.

**Which of them a field admits is decided by its type, and the field may narrow that set further.**
A field over an open text value takes the text operators, one over an ordered value takes the
comparisons, and `is_null` is offered only by a field a row may carry no value for.

| Type | Admits |
| --- | --- |
| `string`, `email`, `phone_number` | `eq` `ne` `contains` `starts_with` |
| `enum`, `uuid`, `timezone` | `eq` `ne` `in` |
| `boolean` | `eq` `ne` |
| `number`, `date`, `date_time` | `eq` `ne` `lt` `lte` `gt` `gte` `in` |

**`in` takes a comma-separated list, and a field whose values may hold a comma does not offer it.**
That is why the text types are without it and the words are not.

**Criteria compose, and they compose with `and`.** Two parameters naming one field is how a range is
asked for — `created_at.gte` beside `created_at.lte` — and there is no `or` and no grouping.

**A field reading several values off one row is satisfied where any of them satisfies the
criterion.** The claims of a user and the places a session was driven from are read that way, and
`is_null` on such a field asks that the row carry none.

**A value is read as the field's type says**, so a date arrives as the ISO form
[the API standard](api-standard.md#json) fixes and a value over a closed set is matched ignoring
case and answered as the set spells it.

## Ordering

**`sort` is a comma-separated list of keys, read left to right, each descending when prefixed with
`-`.** `sort=-created_at,email` reads the newest first and settles ties by email.

**A caller who named no key takes the collection's own order**, which is what the collection
publishes as its default sort.

**The server ends every order on a key that is unique by construction, ascending, whatever the
caller asked for.** That key is not one a caller may name and it is not part of what they sorted by;
it is there to decide what their own keys leave undecided, and it is what makes a walk through the
pages total.

**A collection sorting by default on the key that is already unique publishes no default sort.**
There is nothing to name ahead of the tiebreak, and a console showing which column is sorted has
nothing to show.

**A collection's own order is ascending on a moment the row does not later rewrite**, so a new row
appends at the tail and a caller walking pages 0..N is never shifted under.

**A collection whose own order is on a column the row rewrites says so in its description.** Two
calls still agree on a snapshot; a walk in progress can skip a row or see it twice.

**A collection's own order is named in its own description**, which is what an integrator reads.

## Free text

**`q` is a partial, case-insensitive match across the fields the collection names as searchable**,
and it is spelled `q` on every collection that has any.

**`q` is a further criterion**, so it narrows what the filters kept rather than widening it.

## What is refused

**Every one of these is a `400` naming the parameter**, so that a caller who mistyped is told rather
than handed a collection that does not answer what they asked.

| Situation | Code |
| --- | --- |
| a page below zero | `collection.page.negative` |
| a page whose offset overflows the integer rows are counted with | `collection.page.too_large` |
| a size below one | `collection.size.too_small` |
| a size above the configured ceiling | `collection.size.too_large` |
| a parameter naming no field the collection filters on | `collection.filter.unknown_field` |
| an operator the field does not admit | `collection.filter.unsupported_operator` |
| a value naming nothing the field's set holds | `collection.filter.value.unsupported` |
| a value the field's type cannot read | `collection.filter.value.malformed` |
| a `sort` key the collection does not sort on | `collection.sort.unknown_field` |
| a `q` on a collection that searches nothing | `collection.search.unsupported` |

**A parameter the collection reads for something other than a criterion is reserved rather than
refused.** `page`, `size`, `sort` and `q` are reserved everywhere, and a collection reading one of
its own names it where it resolves its criteria.

**A collection declares `sort` as a parameter where it orders on something, and `q` where it
searches something**, beside the paging pair. Those are what the grammar fixes and therefore what
the published specification carries; a criterion is not a parameter anything can declare, and it is
resolved off the request against the fields the collection named.

**A collection binding neither still refuses one that arrives.** What is read off the request is
what decides, so a `sort` or a `q` a collection cannot answer is a `400` rather than a parameter
nothing looked at.

## The capability document

**Every collection on the administration surface publishes one, at `capabilities` under its own
path**, gated by the scope that gates the collection it describes.

```
GET /api/v1/admin/users/capabilities
GET /api/v1/admin/users/{userId}/claims/capabilities
```

```json
{ "search": { "fields": ["email"] },
  "filters": [
    { "field": "status", "name": "Status", "type": "enum", "operators": ["eq", "ne", "in"],
      "values": [ { "value": "enabled", "name": "Enabled" } ] },
    { "field": "created_at", "name": "Creation date", "type": "date_time",
      "operators": ["eq", "ne", "lt", "lte", "gt", "gte", "in"] } ],
  "sorts": [ { "field": "created_at", "name": "Creation date" } ],
  "default_sort": "created_at" }
```

**A field is described once, by the name it is sent under, the name it is read under, its type and
the operators it admits.** The wire name is what goes in the query string; the read name is a
sentence in the reader's language and may be reworded in any release.

**A field over a closed set enumerates its values, each with its own name in the reader's
language.** A field over an open set — a date, a free-text claim, an identifier — enumerates none,
and a console renders an input rather than a list; a boolean enumerates none either, since its type
says what it holds.

**The set a field enumerates is this deployment's, not the build's.** The claims this deployment
configured, the clients it declares, the audiences and the scopes it serves are what make this a
request rather than a constant, and they are the reason the generated OpenAPI document cannot answer
it.

**`default_sort` is spelled the way `sort` is**, prefix included, and it is absent where the
collection has none.

**The document answers for exactly what the collection beside it accepts.** A field absent from it
is a field the collection refuses, and a field present in it is one the collection admits under
every operator it lists — which is what makes the refusals above a contract rather than a surprise.

**It describes the collection rather than a row, so a document under a parent path reads the same
for every parent.** Nothing is looked up to answer it.

## Where a name in it comes from

**A field declares the display key its names are read under**, and that key is the identifier of the
thing displayed, under [the i18n standard](i18n-standard.md#keys)'s rule. The field's name is that
key with `.name` appended, and a value's name is that key, the value, and `.name`.

**A name is asked of the administrator's bundle first, and of the bundle naming the thing itself
after it.** That is [the i18n standard's rule](i18n-standard.md#a-bundle-per-audience), and it is
what lets a claim keep one name wherever it is shown.

```properties
fields.user_status.name=Status
fields.user_status.enabled.name=Enabled
```

**A field that is a configured item is named by that item's own key.** A claim filter declares
`claims.<id>`, so it is read under the name the claim already has and a deployment overrides one
name rather than two.

**A key no bundle holds falls back to the word the thing is already known by** — the field's own
wire name, a value's own spelling, or the name this deployment gave it where it gave one. A
deployment adding a claim, a client, an audience or a provider gets a document naming it without
touching a bundle.

## What a collection is written as

**A collection declares its fields rather than its parameters.** What a collection offers is the set
it names; the parameters, the refusals and the document are this standard's, and a collection that
adds a field gets all three.

**A field declaration carries the wire name, the type, the display key, whether the collection sorts
and searches on it, and how the value is read off a row.** Reading it is what lets one evaluator
filter, search and order every collection that holds its rows in memory.

**A collection whose criteria reach the database interprets them itself.** It declares the same
fields under the operators it can answer, and turns the criteria it is handed into its query.

**A collection that is a concept of its own is a `…CollectionManager`, and every one of them lives
in `business.manager.collection`.** What one *is* stays [the business layer
standard's](business-layer-code-standard.md#composition); this is where it goes, so that what
answers the same question for every collection is in one place and the next one has an obvious
package to join.

**`capabilities` is a reserved identifier on every collection whose item is named by a string.** A
claim, a client, an audience or a scope named by that word would be unreachable through its own item
route, so the configuration refuses one rather than leaving a deployment with a row it cannot read.

## What this standard does not cover

**`or` and grouping.** Criteria compose with `and` only, and the first collection that genuinely
needs a disjunction is the one that should argue for a grammar that can express it.

**Choosing which fields come back.** `/admin/users`'s `claims` parameter selects what is published
rather than what is kept, and it stays exactly what it is. It names no field, so it is resolved by
the manager that reads the collection rather than against what that collection declared, and a value
it refuses carries a code of its own rather than one of the criteria's.

**Where the filtering runs.** Whether a collection is narrowed in memory or in the database is not
something a caller can see, and nothing here changes either answer or the scale at which the current
one stops being reasonable.

**Cursor paging.** `page` and `size` stay, with the bounds a deployment already configures.

**A capability document on the client surface.** The grammar binds it like everything else, but
nothing describes it at runtime: what reads that surface is generated from the published
specification, and the values it filters on belong to the one client asking.

**Saved or shareable filters.** Nothing stores a set of criteria under a name.

---

← [Design documentation](index.md)

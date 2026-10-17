---
description: The published OpenAPI document — how it is produced, the words its descriptions use,
  what each shape of property and each operation says, and what it leaves out.
paths:
  - "server/src/main/kotlin/com/sympauthy/api/**"
  - "server/src/main/kotlin/com/sympauthy/OpenAPI.kt"
---

# OpenAPI standard

How the published OpenAPI document is produced, and how its text reads: an operation's description,
a parameter's, a response's, a schema's and a property's. That a resource is documented on the class
and on every property is [the `api` layer standard](api-layer-code-standard.md#the-resource).

## The document

**The document is generated from the annotations and the KDoc, and it is not committed.**

**The document names no address at build time.** It carries a placeholder, and the controller
serving it substitutes the configured public URL on the way out.

**A client is generated from the document the server serves.** The integration tests generate
theirs from the same build output, so what they hold the server to is what an integrator reads.

**A security scheme offers every scope its surface defines.** Swagger UI and a generated client ask
only for the scopes a scheme lists, so a scope left out is one neither can obtain. An annotation
takes a constant, so the list is written by hand, and a test holds it to the enum the scopes are
declared in.

## Who a description is for

**A description is written for an operator setting up a deployment or an integrator writing a
client.** Neither has read `docs/`, so write in words they already have, and say what an operation
does, what a field holds and what the caller is answered with.

**A description states the behaviour and leaves the reasoning to `docs/`.** Say what the caller gets
and when; why the server behaves that way is a design decision, and it is argued where a design
decision is.

**A description is written in the present tense, about what this server does.** Never what *we* do,
and never what it *will* do.

## One word per thing

**Where OAuth2 or OpenID Connect names something, a description uses that name, and where the wire
names it, the wire's word.** A thing with neither gets one word here, and that word is used on
every surface.

**This is where a description departs from [the documentation standard](docs-standard.md), which
writes *person* and *account*.** A reader of the reference arrives from the specifications and the
wire rather than from `docs/`, so the reference speaks theirs.

| Write | For | Never |
| --- | --- | --- |
| end-user | the human signing in, consenting, typing a value | user, person, resource owner |
| user | what this server holds for an end-user | account |
| client | an application the configuration registers | application, app |
| this server | the server publishing the document | authorization server, SympAuthy, we |
| flow | the pages an end-user goes through in a browser | authorization flow, authentication flow |
| interactive flow session | one end-user's way through a flow | flow state |
| step | one page of a flow | screen |
| UI | the pages rendering a flow | custom UI, front-end |
| provider | a third party an end-user signs in with | identity provider, third-party provider |
| MFA method | a second factor enrolled for a user | multi-factor method, MFA registration |
| security context | an address and a user agent requests came from | place |
| proxy | what reports the address and the location | edge |
| configuration | what a deployment declares in its YAML | deployment |
| id token | the token OpenID Connect issues | ID Token |
| URL | an address a browser is sent to | URI |
| time zone | a zone of the IANA database | timezone |

**A word the table names stays out of prose in every other sense.** *User* is never the human,
*flow* is never a single session, and *subject* is only the identifier a token or a provider
issues.

**A wire name, a value, a parameter, an error code, a scope and a configuration key are written in
single backticks.** The table governs prose only, so `authorization_flow_id` keeps its name.

## What a property says

**A property opens the way every property of its shape opens:**

| Shape | Opens with |
| --- | --- |
| boolean | *Whether …* |
| identifier | *Identifier of …* |
| date and time | *Date and time at which …, in UTC.* |
| protocol timestamp | *Time at which …, in seconds since the Unix epoch.* |
| count | *Number of …* |
| URL | *URL …* |
| list | a plural noun naming the items |
| map | what it holds, then *keyed by …* |
| closed set | what the value is |

**A page of a collection describes its four properties alike.** Its items are *the <things> on this
page*, and `page`, `size` and `total` are the shared constants beside the paging parameters.

### When a property holds nothing

**A response property left out when it holds nothing ends with *Absent when …* or *Absent unless
…*.** Serialization drops a null, as [the API standard](api-standard.md#json) says, so this is the
common case. A member a specification defines and this server never sends ends with *Always absent:*
and what it does instead.

**A response property included as `null` ends with *Null when …*.** It is one carrying
`@JsonInclude(ALWAYS)`, serialized whatever its value.

**A request property or a parameter a caller may leave out ends with *When absent, …*,** followed by
what this server does instead.

```kotlin
@get:Schema(description = "Identifier of the client the interactive flow session was started " +
    "for. Absent when an administrator started it.")
```

### Closed sets

**A property or a parameter holding a closed set declares its values as allowed values.** The
description says what the value is and never lists the set; a value whose meaning its name does not
carry is explained by name.

**The allowed values are the ones this server emits**, spelled the way the mapper publishes them.
A property mapped from an enum lists every value of that enum the mapper can reach.

**A set mapped from an enum is held to it by a test**, one per surface in the package of its
resources, reading the annotation off the getter. An annotation takes a constant and the published
spelling is computed, so the values are written by hand and the test is what keeps them true.

**A list of a closed set declares the values on its items, with `@ArraySchema`.** Declared on the
list itself, they constrain the whole array to equal one of them.

## What an operation says

**An operation opens with one sentence saying what it does, as an imperative:** *Retrieve*,
*Create*, *Revoke*, *Start*. What follows is only what the caller needs and the parameters and the
response do not already say.

**A rule a caller needs while writing a request is on the parameter it governs.** An operation's own
description says what the operation is for, and a tag's sits above every operation of the surface at
once, so neither is where a reader looks for what one parameter accepts.

**A list operation ends with the order it takes:** *<Things> are ordered by …, unless another order
is asked for.* An admin collection then says where its capabilities are published.

**Every response has a description, and a success response says what its body holds.** A page is
*Paginated list of <things>.*, a redirect says where it sends the browser, and no response keeps the
name the framework generates.

**Every failure the operation answers with is a declared response.** A status this server answers
for more than one reason is declared once.

**A failure response lists the error codes it answers with, one per line, each with the condition
that raises it.** A caller branches on the code, so it is the line's first word.

```markdown
- `not_found`: no invitation has this identifier, or this client did not create it.
```

**The code is the one the caller reads.** On `/api/oauth2` and `/api/openid` it is the code the
specification defines; everywhere else it is the `error_code` [the API
standard](api-standard.md#errors) makes the contract.

**The `401` of an operation behind an access token is `UNAUTHORIZED_RESPONSE_DESCRIPTION`, and its
`403` names the scope the operation requires.** A caller holding a token without that scope reads
which one to ask for.

**A flow operation says what becomes of a failure the end-user cannot correct.** It is not a status:
the interactive flow session fails, and the response carries the URL of the error page.

## What a description leaves out

**A configuration key appears only where the reader needs it to change what is described**, and then
in full, as a deployment writes it — `advanced.pagination.max-size`, not *the configured maximum*.

**Nothing internal appears.** No class, table, design document, issue or Kotlin name, and no
framework default such as an operation's generated response name.

**Only a protocol endpoint cites a specification**, and only the section defining a parameter or a
member. Every other description states the behaviour itself.

**A description is as long as the reader needs to make one call.** An explanation longer than that
belongs in [the public documentation](https://sympauthy.github.io), and the description links to it.

## Mechanics

**A description is Markdown, written in sentences that end in a full stop.** Use a list only for
the alternatives a caller branches on, such as the error codes of a response.

**A description shared by every operation that binds it is a constant**, as [the `api` layer
standard](api-layer-code-standard.md#the-controller) says of the paging parameters. Append what is
particular to one operation rather than restating the shared text — a collection's own refusals
after `COLLECTION_BAD_REQUEST_DESCRIPTION`, for one.

**A description is written as concatenated strings, never as a raw string.** A raw string publishes
its leading newline and its indentation, and a line break is written `\n` where Markdown needs one.

## What this standard does not cover

**The body a failure response carries.** A declared failure carries a description and no schema;
the body is [the API standard's](api-standard.md#errors), and declaring it per response is open.

**The messages a failure's `description` carries.** They are written for whoever sees the failure,
and [the internationalization standard](i18n-standard.md) holds them.

**The public documentation's own pages.** A description may link to one; how those pages are written
belongs to their repository.

---

← [How the code is written](index.md)

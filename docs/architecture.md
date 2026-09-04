# Architecture

SympAuthy is an OAuth2 and OpenID Connect authorization server. It owns accounts, decides what a
token may carry, and drives the browser flow a person actually signs in through.

It is organised into three layers, each owning its own model, with two more packages cutting across
all of them. A request flows down through the three and back:

```
  OAuth2 clients   discovery      the flow pages     client apps     the admin console
        │              │                │                 │                 │
   /api/oauth2   /.well-known    /api/v1/flow      /api/v1/client    /api/v1/admin
   /api/openid          │                │                 │                 │
        ▼              ▼                ▼                 ▼                 ▼
  ┌───────────────────────────────────────────────────────────────────────────────┐
  │ api        controller/  resource/  mapper/  filter/  errorhandler/            │
  ├───────────────────────────────────────────────────────────────────────────────┤   c  s
  │ business   manager/  model/  mapper/                                          │   o  e
  ├───────────────────────────────────────────────────────────────────────────────┤   n  c
  │ data       repository/  model/            postgresql/ + h2/                   │   f  u
  └───────────────────────────────────────────────────────────────────────────────┘   i  r
                                    │  R2DBC, non-blocking                             g  e
                                    ▼
                          PostgreSQL   or   H2
```

- **`api`** — the HTTP boundary. `controller/` holds Micronaut controllers with `suspend` handlers,
  split by surface; `resource/` holds the DTOs that are each surface's public contract; `mapper/`
  turns a business model into one; `filter/` and `errorhandler/` handle CORS and the translation of
  a failure into a status and a body. What a class here looks like is [the `api` layer
  standard](api-layer-code-standard.md); what it serializes to is [the API
  standard](api-standard.md).
- **`business`** — the logic. `manager/` holds the use cases, `model/` the domain types managers
  exchange, `mapper/` the MapStruct mappers that turn an entity into one. Nothing here knows about
  HTTP. See [the `business` layer standard](business-layer-code-standard.md).
- **`data`** — persistence against **PostgreSQL or H2** over [Micronaut Data
  R2DBC](https://micronaut-projects.github.io/micronaut-data/latest/guide/#r2dbc), whose
  non-blocking driver is what lets a repository be `suspend`. See [the `data` layer
  standard](data-layer-code-standard.md) and [the database standard](database-standard.md).

**`config`** turns the deployment's YAML into validated, typed models before anything can use it,
and **`security`** turns a credential into an `Authentication` the controllers can be gated on. Both
are drawn across the three layers because both are consumed by all of them:
[the `config` layer standard](config-layer-code-standard.md) and [security](security.md).

## Surfaces

SympAuthy answers to several very different callers, and the split is visible in the route. **A
surface is a route prefix with one audience and one gate** — which is also the test for whether
something is a new surface or a route on an existing one: a caller authenticated differently, or one
a whole prefix should be refusable for in one place, is its own surface.

The routing is the source of truth for which exist. Today they run from the protocol endpoints under
`/api/oauth2` and `/api/openid`, through the flow the sign-in pages drive under `/api/v1/flow`, to
the server-to-server and back-office prefixes under `/api/v1/client` and `/api/v1/admin`.

**A gate is never one check.** The protocol endpoints are guarded by the protocol itself — client
authentication, PKCE, a signed `state` — rather than by a role; discovery is public by
specification; the flow prefix is gated on a signed session token that is emphatically not a user's
access token; and the application prefixes are gated on scope. [Security](security.md) is where each
is spelled out.

**A surface carries a version when its contract is ours to break.** The protocol prefixes do not:
their routes are named by RFC 6749, RFC 7009, RFC 7662 and the OpenID Connect Core and Discovery
specifications, and a client that finds them by reading `/.well-known/openid-configuration` never
sees the path we chose anyway. Everything we design ourselves is versioned.

**Versioned surfaces freeze on different schedules**, which is why each is its own prefix rather
than one API with sections. The admin console ships alongside the server; a client integration may
be years old; the sign-in pages are replaced whenever the front end is. One shared version would
chain them together and force a bump on one surface for another's benefit.

**The prefix is a security boundary, not only a routing convention.** It is what lets a whole
surface be gated once, in the security configuration, instead of one annotation at a time. What each
gate does and does not protect against is [security](security.md).

## A model per layer

Each layer defines its **own** model and translates at the boundary — the API resources, the
business models, and the persistence entities are separate types even when they describe the same
concept. Keeping them apart means each changes for its own reasons: a column can be added without
touching a published contract, and the domain is never shaped by either.

The rule that enforces it is that **a manager never returns an entity**. When the boundary is
broken, it is broken there first, by a method that returns a `…Entity` because it was quicker.

## The split stops at `api`

`business` and `data` are shared whole. Revoking a consent is the same use case over the same domain
model whether an administrator asked for it or the person did, so managers are divided by **domain**
— user, client, consent, token, flow — never by caller. An `admin/` package below the HTTP boundary
would be a second copy of the domain, and the two copies would drift.

## One schema, more than one database

Every repository interface has an empty implementation per dialect, selected by a condition on the
configured datasource; every migration exists once per dialect folder, under the same name.
PostgreSQL is what a deployment runs; H2 is what a developer and the unit tests run.

The cost is real — a schema change is a file per dialect, and a raw query has to be expressible in
each of them — and it is paid deliberately, so that trying SympAuthy out needs no database at all.
The [database standard](database-standard.md) holds the rules that keep them in step, and the
[integration tests](testing-standard.md) run every scenario against each, which is what stops them
diverging in practice rather than in principle.

## The interactive flow is an engine, not a script

Signing in, enrolling a second factor, confirming an action, re-proving who you are, linking an
identity provider: these are not separate flows. Each is a **purpose**, and a single engine
sequences purposes over one `InteractiveFlowSession` — which is why a session can carry several at
once, why the list grows as the server learns what else is needed, and why the server, never the
client, decides which step comes next.

This is the one subsystem where reading the code in file order does not explain it, so it has its
own description: [the interactive flow](interactive-flow.md).

## Beside the layers

Some packages sit next to the three rather than inside one of them, and the test is the same in
every case: **a package belongs beside the layers when it owns no layer's model.** The ones there
today:

- **`client/`** — requests *leaving* this server: the token, UserInfo and discovery endpoints of a
  third-party identity provider, and the authorization webhook. One package per protocol, each with
  its own `model/` of response types. Those types are a foreign contract, exactly as a database row
  is, and they get the rule `data` already lives under: a client's response type does not reach a
  manager unmapped.
- **`exception/`** — `LocalizedException`, the root every failure in this server extends, and the
  mapper that renders one against a message bundle. It is above all three layers because `business`
  and `api` both throw, and neither may depend on the other. See [the exception
  standard](exception-code-standard.md).
- **`expression/`** — the small language a deployment writes its rules in: the functions an
  expression may call, and the compiler that turns one into a boolean or a failure. It is beside the
  layers because `config` refuses an expression when the file is read and `business` evaluates one
  when a request is served, so putting the grammar in either would force the other to import it.
- **`health/`** — what this server reports about itself to whatever is watching it. A health
  indicator answers for a layer without belonging to it, and configuration in particular must not
  depend on the thing that publishes its verdict.
- **`cron/`** and **`server/`** — scheduled cleanup, and the factories that publish the message
  sources and the executor.
- **`view/`** and **`util/`** — the controllers that serve the two bundled single-page applications,
  and the extension functions shared everywhere.

## Project layout

```
sympauthy/
├── server/
│   ├── src/main/kotlin/com/sympauthy/
│   │   ├── api/
│   │   │   ├── controller/     admin/ client/ flow/ oauth2/ openid/ openapi/
│   │   │   ├── resource/       the DTOs, one package per surface
│   │   │   ├── mapper/         business model → resource, MapStruct
│   │   │   ├── filter/         CORS, one filter per surface
│   │   │   ├── errorhandler/   exception → status and localized body
│   │   │   └── exception/      the HTTP and OAuth2 exceptions
│   │   ├── business/
│   │   │   ├── manager/        the use cases, split by domain
│   │   │   ├── model/          the domain model
│   │   │   ├── mapper/         entity → business model, MapStruct
│   │   │   └── exception/      BusinessException and its factories
│   │   ├── data/
│   │   │   ├── model/          R2DBC entities
│   │   │   ├── repository/     the repository interfaces
│   │   │   ├── postgresql/     dialect implementations and conditions
│   │   │   └── h2/
│   │   ├── config/             properties/ parsing/ validation/ factory/ model/
│   │   ├── security/           the authentications and the token validator
│   │   ├── client/             outbound HTTP, one package per protocol
│   │   ├── exception/          LocalizedException and its mapper
│   │   ├── expression/         the rule language, shared by config and business
│   │   ├── health/             what the server reports about itself
│   │   ├── cron/ server/ util/ view/
│   │   └── Application.kt
│   └── src/main/resources/
│       ├── databases/          Flyway migrations, postgresql/ and h2/
│       ├── views/mails/        FreeMarker mail templates
│       ├── META-INF/native-image/   reflection and resource metadata
│       ├── error_messages.properties  display_messages.properties
│       ├── mail_messages.properties
│       └── application*.yml    defaults and the environment overlays
├── integration-tests/          the server in a container, over real HTTP
├── bruno/                      a request collection for exercising it by hand
├── config/                     the local deployment's configuration
└── docs/                       these documents
```

Two directories in that tree are not written by hand. `server/src/main/resources/sympauthy-flow/`
and `sympauthy-admin/` hold the built single-page applications, injected by CI from their own
repositories, and the OpenAPI document the integration tests generate their client from is produced
at build time and never committed.

---

← [Design documentation](index.md)

# How the code is written

The rules a change is held to. Each is named `<subject>-standard.md` and holds one subject; the
`paths` globs in its frontmatter are what load it, so a standard reaches an agent when a file it
governs is read, through the symlink in `.claude/rules/`. A standard governing what is not in the
tree carries no `paths` and is named from `CLAUDE.md` instead. What each part of the server does is
the other half — [how the system works](../design/index.md).

- **[General code standard](general-code-standard.md)** — the components a feature is made of, what
  each layer may import from another, and the naming that holds everywhere. Each layer then has its
  own: [`api`](api-layer-code-standard.md), [`business`](business-layer-code-standard.md),
  [`data`](data-layer-code-standard.md), [`config`](config-layer-code-standard.md).
- **[Exception standard](exception-code-standard.md)** — which exception each layer may throw, how a
  code names both its technical message and the one a person reads, and the one place the OAuth2
  specification overrides the rule.
- **[API standard](api-standard.md)** — what a client sees: how a route is spelled, what the JSON
  looks like, the body a failure returns, and why no redirect is a 307.
- **[Collection standard](collection-standard.md)** — what a paged collection answers with and what
  a caller may ask of one: the response, the paging, the filter, order and search grammar, and the
  document a collection publishes saying which of its fields it accepts.
- **[Database standard](database-standard.md)** — how a table and a migration are written, and what
  keeps the PostgreSQL and H2 schemas from drifting apart.
- **[Locking standard](locking-standard.md)** — how two instances take turns over one database: the
  lock a transaction holds over an object, the batch a run claims, and the lease a job takes.
- **[Internationalization standard](i18n-standard.md)** — why there is a bundle per audience, how a
  key is named, and how it reaches the reader in their own language.
- **[Comment standard](comment-standard.md)** — what a KDoc carries, and where the rationale that
  does not belong in one goes instead.
- **[Testing standard](testing-standard.md)** — what each kind of subject is tested with, where its
  test lives, how it is named, what it is expected to prove, and why it carries almost no comment.
- **[Native image standard](native-image-standard.md)** — the closed-world rules that compile
  cleanly, pass every test, and then fail in production.
- **[Documentation standard](docs-standard.md)** — what holds of every document in `docs/`,
  whichever half it is in: what it names, how it is kept true, and the mechanics. The two halves
  then have one each — [the standards standard](docs-standards-standard.md) for how a rule here is
  written and what it states in place of the code that happens to follow it, and [the design
  documentation standard](docs-design-standard.md) for what a design document answers about the
  part it names and what it leaves to the code, to the standards and to the FAQ.
- **[Issue standard](issue-standard.md)** — what an issue in the tracker settles and what it leaves
  to the change: the title, the fixed sections, what a feature and a bug each name, and the label
  and milestone it opens under.

---

← [Documentation](../index.md)

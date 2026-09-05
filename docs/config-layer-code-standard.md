---
description: The classes a configuration domain is written as, and the sealed model they produce.
paths:
  - "server/src/main/kotlin/com/sympauthy/config/**"
  - "server/src/main/resources/application*.yml"
---

# The `config` layer code standard

One of the [code standards](general-code-standard.md). This one covers the classes a
configuration domain is written as, and the model they produce. A deployment configures SympAuthy
with a YAML file, so every mistake in it is made by an operator who cannot read this codebase.

## The artifacts of a configuration domain

| Artifact | Does | May not |
| --- | --- | --- |
| properties | receives the raw YAML, every field nullable | convert, validate, default |
| parser | turns text into types, resolves templates | validate, look at another domain |
| validator | decides whether the values are allowed | parse, throw its way out |
| factory | creates the context, calls the other two, assembles | do any of their work |
| model | the validated result other code injects | be constructible from an invalid one |

**Every value the YAML offers arrives as a nullable string-shaped property**, including what looks
like a boolean, a number or a duration. The parser converts it, and names the key when it cannot.

**A value that cannot apply where it was written is refused, not ignored.** The validator records an
error naming the key, so a setting that will not take effect is never accepted in silence.

**The parser only converts.** Route every call through the context so a failure is recorded, and
return an intermediate type whose fields are all nullable.

**The validator only decides.** Check ranges, consistency between two values, and whether a
referenced audience or scope exists, recording each error rather than throwing it.

**A cross-domain reference is passed in already resolved**, as a map the validator can look into.
The factory resolves it and hands it over.

**A validator's only inputs are its own domain's values and what its factory hands it.**
Configuration is built before the managers that read it.

**A configuration carries its complete set, including the entries the server itself adds.** Put the
deployment's values and the ones a specification or a built-in feature supplies together here, and
let everything downstream read the result.

**The factory is thin enough to read in one breath.** Create the context, parse, validate, and
return the enabled model or the disabled one; its non-null assertions are legal there and only
there.

## The model

**Every domain is a sealed type with an enabled variant and a disabled one.** The enabled variant
carries the values, all non-null; the disabled one carries the errors that stopped it being built.

**A consumer says which variant it needs.** Unwrap and throw where the feature is required, and test
for the enabled variant where it is optional.

**A disabled configuration is a value, carried until something needs it.** A feature nobody
configured lets the server start, and fails where it is used, naming what is missing.

## Configuration errors take readiness down

**Any configuration error makes the server report itself unready**, cosmetic ones included. An
operator gets one signal.

**Errors are accumulated and reported together.** A file with four mistakes reports four mistakes.

**Startup work asks readiness before it runs.** A credential minted at startup, a queue replayed
there: each asks `ConfigReadiness` first, and says in the log that it did not run, because silence
is indistinguishable from having had nothing to do. The signal is for the server to obey and not
only to publish. Serving a request is a different matter — there, the configuration a request needs
throws where it is read.

**The verdict is logged above the work that obeyed it.** The listener reporting it is ordered ahead
of the ones that act, so an operator reading from the top learns whether the configuration is
usable before they read what ran under it.

## Nothing is carried across a restart

**A stored record naming something the new configuration no longer offers fails when it is next
resolved.** A scope a deployment turned off, a flow it removed: the record keeps the name it was
written with, the lookup finds nothing, and the person holding it starts again. Nothing sweeps those
records ahead of the change and nothing holds the old value for them.

That is the other half of configuration being read once. Keeping the previous file alongside the new
one would put two answers in the server at the same moment, and pinning each record to the values it
was created under would make every resolved value nullable — a cost paid on every read, in every
manager, to spare a restart that is otherwise free.

## Configuration validates input, and nothing else

**A factory validates the file in front of it.** Fetching a discovery document, probing a database
and resolving DNS belong to the manager that owns the runtime relationship.

**A key under `config.` says the file is wrong, and a key under the feature's own prefix says the
world is.** Two codes, because only one of them is fixed by editing YAML.

## What this standard does not cover

**Reloading.** Configuration is read once, at startup, and changing it is a restart.

**Secrets.** A secret is a string in the same file as everything else, and where it comes from is
the deployment's business.

**Per-tenant configuration.** One file describes one server; multi-tenancy would be designed as a
data model.

---

← [Design documentation](index.md)

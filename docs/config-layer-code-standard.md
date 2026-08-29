# The `config` layer code standard

One of the [code standards](general-code-standard.md), which hold the components a feature is made
of and what each layer may import from another. This one covers the four classes a configuration
domain is written as, and the model they produce.

A deployment configures SympAuthy with a YAML file, so the configuration *is* the product surface:
every mistake in it is a mistake by an operator who cannot read this codebase, at a moment when the
server is not yet up. That is what the rules below are protecting.

## The artifacts a domain is written as

| Artifact | Does | May not |
| --- | --- | --- |
| properties | receives the raw YAML, every field nullable | convert, validate, default |
| parser | turns text into types, resolves templates | validate, look at another domain |
| validator | decides whether the values are allowed | parse, throw its way out |
| factory | creates the context, calls the other two, assembles | do any of their work |
| model | the validated result other code injects | be constructible from an invalid one |

**Everything the YAML offers arrives as a nullable string-shaped property**, including what looks
like a boolean, a number or a duration. Letting the framework coerce them moves the failure to a
place with no error message worth reading: a mistyped duration becomes a binding error naming a
class, not a configuration error naming a key. The parser converts, and the conversion is where the
key can be named.

**The parser only converts.** Every call goes through the context so that a failure is recorded
rather than thrown, and its output is an intermediate type whose fields are all nullable — one
nullable field per value that might not have parsed. It never looks at another configuration domain,
because a parser that does cannot be run before that domain exists.

**The validator only decides.** Ranges, consistency between two values, and whether a referenced
audience or scope exists. It records errors rather than throwing them, so that an operator sees
every problem in one startup rather than one problem per restart — which is the whole reason the
context exists.

**A cross-domain reference is passed in already resolved**, as a map the validator can look into,
never by injecting the other domain's configuration. Injecting it would make the order in which two
configurations are created part of the design, and the cycle that eventually appears is not
diagnosable from either end.

**A validator never injects a manager either.** A manager reads configuration, so a validator that
injects one is reaching back into the layer it is in the middle of building: from config, into
business, and into config again. That is the same loop as injecting another configuration directly,
with one more step hiding it.

**A configuration carries its complete set, including the entries the server itself adds.** Where a
deployment names some of the values and the server supplies the rest — the ones a specification
defines, the ones a built-in feature needs — the two halves are put together here, and everything
downstream reads the result. Assembling the set above this layer instead is what leaves a validator
with no one to ask but a manager, and it means every caller that wants the whole set has to know how
to build it.

**The factory is thin enough to read in one breath.** Create the context, parse, validate, and
return the enabled model or the disabled one. The non-null assertions it needs are legal there and
only there, because it has just checked that the context has no errors.

## The model

**Every domain is a sealed type with an enabled variant and a disabled one.** The enabled variant
carries the values, all non-null; the disabled variant carries the errors that stopped it being
built. Everything else in the application injects the sealed type.

**A consumer says which it needs, and the type makes it say so.** Code that cannot work without the
configuration unwraps it and throws if it is disabled; code for which the feature is optional tests
for the enabled variant and does something else otherwise. Both are one expression, and neither can
be written by accident — which is the point of the two variants rather than one type with a flag.

**A disabled configuration is not an error at startup.** It is a value, carried until something
actually needs it. A feature nobody configured and nobody uses should not stop a server from
starting, and a feature nobody configured that something *does* use should fail where it is used,
naming what is missing.

## Configuration errors take readiness down

**Whether the configuration is usable is answered once, across every domain.** That answer is the
only component here that is not per-domain, and it holds every domain's model in order to give it:
an operator is owed one verdict on the file they wrote, not one per section of it.

**What publishes that verdict is not configuration.** The health check reporting it belongs with the
other things the server exposes about itself, and a configuration that reached out to the component
serving it would have the dependency backwards.

**Any configuration error makes the server report itself unready** — including the cosmetic ones.
There is no severity split, and adding one is how a deployment ends up running for months with a
misconfiguration everybody has learned to ignore. An operator gets one signal, and it is
unambiguous.

**Errors are accumulated, never thrown out of the first one.** A configuration file with four
mistakes reports four mistakes. Failing fast here would mean four restarts to find them, and the
fourth is the one that finally shows the operator what the third broke.

## Configuration validates input, and nothing else

**A factory never makes a network call.** Fetching a third-party provider's discovery document,
probing a database, resolving DNS: none of these belong here, however much they look like
validation. They fail for reasons that have nothing to do with the file being wrong — a provider
that is briefly down would make a correct configuration report itself invalid — and they make
startup depend on the availability of every system the deployment names.

Those checks belong to the manager that owns the runtime relationship, which is also what decides
their error codes: a key under `config.` means the file is wrong, a key under the feature's own
prefix means the world is. Two codes, because they are two operational problems and only one of them
is fixed by editing YAML.

## What this standard does not cover

**Reloading.** Configuration is read once, at startup. Changing it is a restart, which is affordable
while startup is measured in milliseconds and is the reason nothing here has to think about a value
changing under it.

**Secrets.** A secret is a string in the same file as everything else. Where it comes from — an
environment variable, a mounted file, a secret manager — is the deployment's business, and pulling
one of those in would make the server responsible for a system the operator already has.

**Per-tenant configuration.** One file describes one server. Multi-tenancy is not a configuration
shape, it is a data model, and it would be designed as one.

---

← [Design documentation](index.md)

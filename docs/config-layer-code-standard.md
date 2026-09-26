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

**A property prefix is written in kebab-case.** Micronaut hyphenates whatever a properties class
declares, so `rules.act_as` is published as `rules.act-as`, and a file written the way the constant
reads leaves the fields unbound. Only a value the parser requires reports it; the rest default in
silence.

**Every value the YAML offers arrives as a nullable string-shaped property**, including what looks
like a boolean, a number or a duration. The parser converts it, and names the key when it cannot.

**A value that cannot apply where it was written is refused, not ignored.** The validator records an
error naming the key, so a setting that will not take effect is never accepted in silence. It is
refused against the key and not against the value, so a value equal to the one the server would have
used is refused too.

**A rule about a key rather than about a value reads the keys a deployment wrote.**
`WrittenConfigurationKeys` holds them as the files spell them, because a properties class cannot
answer for a key — one written with nothing under it binds to null, exactly as a key nobody wrote
does. The error then names the key the operator wrote rather than the one Micronaut normalised it
to, and a key no property declares is caught alongside the ones that do.

**A key that binds to nothing is refused.** A prefix a configuration domain declares is the server's
to answer for, so a key under one that no domain reads is an error naming the key and the file it
came from. What the server declares is read off the properties classes the compiler generated,
which is what makes the set closed without anything having to be registered on a list.

**A prefix nearly one a domain declares is answered for too.** A section written under `scope` where
the domain is `scopes` does nothing at all, so its keys are refused with the correction rather than
ignored. A prefix resembling none of them is a deployment's own, to name and to interpolate out of
as it likes.

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

## A setting that selects an implementation

**A value that selects an implementation is spelled as that implementation's qualifier, and the
published implementations are its allowed values.** There is no enum beside the beans and no list of
names beside either: what an operator writes is resolved against what the container publishes for
the interface the setting selects from, so the set exists once, where the code implementing it does,
and adding an implementation is adding an implementation.

**The interface the setting selects from lives in `business.model`**, because that is
[the only part of `business` this layer may name](general-code-standard.md#dependency-rules). Its
implementations stay in `business.manager`, each published under the word an operator writes.

**A qualifier matches `[a-z0-9]+(-[a-z0-9]+)*`** — lowercase letters and digits, words separated by
a single dash, as `auto-increment` is. The container passes the name through verbatim, so this is
what reaches the YAML file, and an implementation published under any other spelling is refused
where the set is read.

**The set is read off the bean definitions and never off the beans.** Configuration is built before
the managers that read it, and an implementation is free to inject configuration itself, so deciding
whether a word is valid may not build what is behind it. Those definitions are the annotation
processor's, so a native image's closed world answers what the JVM does — and the integration suite,
which boots that image with a word configured, fails every scenario where it does not.

**A word naming no implementation takes readiness down at startup**, naming the key, the word that
was written and what the server publishes. It is refused in the parser, where every other value that
does not convert is refused, so it never reaches the manager that would have run what it named.

**The model carries the selection typed by the interface it was made from.** A manager is handed the
selection for its own interface rather than a bare string, which the word another setting of this
shape was configured with would satisfy just as well.

**A setting may select several, and the order is the operator's.** The property is a list, each
entry is refused against the index it was written at so a file naming two unknown words reports
both, and what the entries mean together is the domain's own rule rather than the mechanism's —
`advanced.security-context.geo.providers` has each entry override the fields the ones before it
answered. A word repeated in such a list is refused by the validator: the later entry wins over the
earlier, so writing one twice says two things and only one of them can be meant.

**Two settings selecting from two interfaces is how a set that may not be selected from everywhere
is expressed.** An implementation is published for the interfaces it can answer for, so a word
naming one of them and not the other is refused where the set is read, with no rule anywhere saying
which words belong to which setting. `advanced.security-context` names a proxy for the address and
edges for the location that way: an edge publishing no location implements nothing the geo setting
selects from, and naming it there is refused at startup.

**The next setting of this shape names a property and an interface.** The parsing, the refusal and
the message are the mechanism's, and writing any of them again is a sign the setting is not of this
shape after all.

**A value the server reasons about is not of this shape, and stays an enum.** `JwtAlgorithm`,
`AuthorizationFlowType` and a claim's type are closed sets with behaviour of their own, read in more
places than the one that switches on them, and their entries implement nothing. This rule is for a
setting whose entire effect is which bean runs.

## Configuration errors take readiness down

**Any configuration error makes the server report itself unready**, cosmetic ones included. An
operator gets one signal.

**Errors are accumulated and reported together.** A file with four mistakes reports four mistakes.

**Startup work asks readiness before it runs.** A credential minted at startup, a queue replayed
there: each asks `ConfigReadiness` first, and says in the log that it did not run, because silence
is indistinguishable from having had nothing to do. The signal is for the server to obey and not
only to publish. Serving a request is a different matter — there, the configuration a request needs
throws where it is read.

**A filter narrows the sealed type rather than throwing.** A request needing a setting throws where
it reads it and answers that one request with a failure, while something reading on every request
would fail the health endpoint too and have the server restarted over a file a restart cannot fix.
Take the `as?` and fall back to what a deployment that configured nothing would have had.

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

**A key set outside the file.** `System.env` and `System.properties` share a namespace with the
whole machine, and the surface this project describes is a YAML file. The keys that bind to nothing
are looked for in the files a deployment wrote, and nowhere else.

**A key a framework owns.** Micronaut compiles its own configuration classes with the property
lookups inlined rather than declared, so what a deployment writes under `micronaut`, `endpoints`,
`flyway` or `netty` cannot be held to the rule above: there is no list of what those prefixes accept
to hold it against, and guessing one would take readiness down over a key that works.

**A deployment supplying its own implementation.** The set a setting picks from is what the server
ships. Making the declaration single is not making it open: there is no plugin surface here, and
adding one would be its own design.

**Secrets.** A secret is a string in the same file as everything else, and where it comes from is
the deployment's business.

**Per-tenant configuration.** One file describes one server; multi-tenancy would be designed as a
data model.

---

← [Design documentation](index.md)

---
description: What a closed-world image has to be told about, and why a green run on the JVM proves
  none of it.
paths:
  - "server/src/main/resources/META-INF/native-image/**"
  - "server/build.gradle.kts"
---

# Native image standard

The artifact a deployment runs is a GraalVM native image, compiled ahead of time with a closed
world: every class, method and resource reachable at runtime has to be known when the image is
built. The JVM this codebase is developed and unit-tested on has no such constraint.

**That gap is the whole subject of this document.** A rule broken here compiles, passes every unit
test, passes every integration test run against a JVM image, and fails in production — with a
reflection error naming a class that is plainly in the source tree. No other rule in these documents
has that failure shape.

## Anything constructed reflectively is declared

**A class the application only ever instantiates by name must be registered in the reflection
metadata**, with the constructor the application actually calls. The server's own entries are in
`server/src/main/resources/META-INF/native-image/com.sympauthy/server/reflect-config.json`.
Nothing else finds it: the compiler sees no call site, so the class is not reachable and is not
compiled into the image at all.

**The generated mappers are the family this rule exists for.** They are produced by an annotation
processor, published by a factory that looks them up by class, and never constructed by any code a
compiler can see. Adding a mapper is therefore two edits: the mapper, and its generated
implementation's entry in the metadata. Forgetting the second is the single most common way to break
the native build, and it is invisible in review because the diff that needs it does not touch the
metadata file.

**The check is mechanical, so it belongs in review as a question, not as a reading.** Every
generated implementation has a source counterpart and every entry has one too; the two sets should
match exactly, and comparing them is a one-line script rather than a careful look.

## Metadata is split per artifact

**The server's own metadata is separate from the metadata a dependency needs.** They are separate
directories because they have separate lifetimes: the entries the server owns change when the server
changes, and the entries a driver needs change when the driver is upgraded. Merging them would mean
re-deriving, on every dependency bump, which half of one file was ours.

## Configuration read at build time, not run time

**A framework that scans the classpath does its scanning when the image is built.** Migration
locations are the case here: the scan cannot happen at startup because the image no longer has a
classpath to scan, so the locations are passed as a build argument and are baked in.

The consequence is that **a new migration folder is a build change, not a configuration change.** A
folder that is only listed in the runtime configuration works on the JVM and finds nothing in the
image.

## A green JVM run proves nothing here

**Every rule above fails only in the native image, so only a native run tests them.** The unit tests
run on a JVM. The integration tests run against whatever image they are pointed at, and pointing
them at a JVM image — which is the fast, convenient way to test a working tree — exercises none of
this.

This is not a reason to stop using a JVM image locally; it is the reason the native run in
continuous integration is the source of truth, and the reason a change that touches mappers,
reflection or resource loading is not finished when the local suite is green. See
[the testing standard](testing-standard.md).

## What this standard does not cover

**Resources.** Message bundles, mail templates and migrations are reachable today through the
framework's own metadata and the community metadata repository, so nothing here declares them by
hand. The first resource loaded by a name the framework does not know about is the point at which
that stops being true, and a rule about it belongs here when it happens.

**Build-time initialization.** Nothing is deliberately moved into the image build. Classes that
initialize eagerly do so at runtime, which costs startup time nobody has needed to reclaim.

**Image size and startup budgets.** Neither is measured, so neither is a constraint anything is
designed against.

---

← [Design documentation](index.md)

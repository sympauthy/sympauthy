---
description: What a closed-world image has to be told about, and why a green run on the JVM proves
  none of it.
paths:
  - "server/src/main/resources/META-INF/native-image/**"
  - "server/build.gradle.kts"
---

# Native image standard

The artifact a deployment runs is a GraalVM native image, compiled ahead of time with a closed
world: every class, method and resource reachable at runtime is known when the image is built. The
JVM this codebase is developed and unit-tested on has no such constraint.

## Anything constructed reflectively is declared

**A class the application instantiates by name is registered in the reflection metadata**, with the
constructor the application calls. The server's own entries are in
`server/src/main/resources/META-INF/native-image/com.sympauthy/server/reflect-config.json`.

**A generated mapper is two edits — the mapper, and its generated implementation's entry in the
metadata.** Make both in the same change.

**Review compares the two sets.** Every generated implementation has an entry and every entry has a
source counterpart; compare them with a script rather than by reading.

## Metadata is split per artifact

**The server's own metadata lives apart from the metadata a dependency needs.** Put an entry the
server owns under its own directory and one a driver needs under the driver's.

## Configuration read at build time, not run time

**Configuration a framework resolves by scanning the classpath is passed as a build argument.** The
migration locations are declared that way, so a new migration folder is a build change.

## The native run is the source of truth

**A change touching mappers, reflection or resource loading is finished when the native run is
green.** Run the suites on the JVM while working, and read
[the testing standard](testing-standard.md) for what each of them proves.

## What this standard does not cover

**Resources.** Bundles, mail templates and migrations are reachable through the framework's own
metadata and the community metadata repository; a rule belongs here once a resource is loaded by a
name the framework does not know.

**Build-time initialization.** Nothing is moved into the image build, and a class that initializes
eagerly does so at runtime.

**Image size and startup budgets.** Neither is measured, so neither constrains a design.

---

← [Design documentation](index.md)

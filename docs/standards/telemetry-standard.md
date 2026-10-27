---
description: What the server exports to an OpenTelemetry collector, what an exported attribute may
  carry, and how an instrument is named and registered.
paths:
  - "server/src/main/kotlin/com/sympauthy/monitoring/telemetry/**"
  - "server/src/main/resources/application*.yml"
  - "server/src/main/resources/logback.xml"
  - "server/build.gradle.kts"
---

# Telemetry standard

The rules what the server exports is held to. What arrives at the collector, why a query string
never does, and what the server says when the collector stops answering are
[telemetry](../design/telemetry.md)'s; this holds what a change has to get right for that to stay
true.

## What is exported, and through what

**Telemetry stays off until a deployment turns it on.** Ship `micronaut.otel.enabled: false`, and
set it to nothing else in any file or environment the server ships.

**A bean that needs the SDK is conditioned on `TELEMETRY_ENABLED` not being `false`**, the
condition the framework puts on its own, so that the server starts with the integration absent.

**A setting that is the same for every deployment ships in `application.yml`.** The exporters, the
protocol the one sender speaks, the metric export interval and the routes no trace is taken for are
the server's to decide, so that a deployment's file names only the flag and the collector.

**Everything leaves through the one SDK the tracing module builds.** A library exporting through a
client of its own — a Micrometer registry, a second OTLP exporter, a vendor's agent — is not
added.

**An exporter the SDK builds is reported to `Condition.Collector`.** Wrap it in
`TelemetryExportCustomizer`, through the `CollectorReport` the other signals share, so that its
failures degrade the condition and its successes recover it only once no other signal's last export
failed.

**A shipped file names no header to capture.** Leave `otel.http.server.request-headers`, its
response twin and the client's empty; a deployment that names one owns what it copies.

## What an attribute may carry

**An attribute carries a route, a status, a method, an identifier, or a name the configuration
declares.** A path naming an account, a client's id and a provider's id are identifiers.

**An attribute never carries a credential or a value a person typed.** A token, a code, a state, a
secret, a password, a claim's value, or a query string that may hold one.

**An attribute holding a URL has its query redacted where the request's attributes are gathered.**
An extractor added for another instrumenter replaces the query the way
`QueryRedactingHttpServerAttributesExtractor` does, ordered after the extractors that set it.

**A metric attribute takes its values from a set the server bounds.** Key on the route template,
never on the path, and on an id only where the configuration declares the set it comes from.

## An instrument

**An instrument the semantic conventions name is named as they name it**, with their unit and their
attribute keys.

**An instrument they do not name is named `sympauthy.<subject>.<measure>`** —
`sympauthy.gc.collection.count`.

**An instrument is registered on the `OpenTelemetry` bean, and closed when its bean is destroyed.**

**A reading the native image leaves empty is not exported.** Check it on the native run before
adding it, and drop it from the JVM run too, so that both export one set.

## What this standard does not cover

**What a domain event is counted under.** No manager is instrumented, and the first instrument one
adds settles what its attributes are.

**Sampling.** The SDK's default stands, and nothing here constrains a deployment's
`otel.traces.sampler`.

**A record of security events.** It is not telemetry, and nothing here governs its delivery.

---

← [How the code is written](index.md)

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

**Every process exports under a `service.instance.id` of its own.** `TelemetryInstanceCustomizer`
draws it at startup and keeps one a deployment names; a resource the SDK builds without it merges
the series of every instance.

## What an attribute may carry

**An attribute carries a route, a status, a method, an identifier, or a name the configuration
declares.** A path naming an account, a client's id and a provider's id are identifiers.

**An attribute never carries a credential or a value a person typed.** A token, a code, a state, a
secret, a password, a claim's value, or a query string that may hold one.

## Logs

**A record is exported as logback is handed it, and held to [the logging
standard](logging-standard.md).** What a record may carry and where the OpenTelemetry appender sits
are that standard's rules.

## Metrics

### An instrument

**An instrument recorded where an event happens is not conditioned.** It is built on the
`OpenTelemetry` bean, which is OpenTelemetry's no-op while telemetry is off, so the code recording
it has one path whether a collector is named or not.

**A technical instrument takes the name an existing convention gives it, with its unit and its
attribute keys.** Look in OpenTelemetry's semantic conventions first, then in OpenTelemetry's own
instrumentation and JMX metrics, then in a major library's — Micrometer's
`netty.allocator.memory.used`.

**A business instrument, or one about a notion of this server's own, is named
`sympauthy.<subject>.<measure>`** — `sympauthy.token.request.duration`,
`sympauthy.condition.degraded`.

**An instrument is registered on the `OpenTelemetry` bean, and closed when its bean is destroyed.**

**An instrument's name is a public constant in the companion of the class registering it.** The
builder and every test reading the instrument name it through that constant, so that a search for
the constant finds each of them.

**A reading the native image leaves empty is not exported.** Check it on the native run before
adding it, and drop it from the JVM run too, so that both export one set.

### What a metric attribute may carry

**A metric attribute takes its values from a set the server bounds.** Key on the route template,
never on the path, and on an id only where the configuration declares the set it comes from.

**An id is recorded once the configuration has been found to declare it, and left out otherwise.**
`client_id` waits for the client to authenticate: the id a request sent is chosen by whoever sent
it.

**No metric attribute names an account, a session, a grant, a token, a scope list, an address, a
redirect URI or an exception's message.** A single request is a trace's to show.

### An event a metric counts

What a domain event is counted under was left open until the server counted the events an operator
watches; these are the rules those settled, and
[telemetry](../design/telemetry.md#what-an-authorization-server-counts) says why.

**A kind of event is counted by a metric of its own**, carrying the attributes that event has.

**An event that takes time is counted by the histogram timing it, and by nothing beside it.** Record
it in seconds, with bucket boundaries chosen for its scale: the SDK's defaults are sized for
milliseconds.

**How an event ended is `outcome` and `error.type`.** `outcome` is `success`, `refused` where the
caller caused it, or `error` where the server or a dependency failed; take both from
`Throwable.toOutcome()` where a failure was thrown. `error.type` is the error code the caller was
answered with, and the failure's class where it carries none.

**A metric about a use case is recorded in the manager running it.** A controller records nothing;
a request the HTTP layer refuses before a manager is called is `http.server.request.duration`'s.

**An attribute of the server's own is written in snake_case and takes no prefix.** Where a
convention names one — `error.type`, `memory.type` — use its name.

**A call made outside the process is reported through `RemoteCallMonitor.observe` or `reportCall`,
with the operation it made.** It times the call and reports it to the call's condition; never time
a call beside it, and never report one to `ConditionMonitor` directly. A collector's export is not
one of them: it is reported to `Condition.Collector` alone, through `CollectorReport`.

**The database is only a condition.** The connection factory reports to `Condition.Database`, and
no metric times a query: its span is what says how long it took.

**A metric about a condition is named for the condition, and keys it as `condition`.** A condition
is not always reached by a call, so a metric about calls is named for the calls.

**A scheduled job runs through `JobLeaseManager`, and a run never throws.** A run is recorded as
it ended, and a failure is reported the way the logging standard reports one about to be written
at WARN.

## Traces

**A span the server opens itself starts only under a span already open.** A scheduled job's work
has no request around it, and a root span there is a trace per run, per instance, for the life of
the deployment. `QueryTracingListener` opens none for a query made outside a request.

**An attribute holding a URL has its query redacted where the request's attributes are gathered.**
An extractor added for another instrumenter replaces the query the way
`QueryRedactingHttpServerAttributesExtractor` does, ordered after the extractors that set it.

**A statement is exported with every value in it replaced, bound or written into it.** Describe a
query through `DatabaseQuery`, which holds a statement only once OpenTelemetry's SQL analyzer has
replaced its values, and read double quotes as identifiers, which is what both dialects make them.

**A shipped file names no header to capture.** Leave `otel.http.server.request-headers`, its
response twin and the client's empty; a deployment that names one owns what it copies.

## What this standard does not cover

**Sampling.** The SDK's default stands, and nothing here constrains a deployment's
`otel.traces.sampler`.

**A record of security events.** It is not telemetry, and nothing here governs its delivery.

---

← [How the code is written](index.md)

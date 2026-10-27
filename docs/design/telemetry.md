# Telemetry

What the server exports about itself to an OpenTelemetry collector: a span per request it serves
and per call it makes, the metrics that come with them and with the runtime, and every log record
stamped with the trace it was written under. All of it leaves through one SDK, over one exporter,
to one collector a deployment names, and none of it leaves until one is named.

The rules a change is held to — what an exported attribute may carry, how an instrument is named
— are [the telemetry standard](../standards/telemetry-standard.md). What the server says on its
console when the collector stops answering is [logging](logging.md)'s, which this document only
adds a condition to.

## What a deployment gets

**Nothing is exported until a deployment turns telemetry on.** `application.yml` ships
`micronaut.otel.enabled: false`, which removes the integration whole: no SDK is built, no request
opens a span, no exporter thread starts and no socket is opened. That is what a self-hosted server
owes the person running it, who may have no collector at all, and whose server would otherwise be
degraded from its first export — see [the design
FAQ](design-faq.md#is-telemetry-turned-off-by-leaving-the-exporters-unset).

**A deployment turns it on with the flag and the collector's endpoint, and writes nothing else.**

```yaml
micronaut:
  otel:
    enabled: true
otel:
  exporter:
    otlp:
      endpoint: http://collector:4318
```

**One collector for every signal, configured under one prefix.** Every key but the flag is under
`otel`, the endpoint under `otel.exporter.otlp`, and the service is named `sympauthy` without a key
of its own; `otel.resource.attributes` is where a deployment adds its environment or its region. A
single backend of the deployment's choosing sits behind the collector, and this server ships no
integration with any of them — see [the design
FAQ](design-faq.md#why-the-opentelemetry-sdk-rather-than-micrometer).

**What does not depend on the deployment ships in `application.yml` with the flag.** The file
ships:

- every signal exported over OTLP, since the server sends to one collector or to none; a deployment
  that wants one of them alone sets the others' exporter to `none`;
- the protocol, `http/protobuf`, because it is the only one the server can send, while the SDK
  falls back to `grpc` when none is named and would then export nothing;
- a metric export every ten seconds, rather than the SDK's minute;
- `/health.*` excluded from tracing, since a liveness probe every few seconds would otherwise be the
  most-traced route in a deployment.

**An exclusion a deployment writes replaces the shipped one rather than adding to it.**
`otel.exclusions` is a list of regular expressions, and a list in a deployment's file replaces the
list it overrides, so a deployment excluding a route of its own keeps `/health.*` by writing it
again.

| Signal | What arrives |
| --- | --- |
| traces | a server span per request, named by its route; a client span per call to a provider or a webhook, under the span of the request it was made for |
| metrics | the duration of every request served and every call made, keyed by route, method and status; the runtime metrics below |
| logs | every record logback is handed, at every level, carrying the trace and span it was written under |

**The instrumentation is the framework's own, and no manager is instrumented.** What an
authorization server counts — tokens issued, sign-ins refused, mail queued — and under which
attributes is a design of its own, and this one leaves it open.

## What leaves with it

**No query string leaves.** A query is replaced whole with `REDACTED`, both in `url.query` and in
the query half of `url.full`. This server's URLs carry credentials there — the invitation token an
authorization request binds to a flow, the code and the state a provider redirects back with — and
a list of the parameters safe to keep would go stale the next time a specification adds one, in
silence. Nothing is lost that matters: the route names the endpoint, and the path says what was
asked for. See [the design FAQ](design-faq.md#is-a-query-string-redacted-parameter-by-parameter).

**It is redacted where the request's attributes are gathered, not where spans are exported.** The
span and the request's duration are both recorded from those attributes, and a histogram's
exemplar keeps the ones its series is not keyed by: a rule applied to spans alone still exports the
query, inside the metric.

**The path stays, and the distinction is the point.** A path under `/api/v1/admin/users/{userId}`
names a person, and it is kept: an identifier names somebody the operator can already look up in
their own database, while a credential lets whoever reads it act as them. An operator running the
collector already holds the users table; they should not also hold a working authorization code.

**A header is exported only when a deployment names it.** `otel.http.server.request-headers` and
its client and response twins copy a named header onto every span, and on this server the
interesting-looking ones are `Authorization`, `Cookie` and `DPoP`. None is named by default.

**A log record and an exception carry what the line carries.** The record is the line, its level
and its logger, held to [the logging standard](../standards/logging-standard.md)'s rule that no line
carries a credential; an exception a failed request recorded on its span carries its message and
stack trace, written by this server under the same rule.

## Only the runtime metrics a native image can fill

**The production artifact is a native image, and most of what the platform's management beans
report is empty there.** A probe built with GraalVM 25, its Serial collector and no monitoring
options, compared each reading against a JVM run:

| Read from | In a native image | Exported |
| --- | --- | --- |
| heap usage, per pool | real values | `jvm.memory.used`, `.committed`, `.limit` |
| non-heap pools | always `0`: code is compiled into the image | no |
| garbage-collection notifications | registered, never sent | no `jvm.gc.duration` |
| each collector's count and accumulated time | real values | `sympauthy.gc.collection.count`, `.time` |
| thread count, daemon or not | real values | `jvm.thread.count` |
| threads per state | nothing | no |
| classes loaded, JIT compilation | always `0`, or absent | no |
| buffer pools | no bean at all | no |
| process CPU time, processor count | real values | `jvm.cpu.time`, `jvm.cpu.count` |

**A metric that is always zero is not exported.** A permanently empty `jvm.class.loaded` reads as a
fact about the server, and it is not one. The JVM run exports the same set, so development and
production are read the same way.

**Garbage collection is read from the collectors themselves.** It says how many collections there
were and how long they took in total, and never how long one pause took: nothing a native image
fills can say more. The names are the server's own because the semantic conventions' only GC
metric is the per-pause duration.

**The server reads these itself rather than through OpenTelemetry's runtime instrumentation.** That
instrumentation registers all of its readings or none, and the empty non-heap pool is a series of
the heap metrics rather than an instrument that could be left out — see [the design
FAQ](design-faq.md#are-the-runtime-metrics-read-through-opentelemetrys-runtime-instrumentation).

## When the collector does not answer

**The collector is a condition**, reported the way
[logging](logging.md#when-the-server-is-not-fully-operational) reports any dependency: the first
failed export writes the ERROR, every failure after it is counted, and an export that succeeds
followed by the quiet period recovers it in one line. `/health` lists it as `collector`. One
condition stands for every signal, since they all leave through one endpoint and fail together.

**Every failed export counts, whatever the collector answered.** The request is composed by this
server alone, so a refusal says the collector, or what this server was configured to reach it with,
is wrong — the reason a client's authorization webhook's `4xx` counts too.

**The recovery line says what the outage lost.** It counts the spans and log records in every export
that failed. A metric loses nothing worth counting, since each is exported as its running total and
the next export that succeeds carries what a failed one did not.

**What the SDK's own queues drop is not counted on the console.** The SDK calls nothing back when a
queue is full; it records the drop in metrics of its own, which are exported with the rest. A queue
dropping while the collector answers is therefore visible at the collector and nowhere on the
console.

**The SDK's own report of a failed export is silenced.** It writes one per failure, to stderr, on
its own schedule, which is a second and unbounded account of an outage the condition states once.

**Nothing the collector does can slow a request.** A span and a log record are added to the SDK's
bounded queues, which drop rather than wait, and exported by its own threads; a metric aggregates in
memory until it is read. Those threads compete for the CPU as equals of the ones serving requests,
for [the same reason the console's writer
does](logging.md#what-this-document-does-not-settle), so what bounds their share is the volume they
are handed: the sampling ratio and the export interval.

**The ERROR about the collector cannot feed itself.** It is exported like any record and lost with
the rest while the collector is down, and the failure to export it writes nothing, because only a
change of state does.

## What implements it

**Micronaut's tracing module builds the SDK and instruments the HTTP server and client.** Its
factory reads every `otel` key and hands the result to the SDK's own autoconfiguration; its filters
open the spans, and the request durations come from the same instrumenters. The log appender is
OpenTelemetry's, installed by the module once the application has started. Every bean of it is
conditioned on `micronaut.otel.enabled` not being `false`, and so is every bean the server adds.

**What the server adds is in `monitoring/telemetry/`, beside the conditions it reports to.** Two
attribute extractors, one for the server and one for the client, redact the query; they are ordered
after the framework's own, and they overwrite rather than remove because the builder they are handed
cannot remove an attribute once it is set. A customizer wraps every exporter the SDK builds so that
each export is reported to the collector's condition, and the runtime metrics are read there too.
They report on the server rather than serve anything, which by [the architecture's
test](architecture.md#beside-the-layers) puts them beside the layers.

**`logback.xml` places the appender beside the console's queue, not behind it.** The trace a record
is stamped with is read from the thread that logged it, so a record handed to the queue's thread
first would carry none; what it costs the caller is one add to the SDK's queue. The same file
silences the SDK's own loggers, which write through `java.util.logging`, by setting the levels
logback propagates to them.

**With telemetry off, the server detaches the appender at startup.** `logback.xml` is read before
the flag can be, so the appender is always attached; with no SDK ever installed into it, it would
hold every record it is handed until its queue of a thousand filled, then say so on stderr.

**The exporter sends over the JDK's HTTP client.** The default sender is OkHttp, a second HTTP stack
and a second Kotlin runtime in a distroless image for one POST the JDK already makes. It speaks
`http/protobuf` alone: no sender for `grpc` ships with the server.

**A trace survives the `suspend` boundaries of a request.** The tracing module carries it on
Micronaut's propagated context rather than in a thread local, and that is what puts the client span
of a webhook call under the server span of the request that made it; a flat trace is the symptom of
it breaking.

## What this document does not settle

**What an authorization server counts.** No manager is instrumented, and the instruments a domain
event deserves — and what their attributes may carry — are a design of their own.

**Spans for database queries.** Micronaut's R2DBC tracing wraps the connection factory through a
proxy, which is a decision about the `data` layer and about the native image, taken on its own.

**A sampling policy.** The SDK's default stands: a trace is sampled whenever its parent was, and
always when it has none. A deployment that needs a ratio sets `otel.traces.sampler`.

**Whether a trace context a client sends is trusted.** The SDK's default stands: a `traceparent`
header makes the request's span the child of the caller's. On a server whose callers include the
public, that is a choice to make rather than a default to inherit, and it has not been made.

**A scrape endpoint.** A metrics endpoint would be a new surface, with an audience and a gate of its
own; this server pushes, so there is nothing new to gate.

**A record of security events.** An audit trail is a different object, with a different retention
and a different audience, from the operational records exported here.

**A key under `otel` written wrong.** It is ignored in silence, where a key under a prefix the
server declares takes readiness down — [the `config` layer
standard](../standards/config-layer-code-standard.md) says why the framework's keys cannot be held
to that.

**A GraalVM release filling different readings.** The exported set is the one the probe found on
GraalVM 25 with the Serial collector, and nothing notices a reading that falls silent after an
upgrade.

**A scenario exporting to a collector.** The integration suite runs no server against one. The
unit tests prove the redaction and the reports to the collector's condition, piece by piece; what
the server assembled sends — the trace across a webhook call, nothing at all with the flag off — no
test checks.

---

← [How the system works](index.md)

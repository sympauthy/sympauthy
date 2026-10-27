---
description: What a log line is written at, how its message is built, what it may never carry, and
  how a dependency's failure is reported.
paths:
  - "server/src/main/kotlin/**"
  - "server/src/main/resources/logback.xml"
---

# Logging standard

The rules a line written to the log is held to. Why a line costs its caller almost nothing, what
the queue in front of the console does when it is full, and what `/health` publishes are
[logging](../design/logging.md)'s; this holds what a call site has to get right for that to stay
true.

## Levels

**ERROR is written when the server stops being fully operational, and at no other time.** At
runtime that is a condition changing state, and at startup the configuration's verdict; a failure
anywhere else is reported to its condition or written at a lower level.

**A dependency reached outside this process is reported to its condition, not to the logger.**
Report its failures and its successes both: the first degrades it and writes the ERROR, the second
is what lets it recover.

**A failure about to be written at WARN is first handed to `ConditionMonitor.absorb()`, and written
only if it answers false.** A dependency that is down fails every operation meeting it, and its
condition says so once.

**Only a failure saying the dependency is unwell is reported as one.** A refused connection, a
timeout and a `5xx` are; a `4xx` caused by what a person or a client sent is not. A `4xx` answering
a request this server composed alone — a client's authorization webhook — is.

**Below ERROR, the level is what an operator has to be doing to need the line.**

| Level | The line says |
| --- | --- |
| WARN | one operation failed in a way the server did not expect, and an operator would act on it |
| INFO | the server's lifecycle: startup, a recovery, a backlog replayed |
| DEBUG | a decision an operator turns on to investigate |

**An outcome the caller caused is not logged.** The response carries it.

**A line any caller can provoke at will is written at DEBUG at most.** Above it, anybody can crowd
the WARN an operator needs out of the queue.

## A call site

**A logger is obtained with `loggerForClass()`**, which answers the server's own `Logger` rather than
SLF4J's. Nothing reaches SLF4J's directly.

**A message is passed as a lambda, never as a built string.** A string template is built on every
call whether its level is enabled or not, and the server's `Logger` takes nothing else.

```kotlin
logger.debug { "Skipping ${job.leaseName}: another instance holds its lease." }
```

**A throwable is passed as the throwable, never interpolated into the message.** The stack trace is
the writer's to format.

**A record carries no credential, at any level.** A token, a code, a password, a secret, a key, or a
URL or query string that may carry one.

## The configuration

**A pattern carries no caller data.** `%caller`, `%file`, `%line`, `%method` and `%class` walk the
stack on the caller's thread.

**Every appender below ERROR is reached through the queue, except OpenTelemetry's.** An appender
added beside it writes on the caller's thread for every line, which is the cost the queue exists to
remove. OpenTelemetry's has to read the trace from the caller's thread, and costs it the record it
builds there — a throwable's stack trace rendered as text included — and one add to the SDK's own
bounded queue.

**The OpenTelemetry appender captures no code attribute.** Leave `captureCodeAttributes` off, for
the reason a pattern carries no caller data.

## What this standard does not cover

**What a span, a metric or an exported attribute may carry.** [The telemetry
standard](telemetry-standard.md) holds it.

**A record of security events.** Its delivery is not a log line's, and nothing here governs it.

**The console's format.** It is written for a person to read, and nothing here shapes it beyond the
caller data it may not carry.

---

← [How the code is written](index.md)

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

**A logger is obtained with `loggerForClass()`.**

**A message is passed as a lambda, never as a built string.** A string template is built on every
call whether its level is enabled or not.

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

**Every appender below ERROR is reached through the queue.** An appender added beside it writes on
the caller's thread for every line, which is the cost the queue exists to remove.

## What this standard does not cover

**What a span, a metric or an exported attribute may carry.** The server exports none, and the first
that does settles it.

**A record of security events.** Its delivery is not a log line's, and nothing here governs it.

**The console's format.** It is written for a person to read, and nothing here shapes it beyond the
caller data it may not carry.

---

← [How the code is written](index.md)

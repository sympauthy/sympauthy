# Logging

What the server says about itself while it runs: the lines it writes to the console, and what it
publishes on `/health` about the dependencies it cannot work without. Both rest on one rule, that a
log line costs the code writing it almost nothing and never makes it wait, and on one meaning, that
an ERROR says the server is not fully operational and says it once.

The rules a call site is held to — which level a line takes, how its message is built, what it may
never carry — are [the logging standard](../standards/logging-standard.md). The configuration's own
verdict, the one thing readiness reports, is [the `config` layer
standard](../standards/config-layer-code-standard.md)'s.

## What an operator reads

**An ERROR line means the server is not fully operational.** A dependency stopped answering, a queue
is dropping what it was handed, or the configuration leaves the server unready. Nothing else is an
ERROR: a request that failed is not, and neither is a person typing a wrong password a thousand
times.

**An outage costs two lines, however long it lasts.** The ERROR is written when a dependency first
fails, and one line at INFO when it has recovered, saying for how long it was degraded and how many
failures it absorbed. Every failure in between is counted and writes nothing, so an outage under
load says what an outage on an idle server says.

**`/health` says what is degraded now.** Each degraded dependency is listed by name with the moment
it degraded, and the response stays `200`. The console is where the server announces a change; this
is where somebody arriving later reads the state.

**Below ERROR, a level says how far an operator has to be looking.**

| Level | What it carries |
| --- | --- |
| WARN | one operation failed in a way the server did not expect, and an operator would act on it |
| INFO | the server's lifecycle: startup, a recovery, a backlog replayed |
| DEBUG | the decisions an operator turns on to investigate |

**What a caller caused is not logged at all.** A wrong password, a refused grant, a stale `state`:
the response says it, and a line for each would let anybody fill the log on demand.

## When the server is not fully operational

**A dependency the server cannot fully work without is a condition, operational or degraded.** It is
anything reached outside this process on a request's behalf — the database, the mail sender, a
provider, a client's authorization webhook — and the console's own queue, whose dropping a line is
the server failing to say what it was asked to. The declaration of the conditions is the authority
on which exist.

**The first failure degrades a condition, and writes the ERROR.** A failure while it is degraded
only counts. That is what keeps the cost of an outage flat: written per failure, a provider down
under a thousand requests a second would be a thousand synchronous writes a second, at the moment
the server can least afford them — see [the design
FAQ](design-faq.md#is-an-error-written-per-failure-or-per-change-of-state).

**A condition recovers on a success followed by a quiet period without a failure.** A dependency
failing one call in two would otherwise flip on every call, and bring back by another route the line
per failure the first rule removed. The quiet period bounds the ERROR lines a condition writes to
one per period.

**Only a failure that says the dependency is unwell counts.** A connection refused, a timeout, a
`5xx`, a database connection that cannot be obtained or is lost. A `4xx` caused by what was sent — a
provider refusing a code somebody replayed — says nothing about the provider, and counting it would
let anybody degrade the server with bad input.

**A `4xx` from a client's authorization webhook counts.** The request is composed by this server
alone, from what it has already validated, so a refusal says the webhook or the secret it shares is
wrong; not counting it would let a webhook answering every call with a `401` deny every
authorization in silence. A webhook refusing one person's claims degrades its condition for
everybody, at most once per quiet period.

**A provider that cannot be reached at startup starts degraded**, and recovers like any other
condition once it answers. A failed resolution at startup is otherwise a line nothing ever follows
up.

**The configuration is the one ERROR that is not a condition.** It is read once and never recovers
while the server runs, and an invalid one already takes readiness down — a stronger answer than
degraded, and the right one, because a restart with a corrected file is what fixes it.

### What `/health` publishes

**A degraded condition degrades `/health`, and changes neither readiness nor liveness.** A provider
that is down is down for every instance at once, so taking readiness down would pull every instance
out of the load balancer and make one broken sign-in method an outage; restarting fixes nothing
somebody else is running. Readiness keeps reporting the configuration alone — see [the design
FAQ](design-faq.md#does-a-degraded-dependency-take-readiness-down).

**A condition is published by name and state, never with its failure.** The details of `/health` are
visible anonymously, and an exception message carries hostnames, internal addresses and response
bodies. The names say nothing the sign-in pages do not — a provider's id, a client's id, `database`
— and the failure is in the ERROR line, for whoever reads the console.

**Each instance publishes what it observed itself.** Two instances may disagree for the length of a
quiet period, each answering for the calls it made.

## What a log call costs the caller

### Where the caller is running

**Every thread a request runs on is one the server cannot afford to block.** A `suspend` handler
starts on the Netty event loop and, from its first suspension on, resumes on the coroutine pool
Micronaut gives a route that names no executor — as many threads as there are cores. A repository
call runs partly on the database driver's I/O threads under PostgreSQL, and entirely on the caller's
thread under H2, whose driver runs a statement where it is subscribed. A line written to stdout on
any of them stalls everything sharing it whenever stdout is slow, which in a container is whenever
the log driver is.

### The queue

**A log call decides and captures, and writes nowhere.** The caller checks the level and captures
what only it can see — the arguments, the time, its thread — and hands the line to a bounded queue.
One writer thread takes it from there: the layout, the encoding and the write to stdout are its.

**A full queue drops a line rather than make its caller wait.** INFO and below go first, once the
queue is four fifths full, so that a burst of them leaves room for a WARN; a WARN is dropped only
when the queue is full. Nothing below ERROR is worth a request waiting for — see [the design
FAQ](design-faq.md#does-a-full-log-queue-make-its-caller-wait).

**The writer competes for the CPU as an equal of the threads serving requests.** It is not
deprioritized, so under contention it takes a share of a core like any other runnable thread; what
bounds that share is how much it is handed, which is the level a deployment logs at. A writer that
falls behind costs lines, never requests: the queue fills and drops.

**A dropped line is counted, and the first drop degrades the console's condition.** A queue that
drops is the backpressure an operator has to hear about, and it says so the way any condition does.

**On shutdown, the queue is drained for up to a second and the rest is abandoned.** A rolling
redeploy is not held for lines that are, by their level, not critical; the ones that are were never
queued.

### The ERROR path

**An ERROR is written synchronously, straight to the console, on the caller's thread.** It is the
one line that must not be lost, and it is written exactly when it would be: when the server is
degraded, the queue may be full, and a crash may follow — see [the design
FAQ](design-faq.md#is-an-error-written-through-the-log-queue). It is the one path that can wait on
stdout, which is why only a condition changing state takes it.

**An ERROR may overtake a line queued before it.** Every line carries its timestamp, which is what
orders them; holding an ERROR until the queue drained would undo the reason it does not queue.

## What implements it

**`logback.xml` is where the two paths are wired**: the queue every level below ERROR goes through,
in front of the console, and the console written to directly for ERROR. The queue's size, its drop
thresholds and its drain on shutdown are fixed there rather than in the server's configuration — a
deployment supplying its own logback file in place of the shipped one keeps whichever of these
guarantees it reproduces.

**The appender in front of the queue is the server's own**, a subclass of logback's that counts what
it drops. Logback builds it from that file, outside the application context, so it cannot be handed
the conditions it reports to; the count it keeps is the one static bridge in the design, and a job
running every second hands it to the console's condition. That job is also what ends a quiet period,
so a condition recovers up to a second after its quiet period has run out.

**The conditions live in `monitoring/`, beside the layers.** `business` reports the dependencies its
managers call, `data` reports the database from the connection factory every repository obtains its
connections from, and the console's appender reports itself, so by [the architecture's
test](architecture.md#beside-the-layers) they belong to none of them. `api` reads the database's
condition to leave out of the log a `500` it has already reported. The health indicator in `health/`
reads them, the way it reads the configuration's verdict, and Micronaut's own R2DBC indicator is
turned off: it answers `503` when the database cannot be reached, and publishes the exception.

**A provider that could not be resolved is tried again every quiet period** until it resolves. Its
condition counts every attempt that fails, and the provider is served from the attempt that does not.

**A message is built only when its level is enabled**, because the server's own `Logger`, in
`monitoring/`, takes a message as a lambda and nothing else. It wraps SLF4J's rather than extending
it, so the overloads taking a built string are not there to be called.

## What this document does not settle

**Telemetry.** The server exports no trace, metric or log record anywhere but the console. Where
those would leave from, and which threads would carry them, is open.

**A record of security events.** An audit trail kept for operators and for the person concerned is
not a log line, and nothing here is its delivery: a queue that drops under pressure is the wrong
home for a record somebody will be held to.

**The security context.** Its writes stay on the request path, in the order a later step of the
same flow reads them; [the security context](security-context.md) is their design, and nothing here
moves them.

**Whether readiness checks the database.** It does not, and a database that cannot be reached is a
degraded condition rather than an unready server. Whether it should be both is undecided.

**The console's format.** Lines are written for a person to read. A structured format for a
deployment shipping them to a log store is not offered.

**A stdout that stops being read.** The queue fills and says so, but an ERROR written while stdout
is blocked waits with it, and nothing here detects that.

**The numbers.** The queue's size, the drop threshold, the drain and the quiet period are fixed, and
none was measured against a benchmark.

**Deprioritizing the writer.** Yielding the CPU to requests under contention is not done. Java's own
thread priority cannot do it: the native image discards it, and the JVM ignores it on Linux by
default. What would is the thread lowering its own `nice` through a native call, on Linux only, and
the cost of a binding per runtime was not taken.

---

← [How the system works](index.md)

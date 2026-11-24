# Delivery

What this server promises somebody who is not waiting on the request that made the promise: a mail
today, and the events a client receives through OpenID Shared Signals and Back-Channel Logout later.
This document holds when such an **item** leaves, how long it is tried against its **destination**
— the mail server today, a client's endpoint later — how the instances share the work, and what an
operator sees of it. Which mails exist, what each answer of the mail server means for one, and how a
mail is drawn are [mail](mail.md)'s; the metrics an item is counted under are
[telemetry](telemetry.md)'s.

Mail is the one kind built. The promises below are what the client events deliver under; the first
of them makes the delivery carry its own kind, against what that kind needs.

## An item leaves only once the change it reports has committed

**Whoever queues an item writes it inside the transaction of the change it reports.** A change
that rolls back — the request that lost a flow's version check, a sign-up whose identifier was
taken — takes its items back with it, so nobody is told about something that did not happen. A
process that dies after the commit leaves its items to whichever instance looks next, so nothing
committed is lost with it. That is one answer for every caller, which is the point: [the design
FAQ](design-faq.md#is-an-item-written-inside-the-transaction-of-the-change-it-reports) holds the two
answers it replaced, and [mail](mail.md#a-mail-commits-with-what-it-reports) what each mail commits
with.

## The instance that committed attempts it at once, and a poll catches up

**The instance that committed an item attempts it as soon as the commit returns.** That is the
**nudge**, and a person waiting on the page for their code gets it as fast as the mail server
answers. An attempt made on a nudge never waits behind the ones a backlog holds, and [the design
FAQ](design-faq.md#is-an-item-attempted-on-a-nudge-or-only-by-the-poll) holds why there is a poll
beside it.

**Every instance also looks for what is due, every `advanced.queue.poll-interval`.** That picks up
what an instance left when it died, a retry whose time has come, and an item whose nudge never
came: the stored item is what is safe, and a missed nudge costs it one poll. An instance attempts
at most `advanced.queue.batch-size` items of one destination at once, nudged or polled, so the rest
of a backlog stays for the other instances, and a destination that never answers holds at most that
many attempts on one of them. A nudge finding them all taken leaves its item to the next poll.

**A server that reports itself unready does not poll.** Configuration is read once, so it stays
unready until it restarts, and an item sent from a server answering errors points its reader at
it — a mail's links. It says so once, at WARN. An item a request it did serve queued is still
attempted on its nudge, and waits for a ready instance if that attempt fails.

## An item is tried until it goes through or expires

**Delivery is at least once.** An instance that dies after the destination accepted an item and
before it recorded that leaves the item to be delivered again; nothing reached over a network can
promise more, and the specifications the client events follow expect repeats.

**A retry waits `advanced.queue.retry.first-delay` after the attempt before it, and twice as long
each time after that, up to `advanced.queue.retry.max-delay`.** With the shipped values a ten-minute
validation code is tried eight times and outlasts a mail server down for about eight and a half
minutes.

**An item is given up as soon as its next attempt would fall after its expiry.** The expiry is set
by whoever queues it, because only they know how long it stays worth sending, as
[mail](mail.md#a-mail-is-worth-sending-for-as-long-as-what-it-carries) sets each mail's. Every item
has one, so none is tried for ever, and none goes out long after anybody wanted it. A give-up by
expiry is written at DEBUG: an expiry during an outage is the outage's, which [costs two lines
however long it lasts](logging.md).

**An item holds what it was queued for, and each attempt builds what it sends anew**, so what is
read at the attempt — [a mail's brand and the minutes its code has
left](mail.md#mails-are-drawn-in-the-brand) — is current at every retry.

**No order is guaranteed.** Items go oldest due first where they can, and a retried one may arrive
after one queued later: an order would need one reader per destination, and one item that keeps
failing would hold up every one behind it — [the design
FAQ](design-faq.md#does-the-delivery-guarantee-an-order) holds the rest.

**An item delivered or given up is deleted.** The queue is a hand-off and keeps no record; a
given-up item leaves its count.

**A destination that stops delivering keeps its items until each expires, and the poll gives them
up then.** Its deliverer is disabled — for mail, no mail server is configured — so nothing of it is
attempted. What was queued before waits, and is counted while it does: given up only at its expiry,
an item outlasts a destination turned off for a moment, and still never goes out long after.

## A failed attempt is retried while its destination may yet take the item

**What a destination answered puts a failed attempt in one of three outcomes.** A destination that
is unwell — unreachable, silent, or refusing this server itself — has its item retried and its
condition degraded, since every item meets it the same way. A refusal that may pass later has its
item retried, and leaves the condition as it was. A refusal that will not pass has its item given
up. Which of a destination's answers falls where is its kind's to say:
[mail](mail.md#what-the-mail-server-answers-decides-whether-a-mail-is-tried-again) holds the mail
server's.

**An item that fails for a reason of this server's own is given up at once**, and written at WARN:
it would fail the same way at every attempt, and nothing else reports it.

## The instances share the work, each holding the item it attempts under a lease

**An instance takes a lease on an item before it attempts it**, so two instances looking at once
take different items, and a deployment delivers its backlog in parallel instead of leaving it to
one. An item is written held by nobody, and whichever instance takes it first — the one that
committed it, on its nudge, or another on its poll — attempts it. Between two attempts nobody
holds it.

**A lease on an item lasts as long as its attempt, and is kept alive the way a job's lease is**:
renewed while the attempt runs, and taken by the next poll once it has gone a job lease's grace
period unrenewed, as [the locking standard](../standards/locking-standard.md#the-lease) has a lease
expire. Nobody guesses how long an attempt takes. [The design
FAQ](design-faq.md#is-an-item-leased-or-is-the-backlog) holds the leases that lost.

**An attempt that fails for a reason of this server's own hands its item back**, due as it was, so
that the next poll takes it again: the database not answering mid-attempt costs the item a poll, not
the lease. An item whose hand-back fails too is no longer renewed, and its lease lapses.

**An instance shutting down stops taking items, lets the attempts under way finish for
`advanced.queue.shutdown-grace`, and hands back what it still holds**, so another instance takes
those items on its next poll rather than once the leases lapse. One killed outright hands back
nothing, and its leases lapse instead; either way nothing committed is lost, and at worst an item
is sent twice.

## What implements it

**`QueueManager.enqueue` writes an item in the caller's transaction, and gives the nudge when that
transaction's connection closes.** A connection closes once its transaction has committed or
rolled back, so the nudge needs nothing from the caller, and after a rollback it finds nothing to
take. A caller outside a transaction commits the item on its own, and is nudged at once.

**`QueueDelivery` is everything after the write**: the poll, the claim and the lease, the retry or
the give-up at expiry, the hand-back at shutdown. It hands each item to the `QueueItemDeliverer`
its destination's package implements, which reads the payload, makes the attempt and answers it as
a `DeliveryAttempt`; that sealed type is the authority on what becomes of an item for each answer.
The mail server's deliverer is [mail](mail.md#what-implements-it)'s.

**An item is a row of `queue_items`, whatever its destination.** What the delivery reads of it is
columns: the destination and the kind it is counted under, its dates and where its retries stand.
Everything else is a JSON payload whose shape only that destination's deliverer knows, so a new
destination adds no table, and every one shares the claim, the lease and the gauges. A poll claims
the items of its own destination alone, so an instance of a build that came before a destination
never takes an item it cannot deliver. [The design
FAQ](design-faq.md#is-the-queue-one-table-or-a-table-per-destination) holds the tables that lost.

**An item's lease is two columns of its row**, held under the instance's name `JobLeaseManager`
mints and renewed by a cron of the queue's own, on the period a job lease is renewed on. The poll
is not a job run: it runs on every instance, takes no job lease, and is not recorded on
`sympauthy.job.run.duration`.

## What this document does not settle

**The client events.** Which exist, how a client subscribes, a paused stream and its order, how
long an event is retried, and how an endpoint's answers split between retried and given up are each
their own feature's, delivering through this.

**Configuration per kind.** Every `advanced.queue` key applies to every kind of item; a kind that
needs a value of its own adds it.

**Circuit breaking.** A destination that is down is retried by each item on its own schedule, and
nothing stops attempting it as a whole.

**Sharing a poll between destinations.** Each destination's poll claims its own items under its own
`advanced.queue.batch-size`. One poll claiming for every destination would need a cap per
destination as well, so that slow attempts against one destination could not hold every slot
another needs.

**Exactly-once delivery.** A destination that cannot be fenced cannot be promised it, and it is not
deferred.

---

← [How the system works](index.md)

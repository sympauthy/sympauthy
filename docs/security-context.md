# The security context

Every request carries three things this server may want to remember: the address it came from, the
user agent it claimed, and the location the deployment's edge worked out. Together they are the
security context, and they are the whole of what the server knows about *where* a person signs in
from.

None of it is trustworthy on its own. An address read from a header is whatever the caller wrote
there, unless something in front of this server overwrote it — so most of this document is about the
promise a deployment makes when it names a proxy, and about what the server does when no such
promise has been made. The settings are `advanced.security-context`, and the authentication and
authorization this sits beside are [security](security.md).

## What is believed, and on whose word

**No header is believed until an operator names the proxy that sets it.** With nothing configured
under `advanced.security-context`, the address is the peer of the socket the request arrived on, no
forwarded header is read at all, and no location is recorded.

**Naming a proxy is a promise that this server is only reachable through it.** There is no proxy
allow-list here, and nothing checks that a request carrying `CF-Connecting-IP` came from Cloudflare.
That check is the deployment's, made once with a firewall rule or an origin lock. It belongs there
because the server cannot know its own topology, and a list of CIDRs in a configuration file is a
second copy of that topology which goes stale in silence.

**The consequence, stated plainly: if the origin is reachable directly while a proxy is named,
anyone can set that header and choose what gets recorded about them.** Nothing in this server can
detect it. A deployment that cannot guarantee a closed origin names no proxy and accepts that what
it records is the proxy's own address, which is the shipped default.

## The address and the location are configured apart

**The address comes from exactly one named proxy; the location may come from several, or be
detected.** They are separate settings because they carry different risk and admit different
answers, and holding them together would give the weaker half the reach of the stronger one.

**Only the proxy nearest this server knows the address**, as the peer it accepted a connection from
rather than a value it was handed. So `advanced.security-context.ip` names one proxy, or one header
read as it stands, and falls back to the socket peer.

There is no detecting it, and no merging two answers. An edge reading an entry of `X-Forwarded-For`
reads it at a position only its own hop count explains, so a server guessing which edge is in front
would read that position under the wrong assumption and reach an entry the caller wrote — a forgery
that works through a *legitimate* proxy, which a closed origin does not stop.

**A location is published by each edge under a header of its own** — `CF-IPCountry`,
`X-Akamai-Edgescape`, `CloudFront-Viewer-City` — rather than at a position in one they share. Two
edges reading their own headers cannot be read at cross purposes, so
`advanced.security-context.geo` takes a list applied in order, each entry overriding the fields the
ones before it answered, and `auto-detect` reads every edge that publishes one. A wrong answer here
costs a wrong location on a record, not a request attributed to whoever asked for it.

**An edge publishing no location cannot be named under the geo setting.** nginx, Traefik, Caddy,
Fastly and Azure Front Door publish an address and nothing else, so naming one there is refused at
startup rather than accepted to no effect.

**A Kubernetes cluster on Google behind an nginx ingress is the shape this split is for**: the
address comes from the ingress, which is adjacent to this server, and the location from the load
balancer in front of it.

## What is read, and how

**An edge is a rule for extracting values, not a table of header names.** A `Map<field, header>`
cannot describe an edge that packs several fields into one header, as Google's load balancer and
Akamai's EdgeScape do, or one that puts a port beside the address, as CloudFront does — so each edge
carries the extraction it needs.

**Where a header arrives more than once, the last value is the edge's.** A caller may have sent it
already and a proxy may append rather than replace, so everything before the last is the caller's —
the same rule that makes only the rightmost entries of `X-Forwarded-For` worth reading.

**A header a deployment names for one field replaces that field and never parses.** An operator
naming a header is saying the value is in it, as it stands; a deployment needing a value dug out of
a packed header names the edge that knows how. A name no request could carry — one holding a space
or a colon — is refused at startup rather than silently matching nothing.

**It is read once, at the boundary, and passed on as an ordinary parameter.** A filter reads every
request ahead of the security filter and leaves the result on the request; a handler is handed it
and passes it down.

**What is refused is a manager reaching back for it.** There is no request-scoped bean and no
thread-local: [the general standard](general-code-standard.md#dependency-rules) keeps a manager
callable from a scheduled job and a unit test, and every manager here is `suspend`, so a
thread-local would be intermittently absent across the coroutine boundaries they cross — recording a
null address against a real security decision, silently.

**Reading it early is what lets something act on it.** An address that exists only once a caller has
been authenticated is no use to anything deciding whether to answer them at all, which is what
throttling will have to decide about a caller who has presented nothing yet.

**A configuration that did not parse is believed about nothing.** The reading narrows the sealed
configuration type rather than throwing, and falls back to the socket peer — which is where a
deployment that configured nothing lands anyway. A file that did not parse names no proxy, so it
makes none of the promise that believing one rests on; and a reading that threw would fail every
request in the chain, including the one telling an operator which key is at fault.

## What is kept, and for how long

**A place is recorded once a flow completes, against the person it completed for.** What is observed
when a credential verifies is held against the session that saw it, then folded into that person's
places once their flow succeeds — deduplicated on the address and the user agent, so a row is a
place somebody keeps signing in from rather than one per sign-in.

**Nothing unidentified is kept.** An observation made before a person is known belongs to the
interactive flow session that made it and is collected with it, so a failed sign-in and an abandoned
flow leave nothing behind. There is no retention setting for a population that is not stored.

**A place is kept for as long as it goes on being used**, and
`advanced.security-context.known-user-retention` says how long after it stops. The expiry is
measured from the last sighting rather than the first, because deleting the address somebody has
signed in from every week for six months is the opposite of what the record is for.

**A place is only as particular as the address it was read from.** Where nothing was configured, the
address is the socket peer — the proxy's own, identical for every caller — so what a deployment
behind one accumulates is a row per user agent naming its own ingress, with nothing to tell a reader
that apart from a place. Naming the proxy is what makes the record say anything about where a person
is.

**A postal code is read and not kept.** It arrives where an edge publishes one and reaches the
observation, and the record declines it for the reason a coordinate pair is declined: it narrows to
a street group, and nothing here has a use for that.

**An address is personal data, and the deletion ships with the record rather than after it.** The
cutoff is computed when the sweep runs, so lowering the retention takes effect on the next run
instead of on each row's next sighting.

---

← [Design documentation](index.md)

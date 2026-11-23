# Mail

The mails this server sends: the code proving a claim's value, the code proving a password reset,
the reset's notices to an address no account uses or to an account holding no password, and the
notice that a reset changed a password. This document holds what each mail commits with, how long
it stays worth sending, what the mail server's answers mean for it, and how it is drawn. The queue
every mail leaves through, and what it promises any item, is [delivery](delivery.md)'s; the brand a
deployment configures is [branding](branding.md)'s; the metrics a mail is counted under are
[telemetry](telemetry.md)'s.

## A mail commits with what it reports

**A code commits together with the mail carrying it.** A validation code, a reset code and each
code a resend draws are stored in the same commit as their mail, so a person is never sent a code
the server did not store, and a code is never stored with no mail on its way.

**A password reset request commits what it records together with its mail, code or notice.** The
reset's record is what [the resend delay counts from](security.md#a-forgotten-password), whatever
the request sent, so a notice to an address no account uses is held to the same rule as a code: it
goes out only if the record that rate-limits the next request is committed.

**The notice that a password changed is written with the new password**, inside the transaction
completing the reset, so a completion that rolls back tells nobody of it. [The design
FAQ](design-faq.md#when-is-the-mail-saying-a-password-changed-sent) holds the moments that lost.

## A mail is worth sending for as long as what it carries

**A code's mail expires with the code.** Sent after it, it carries a code the server refuses.

**A reset's notice expires when the code it stands in for would have**, so a person whose mail an
outage held up is not told, long after, about a request they made.

**The notice that a password changed never expires**, and is tried until the mail server takes it:
it is the one way a person learns of a reset they did not make.

## What the mail server answers decides whether a mail is tried again

| The mail server | The mail | Its condition |
| --- | --- | --- |
| could not be reached, did not answer or take the mail within its timeouts, refused this server's credentials, or refused its session or sender whatever the code | retried | degraded |
| refused a recipient or the mail permanently under a security or policy status, `5.7.x` — a relay this server may not use | retried | degraded |
| refused a recipient or the mail temporarily, a `4xx`, or greeted this server with one | retried | not degraded |
| refused a recipient permanently, a `5xx`, or reported the mailbox full, `4.2.2` or `5.2.2` | given up | not degraded |

**A refusal of this server's own sending is the operator's to see, not one mail's fault.** It fails
every mail the same way, so it degrades the condition and the mails wait for it to be fixed,
rather than each being given up at DEBUG under a healthy condition. A greylisting reply is often
`4.7.1`, which is why only the permanent policy statuses count as this server's sending. [The design
FAQ](design-faq.md#which-failures-of-a-mail-are-retried) holds the splits that lost.

**A mail that cannot be built, and a failure carrying no reply and no network cause, are this
server's own**, which [the delivery gives up at
once](delivery.md#a-failed-attempt-is-retried-while-its-destination-may-yet-take-the-item); the
WARN names the mail and its kind. A permanent refusal and a full mailbox are written at
DEBUG: anybody can type an address a mail server refuses.

**The `mail` environment ships the mail server's timeouts.** JavaMail waits for ever without them,
and a mail server that stops reading mid-message would hold its attempt, and the mail, for good.

**Where no mail server is configured, no mail is queued**, and the delivery does not poll. Mails
would otherwise pile up for a mail server that does not exist, and a notice with no expiry would go
out, long after the event, the day one is configured.

## Mails are drawn in the brand

**A mail is the flow's auth card page drawn for a mail client**: the brand header, one card of at
most 480px and the footer, on `ground`. `views/mails/kit.ftl` holds the pieces every mail is
assembled from, and a mail template only orders them; its strings are the mail bundle's.

**A mail reads colours as values, not variables.** A mail client applies no stylesheet it fetches
and reads no CSS variable, so `MailBrandResolver` resolves each of the stylesheet's colours to its
value, an alias to the colour of the token it names, and the kit inlines each value. A dark alias
takes the dark colour of that token, or its light one where the dark theme does not carry it, as the
variable does in the stylesheet. The dark roles the kit paints are repainted by a
`prefers-color-scheme` rule where the client honours one, and left out under `dark-mode: "off"`.
Status colours stay SympAuthy's, as on the pages.

**A mail shows a PNG logo, or the name alone.** A mail client draws no SVG. `/branding/logo.png`
and `/branding/logo-dark.png` answer the deployment's logo where it resolved to a PNG, SympAuthy's
PNG where it configured none, and nothing where it configured an SVG or an image not resolved yet,
so that its mails show its name rather than SympAuthy's mark. A mail links them by absolute address
under `urls.root`, versioned by their content so that a client's image proxy does not keep the
previous logo, and links none where no root URL is configured.

**A root URL with a local host links no logo**: a name of a single label or one reserved for a local
network, or a loopback, unspecified, private, shared or link-local address, IPv4 or IPv6. A mail is
read through its provider, whose image proxy fetches the logo from the internet, so a link there
draws a broken image where the name alone would do. The host is judged as it is written and never
resolved, since a deployment's name may resolve to a private address inside its own network and to
a public one outside it.

**A mail names the deployment in its subject, its header and its footer, and in a sentence that
speaks of the deployment itself.** The subject is read in the inbox before the header is, and the
footer says why the reader got the mail, from whom. The header shows the name above everything else,
so the preview text, which the inbox shows beside the subject, does not repeat it, and neither does
a sentence of the card unless the deployment is what it is about: the sign-in page a code is entered
on, or that it never asks for one.

**A mail is drawn at each attempt, not when it is queued.** A queued mail holds what it was sent
for, a code; the brand, the name it is signed under and the minutes a code has left are read when
the delivery attempts it, so a retried code says how many minutes it has left at that attempt.

## What implements it

**`Mail` is the authoritative list of mails, and its KDoc the description of each.** A mail is its
template under `views/mails`, and its strings are keyed by that template in
`mail_messages.properties`.

**`ValidationCodeMailSender` and `PasswordResetMailSender` are where a mail is queued**, behind the
interfaces `business` declares for them, and each sets the expiry of the mails it queues.
`MailQueue` writes the mail into the caller's transaction, as an item of the `Mail` it is and a
`MailPayload` holding what it was sent for.

**`TemplatedMailBuilderFactory` draws a mail at its attempt**, writing what is read then over what
was queued, and `MailBrandResolver` is the brand it draws in.

**`MailFailure` is the authority on how a failed attempt is read off what JavaMail raised**, and its
KDoc on each case.

## What this document does not settle

**A mail in the reader's language.** Every mail is drawn in one locale, whatever the person reading
it chose on the sign-in pages.

**A bounce.** A mail the mail server took and the recipient's server later returned is not seen:
nothing reads what comes back.

**A transport other than SMTP.** A provider's HTTP API is reached through a mail server it runs, or
not at all.

---

← [How the system works](index.md)

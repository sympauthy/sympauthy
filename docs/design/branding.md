# Branding

What a person sees of a deployment while they sign in: the colours, the images and the name its
sign-in pages and admin show. A deployment configures them under `ui`, and the server answers them
under `/branding`, so changing a brand is a change of configuration and a restart rather than a
rebuild of either app. With nothing configured these are SympAuthy's own, and nothing a person sees
changes.

The keys are written up for an operator in [the public
documentation](https://sympauthy.github.io/technical/configuration/). How an outage is reported is
[logging](logging.md#when-the-server-is-not-fully-operational)'s, and the options that lost are in
[the design FAQ](design-faq.md#branding).

## The brand is answered outside the apps

**The brand belongs to the deployment, so it is answered under a prefix of its own.**

| Path | Answers |
| --- | --- |
| `/branding/tokens.css` | the stylesheet |
| `/branding/icon` | the favicon and small mark |
| `/branding/logo` | the logo for light grounds |
| `/branding/logo-dark` | the logo for dark grounds |
| `/branding/brand.json` | the display name and the dark mode |

**Anyone can read them, without authentication.** A deployment that turned the admin off still
answers them, and a flow hosted on another origin through `flows.<id>` links the same stylesheet and
images rather than copying them.

**`/branding` is not an API surface.** It has one audience and no gate, like the apps' own assets,
so [the API standard](../standards/api-standard.md)'s route rules do not apply and it is not in the
OpenAPI document. `brand.json` carries no version, because a prefix on one path of five would split
a set that changes together; its body still follows the standard's JSON rules, and it answers every
origin, since it holds nothing private and an allow-list would add a configuration step for every
hosted flow.

**A reverse proxy has to forward `/branding`** beside `/api`, `/flow` and `/admin`, or the apps lose
their stylesheet once they link it.

## One stylesheet serves both apps

**The stylesheet holds every token of the design system the server ships, with each configured
colour written over its default.** A page linking it needs nothing else, and because it is linked
from the page the brand is in place before any script runs.

**Light roles and the admin's `nav-*` sit on `:root`.** Dark roles apply under
`prefers-color-scheme: dark` and `[data-theme="dark"]`, and a page pinning `data-theme="light"`
stays light, which is how the admin stays light. `ui.theme.dark-mode: off` leaves the dark roles
out, and `brand.json` says so, since an app choosing its logo by the device's preference alone would
put the dark one on a light page.

**Only colours can be changed.** `space-*`, `radius-*` and `size-*` stay at SympAuthy's values, and
so do the status colours, because red means a failure whatever the brand.

**The server serves what is configured, and derives nothing.** A role left out keeps SympAuthy's
value even when `primary` is changed, and no text pair is checked for contrast. The theme builder
writes the full set. The only colour refused is a badly formatted one.

**Which roles exist under `ui.theme.light` and `ui.theme.dark` is `THEME_ROLES` and
`LIGHT_ONLY_THEME_ROLES`.** `accent` is the one whose token has another name: the stylesheet's
`--gold`.

## Images are held by the server

**The browser only ever asks the server, so a third party never learns that a sign-in is starting.**
An image's value is a URL or a file path: one starting with `http://` or `https://` is fetched, and
anything else is read as a file. Either is obtained at startup, held in memory and answered from
`/branding`, so a new image takes a restart. SVG and PNG are accepted, by what the bytes are rather
than by what they are called, up to 1 MiB.

| Image | When not configured |
| --- | --- |
| `icon` | SympAuthy's icon |
| `logo` | SympAuthy's logo |
| `logo-dark` | `logo` if it is configured, otherwise SympAuthy's dark logo, so a page never shows two brands |

**An image cannot run a script on the server's origin.** An `<img>` does not run an SVG's scripts,
but opening `/branding/logo` directly does, on the origin the sign-in pages live on. Every image is
answered with a `Content-Security-Policy` sandboxing it and `X-Content-Type-Options: nosniff`, and
the server does not try to clean the SVG.

### An image host being down does not take sign-ins down

**An image follows the rules of a provider's discovery.** A connection failure, a timeout or a `5xx`
is an outage: the image's condition starts degraded, it is retried every quiet period, and it
recovers once an attempt succeeds. Any other answer that is not an SVG or a PNG within the limit, a
`4xx` included, is one ERROR, and the image stays unresolved until a restart. Until it resolves, an
image answers as if it were not configured.

**A local file that is wrong is a configuration error**, which [takes readiness
down](../standards/config-layer-code-standard.md#configuration-errors-take-readiness-down). Waiting
will not change a local file, and a wrong path is fixed by correcting the deployment.

## A rebrand shows at once after a restart

**Every answer under `/branding` carries an `ETag` computed from its content and `Cache-Control:
no-cache`.** A browser revalidates and gets a `304` when nothing changed, and two instances holding
the same bytes answer the same tag. The paths never change, so a long `max-age` would keep showing
the old logo for days.

## What implements it

**`BrandManager` holds what is answered**: it fetches and retries the images, and falls back to the
marks in `server/src/main/resources/branding/`. A configuration that failed reads as one that
configured nothing, since it already takes readiness down and a sign-in page is better served with
SympAuthy's brand than with none.

**`BrandStylesheetRenderer` writes the stylesheet from the design system's `tokens.json`.** That
file and the marks beside it are copies out of `sympauthy-design`, of `tokens.json` and of its
`sympauthy-favicon`, `sympauthy-icon` and `sympauthy-icon-dark` logos, and a new version of the
design system is taken by replacing them.

**`UIConfigParser` reads a local image, and `UIConfigValidator` refuses a role by the keys a
deployment wrote**, so a colour YAML read as a comment is refused rather than absent. The routes are
`BrandingController`, beside the controllers serving the apps.

## What this document does not settle

**The apps taking the brand.** Linking the stylesheet, showing the images and reading `brand.json`
are each app's to do.

**Deriving roles from `primary`, and refusing weak contrast.** Neither is done here, and either may
follow.

**Custom CSS, and per-app branding.** Nothing here supports them.

**A key set outside the file.** What an environment variable reaches here is what [the `config`
layer standard](../standards/config-layer-code-standard.md#what-this-standard-does-not-cover) leaves
open for every key; `UI_THEME_LIGHT_INK_50` does not reach `ink-50`.

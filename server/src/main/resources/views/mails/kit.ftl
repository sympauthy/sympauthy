<#ftl output_format="HTML" strip_whitespace=true>
<#--
  SympAuthy mail kit.

  The pieces every mail is assembled from: the page (`mail`), the brand header, the card and the blocks
  it stacks, and the footer. A mail is the flow's AuthCard page drawn for a mail client: brand header,
  one card of at most 480px, footer, on `ground`.

  Every color comes from `brand`, which the server resolves from the design system's tokens.json with
  the deployment's `ui.theme` written over it, so a mail follows the operator's colors as the sign-in
  pages do. Nothing here holds a hex value.

  The model a template is rendered with:
    brand.displayName   the name the deployment signs with ("SympAuthy" when it configured none)
    brand.isSympAuthy   true when the deployment configured no name, so the mail is signed as SympAuthy
    brand.logoUrl       optional, absolute URL of a PNG logo (mail clients do not draw SVG)
    brand.logoWidth     width of the logo at 40px tall
    brand.logoDarkUrl   optional, the PNG logo for dark grounds, and brand.logoDarkWidth
    brand.darkMode      "auto" (follow the device where the client lets us) or "off"
    brand.light         every color token by name, light theme
    brand.dark          the tokens the dark theme carries
    msg(key)            the localized message for key, interpolated with the model
    lang                the language tag of the mail

  Usage, in a template:

    <#import "/mails/kit.ftl" as k>
    <@k.mail title=msg("mail.x.title") preview=msg("mail.x.preview_text")>
      <@k.header/>
      <@k.card>
        <@k.title>${msg("mail.x.title")}</@k.title>
        <@k.paragraph>${msg("mail.x.intro")}</@k.paragraph>
        <@k.code value=code/>
      </@k.card>
      <@k.footer>${msg("mail.x.footer_reason")}</@k.footer>
    </@k.mail>

  Inside a card, every block is followed by space-4 (16px), as the auth card stacks its content.
  A color drawn by a block carries a class (`sa-bg-*`, `sa-fg-*`, `sa-bd-*`) naming its token, which
  the dark rules in the head repaint where the mail client honors prefers-color-scheme. A block that
  paints a token lists it in `painted`, under the kind of its class.
-->

<#-- ===== Tokens ======================================================================== -->

<#assign sans = "'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif">
<#assign mono = "'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', 'Courier New', monospace">

<#--
  The tokens the blocks paint, by the class they paint them with. The dark rules repaint these and no
  others, so a mail carries no rule for a token none of its blocks uses.
-->
<#assign painted = {
    "bg": ["ground", "surface", "ink-50", "ink-200", "primary", "primary-subtle", "warning-subtle", "danger-subtle"],
    "fg": ["ink-900", "ink-500", "on-primary", "on-primary-subtle", "primary", "warning", "danger"],
    "bd": ["ink-200", "warning-line", "danger-line"]
}>
<#assign paintedProperties = {"bg": "background-color", "fg": "color", "bd": "border-color"}>

<#-- The light value of a color token. Light is what a client that ignores dark rules shows. -->
<#function c token>
    <#return brand.light[token]>
</#function>

<#-- An inline text style from the type scale, in a color token. -->
<#function text size lineHeight weight=400 color="ink-900" family=sans>
    <#return "margin:0; font-family:${family}; font-size:${size?c}px; line-height:${lineHeight?c}px; font-weight:${weight?c}; color:${c(color)};">
</#function>

<#-- ===== Page ========================================================================== -->

<#--
  The page: head, preview text, the ground, and a 480px column holding header, card and footer.
  title    the <title>, which some clients show in their notifications
  preview  the line a client shows beside the subject; hidden in the mail itself
-->
<#macro mail title="" preview="">
<!DOCTYPE html>
<html lang="${lang!'en'}" dir="ltr" xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="x-apple-disable-message-reformatting">
    <meta name="format-detection" content="telephone=no,address=no,email=no,date=no,url=no">
    <#if brand.darkMode == "auto">
    <meta name="color-scheme" content="light dark">
    <meta name="supported-color-schemes" content="light dark">
    <#else>
    <meta name="color-scheme" content="light only">
    <meta name="supported-color-schemes" content="light only">
    </#if>
    <title>${title}</title>
    <!--[if mso]>
    <noscript><xml><o:OfficeDocumentSettings><o:AllowPNG/><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml></noscript>
    <style>table, td, p, a, h1, span { font-family: Arial, sans-serif !important; }</style>
    <![endif]-->
    <style>
        :root { color-scheme: ${(brand.darkMode == "auto")?then("light dark", "light only")}; }
        html, body { margin: 0 auto !important; padding: 0 !important; height: 100% !important; width: 100% !important; }
        * { -ms-text-size-adjust: 100%; -webkit-text-size-adjust: 100%; }
        table, td { mso-table-lspace: 0pt !important; mso-table-rspace: 0pt !important; }
        table { border-spacing: 0; }
        img { -ms-interpolation-mode: bicubic; border: 0; outline: none; text-decoration: none; }
        #MessageViewBody, #MessageWebViewDiv { width: 100% !important; }
        a[x-apple-data-detectors], u + #body a, #MessageViewBody a {
            color: inherit !important; text-decoration: none !important; font-size: inherit !important;
            font-family: inherit !important; font-weight: inherit !important; line-height: inherit !important;
        }
        .sa-btn:hover { background-color: ${c("primary-hover")} !important; }
        .sa-link:hover { color: ${c("primary-hover")} !important; }

        <#-- Below sm:, the card pads at space-4 as the auth card does. -->
        @media screen and (max-width: 639px) {
            .sa-page { padding: 24px 16px !important; }
            .sa-card-pad { padding: 16px 16px 0 16px !important; }
        }
        <#if brand.darkMode == "auto">

        <#-- Dark: repaint every token a block paints. Apple Mail, iOS Mail, Outlook for Mac and others honor this. -->
        @media (prefers-color-scheme: dark) {
            <#list painted as kind, tokens>
            <#list tokens as token>
            <#if brand.dark[token]??>
            .sa-${kind}-${token} { ${paintedProperties[kind]}: ${brand.dark[token]} !important; }
            </#if>
            </#list>
            </#list>
            .sa-btn:hover { background-color: ${brand.dark["primary-hover"]} !important; }
            .sa-link:hover { color: ${brand.dark["primary-hover"]} !important; }
            .sa-light-only { display: none !important; max-height: 0 !important; overflow: hidden !important; }
            .sa-dark-only { display: block !important; max-height: none !important; overflow: visible !important; }
        }
        </#if>
    </style>
</head>
<body id="body" class="sa-bg-ground" style="margin:0; padding:0; width:100%; background-color:${c('ground')}; mso-line-height-rule:exactly;">
    <#if preview?has_content>
    <div style="display:none; font-size:1px; line-height:1px; max-height:0; max-width:0; opacity:0; overflow:hidden; mso-hide:all;">
        ${preview}<#list 1..60 as _>&#847;&zwnj;&nbsp;</#list>
    </div>
    </#if>
    <table role="presentation" width="100%" border="0" cellpadding="0" cellspacing="0" class="sa-bg-ground" style="width:100%; background-color:${c('ground')};">
        <tr>
            <td align="center" class="sa-page" style="padding:32px 16px 24px 16px;">
                <!--[if mso]><table role="presentation" width="480" align="center" border="0" cellpadding="0" cellspacing="0"><tr><td><![endif]-->
                <div style="max-width:480px; margin:0 auto;">
                    <#nested>
                </div>
                <!--[if mso]></td></tr></table><![endif]-->
            </td>
        </tr>
    </table>
</body>
</html>
</#macro>

<#-- ===== Brand header ================================================================== -->

<#--
  The brand header above the card: the logo at 40px beside the name (`flow-brand`), as the flow's
  BasePage draws it. SympAuthy's own name is its wordmark, "Symp" at 500 and "Authy" at 800, with its
  tagline under it; a deployment's name is written as it is, without a tagline.
  A deployment whose logo is no PNG shows its name alone: mail clients do not draw SVG.
-->
<#macro header>
    <#local isSympAuthy = brand.isSympAuthy>
    <table role="presentation" width="100%" border="0" cellpadding="0" cellspacing="0">
        <tr>
            <td align="center" style="padding:0 0 24px 0;">
                <table role="presentation" align="center" border="0" cellpadding="0" cellspacing="0">
                    <tr>
                        <#if brand.logoUrl??>
                        <td valign="middle" style="padding:0 12px 0 0;">
                            <#if brand.logoDarkUrl?? && brand.darkMode == "auto">
                            <div class="sa-light-only"><@logo_image src=brand.logoUrl width=brand.logoWidth/></div>
                            <!--[if !mso]><!-->
                            <div class="sa-dark-only" style="display:none; max-height:0; overflow:hidden;"><@logo_image src=brand.logoDarkUrl width=brand.logoDarkWidth/></div>
                            <!--<![endif]-->
                            <#else>
                            <@logo_image src=brand.logoUrl width=brand.logoWidth/>
                            </#if>
                        </td>
                        </#if>
                        <td valign="middle" class="sa-fg-ink-900" style="${text(30, 36, 500)} letter-spacing:-0.02em;">
                            <#if isSympAuthy>Symp<span style="font-weight:800;">Authy</span><#else>${brand.displayName}</#if>
                        </td>
                    </tr>
                </table>
                <#if isSympAuthy>
                <p class="sa-fg-ink-500" style="${text(14, 20, 400, 'ink-500')} padding:4px 0 0 0;">${msg("mail.kit.tagline")}</p>
                </#if>
            </td>
        </tr>
    </table>
</#macro>

<#-- The logo at 40px tall. Its width is given, since Outlook draws an image without one at its own size. -->
<#macro logo_image src width>
    <img src="${src}" width="${width?c}" height="40" alt="" style="display:block; width:${width?c}px; height:40px; border:0;">
</#macro>

<#-- ===== Card ========================================================================== -->

<#--
  The one card: `surface`, a 1px `ink-200` border, `radius-lg`, padded at space-6 (space-4 below sm:).
  It sets `flow-body` and stacks the blocks nested in it.
-->
<#macro card>
    <table role="presentation" width="100%" border="0" cellpadding="0" cellspacing="0" class="sa-bg-surface sa-bd-ink-200" style="width:100%; background-color:${c('surface')}; border:1px solid ${c('ink-200')}; border-radius:8px; border-collapse:separate;">
        <tr>
            <td class="sa-card-pad" style="padding:24px 24px 8px 24px;">
                <table role="presentation" width="100%" border="0" cellpadding="0" cellspacing="0" style="width:100%;">
                    <#nested>
                </table>
            </td>
        </tr>
    </table>
</#macro>

<#-- ===== Blocks (inside a card) ======================================================== -->

<#-- The card's title: `flow-title`, centred. One per mail. -->
<#macro title>
    <tr>
        <td align="center" style="padding:0 0 16px 0;">
            <h1 class="sa-fg-ink-900" style="${text(24, 32, 800)} letter-spacing:-0.01em; text-align:center;"><#nested></h1>
        </td>
    </tr>
</#macro>

<#-- A paragraph in `flow-body`. align: left or center. -->
<#macro paragraph align="left">
    <tr>
        <td align="${align}" style="padding:0 0 16px 0;">
            <p class="sa-fg-ink-900" style="${text(16, 24)} text-align:${align};"><#nested></p>
        </td>
    </tr>
</#macro>

<#-- Secondary text in `flow-small` / `ink-500`: helper lines, an expiry. -->
<#macro small align="left">
    <tr>
        <td align="${align}" style="padding:0 0 16px 0;">
            <p class="sa-fg-ink-500" style="${text(14, 20, 400, 'ink-500')} text-align:${align};"><#nested></p>
        </td>
    </tr>
</#macro>

<#--
  A one-time code, in `flow-code` (JetBrains Mono 30px), centred in an `ink-50` box with an `ink-200`
  border and `radius-md`. A code whose length is a multiple of three reads in groups of three, as the
  flow's CodeInput groups it; the groups are spaced by padding, not by a character, so that the code
  copies as one string.
-->
<#macro code value>
    <#local groups = []>
    <#if value?length gt 3 && value?length % 3 == 0>
        <#list 0..<(value?length / 3) as i><#local groups += [value?substring(i * 3, i * 3 + 3)]></#list>
    <#else>
        <#local groups = [value]>
    </#if>
    <tr>
        <td align="center" style="padding:0 0 16px 0;">
            <table role="presentation" align="center" border="0" cellpadding="0" cellspacing="0" class="sa-bg-ink-50 sa-bd-ink-200" style="background-color:${c('ink-50')}; border:1px solid ${c('ink-200')}; border-radius:6px; border-collapse:separate;">
                <tr>
                    <td align="center" class="sa-fg-ink-900" style="${text(30, 36, 500, 'ink-900', mono)} padding:14px 24px; letter-spacing:0.08em; white-space:nowrap;"><#list groups as g><#if g?is_first>${g}<#else><span style="padding-left:16px;">${g}</span></#if></#list></td>
                </tr>
            </table>
        </td>
    </tr>
</#macro>

<#--
  The one primary button of the mail, full width as the auth card's `size="large"` button:
  `primary` fill, `on-primary` label in 16px/600, `control-height` (44px) tall, `radius-md`.
  Outlook for Windows draws it as VML so it keeps its fill and its shape.
-->
<#macro button href label>
    <tr>
        <td align="center" style="padding:0 0 16px 0;">
            <!--[if mso]>
            <v:roundrect xmlns:v="urn:schemas-microsoft-com:vml" xmlns:w="urn:schemas-microsoft-com:office:word" href="${href}" style="height:44px; v-text-anchor:middle; width:430px;" arcsize="14%" stroke="f" fillcolor="${c('primary')}">
                <w:anchorlock/>
                <center style="color:${c('on-primary')}; font-family:Arial, sans-serif; font-size:16px; font-weight:600;">${label}</center>
            </v:roundrect>
            <![endif]-->
            <!--[if !mso]><!-->
            <a href="${href}" target="_blank" class="sa-btn sa-bg-primary sa-fg-on-primary" style="display:block; background-color:${c('primary')}; color:${c('on-primary')}; font-family:${sans}; font-size:16px; line-height:20px; font-weight:600; text-align:center; text-decoration:none; padding:12px 16px; border-radius:6px; mso-hide:all;">${label}</a>
            <!--<![endif]-->
        </td>
    </tr>
</#macro>

<#-- The address a button opens, written out for a client that does not let the button work. -->
<#macro link_fallback href>
    <tr>
        <td style="padding:0 0 16px 0;">
            <p class="sa-fg-ink-500" style="${text(14, 20, 400, 'ink-500')}">${msg("mail.kit.link_fallback")}</p>
            <p style="${text(14, 20, 400, 'primary', mono)} padding:4px 0 0 0; word-break:break-all;"><@link href=href>${href}</@link></p>
        </td>
    </tr>
</#macro>

<#-- A quiet box in `primary-subtle` / `on-primary-subtle`, as the consent summary: what the mail is about. -->
<#macro notice>
    <tr>
        <td style="padding:0 0 16px 0;">
            <table role="presentation" width="100%" border="0" cellpadding="0" cellspacing="0" class="sa-bg-primary-subtle" style="width:100%; background-color:${c('primary-subtle')}; border-radius:6px; border-collapse:separate;">
                <tr>
                    <td class="sa-fg-on-primary-subtle" style="${text(14, 20, 400, 'on-primary-subtle')} padding:12px;"><#nested></td>
                </tr>
            </table>
        </td>
    </tr>
</#macro>

<#--
  An alert, as the design system's Alert: `warning` for what must be read first, `danger` for a failure.
  Status colors keep their meaning whatever the brand: a deployment cannot color them.
-->
<#macro alert variant="warning">
    <tr>
        <td style="padding:0 0 16px 0;">
            <table role="presentation" width="100%" border="0" cellpadding="0" cellspacing="0" class="sa-bg-${variant}-subtle sa-bd-${variant}-line" style="width:100%; background-color:${c(variant + '-subtle')}; border:1px solid ${c(variant + '-line')}; border-radius:6px; border-collapse:separate;">
                <tr>
                    <td class="sa-fg-${variant}" style="${text(14, 20, 400, variant)} padding:12px;"><#nested></td>
                </tr>
            </table>
        </td>
    </tr>
</#macro>

<#-- A rule between two parts of the card, in `ink-200`. -->
<#macro divider>
    <tr>
        <td style="padding:0 0 16px 0;">
            <div class="sa-bg-ink-200" style="height:1px; line-height:1px; font-size:1px; background-color:${c('ink-200')};">&nbsp;</div>
        </td>
    </tr>
</#macro>

<#-- ===== Inline ======================================================================== -->

<#-- A link inside a sentence: `primary`, underlined. -->
<#macro link href>
    <a href="${href}" target="_blank" class="sa-link sa-fg-primary" style="color:${c('primary')}; text-decoration:underline;"><#nested></a><#t>
</#macro>

<#-- Words that stand out in a sentence. -->
<#macro strong><strong style="font-weight:600;"><#nested></strong></#macro><#t>

<#-- ===== Footer ======================================================================== -->

<#--
  The footer under the card, in `flow-small` / `ink-500`, centred: each line nested in it (why the
  reader got the mail, which names who sent it), then SympAuthy's credit, as the flow's footer has it.
-->
<#macro footer>
    <table role="presentation" width="100%" border="0" cellpadding="0" cellspacing="0">
        <tr>
            <td align="center" style="padding:24px 0 0 0;">
                <#local lines><#nested></#local>
                <#if lines?markup_string?trim?has_content>
                <p class="sa-fg-ink-500" style="${text(14, 20, 400, 'ink-500')} text-align:center; padding:0 0 8px 0;">${lines}</p>
                </#if>
                <p class="sa-fg-ink-500" style="${text(14, 20, 400, 'ink-500')} text-align:center;"><@powered_by/></p>
            </td>
        </tr>
    </table>
</#macro>

<#--
  "Powered by SympAuthy.", with SympAuthy linked to its site. The sentence is the bundle's, so a
  language puts the name where it goes; the name itself is never translated.
-->
<#macro powered_by>
    <#local parts = msg("mail.kit.powered_by")?split("SympAuthy")>
    <#if parts?size == 2>${parts[0]}<@link href="https://sympauthy.github.io/">SympAuthy</@link>${parts[1]}<#else>${msg("mail.kit.powered_by")}</#if><#t>
</#macro>

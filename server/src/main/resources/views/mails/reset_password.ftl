<#ftl output_format="HTML">
<#--
  The code, or the link, letting an end user choose a new password.
  Model: displayName, code and/or resetUrl, expiryMinutes (optional), and what the kit reads (see kit.ftl).
  With a code the end user types it in the flow; with resetUrl the mail carries the button instead.
-->
<#import "/mails/kit.ftl" as k>
<#assign by = resetUrl???then("link", "code")>
<@k.mail title=msg("mail.reset_password.title") preview=msg("mail.reset_password.preview_text")>
    <@k.header/>
    <@k.card>
        <@k.title>${msg("mail.reset_password.title")}</@k.title>
        <#if resetUrl??>
        <@k.paragraph>${msg("mail.reset_password.intro_link")}</@k.paragraph>
        <@k.button href=resetUrl label=msg("mail.reset_password.button")/>
        <#else>
        <@k.paragraph>${msg("mail.reset_password.intro_code")}</@k.paragraph>
        <@k.code value=code/>
        </#if>
        <#if expiryMinutes??>
        <@k.small align="center">${msg("mail.reset_password.expiry_" + by)}</@k.small>
        </#if>
        <@k.alert variant="warning">${msg("mail.reset_password.never_share_" + by)}</@k.alert>
        <@k.small>${msg("mail.reset_password.not_requested")}</@k.small>
        <#if resetUrl??>
        <@k.link_fallback href=resetUrl/>
        </#if>
    </@k.card>
    <@k.footer>${msg("mail.reset_password.reason")}</@k.footer>
</@k.mail>

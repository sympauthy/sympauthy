<#ftl output_format="HTML">
<#--
  The code proving a password reset, which the end user types in the flow.
  Model: code, displayName, expiryMinutes (optional), and what the kit reads (see kit.ftl).
-->
<#import "/mails/kit.ftl" as k>
<@k.mail title=msg("mail.reset_password.title") preview=msg("mail.reset_password.preview_text")>
    <@k.header/>
    <@k.card>
        <@k.title>${msg("mail.reset_password.title")}</@k.title>
        <@k.paragraph>${msg("mail.reset_password.intro")}</@k.paragraph>
        <@k.code value=code/>
        <#if expiryMinutes??>
        <@k.small align="center">${msg("mail.reset_password.expiry")}</@k.small>
        </#if>
        <@k.alert variant="warning">${msg("mail.reset_password.never_share")}</@k.alert>
        <@k.small>${msg("mail.reset_password.not_requested")}</@k.small>
    </@k.card>
    <@k.footer>${msg("mail.reset_password.reason")}</@k.footer>
</@k.mail>

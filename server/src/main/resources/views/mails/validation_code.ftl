<#ftl output_format="HTML">
<#--
  The code verifying an email address the end user entered in the flow.
  Model: code, displayName, expiryMinutes (optional), and what the kit reads (see kit.ftl).
-->
<#import "/mails/kit.ftl" as k>
<@k.mail title=msg("mail.validation_code.title") preview=msg("mail.validation_code.preview_text")>
    <@k.header/>
    <@k.card>
        <@k.title>${msg("mail.validation_code.title")}</@k.title>
        <@k.paragraph>${msg("mail.validation_code.intro")}</@k.paragraph>
        <@k.code value=code/>
        <#if expiryMinutes??>
        <@k.small align="center">${msg("mail.validation_code.expiry")}</@k.small>
        </#if>
        <@k.divider/>
        <@k.small>${msg("mail.validation_code.not_requested")}</@k.small>
    </@k.card>
    <@k.footer>${msg("mail.validation_code.reason")}</@k.footer>
</@k.mail>

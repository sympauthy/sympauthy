<#ftl output_format="HTML">
<#--
  The notice to an account holding no password that a reset was asked for, naming the providers it signs in with.
  Model: displayName, providers (the names of the providers, joined; empty where none is linked), and what the kit
  reads (see kit.ftl).
-->
<#import "/mails/kit.ftl" as k>
<#assign by = providers?has_content?then("providers", "none")>
<@k.mail title=msg("mail.reset_password_no_password.title") preview=msg("mail.reset_password_no_password.preview_text_" + by)>
    <@k.header/>
    <@k.card>
        <@k.title>${msg("mail.reset_password_no_password.title")}</@k.title>
        <@k.paragraph>${msg("mail.reset_password_no_password.intro_" + by)}</@k.paragraph>
        <#if providers?has_content>
        <@k.notice>${msg("mail.reset_password_no_password.sign_in")}</@k.notice>
        </#if>
        <@k.small>${msg("mail.reset_password_no_password.not_requested")}</@k.small>
    </@k.card>
    <@k.footer>${msg("mail.reset_password_no_password.reason")}</@k.footer>
</@k.mail>

<#ftl output_format="HTML">
<#--
  The notice that a password reset changed the password of the account, and what to do first if it was not the end
  user.
  Model: displayName, changedAt (when it changed, already written out), and what the kit reads (see kit.ftl).
-->
<#import "/mails/kit.ftl" as k>
<@k.mail title=msg("mail.password_changed.title") preview=msg("mail.password_changed.preview_text")>
    <@k.header/>
    <@k.card>
        <@k.title>${msg("mail.password_changed.title")}</@k.title>
        <@k.paragraph>${msg("mail.password_changed.intro")}</@k.paragraph>
        <@k.alert variant="warning">${msg("mail.password_changed.not_you")}</@k.alert>
        <@k.small>${msg("mail.password_changed.you")}</@k.small>
    </@k.card>
    <@k.footer>${msg("mail.password_changed.reason")}</@k.footer>
</@k.mail>

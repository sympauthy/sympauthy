<#ftl output_format="HTML">
<#--
  The notice to an email address a password reset was asked for, that no account uses it and no code was sent.
  Model: displayName, and what the kit reads (see kit.ftl).
-->
<#import "/mails/kit.ftl" as k>
<@k.mail title=msg("mail.reset_password_no_account.title") preview=msg("mail.reset_password_no_account.preview_text")>
    <@k.header/>
    <@k.card>
        <@k.title>${msg("mail.reset_password_no_account.title")}</@k.title>
        <@k.paragraph>${msg("mail.reset_password_no_account.intro")}</@k.paragraph>
        <@k.notice>${msg("mail.reset_password_no_account.other_address")}</@k.notice>
        <@k.small>${msg("mail.reset_password_no_account.not_requested")}</@k.small>
    </@k.card>
    <@k.footer>${msg("mail.reset_password_no_account.reason")}</@k.footer>
</@k.mail>

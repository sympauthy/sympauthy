package com.sympauthy.mail.model

import com.sympauthy.mail.model.MailFailure.MailServerUnwell
import com.sympauthy.mail.model.MailFailure.PermanentRefusal
import com.sympauthy.mail.model.MailFailure.TemporaryRefusal
import com.sympauthy.mail.model.MailFailure.Unclassified
import io.micronaut.email.EmailException
import jakarta.mail.AuthenticationFailedException
import jakarta.mail.MessagingException
import jakarta.mail.SendFailedException
import jakarta.mail.internet.InternetAddress
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException
import org.eclipse.angus.mail.smtp.SMTPSendFailedException
import org.eclipse.angus.mail.smtp.SMTPSenderFailedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.SocketTimeoutException

/**
 * What a failed send is taken to say, from the exceptions JavaMail raises for each answer a mail server gives.
 */
class MailFailureTest {

    private val address = InternetAddress("person@example.com")

    @Test
    fun `of - Takes a mail server that cannot be reached as unwell`() {
        assertEquals(
            MailServerUnwell("connection"),
            of(MessagingException("Couldn't connect to host", ConnectException("Connection refused")))
        )
        assertEquals(
            MailServerUnwell("timeout"),
            of(MessagingException("Exception reading response", SocketTimeoutException("Read timed out")))
        )
    }

    @Test
    fun `of - Takes a refusal of this server's credentials as unwell`() {
        assertEquals(MailServerUnwell("authentication"), of(AuthenticationFailedException("535 5.7.8 Bad credentials")))
    }

    @Test
    fun `of - Takes a refusal of this server's sender as unwell, whatever its code`() {
        val response = "550 5.1.0 Sender rejected"
        val command = "MAIL FROM:<sympauthy@example.org>"
        val refusal = SMTPSendFailedException(command, 550, response, null, null, null, null)
            .apply { setNextException(SMTPSenderFailedException(address, "MAIL FROM", 550, response)) }

        assertEquals(MailServerUnwell("550"), of(refusal))
    }

    @Test
    fun `of - Takes a recipient refused for good under a policy status as a permanent refusal`() {
        assertEquals(PermanentRefusal(554), of(recipientRefused(554, "554 5.7.1 Relay access denied")))
        assertEquals(PermanentRefusal(550), of(recipientRefused(550, "550 5.7.26 Unauthenticated email rejected")))
    }

    @Test
    fun `of - Takes a mail server that closed the connection before it answered as unwell, at every stage`() {
        val greeting = MessagingException(
            "Got bad greeting from SMTP host: mail.example.org, port: 25, response: [EOF]"
        )
        assertEquals(MailServerUnwell("connection_closed"), of(greeting))
        assertEquals(MailServerUnwell("connection_closed"), of(recipientRefused(-1, "[EOF]")))
        assertEquals(
            MailServerUnwell("connection_closed"),
            of(SMTPSendFailedException(".", -1, "[EOF]", null, null, null, null))
        )
    }

    @Test
    fun `of - Takes a temporary refusal of a recipient as one, greylisting included`() {
        assertEquals(TemporaryRefusal(451), of(recipientRefused(451, "451 4.7.1 Greylisted, try again later")))
        assertEquals(TemporaryRefusal(450), of(recipientRefused(450, "450 Mailbox busy")))
    }

    @Test
    fun `of - Takes a recipient refused for good as a permanent refusal`() {
        assertEquals(PermanentRefusal(550), of(recipientRefused(550, "550 5.1.1 User unknown")))
    }

    @Test
    fun `of - Takes a full mailbox as a permanent refusal, whatever its reply code`() {
        assertEquals(PermanentRefusal(452), of(recipientRefused(452, "452 4.2.2 Mailbox full")))
        assertEquals(PermanentRefusal(552), of(recipientRefused(552, "552 5.2.2 Mailbox full")))
    }

    @Test
    fun `of - Takes a temporary refusal of the mail as one`() {
        val refusal = SMTPSendFailedException(".", 451, "451 4.3.0 Try again later", null, null, null, null)

        assertEquals(TemporaryRefusal(451), of(refusal))
    }

    @Test
    fun `of - Takes a greeting asking to be tried later as a temporary refusal`() {
        val refusal = MessagingException(
            "Got bad greeting from SMTP host: mail.example.org, port: 25, response: 421 4.3.2 Service busy"
        )

        assertEquals(TemporaryRefusal(421), of(refusal))
    }

    @Test
    fun `of - Takes a refusal of the session as unwell`() {
        assertEquals(MailServerUnwell("530"), of(MessagingException("530 5.7.0 Must issue a STARTTLS command first")))
    }

    @Test
    fun `of - Takes a template that cannot be read, and a failure carrying nothing, as unclassified`() {
        assertEquals(Unclassified, of(FileNotFoundException("validation_code.ftlh")))
        assertEquals(Unclassified, of(IllegalStateException("broken")))
    }

    private fun of(failure: Throwable) = MailFailure.of(EmailException(failure))

    private fun recipientRefused(code: Int, response: String) = SendFailedException(
        "Invalid Addresses",
        SMTPAddressFailedException(address, "RCPT TO:<person@example.com>", code, response)
    )
}

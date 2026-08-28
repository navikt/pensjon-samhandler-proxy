package no.nav.pensjon_samhandler_proxy

import jakarta.xml.soap.MessageFactory
import jakarta.xml.soap.SOAPElement
import jakarta.xml.soap.SOAPMessage
import jakarta.xml.ws.handler.MessageContext
import jakarta.xml.ws.handler.soap.SOAPMessageContext
import org.junit.jupiter.api.Test
import javax.xml.namespace.QName
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsernameTokenHandlerTest {

    @Test
    fun `handleMessage legger til wsse UsernameToken header med riktig brukernavn og passord`() {
        val message: SOAPMessage = MessageFactory.newInstance().createMessage()
        val context = FakeSoapMessageContext(message)

        val handler = UsernameTokenHandler("testbruker", "testpassord")

        val resultat = handler.handleMessage(context)

        assertTrue(resultat)

        val header = message.soapPart.envelope.header
        val security = header.getChildElements(QName(SECURITY_URL, "Security"))
            .asSequence().filterIsInstance<SOAPElement>().first()
        val usernameToken = security.getChildElements(QName(SECURITY_URL, "UsernameToken"))
            .asSequence().filterIsInstance<SOAPElement>().first()

        val username = usernameToken.getChildElements(QName(SECURITY_URL, "Username"))
            .asSequence().filterIsInstance<SOAPElement>().first().value
        val password = usernameToken.getChildElements(QName(SECURITY_URL, "Password"))
            .asSequence().filterIsInstance<SOAPElement>().first().value

        assertEquals("testbruker", username)
        assertEquals("testpassord", password)
    }

    @Test
    fun `getHeaders annonserer wsse Security som håndtert header`() {
        val handler = UsernameTokenHandler("u", "p")

        assertEquals(setOf(QName(SECURITY_URL, "Security")), handler.headers)
    }

    @Test
    fun `handleFault feiler ikke og returnerer true`() {
        val handler = UsernameTokenHandler("u", "p")

        assertTrue(handler.handleFault(null))
    }

    companion object {
        private const val SECURITY_URL =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
    }

    private class FakeSoapMessageContext(private var message: SOAPMessage) :
        HashMap<String, Any>(), SOAPMessageContext {
        override fun getMessage(): SOAPMessage = message
        override fun setMessage(message: SOAPMessage) {
            this.message = message
        }

        override fun getHeaders(
            qname: QName?,
            context: jakarta.xml.bind.JAXBContext?,
            allRoles: Boolean
        ): Array<Any> = emptyArray()

        override fun getRoles(): Set<String> = emptySet()

        override fun setScope(name: String?, scope: MessageContext.Scope?) {}
        override fun getScope(name: String?): MessageContext.Scope = MessageContext.Scope.HANDLER
    }
}


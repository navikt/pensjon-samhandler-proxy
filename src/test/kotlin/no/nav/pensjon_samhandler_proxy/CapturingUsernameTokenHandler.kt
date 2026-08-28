package no.nav.pensjon_samhandler_proxy

import jakarta.xml.soap.SOAPElement
import jakarta.xml.ws.handler.MessageContext
import jakarta.xml.ws.handler.soap.SOAPHandler
import jakarta.xml.ws.handler.soap.SOAPMessageContext
import java.util.Collections.unmodifiableSet
import javax.xml.namespace.QName

class CapturingUsernameTokenHandler : SOAPHandler<SOAPMessageContext> {
    var sistMottattBrukernavn: String? = null
    var sistMottattPassord: String? = null

    fun reset() {
        sistMottattBrukernavn = null
        sistMottattPassord = null
    }

    override fun getHeaders(): Set<QName> = unmodifiableSet(
        setOf(QName(SECURITY_URL, "Security"))
    )

    override fun handleMessage(context: SOAPMessageContext): Boolean {
        val outbound = context[MessageContext.MESSAGE_OUTBOUND_PROPERTY] as Boolean
        if (!outbound) {
            val header = context.message.soapPart.envelope.header
            val security = header?.getChildElements(QName(SECURITY_URL, "Security"))
                ?.asSequence()
                ?.filterIsInstance<SOAPElement>()
                ?.firstOrNull()

            val usernameToken = security
                ?.getChildElements(QName(SECURITY_URL, "UsernameToken"))
                ?.asSequence()
                ?.filterIsInstance<SOAPElement>()
                ?.firstOrNull()

            sistMottattBrukernavn = usernameToken
                ?.getChildElements(QName(SECURITY_URL, "Username"))
                ?.asSequence()
                ?.filterIsInstance<SOAPElement>()
                ?.firstOrNull()
                ?.value

            sistMottattPassord = usernameToken
                ?.getChildElements(QName(SECURITY_URL, "Password"))
                ?.asSequence()
                ?.filterIsInstance<SOAPElement>()
                ?.firstOrNull()
                ?.value
        }
        return true
    }

    override fun handleFault(context: SOAPMessageContext?): Boolean = true

    override fun close(context: MessageContext) {}

    companion object {
        private const val SECURITY_URL =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
    }
}

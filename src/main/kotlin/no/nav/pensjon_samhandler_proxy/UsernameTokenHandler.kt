package no.nav.pensjon_samhandler_proxy

import jakarta.xml.soap.SOAPElement
import jakarta.xml.soap.SOAPFactory
import jakarta.xml.ws.handler.MessageContext
import jakarta.xml.ws.handler.soap.SOAPHandler
import jakarta.xml.ws.handler.soap.SOAPMessageContext
import java.util.Collections.unmodifiableSet
import javax.xml.namespace.QName

class UsernameTokenHandler(private val username: String, private val password: String) :
    SOAPHandler<SOAPMessageContext> {
    override fun getHeaders(): Set<QName> {
        return unmodifiableSet(
            setOf(
                QName(
                    "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd",
                    "Security"
                )
            )
        )
    }

    override fun handleMessage(context: SOAPMessageContext): Boolean {
        return context.message.soapPart.envelope.apply {
            if (header == null) {
                addHeader()
            }
            header.addChildElement(createUsernameToken())
        } != null
    }

    private fun createUsernameToken(): SOAPElement? =
        SOAPFactory.newInstance()
            .createElement(SOAPFactory.newInstance().createName("Security", WSSE, SECURITY_URL)).apply {
                addNamespaceDeclaration("soapenc", "http://schemas.xmlsoap.org/soap/encoding/")
                addNamespaceDeclaration("xsd", "http://www.w3.org/2001/XMLSchema")
                addNamespaceDeclaration("xsi", "http://www.w3.org/2001/XMLSchema-instance")

                addAttribute(
                    SOAPFactory.newInstance()
                        .createName("mustUnderstand", "soapenv", "http://schemas.xmlsoap.org/soap/envelope/"), "1"
                )

                addChildElement(
                    SOAPFactory.newInstance()
                        .createElement(SOAPFactory.newInstance().createName("UsernameToken", WSSE, SECURITY_URL))
                        .apply {
                            addChildElement(
                                SOAPFactory.newInstance()
                                    .createElement(SOAPFactory.newInstance().createName("Username", WSSE, SECURITY_URL))
                                    .apply { addTextNode(username) })

                            addChildElement(
                                SOAPFactory.newInstance()
                                    .createElement(SOAPFactory.newInstance().createName("Password", WSSE, SECURITY_URL))
                                    .apply {
                                        addAttribute(
                                            SOAPFactory.newInstance().createName("Type"),
                                            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText"
                                        )
                                        addTextNode(password)
                                    }
                            )
                        }
                )
            }

    override fun handleFault(context: SOAPMessageContext?): Boolean = true


    override fun close(context: MessageContext) {}

    companion object {
        private const val SECURITY_URL =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
        private const val WSSE = "wsse"
    }
}
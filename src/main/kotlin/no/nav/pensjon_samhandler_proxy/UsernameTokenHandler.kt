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

    private fun createUsernameToken(): SOAPElement? {
        val factory = SOAPFactory.newInstance()

        return factory
            .createElement(factory.createName("Security", WSSE, SECURITY_URL)).apply {
                addNamespaceDeclaration("soapenc", "http://schemas.xmlsoap.org/soap/encoding/")
                addNamespaceDeclaration("xsd", "http://www.w3.org/2001/XMLSchema")
                addNamespaceDeclaration("xsi", "http://www.w3.org/2001/XMLSchema-instance")

                addAttribute(
                    factory
                        .createName("mustUnderstand", "soapenv", "http://schemas.xmlsoap.org/soap/envelope/"), "1"
                )

                addChildElement(
                    factory
                        .createElement(factory.createName("UsernameToken", WSSE, SECURITY_URL))
                        .apply {
                            addChildElement(
                                factory
                                    .createElement(factory.createName("Username", WSSE, SECURITY_URL))
                                    .apply { addTextNode(username) })

                            addChildElement(
                                factory
                                    .createElement(factory.createName("Password", WSSE, SECURITY_URL))
                                    .apply {
                                        addAttribute(
                                            factory.createName("Type"),
                                            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText"
                                        )
                                        addTextNode(password)
                                    }
                            )
                        }
                )
            }
    }

    override fun handleFault(context: SOAPMessageContext?): Boolean = true


    override fun close(context: MessageContext) {}

    companion object {
        private const val SECURITY_URL =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
        private const val WSSE = "wsse"
    }
}
package no.nav.pensjon_samhandler_proxy

import no.nav.virksomhet.tjenester.samhandler.v2.binding.Samhandler
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import javax.xml.namespace.QName

@SpringBootApplication
class PensjonSamhandlerProxyApplication {
    @Bean("samhandlerPort")
    fun samhandlerPort(
        @Value("\${tss.samhandlerv2.endpoint.url}") endpoint: String,
        @Value("\${tss.samhandlerv2.serviceuser.username}") username: String,
        @Value("\${tss.samhandlerv2.serviceuser.password}") password: String,
    ): Samhandler =
        JaxWsProxyFactoryBean().also { factory ->
            factory.address = endpoint
            factory.serviceClass = Samhandler::class.java
            factory.serviceName =
                QName(
                    "http://nav.no/virksomhet/tjenester/samhandler/v2/Binding/",
                    "Samhandler"
                )
            factory.handlers = listOf(
                UsernameTokenHandler(username, password),
            )
        }.create() as Samhandler
}


fun main(args: Array<String>) {
    runApplication<PensjonSamhandlerProxyApplication>(*args)
}
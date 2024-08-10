package no.nav.pensjon_samhandler_proxy

import no.nav.security.mock.oauth2.MockOAuth2Server
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.support.GenericApplicationContext
import java.util.function.Supplier

class MockOAuth2ServerInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val server = registerMockOAuth2Server(applicationContext as GenericApplicationContext)
        val baseUrl = server.baseUrl().toString().removeSuffix("/")

        TestPropertyValues
            .of(
                mapOf(
                    "AZURE_OPENID_CONFIG_ISSUER" to "$baseUrl/issuer1",
                    "AZURE_APP_CLIENT_ID" to "acceptedAudience"
                )
            )
            .applyTo(applicationContext)
    }

    private fun registerMockOAuth2Server(applicationContext: GenericApplicationContext) =
        MockOAuth2Server().also {
            it.start()
            applicationContext.registerBean(MockOAuth2Server::class.java, Supplier { it })
        }
}

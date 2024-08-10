package no.nav.pensjon_samhandler_proxy

import no.nav.security.mock.oauth2.MockOAuth2Server
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait.forLogMessage
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Duration.ofMinutes

@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    classes = [PensjonSamhandlerProxyApplication::class],
)
@ContextConfiguration(
    initializers = [
        MockOAuth2ServerInitializer::class,
    ]
)
@Testcontainers
@Disabled("Avklar om vi har lisens til å kjøre MQ dev container på GitHub, hvis ikke så kan dette kun kjøres på utviklermaskin")
class PensjonSamhandlerProxyApplicationTest @Autowired constructor(
    val samhandlerViaKoe: SamhandlerViaKoe,
    val mockOAuth2Server: MockOAuth2Server,
    val webClient: WebTestClient,
) {
    @Test
    fun kallTilTssFeilerMedManglendeSvar() {
        assertThrows<IkkeSvarFraTssException> { samhandlerViaKoe.hentSamhandlerXml("123", false) }
    }

    @Test
    fun `kall uten bearer token gir 401`() {
        webClient.get()
            .uri("/api/samhandler/hentSamhandlerNavn/{tssId}", mapOf("tssId" to "123"))
            .exchange()
            .expectStatus()
            .isUnauthorized()
    }

    @Test
    fun `kall med feil audience i token gir 401`() {
        val token = mockOAuth2Server.issueToken("issuer1", "foo")
        webClient.mutate().responseTimeout(Duration.ofSeconds(30)).build()
            .get()
            .uri("/api/samhandler/hentSamhandlerNavn/{tssId}", mapOf("tssId" to "123"))
            .headers {
                it.setBearerAuth(
                    token.serialize()
                )
            }
            .exchange()
            .expectStatus()
            .isUnauthorized()
    }

    @Test
    fun `kall med gyldig token gir ikke 401`() {
        val token = mockOAuth2Server.issueToken("issuer1", "foo", audience = "acceptedAudience")
        webClient.mutate().responseTimeout(Duration.ofSeconds(30)).build()
            .get()
            .uri("/api/samhandler/hentSamhandlerNavn/{tssId}", mapOf("tssId" to "123"))
            .headers {
                it.setBearerAuth(
                    token.serialize()
                )
            }
            .exchange()
            .expectStatus()
            .is5xxServerError()
    }

    companion object {
        @Container
        @JvmStatic
        val mq = GenericContainer(DockerImageName.parse("icr.io/ibm-messaging/mq:9.4.0.0-r3"))
            .withEnv("LICENSE", "accept")
            .withEnv("MQ_DEV", "true")
            .withEnv("MQ_APP_PASSWORD", "passw0rd")
            .withEnv("MQ_QMGR_NAME", "QM1")
            .withEnv("MQ_ENABLE_EMBEDDED_WEB_SERVER", "false")
            .withExposedPorts(1414)
            .waitingFor(
                forLogMessage(".*Started queue manager.*", 1).withStartupTimeout(ofMinutes(1))
            )
            .withReuse(true)!!


        @DynamicPropertySource
        @JvmStatic
        fun mqProperties(registry: DynamicPropertyRegistry) {
            registry.add("mqGateway01.hostname") { mq.host }
            registry.add("mqGateway01.queueManager") { "QM1" }
            registry.add("mqGateway01.channel") { "DEV.APP.SVRCONN" }
            registry.add("mqGateway01.port") { mq.getMappedPort(1414) }
            registry.add("mqGateway01.temporaryModel") { "DEV.APP.MODEL.QUEUE" }
            registry.add("samhandler.xml.queueName") { "DEV.QUEUE.1" }
            registry.add("SRVPENMQ_USERNAME") { "app" }
            registry.add("SRVPENMQ_PASSWORD") { "passw0rd" }
        }
    }

}

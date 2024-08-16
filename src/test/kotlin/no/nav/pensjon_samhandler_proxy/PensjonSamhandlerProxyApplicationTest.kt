package no.nav.pensjon_samhandler_proxy

import no.nav.security.mock.oauth2.MockOAuth2Server
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.jms.core.JmsTemplate
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
import kotlin.concurrent.thread

@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    classes = [PensjonSamhandlerProxyApplication::class],
    properties = [
        "management.health.livenessstate.enabled=true",
        "management.health.readinessstate.enabled=true",
        "management.endpoint.health.probes.enabled=true",
    ]
)
@ContextConfiguration(
    initializers = [
        MockOAuth2ServerInitializer::class,
    ]
)
@Testcontainers
@Disabled("Avklar om vi har lisens til å kjøre MQ dev container på GitHub, hvis ikke så kan dette kun kjøres på utviklermaskin")
class PensjonSamhandlerProxyApplicationTest @Autowired constructor(
    val samhandlerService: SamhandlerService,
    val mockOAuth2Server: MockOAuth2Server,
    val webClient: WebTestClient,
    val jmsTemplate: JmsTemplate,
) {
    @Test
    fun kallTilTssFeilerMedManglendeSvar() {
        assertThrows<IkkeSvarFraTssException> { samhandlerService.hentSamhandlerEnkel("123") }
    }

    @Test
    fun `actuator health prober kan kalles uten token`() {
        webClient.get()
            .uri("/actuator/health/liveness")
            .exchange()
            .expectStatus()
            .is2xxSuccessful()

        webClient.get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
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
    fun `kall med gyldig token gir ikke 200`() {
        val listnerThread = lagListener()

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
            .is2xxSuccessful()

        listnerThread.interrupt()
    }

    private fun lagListener() =
        thread {
            val message = jmsTemplate.receive("DEV.QUEUE.1")!!

            jmsTemplate.send(message.jmsReplyTo) {
                it.createTextMessage(
                    """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <tssSamhandlerData xmlns="http://www.rtv.no/NamespaceTSS">
                            <tssInputData>
                                <tssServiceRutine>
                                    <samhandlerIDataB980>
                                        <idOffTSS>80000748058</idOffTSS>
                                        <hentNavn>J</hentNavn>
                                        <brukerID>PP01</brukerID>
                                    </samhandlerIDataB980>
                                </tssServiceRutine>
                            </tssInputData>
                            <tssOutputData>
                                <svarStatus>
                                    <alvorligGrad>00</alvorligGrad>
                                    <kodeMelding></kodeMelding>
                                    <beskrMelding></beskrMelding>
                                </svarStatus>
                                <samhandlerODataB980>
                                    <antIdenter>1</antIdenter>
                                    <ident>
                                        <idOff>3943</idOff>
                                        <kodeIdentType>TPNR</kodeIdentType>
                                        <kodeSamhType>TEPE</kodeSamhType>
                                        <navnSamh>SKAGERAK ENERGI PENSJONSKASSE</navnSamh>
                                        <avdelingsNr>01</avdelingsNr>
                                        <offNrAvd></offNrAvd>
                                        <avdelingsNavn>Skagerak Energi pensjonskasse</avdelingsNavn>
                                        <idOffTSS>80000748058</idOffTSS>
                                    </ident>
                                </samhandlerODataB980>
                            </tssOutputData>
                        </tssSamhandlerData>
                    """.trimIndent()
                )
            }
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
            registry.add("ibm.mq.channel") { "DEV.APP.SVRCONN" }
            registry.add("ibm.mq.connName") { "${mq.host}(${mq.getMappedPort(1414)})" }
            registry.add("ibm.mq.queueManager") { "QM1" }
            registry.add("ibm.mq.tempModel") { "DEV.APP.MODEL.QUEUE" }

            registry.add("ibm.mq.user") { "app" }
            registry.add("ibm.mq.password") { "passw0rd" }

            registry.add("samhandler.xml.queueName") { "DEV.QUEUE.1" }
        }
    }
}

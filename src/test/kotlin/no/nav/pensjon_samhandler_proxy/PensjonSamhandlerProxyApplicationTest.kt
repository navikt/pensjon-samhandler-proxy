package no.nav.pensjon_samhandler_proxy

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.virksomhet.part.samhandler.v2.Adresse
import no.nav.virksomhet.part.samhandler.v2.Land
import no.nav.virksomhet.tjenester.samhandler.meldinger.v2.HentSamhandlerPrioritertAdresseResponse
import no.nav.virksomhet.tjenester.samhandler.v2.binding.Samhandler
import org.apache.cxf.jaxws.JaxWsServerFactoryBean
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.MediaType
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
import java.net.ServerSocket
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
        "tss.samhandlerv2.serviceuser.username=testbruker",
        "tss.samhandlerv2.serviceuser.password=testpassord",
    ]
)
@ContextConfiguration(
    initializers = [
        MockOAuth2ServerInitializer::class,
    ]
)
@AutoConfigureWebTestClient
@Testcontainers
class PensjonSamhandlerProxyApplicationTest @Autowired constructor(
    val samhandlerService: SamhandlerService,
    val mockOAuth2Server: MockOAuth2Server,
    val webClient: WebTestClient,
    val jmsTemplate: JmsTemplate,
) {
    @BeforeEach
    fun setUpFakeSamhandlerV2Service() {
        fakeSamhandlerV2Service.reset()
        capturingUsernameTokenHandler.reset()
    }

    @Test
    fun `kall på hentSamhandlerPostadresse med gyldig token gir 200 og sender riktig wsse UsernameToken`() {
        fakeSamhandlerV2Service.nestePrioritertAdresseSvar = HentSamhandlerPrioritertAdresseResponse().apply {
            navn = "STATENS PENSJONSKASSE FORVALTNINGS"
            postadresse = Adresse().apply {
                adresselinje1 = "Postboks 10 Skøyen"
                postnr = "0212"
                poststed = "OSLO"
                land = Land().apply { kode = "NOR" }
            }
        }

        val token = mockOAuth2Server.issueToken("issuer1", "foo", audience = "acceptedAudience")
        webClient.mutate().responseTimeout(Duration.ofSeconds(30)).build()
            .get()
            .uri("/api/samhandler/hentSamhandlerPostadresse/{tssId}", mapOf("tssId" to "80000483597"))
            .headers { it.setBearerAuth(token.serialize()) }
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
            .expectBody()
            .jsonPath("$.adresse.navn").isEqualTo("STATENS PENSJONSKASSE FORVALTNINGS")
            .jsonPath("$.adresse.linje1").isEqualTo("Postboks 10 Skøyen")
            .jsonPath("$.adresse.postnr").isEqualTo("0212")
            .jsonPath("$.adresse.poststed").isEqualTo("OSLO")
            .jsonPath("$.adresse.land").isEqualTo("NOR")
            .jsonPath("$.failureType").doesNotExist()

        assertEquals("80000483597", fakeSamhandlerV2Service.sisteRequest?.ident)

        assertEquals("testbruker", capturingUsernameTokenHandler.sistMottattBrukernavn)
        assertEquals("testpassord", capturingUsernameTokenHandler.sistMottattPassord)
    }

    @Test
    fun `kall på hentSamhandlerPostadresse hvor samhandler ikke finnes gir NOT_FOUND`() {
        fakeSamhandlerV2Service.samhandlerIkkeFunnet = true

        val token = mockOAuth2Server.issueToken("issuer1", "foo", audience = "acceptedAudience")
        webClient.mutate().responseTimeout(Duration.ofSeconds(30)).build()
            .get()
            .uri("/api/samhandler/hentSamhandlerPostadresse/{tssId}", mapOf("tssId" to "finnes-ikke"))
            .headers { it.setBearerAuth(token.serialize()) }
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
            .expectBody()
            .jsonPath("$.failureType").isEqualTo("NOT_FOUND")
    }

    @Test
    fun `kall på hentSamhandlerPostadresse med uventet feil fra TSS gir GENERISK`() {
        fakeSamhandlerV2Service.kastUventetFeil = true

        val token = mockOAuth2Server.issueToken("issuer1", "foo", audience = "acceptedAudience")
        webClient.mutate().responseTimeout(Duration.ofSeconds(30)).build()
            .get()
            .uri("/api/samhandler/hentSamhandlerPostadresse/{tssId}", mapOf("tssId" to "123"))
            .headers { it.setBearerAuth(token.serialize()) }
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
            .expectBody()
            .jsonPath("$.failureType").isEqualTo("GENERISK")
    }

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
    fun `kall på hentSamhandler med gyldig token gir 200`() {
        val listnerThread = lagListener("hentSamhandler.response.xml")

        val token = mockOAuth2Server.issueToken("issuer1", "foo", audience = "acceptedAudience")
        webClient.mutate().responseTimeout(Duration.ofSeconds(30)).build()
            .get()
            .uri("/api/samhandler/hentSamhandler/{tssId}", mapOf("tssId" to "123"))
            .headers {
                it.setBearerAuth(
                    token.serialize()
                )
            }
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
            .expectBody()
            .json(lesResource("hentSamhandler.response.json"), true)

        listnerThread.interrupt()
    }

    @Test
    fun `kall på hentSamhandler med konto gir 200 og korrekt kontodata`() {
        val listnerThread = lagListener("hentSamhandlerMedKonto.response.xml")

        val token = mockOAuth2Server.issueToken("issuer1", "foo", audience = "acceptedAudience")
        webClient.mutate().responseTimeout(Duration.ofSeconds(30)).build()
            .get()
            .uri("/api/samhandler/hentSamhandler/{tssId}", mapOf("tssId" to "123"))
            .headers {
                it.setBearerAuth(
                    token.serialize()
                )
            }
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
            .expectBody()
            .json(lesResource("hentSamhandlerMedKonto.response.json"), true)

        listnerThread.interrupt()
    }

    @Test
    fun `kall på hentSamhandlerEnkel med gyldig token gir 200`() {
        val listnerThread = lagListener("hentSamhandlerEnkel.response.xml")

        val token = mockOAuth2Server.issueToken("issuer1", "foo", audience = "acceptedAudience")
        webClient.mutate().responseTimeout(Duration.ofSeconds(30)).build()
            .get()
            .uri("/api/samhandler/hentSamhandlerEnkel/{tssId}", mapOf("tssId" to "123"))
            .headers {
                it.setBearerAuth(
                    token.serialize()
                )
            }
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
            .expectBody()
            .json(lesResource("hentSamhandlerEnkel.response.json"), true)

        listnerThread.interrupt()
    }

    private fun lagListener(responseFil: String) =
        thread {
            val message = jmsTemplate.receive("DEV.QUEUE.1")
            print("Fikk melding $message")
            if (message == null) {
                throw IllegalStateException("Melding var null")
            }

            jmsTemplate.send(message.jmsReplyTo) {
                it.createTextMessage(lesResource(responseFil))
            }
        }

    private fun lesResource(responseFil: String) =
        javaClass.getResource("/no/nav/pensjon_samhandler_proxy/$responseFil")
            ?.readText()
            ?: throw IllegalStateException("Fant ikke responsefil $responseFil")

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

        @JvmStatic
        val fakeSamhandlerV2Service = FakeSamhandlerV2Service()

        @JvmStatic
        val capturingUsernameTokenHandler = CapturingUsernameTokenHandler()

        @JvmStatic
        private val fakeSamhandlerV2ServerPort: Int = ServerSocket(0).use { it.localPort }

        @JvmStatic
        private val fakeSamhandlerV2Server = JaxWsServerFactoryBean().apply {
            address = "http://localhost:$fakeSamhandlerV2ServerPort/samhandlerv2"
            serviceClass = Samhandler::class.java
            serviceBean = fakeSamhandlerV2Service
            handlers = listOf(capturingUsernameTokenHandler)
        }.create()!!

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

            registry.add("tss.samhandlerv2.endpoint.url") {
                "http://localhost:$fakeSamhandlerV2ServerPort/samhandlerv2"
            }
        }

        @JvmStatic
        @AfterAll
        fun stoppFakeSamhandlerV2Server() {
            fakeSamhandlerV2Server.destroy()
        }
    }
}

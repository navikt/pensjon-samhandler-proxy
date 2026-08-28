package no.nav.pensjon_samhandler_proxy

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import jakarta.jms.Message
import jakarta.jms.TextMessage
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
import org.springframework.jms.core.MessageCreator
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.net.ServerSocket
import java.time.Duration

/**
 * Full Spring ApplicationContext-test, tilsvarende produksjonsoppsettet, men uten avhengighet
 * til en ekte MQ-instans. JmsTemplate mockes med SpringMockK slik at hele bean-grafen (inkludert
 * auto-konfigurasjon for ibm.mq/JMS) lastes akkurat som i produksjon, mens selve JMS-kallet
 * kontrolleres i testen. Dette gjør testen robust og reproduserbar uten Docker/Testcontainers,
 * og bygger på samme mønster som [PensjonSamhandlerProxyApplicationTest].
 */
@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    classes = [PensjonSamhandlerProxyApplication::class],
    properties = [
        "management.health.livenessstate.enabled=true",
        "management.health.readinessstate.enabled=true",
        "management.endpoint.health.probes.enabled=true",
        "tss.samhandlerv2.serviceuser.username=testbruker",
        "tss.samhandlerv2.serviceuser.password=testpassord",
        "ibm.mq.channel=DEV.APP.SVRCONN",
        "ibm.mq.connName=localhost(1414)",
        "ibm.mq.queueManager=QM1",
        "ibm.mq.tempModel=DEV.APP.MODEL.QUEUE",
        "ibm.mq.user=app",
        "ibm.mq.password=passw0rd",
        "samhandler.xml.queueName=DEV.QUEUE.1",
    ]
)
@ContextConfiguration(
    initializers = [
        MockOAuth2ServerInitializer::class,
    ]
)
@AutoConfigureWebTestClient
class PensjonSamhandlerProxyMockkJmsApplicationTest @Autowired constructor(
    val samhandlerService: SamhandlerService,
    val mockOAuth2Server: MockOAuth2Server,
    val webClient: WebTestClient,
) {
    @MockkBean(relaxed = true)
    lateinit var jmsTemplate: JmsTemplate

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
        every { jmsTemplate.sendAndReceive(any<MessageCreator>()) } returns null

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
        mockJmsSvar("hentSamhandler.response.xml")

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
    }

    @Test
    fun `kall på hentSamhandlerEnkel med gyldig token gir 200`() {
        mockJmsSvar("hentSamhandlerEnkel.response.xml")

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
    }

    @Test
    fun `kall på hentAvdelingstype med gyldig token gir 200 og eksakt json payload`() {
        mockJmsSvar("hentSamhandler.response.xml")

        val token = mockOAuth2Server.issueToken("issuer1", "foo", audience = "acceptedAudience")
        webClient.mutate().responseTimeout(Duration.ofSeconds(30)).build()
            .get()
            .uri("/api/samhandler/hentAvdelingstype/{tssId}", mapOf("tssId" to "123"))
            .headers { it.setBearerAuth(token.serialize()) }
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
            .expectBody()
            .json(lesResource("hentAvdelingstype.response.json"), true)
    }

    @Test
    fun `kall på hentOffentligId med gyldig token gir 200 og eksakt json payload`() {
        mockJmsSvar("hentSamhandler.response.xml")

        val token = mockOAuth2Server.issueToken("issuer1", "foo", audience = "acceptedAudience")
        webClient.mutate().responseTimeout(Duration.ofSeconds(30)).build()
            .get()
            .uri("/api/samhandler/hentOffentligId/{tssId}", mapOf("tssId" to "123"))
            .headers { it.setBearerAuth(token.serialize()) }
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
            .expectBody()
            .json(lesResource("hentOffentligId.response.json"), true)
    }

    @Test
    fun `kall på finnSamhandler med gyldig token gir 200 og eksakt json payload`() {
        mockJmsSvar("finnSamhandler.response.xml")

        val token = mockOAuth2Server.issueToken("issuer1", "foo", audience = "acceptedAudience")
        webClient.mutate().responseTimeout(Duration.ofSeconds(30)).build()
            .post()
            .uri("/api/samhandler/finnSamhandler")
            .headers { it.setBearerAuth(token.serialize()) }
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "navn" to "Skagerak",
                    "samhandlerType" to "TEPE",
                    "offentligId" to null,
                    "idType" to null,
                )
            )
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
            .expectBody()
            .json(lesResource("finnSamhandler.response.json"), true)
    }

    private fun mockJmsSvar(responseFil: String) {
        val svar = mockk<TextMessage>(relaxed = true) {
            every { text } returns lesResource(responseFil)
        }
        every { jmsTemplate.sendAndReceive(any<MessageCreator>()) } returns (svar as Message)
    }

    private fun lesResource(responseFil: String) =
        javaClass.getResource("/no/nav/pensjon_samhandler_proxy/$responseFil")
            ?.readText()
            ?: throw IllegalStateException("Fant ikke responsefil $responseFil")

    companion object {
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
        fun properties(registry: DynamicPropertyRegistry) {
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

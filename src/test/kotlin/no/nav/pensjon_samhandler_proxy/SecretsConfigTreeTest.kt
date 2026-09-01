package no.nav.pensjon_samhandler_proxy

import org.junit.jupiter.api.Test
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Verifiserer at secrets montert av nais (fra Vault) som filer under en katalog
 * (f.eks. /secrets/srvpenmq/username) blir bundet til Spring-properties via
 * spring.config.import=optional:configtree:..., slik det er konfigurert i
 * application.properties. Dette erstatter tidligere logikk i java-opts.sh som
 * leste filene med `cat` og eksporterte dem som miljøvariabler - noe som krever
 * et shell i containeren.
 */
class SecretsConfigTreeTest {

    @Test
    fun `secrets montert som filer under configtree bindes til srvpenmq og srvtss properties`() {
        val secretsDir = Files.createTempDirectory("secrets")
        writeSecret(secretsDir, "srvpenmq", "username", "penmq-bruker")
        writeSecret(secretsDir, "srvpenmq", "password", "penmq-passord")
        writeSecret(secretsDir, "srvtss", "username", "tss-bruker")
        writeSecret(secretsDir, "srvtss", "password", "tss-passord")

        val environment = loadEnvironment("optional:configtree:$secretsDir/")

        assertEquals("penmq-bruker", environment.getProperty("srvpenmq.username"))
        assertEquals("penmq-passord", environment.getProperty("srvpenmq.password"))
        assertEquals("tss-bruker", environment.getProperty("srvtss.username"))
        assertEquals("tss-passord", environment.getProperty("srvtss.password"))
    }

    @Test
    fun `tss serviceuser username og password i application-properties resolves fra configtree`() {
        val secretsDir = Files.createTempDirectory("secrets")
        writeSecret(secretsDir, "srvtss", "username", "tss-bruker")
        writeSecret(secretsDir, "srvtss", "password", "tss-passord")

        val environment = loadEnvironment(
            "optional:configtree:$secretsDir/",
            "tss.samhandlerv2.serviceuser.username" to "\${srvtss.username}",
            "tss.samhandlerv2.serviceuser.password" to "\${srvtss.password}",
        )

        assertEquals("tss-bruker", environment.getProperty("tss.samhandlerv2.serviceuser.username"))
        assertEquals("tss-passord", environment.getProperty("tss.samhandlerv2.serviceuser.password"))
    }

    @Test
    fun `manglende secrets-katalog feiler ikke oppstart siden import er optional`() {
        val environment = loadEnvironment("optional:configtree:/secrets-som-garantert-ikke-finnes/")

        assertNull(environment.getProperty("srvpenmq.username"))
    }

    private fun loadEnvironment(
        configImport: String,
        vararg additionalProperties: Pair<String, String>,
    ): StandardEnvironment {
        val environment = StandardEnvironment()
        val properties = mapOf("spring.config.import" to configImport) + additionalProperties
        environment.propertySources.addFirst(MapPropertySource("test", properties))

        ConfigDataEnvironmentPostProcessor.applyTo(environment)

        return environment
    }

    private fun writeSecret(root: Path, secretDir: String, key: String, value: String) {
        val target = root.resolve(secretDir)
        target.createDirectories()
        target.resolve(key).writeText(value)
    }
}

package no.nav.pensjon_samhandler_proxy

import no.nav.freg.tss.TOutputElementer
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(path = ["/api/samhandler"])
class SamhandlerController(
    private val samhandlerViaKoe: SamhandlerViaKoe,
) {
    @GetMapping("/hentSamhandler/{tssId}")
    fun hentSamhandler(@PathVariable("tssId") tssId: String): Samhandler? {
        return samhandlerViaKoe.hentSamhandlerXml(tssId, true)
    }

    @Deprecated("Bruk heller hentSamhandler")
    @GetMapping("/hentSamhandler/raw/{tssId}")
    fun hentSamhandlerRaw(@PathVariable("tssId") tssId: String): TOutputElementer? {
        return samhandlerViaKoe.hentSamhandlerXmlRaw(tssId, true)
    }

    @GetMapping("/hentSamhandlerNavn/{tssId}")
    fun hentSamhandlerNavn(@PathVariable("tssId") tssId: String): Samhandler? {
        return samhandlerViaKoe.hentSamhandlerXml(tssId, false)
    }

    @Deprecated("Bruk heller hentSamhandlerNavn")
    @GetMapping("/hentSamhandlerNavn/raw/{tssId}")
    fun hentSamhandlerNavnRaw(@PathVariable("tssId") tssId: String): TOutputElementer? {
        return samhandlerViaKoe.hentSamhandlerXmlRaw(tssId, false)
    }

    @PostMapping("/finnSamhandler")
    fun finnSamhandler(@RequestBody soek: Soek): List<Samhandler> {
        return samhandlerViaKoe.finnSamhandlerXml(
            soek.navn,
            soek.idType,
            soek.offentligId,
            soek.samhandlerType
        )
    }

    @Deprecated("Bruk heller finnSamhandler")
    @PostMapping("/finnSamhandler/raw")
    fun finnSamhandlerRaw(@RequestBody soek: Soek): TOutputElementer? {
        return samhandlerViaKoe.finnSamhandlerXmlRaw(
            soek.navn,
            soek.idType,
            soek.offentligId,
            soek.samhandlerType
        )
    }

    @GetMapping("/ping")
    fun ping() = "PONG"

    data class Soek(
        val navn: String?,
        val samhandlerType: String?,
        val offentligId: String?,
        val idType: String?,
    )
}

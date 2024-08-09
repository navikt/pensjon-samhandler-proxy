package no.nav.pensjon_samhandler_proxy

import no.nav.freg.tss.TOutputElementer
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(path = ["/api/samhandler"])
class SamhandlerController(
    private val samhandlerViaKoe: SamhandlerViaKoe,
) {
    @GetMapping("/hentSamhandler/{tssId}")
    fun hentSamhandler(@PathVariable("tssId") tssId: String): TOutputElementer? {
        return samhandlerViaKoe.hentSamhandlerXml(tssId, true)
    }

    @GetMapping("/hentSamhandlerNavn/{tssId}")
    fun hentSamhandlerNavn(@PathVariable("tssId") tssId: String): TOutputElementer? {
        return samhandlerViaKoe.hentSamhandlerXml(tssId, false)
    }

    @PostMapping("/finnSamhandler")
    fun finnSamhandler(@RequestBody soek: Soek): TOutputElementer? {
        return samhandlerViaKoe.finnSamhandlerXml(
            soek.navn,
            soek.idType,
            soek.offentligId,
            soek.samhandlerType
        )
    }

    data class Soek(
        val navn: String?,
        val samhandlerType: String?,
        val offentligId: String?,
        val idType: String?,
    )
}

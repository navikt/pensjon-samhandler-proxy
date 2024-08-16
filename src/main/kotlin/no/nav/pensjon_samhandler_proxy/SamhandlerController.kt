package no.nav.pensjon_samhandler_proxy

import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(path = ["/api/samhandler"])
class SamhandlerController(
    private val samhandlerViaKoe: SamhandlerViaKoe,
) {
    @GetMapping("/hentSamhandler/{tssId}")
    fun hentSamhandler(@PathVariable("tssId") tssId: String): Samhandler? {
        return samhandlerViaKoe.hentSamhandler(tssId)
    }

    @GetMapping("/hentSamhandlerEnkel/{tssId}")
    fun hentSamhandlerEnkel(@PathVariable("tssId") tssId: String): SamhandlerEnkel? {
        return samhandlerViaKoe.hentSamhandlerEnkel(tssId)
    }

    @PostMapping("/finnSamhandler")
    fun finnSamhandler(@RequestBody soek: Soek): List<Samhandler> {
        return samhandlerViaKoe.finnSamhandler(
            soek.navn?.takeIf { it.isNotBlank() },
            soek.idType?.takeIf { it.isNotBlank() },
            soek.offentligId?.takeIf { it.isNotBlank() },
            soek.samhandlerType?.takeIf { it.isNotBlank() },
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

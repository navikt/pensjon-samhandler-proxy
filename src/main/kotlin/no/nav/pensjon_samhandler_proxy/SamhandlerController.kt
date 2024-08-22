package no.nav.pensjon_samhandler_proxy

import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(path = ["/api/samhandler"])
class SamhandlerController(
    private val samhandlerService: SamhandlerService,
) {
    @GetMapping("/hentSamhandler/{tssId}")
    fun hentSamhandler(@PathVariable("tssId") tssId: String): Samhandler? {
        return samhandlerService.hentSamhandler(tssId)
    }

    @GetMapping("/hentSamhandlerEnkel/{tssId}")
    fun hentSamhandlerEnkel(@PathVariable("tssId") tssId: String): SamhandlerEnkel? {
        return samhandlerService.hentSamhandlerEnkel(tssId)
    }

    @PostMapping("/finnSamhandler")
    fun finnSamhandler(@RequestBody soek: Soek): List<Samhandler> {
        return samhandlerService.finnSamhandler(
            soek.navn?.takeIf { it.isNotBlank() },
            soek.idType?.takeIf { it.isNotBlank() },
            soek.offentligId?.takeIf { it.isNotBlank() },
            soek.samhandlerType?.takeIf { it.isNotBlank() },
        )
    }

    @PostMapping("/finnSamhandlerResponse")
    fun finnSamhandlerResponse(@RequestBody soek: Soek): FinnSamhandlerResponse {
        return FinnSamhandlerResponse(
            samhandlerService.finnSamhandler(
                soek.navn?.takeIf { it.isNotBlank() },
                soek.idType?.takeIf { it.isNotBlank() },
                soek.offentligId?.takeIf { it.isNotBlank() },
                soek.samhandlerType?.takeIf { it.isNotBlank() },
            )
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

    data class FinnSamhandlerResponse(
        val samhandlerList: List<Samhandler>,
    )
}

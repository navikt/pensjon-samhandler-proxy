package no.nav.pensjon_samhandler_proxy

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(path = ["/api/samhandler"])
class SamhandlerController(
    private val samhandlerService: SamhandlerService,
) {
    @GetMapping("/hentSamhandler/{tssId}")
    fun hentSamhandler(@PathVariable("tssId") tssId: String): ResponseEntity<Samhandler> {
        return ResponseEntity.ofNullable(samhandlerService.hentSamhandler(tssId))
    }

    @GetMapping("/hentSamhandlerEnkel/{tssId}")
    fun hentSamhandlerEnkel(@PathVariable("tssId") tssId: String): ResponseEntity<SamhandlerEnkel> {
        return ResponseEntity.ofNullable(samhandlerService.hentSamhandlerEnkel(tssId))
    }

    @GetMapping("/hentAvdelingstype/{tssId}")
    fun hentAvdelingstype(@PathVariable("tssId") tssId: String): ResponseEntity<HentAvdelingstypeResponse> {
        return ResponseEntity.ofNullable(samhandlerService.hentAvdelingstype(tssId)?.let { HentAvdelingstypeResponse(it) })
    }

    @GetMapping("/hentOffentligId/{tssId}")
    fun hentOffentligId(@PathVariable("tssId") tssId: String): ResponseEntity<HentOffentligIdResponse> {
        return ResponseEntity.ofNullable(samhandlerService.hentOffentligId(tssId)?.let { HentOffentligIdResponse(it) })
    }

    @PostMapping("/finnSamhandler")
    fun finnSamhandler(@RequestBody soek: Soek): FinnSamhandlerResponse {
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

    data class HentAvdelingstypeResponse(
        val avdelingstype: String
    )
    data class HentOffentligIdResponse(
        val offentligId: String
    )
}

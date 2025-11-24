package no.nav.pensjon_samhandler_proxy

import no.nav.virksomhet.tjenester.samhandler.meldinger.v2.HentSamhandlerPrioritertAdresseRequest
import no.nav.virksomhet.tjenester.samhandler.v2.binding.HentSamhandlerPrioritertAdresseSamhandlerIkkeFunnet
import org.slf4j.LoggerFactory.getLogger
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(path = ["/api/samhandler"])
class SamhandlerController(
    private val samhandlerPort: no.nav.virksomhet.tjenester.samhandler.v2.binding.Samhandler,
    private val samhandlerService: SamhandlerService,
) {
    private val logger = getLogger(javaClass)

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

    @GetMapping("/hentSamhandlerPostadresse/{tssId}")
    fun hentSamhandlerPostadresse(@PathVariable("tssId") tssId: String): HentSamhandlerAdresseResponseDto? =
        try {
            samhandlerPort.hentSamhandlerPrioritertAdresse(
                HentSamhandlerPrioritertAdresseRequest().apply {
                    this.ident = tssId
                    this.identKode = "TSS_EKSTERN_ID"
                }
            )?.let {
                HentSamhandlerAdresseResponseDto(
                    HentSamhandlerAdresseResponseDto.SamhandlerPostadresse(
                        navn = it.navn.trim(),
                        linje1 = it.postadresse?.adresselinje1?.trim(),
                        linje2 = it.postadresse?.adresselinje2?.trim(),
                        linje3 = it.postadresse?.adresselinje3?.trim(),
                        postnr = it.postadresse?.postnr?.trim(),
                        poststed = it.postadresse?.poststed?.trim(),
                        land = it.postadresse?.land?.kode?.trim(),
                    )
                )
            }
        } catch (e: HentSamhandlerPrioritertAdresseSamhandlerIkkeFunnet) {
            HentSamhandlerAdresseResponseDto(HentSamhandlerAdresseResponseDto.FailureType.NOT_FOUND, e)
        } catch (e: Exception) {
            logger.error("Feil ved henting av aamhandler prioritert adresse", e)
            HentSamhandlerAdresseResponseDto(HentSamhandlerAdresseResponseDto.FailureType.GENERISK, e)
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


    data class HentSamhandlerAdresseResponseDto(
        val adresse: SamhandlerPostadresse?,
        val failureType: FailureType?,
        val message: String? = null,
        val stackTrace: String? = null,
    ) {
        constructor(adresse: SamhandlerPostadresse) : this(adresse, null)
        constructor(failureType: FailureType, e: Exception) : this(null, failureType, e.message, e.stackTraceToString())

        data class SamhandlerPostadresse(
            val navn: String,
            val linje1: String?,
            val linje2: String?,
            val linje3: String?,
            val postnr: String?,
            val poststed: String?,
            val land: String?,
        )

        enum class FailureType {
            NOT_FOUND,
            GENERISK,
        }
    }
}

package no.nav.pensjon_samhandler_proxy

import jakarta.xml.ws.WebServiceException
import no.nav.virksomhet.tjenester.samhandler.meldinger.v2.HentAutorisasjonOgRettighetListeRequest
import no.nav.virksomhet.tjenester.samhandler.meldinger.v2.HentAutorisasjonOgRettighetListeResponse
import no.nav.virksomhet.tjenester.samhandler.meldinger.v2.HentSamhandlerNavnRequest
import no.nav.virksomhet.tjenester.samhandler.meldinger.v2.HentSamhandlerNavnResponse
import no.nav.virksomhet.tjenester.samhandler.meldinger.v2.HentSamhandlerPrioritertAdresseRequest
import no.nav.virksomhet.tjenester.samhandler.meldinger.v2.HentSamhandlerPrioritertAdresseResponse
import no.nav.virksomhet.tjenester.samhandler.v2.SamhandlerIkkeFunnet
import no.nav.virksomhet.tjenester.samhandler.v2.binding.HentSamhandlerNavnSamhandlerIkkeFunnet
import no.nav.virksomhet.tjenester.samhandler.v2.binding.HentSamhandlerPrioritertAdresseSamhandlerIkkeFunnet
import no.nav.virksomhet.tjenester.samhandler.v2.binding.Samhandler

class FakeSamhandlerV2Service : Samhandler {
    var nestePrioritertAdresseSvar: HentSamhandlerPrioritertAdresseResponse? = null
    var samhandlerIkkeFunnet: Boolean = false
    var kastUventetFeil: Boolean = false
    var sisteRequest: HentSamhandlerPrioritertAdresseRequest? = null

    fun reset() {
        nestePrioritertAdresseSvar = null
        samhandlerIkkeFunnet = false
        kastUventetFeil = false
        sisteRequest = null
    }

    override fun hentAutorisasjonOgRettighetListe(
        request: HentAutorisasjonOgRettighetListeRequest
    ): HentAutorisasjonOgRettighetListeResponse =
        throw WebServiceException("Ikke implementert i test-stub")

    override fun hentSamhandlerNavn(
        request: HentSamhandlerNavnRequest
    ): HentSamhandlerNavnResponse =
        throw HentSamhandlerNavnSamhandlerIkkeFunnet(
            "Ikke implementert i test-stub",
            SamhandlerIkkeFunnet(),
        )

    override fun hentSamhandlerPrioritertAdresse(
        request: HentSamhandlerPrioritertAdresseRequest
    ): HentSamhandlerPrioritertAdresseResponse {
        sisteRequest = request

        if (kastUventetFeil) {
            throw WebServiceException("Simulert uventet feil fra TSS")
        }
        if (samhandlerIkkeFunnet) {
            throw HentSamhandlerPrioritertAdresseSamhandlerIkkeFunnet(
                "Fant ikke samhandler med ident ${request.ident}",
                SamhandlerIkkeFunnet(),
            )
        }
        return nestePrioritertAdresseSvar
            ?: throw IllegalStateException("Test-stub er ikke konfigurert med et svar")
    }
}

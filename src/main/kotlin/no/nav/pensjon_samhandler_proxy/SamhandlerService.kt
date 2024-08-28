package no.nav.pensjon_samhandler_proxy

import no.nav.freg.tss.*
import org.springframework.jms.core.JmsTemplate
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.StringReader
import jakarta.jms.Message
import jakarta.jms.TextMessage
import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.Marshaller
import javax.xml.stream.XMLInputFactory
import javax.xml.transform.stream.StreamSource

@Component
class SamhandlerService(
    private val xmlJmsTemplate: JmsTemplate,
) {
    fun hentSamhandler(tssId: String): Samhandler? {
        return kallSamhandler {
            samhandlerIDataB910 = SamhandlerIDataB910Type().apply {
                idOffTSS = tssId
                brukerID = "PP01"
            }
        }?.samhandlerODataB910?.enkeltSamhandler?.first()?.toSamhandler()
    }

    fun hentSamhandlerEnkel(tssId: String): SamhandlerEnkel? {
        return kallSamhandler {
            samhandlerIDataB980 = SamhandlerIDataB980Type().apply {
                idOffTSS = tssId
                hentNavn = "J"
                brukerID = "PP01"
            }
        }?.samhandlerODataB980?.ident?.first()?.toSamhandlerEnkel()
    }

    fun finnSamhandler(
        navn: String?,
        idType: String?,
        offentligId: String?,
        samhandlerType: String?
    ): List<Samhandler> {
        return if (navn != null && idType == null && offentligId == null) {
            kallSamhandler {
                samhandlerIDataB98A = SamhandlerIDataB98AType().apply {
                    navnSamh = navn
                    kodeSamhType = samhandlerType
                    delNavn = "N"
                    brukerID = "PP01"
                    aksjonsKode = "A"
                    aksjonsKode2 = "0"
                }
            }?.samhandlerODataB98A?.ident?.map { it.toSamhandler() } ?: emptyList()

        } else if (idType != null && offentligId != null && navn == null) {
            //Søke på offentligId og type. Kall hentSamhandler
            kallSamhandler {
                samhandlerIDataB910 = SamhandlerIDataB910Type().apply {
                    ofFid = TidOFF1().apply {
                        idOff = offentligId
                        kodeIdType = idType
                        kodeSamhType = samhandlerType
                    }
                    brukerID = "PP01"
                }
            }?.samhandlerODataB910?.enkeltSamhandler?.map { it.toSamhandler() } ?: emptyList()

        } else if (idType == null && offentligId == null && navn == null && samhandlerType != null) {
            //search in TSS, only with samhandlerType as input
            kallSamhandler {
                samhandlerIDataB940 = SamhandlerIDataB940Type().apply {
                    kodeSamhType = samhandlerType
                    brukerID = "PP01"
                }
            }?.samhandlerODataB940?.enkeltSamhandler?.map { it.toSamhandler() } ?: emptyList()
        } else {
            //Ugyldig input. Kast exception
            throw RuntimeException("Ugyldig input")
        }
    }

    private fun kallSamhandler(block: TServicerutiner.() -> Unit): TOutputElementer? {
        return kallSamhandler(
            TssSamhandlerData().apply {
                tssInputData = TssSamhandlerData.TssInputData().apply {
                    tssServiceRutine = TServicerutiner().apply {
                        this.block()
                    }
                }
            }
        )
    }

    private fun kallSamhandler(tssSamhandlerData: TssSamhandlerData): TOutputElementer? {
        val context: JAXBContext = JAXBContext.newInstance(
            TssSamhandlerData::class.java,
        )

        val m: Marshaller = context.createMarshaller()
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)

        val baos = ByteArrayOutputStream()
        m.marshal(tssSamhandlerData, baos)

        val document = baos.toString(java.nio.charset.StandardCharsets.UTF_8)

        val message: Message? = xmlJmsTemplate.sendAndReceive {
            it.createTextMessage(document)
        }
        val text = (message as? TextMessage)?.text
        if (text.isNullOrEmpty()) {
            throw IkkeSvarFraTssException()
        } else {
            val unmarshaller = context.createUnmarshaller()
            val xmlInputFactory: XMLInputFactory = XMLInputFactory.newInstance()


            val unmarshal = unmarshaller.unmarshal(
                xmlInputFactory.createXMLStreamReader(
                    StreamSource(
                        StringReader(
                            text.replace(
                                "samhandlerODataB982",
                                "samhandlerODataB98A"
                            )
                        )
                    )
                ), TssSamhandlerData::class.java
            )
            return unmarshal.value.tssOutputData
        }
    }

    fun no.nav.freg.tss.Samhandler.toSamhandler(): Samhandler {
        return Samhandler(
            navn = samhandler110?.samhandler?.first()?.navnSamh,
            sprak = samhandler110?.samhandler?.first()?.kodeSpraak,
            samhandlerType = samhandler110?.samhandler?.first()?.kodeSamhType,
            offentligId = samhandler110?.samhandler?.first()?.idOff,
            idType = samhandler110?.samhandler?.first()?.kodeIdentType,
            avdelinger = samhandlerAvd125?.samhAvd?.map {
                mapToAvdeling(
                    it,
                    this.adresse130,
                    this.konto140,
                    this.kontakter150
                )
            },
            alternativeIder = alternativId111?.samhId?.map {
                AlternativId(
                    alternativId = it.idAlternativ,
                    alternativIdKode = it.kodeAltIdentType,
                )
            },
        )
    }

    fun IdentOffEks.toSamhandlerEnkel(): SamhandlerEnkel {
        return SamhandlerEnkel(
            offentligId = idOff,
            idType = kodeIdentType,
            samhandlerType = kodeSamhType,
            navn = navnSamh,
        )
    }

    fun IdentOffEks2.toSamhandler(): Samhandler {
        return Samhandler(
            offentligId = idOff,
            idType = kodeIdentType,
            samhandlerType = kodeSamhType,
            navn = navnSamh,
            avdelinger = listOf(
                Avdeling(
                    avdelingNavn = avdelingsNavn?.trim(),
                    avdelingType = avdelingsType?.trim(),
                    avdelingsnr = avdelingsNr,
                    idTSSEkstern = idOffTSS,
                )
            ),
            alternativeIder = emptyList(),
        )
    }

    fun TypeKomp940.toSamhandler(): Samhandler {
        return Samhandler(
            navn = samhandler110?.samhandler?.first()?.navnSamh,
            sprak = samhandler110?.samhandler?.first()?.kodeSpraak,
            samhandlerType = samhandler110?.samhandler?.first()?.kodeSamhType,
            offentligId = samhandler110?.samhandler?.first()?.idOff,
            idType = samhandler110?.samhandler?.first()?.kodeIdentType,
            avdelinger = samhandlerAvd125?.samhAvd?.map {
                mapToAvdeling(
                    it,
                    this.adresse130,
                    null,
                    null,
                )
            },
            alternativeIder = emptyList(),
        )
    }

    private fun mapToAvdeling(
        avdeling: SamhAvdPraType,
        alleAdresser: TypeSamhAdr?,
        alleKontoer: TypeSamhKonto?,
        alleKontakter: TypeSamhKontakt?
    ): Avdeling {
        val adresser = alleAdresser?.adresseSamh?.filter { it.avdNr == avdeling.avdNr }.orEmpty()
        val kontoer = alleKontoer?.konto?.filter { it.avdNr == avdeling.avdNr }.orEmpty()
        val kontakter = alleKontakter?.enKontakt?.filter { it.avdNr == avdeling.avdNr }.orEmpty()
        return Avdeling(
            idTSSEkstern = avdeling.idOffTSS,
            avdelingNavn = avdeling.avdNavn?.trim(),
            avdelingType = avdeling.typeAvd?.trim(),
            avdelingsnr = avdeling.avdNr,
            pAdresse = adresser.firstOrNull { it.kodeAdresseType == "PST" }?.asAdresse(),
            aAdresse = adresser.firstOrNull { it.kodeAdresseType == "WP" }?.asAdresse(),
            tAdresse = adresser.firstOrNull { it.kodeAdresseType == "TILL" }?.asAdresse(),
            uAdresse = adresser.firstOrNull { it.kodeAdresseType == "UTL" }?.asAdresse(),
            kontoer = kontoer.map { it.asKonto() },
            ePost = kontakter.firstOrNull { it.avdNr == avdeling.avdNr && it.kodeKontaktType == "EPOS" }?.kontakt,
            telefon = kontakter.firstOrNull { it.avdNr == avdeling.avdNr && it.kodeKontaktType == "TLF" }?.kontakt,
            mobil = kontakter.firstOrNull { it.avdNr == avdeling.avdNr && it.kodeKontaktType == "MTLF" }?.kontakt,
        )
    }

    private fun KontoType.asKonto(): Konto {
        return Konto(
            bankadresse = Adresse(
                adresselinje1 = this.utlBankAdrInfo?.utlBankAdr?.get(0)?.adresse?.get(0)?.trim(),
                adresselinje2 = this.utlBankAdrInfo?.utlBankAdr?.get(0)?.adresse?.get(1)?.trim(),
                adresselinje3 = this.utlBankAdrInfo?.utlBankAdr?.get(0)?.adresse?.get(2)?.trim(),
                land = this.kodeLand?.trim(),
            ),
            bankkode = this.bankKode?.trim(),
            banknavn = this.bankNavn?.trim(),
            kontonummer = (this.gironrInnland ?: this.gironrUtland)?.trim(),
            kontoType = this.kodeKontoType?.value(),
            swiftkode = this.swiftKode?.trim(),
            valuta = this.kodeValuta?.trim(),
        )
    }

    private fun AdresseSamhType.asAdresse(): Adresse {
        return Adresse(
            adresselinje1 = this.adrLinjeInfo?.adresseLinje?.getOrNull(0),
            adresselinje2 = this.adrLinjeInfo?.adresseLinje?.getOrNull(1),
            adresselinje3 = this.adrLinjeInfo?.adresseLinje?.getOrNull(2),
            adresseTypekode = this.kodeAdresseType,
            postNr = this.postNr?.trim(),
            poststed = this.poststed?.trim(),
            land = this.kodeLand,
            kommuneNr = this.kommuneNr?.trim(),
            erGyldig = this.gyldigAdresse == "J",
        )
    }
}

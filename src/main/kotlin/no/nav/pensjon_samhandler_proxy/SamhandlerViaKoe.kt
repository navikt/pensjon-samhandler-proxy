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
class SamhandlerViaKoe(
    private val xmlJmsTemplate: JmsTemplate,
) {
    fun hentSamhandlerXml(tssId: String, detaljert: Boolean): Samhandler? {
        return if (detaljert) {
            hentSamhandlerXmlRaw(tssId, detaljert)?.samhandlerODataB910?.enkeltSamhandler?.first()?.toSamhandler()
        } else {
            hentSamhandlerXmlRaw(tssId, detaljert)?.samhandlerODataB980?.ident?.first()?.toSamhandler()
        }
    }

    fun hentSamhandlerXmlRaw(tssId: String, detaljert: Boolean): TOutputElementer? {
        return if (detaljert) {
            kallSamhandlerXml(TssSamhandlerData().apply {
                tssInputData = TssSamhandlerData.TssInputData().apply {
                    tssServiceRutine = TServicerutiner().apply {
                        samhandlerIDataB910 = SamhandlerIDataB910Type().apply {
                            idOffTSS = tssId
                            brukerID = "PP01"
                        }
                    }
                }
            })
        } else {
            kallSamhandlerXml(TssSamhandlerData().apply {
                tssInputData = TssSamhandlerData.TssInputData().apply {
                    tssServiceRutine = TServicerutiner().apply {
                        samhandlerIDataB980 = SamhandlerIDataB980Type().apply {
                            idOffTSS = tssId
                            hentNavn = "J"
                            brukerID = "PP01"
                        }
                    }
                }
            })
        }
    }

    fun finnSamhandlerXml(navn: String?, idType: String?, offentligId: String?, samhandlerType: String?): List<Samhandler> {
        return if ((navn?.isNotBlank() == true) && idType.isNullOrBlank() && offentligId.isNullOrBlank()) {
            finnSamhandlerXmlRaw(navn, idType, offentligId, samhandlerType)?.samhandlerODataB98A?.ident?.map { it.toSamhandler() } ?: emptyList()
        } else if (((idType?.isNotBlank() == true) && (offentligId?.isNotBlank() == true)) && navn.isNullOrBlank()) {
            finnSamhandlerXmlRaw(navn, idType, offentligId, samhandlerType)?.samhandlerODataB910?.enkeltSamhandler?.map { it.toSamhandler() } ?: emptyList()
        } else if ((idType.isNullOrBlank() && offentligId.isNullOrBlank()) && navn.isNullOrBlank() && (samhandlerType?.isNotBlank() == true)) {
            finnSamhandlerXmlRaw(navn, idType, offentligId, samhandlerType)?.samhandlerODataB940?.enkeltSamhandler?.map { it.toSamhandler() } ?: emptyList()
        } else {
            //Ugyldig input. Kast exception
            throw RuntimeException("Ugyldig input")
        }
    }

    fun finnSamhandlerXmlRaw(navn: String?, idType: String?, offentligId: String?, samhandlerType: String?): TOutputElementer? {
        return if ((navn?.isNotBlank() == true) && idType.isNullOrBlank() && offentligId.isNullOrBlank()) {
            kallSamhandlerXml(TssSamhandlerData().apply {
                tssInputData = TssSamhandlerData.TssInputData().apply {
                    tssServiceRutine = TServicerutiner().apply {
                        samhandlerIDataB98A = SamhandlerIDataB98AType().apply {
                            navnSamh = navn
                            kodeSamhType = samhandlerType
                            delNavn = "N"
                            brukerID = "PP01"
                            aksjonsKode = "A"
                            aksjonsKode2 = "0"
                        }
                    }
                }
            })

        } else if (((idType?.isNotBlank() == true) && (offentligId?.isNotBlank() == true)) && navn.isNullOrBlank()) {
            //Søke på offentligId og type. Kall hentSamhandler

            kallSamhandlerXml(TssSamhandlerData().apply {
                tssInputData = TssSamhandlerData.TssInputData().apply {
                    tssServiceRutine = TServicerutiner().apply {
                        samhandlerIDataB910 = SamhandlerIDataB910Type().apply {
                            this.ofFid = TidOFF1().apply {
                                idOff = offentligId
                                kodeIdType = idType
                                kodeSamhType = samhandlerType
                            }
                            brukerID = "PP01"
                        }
                    }
                }
            })

        } else if ((idType.isNullOrBlank() && offentligId.isNullOrBlank()) && navn.isNullOrBlank() && (samhandlerType?.isNotBlank() == true)) {
            //search in TSS, only with samhandlerType as input

            kallSamhandlerXml(TssSamhandlerData().apply {
                tssInputData = TssSamhandlerData.TssInputData().apply {
                    tssServiceRutine = TServicerutiner().apply {
                        samhandlerIDataB940 = SamhandlerIDataB940Type().apply {
                            kodeSamhType = samhandlerType
                            brukerID = "PP01"
                        }
                    }
                }
            })
        } else {
            //Ugyldig input. Kast exception
            throw RuntimeException("Ugyldig input")
        }
    }

    private fun kallSamhandlerXml(tssSamhandlerData: TssSamhandlerData): TOutputElementer? {
        val context: JAXBContext = JAXBContext.newInstance(
            TssSamhandlerData::class.java,
        )

        val m: Marshaller = context.createMarshaller()
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)

        val baos = ByteArrayOutputStream()
        m.marshal(tssSamhandlerData, baos)

        val document = baos.toString(java.nio.charset.StandardCharsets.UTF_8)

        val message: Message? = xmlJmsTemplate.sendAndReceive {
            println(document)
            it.createTextMessage(document)
        }
        val text = (message as? TextMessage)?.text
        if (text.isNullOrEmpty()) {
            println("Tomt svar")
            throw IkkeSvarFraTssException()
        } else {
            val unmarshaller = context.createUnmarshaller()
            val xmlInputFactory: XMLInputFactory = XMLInputFactory.newInstance()


            val unmarshal = unmarshaller.unmarshal(xmlInputFactory.createXMLStreamReader(StreamSource(StringReader(text.replace("samhandlerODataB982", "samhandlerODataB98A")))), TssSamhandlerData::class.java)
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
            avdelinger = samhandlerAvd125?.samhAvd?.map { mapToAvdeling(it, this.adresse130, this.konto140, this.kontakter150) },
        )
    }

    fun IdentOffEks.toSamhandler(): Samhandler {
        return Samhandler(
            offentligId = idOff,
            idType = kodeIdentType,
            samhandlerType = kodeSamhType,
            navn = navnSamh,
            avdelinger = listOf(Avdeling(
                avdelingNavn = avdelingsNavn?.trim(),
                avdelingsnr = avdelingsNr,
                idTSSEkstern = idOffTSS,
                )),
        )
    }

    fun IdentOffEks2.toSamhandler(): Samhandler {
        return Samhandler(
            offentligId = idOff,
            idType = kodeIdentType,
            samhandlerType = kodeSamhType,
            navn = navnSamh,
            avdelinger = listOf(Avdeling(
                avdelingNavn = avdelingsNavn?.trim(),
                avdelingType = avdelingsType?.trim(),
                avdelingsnr = avdelingsNr,
                idTSSEkstern = idOffTSS,
            ))
        )
    }

    fun TypeKomp940.toSamhandler(): Samhandler {
        return Samhandler(
            navn = samhandler110?.samhandler?.first()?.navnSamh,
            sprak = samhandler110?.samhandler?.first()?.kodeSpraak,
            samhandlerType = samhandler110?.samhandler?.first()?.kodeSamhType,
            offentligId = samhandler110?.samhandler?.first()?.idOff,
            idType = samhandler110?.samhandler?.first()?.kodeIdentType,
            avdelinger = samhandlerAvd125?.samhAvd?.map { mapToAvdeling(it, this.adresse130, null, null) },
        )
    }

    private fun mapToAvdeling(avdelingDto: SamhAvdPraType, adresse130: TypeSamhAdr?, konto140: TypeSamhKonto?, kontakter150: TypeSamhKontakt?): Avdeling {
        return Avdeling(
            idTSSEkstern = avdelingDto.idOffTSS,
            avdelingNavn = avdelingDto.avdNavn?.trim(),
            avdelingType = avdelingDto.typeAvd?.trim(),
            avdelingsnr = avdelingDto.avdNr,
            pAdresse = adresse130?.adresseSamh?.firstOrNull { it.avdNr == avdelingDto.avdNr && it.kodeAdresseType == "PST" }?.asAdresse(),
            aAdresse = adresse130?.adresseSamh?.firstOrNull { it.avdNr == avdelingDto.avdNr && it.kodeAdresseType == "WP" }?.asAdresse(),
            tAdresse = adresse130?.adresseSamh?.firstOrNull { it.avdNr == avdelingDto.avdNr && it.kodeAdresseType == "TILL" }?.asAdresse(),
            uAdresse = adresse130?.adresseSamh?.firstOrNull { it.avdNr == avdelingDto.avdNr && it.kodeAdresseType == "UTL" }?.asAdresse(),
            kontoer = konto140?.konto?.filter { it.avdNr == avdelingDto.avdNr }?.map { it.asKonto() },
            ePost = kontakter150?.enKontakt?.firstOrNull { it.avdNr == avdelingDto.avdNr && it.kodeKontaktType == "EPOS" }?.kontakt,
            telefon = kontakter150?.enKontakt?.firstOrNull { it.avdNr == avdelingDto.avdNr && it.kodeKontaktType == "TLF" }?.kontakt,
            mobil = kontakter150?.enKontakt?.firstOrNull { it.avdNr == avdelingDto.avdNr && it.kodeKontaktType == "MTLF" }?.kontakt,
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

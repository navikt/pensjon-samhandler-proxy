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
    fun hentSamhandlerXml(tssId: String, detaljert: Boolean): TOutputElementer? {
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

    fun finnSamhandlerXml(navn: String?, idType: String?, offentligId: String?, samhandlerType: String?): TOutputElementer? {
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
}

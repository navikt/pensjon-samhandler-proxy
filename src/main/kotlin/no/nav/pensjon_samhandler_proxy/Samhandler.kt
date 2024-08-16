package no.nav.pensjon_samhandler_proxy

data class Samhandler(
    val navn: String?,
    val sprak: String? = null,
    val samhandlerType: String?,
    val offentligId: String?,
    val idType: String?,
    val avdelinger: List<Avdeling>?,
)

data class SamhandlerEnkel(
    val navn: String?,
    val samhandlerType: String?,
    val offentligId: String?,
    val idType: String?,
)

data class Avdeling(
    val idTSSEkstern: String?,
    val avdelingNavn: String?,
    val avdelingType: String? = null,
    val avdelingsnr: String?,
    val ePost: String? = null,
    val telefon: String? = null,
    val mobil: String? = null,
    val kontoer: List<Konto>? = null,
    val aAdresse: Adresse? = null,
    val pAdresse: Adresse? = null,
    val tAdresse: Adresse? = null,
    val uAdresse: Adresse? = null,
)

data class Konto(
    val kontoType: String?,
    val kontonummer: String?,
    val banknavn: String?,
    val bankkode: String?,
    val swiftkode: String?,
    val valuta: String?,
    val bankadresse: Adresse?,
)

data class Adresse(
    val adresselinje1: String?,
    val adresselinje2: String?,
    val adresselinje3: String?,
    val adresseTypekode: String? = null,
    val postNr: String? = null,
    val poststed: String? = null,
    val land: String?,
    val kommuneNr: String? = null,
    val erGyldig: Boolean? = null,
)

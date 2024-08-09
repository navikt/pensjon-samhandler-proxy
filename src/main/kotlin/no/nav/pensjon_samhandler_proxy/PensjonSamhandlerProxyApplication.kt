package no.nav.pensjon_samhandler_proxy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PensjonSamhandlerProxyApplication

fun main(args: Array<String>) {
	runApplication<PensjonSamhandlerProxyApplication>(*args)
}

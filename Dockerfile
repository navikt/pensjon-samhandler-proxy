FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25

WORKDIR /app

ENV LOGGING_CONFIG=classpath:logback-nais.xml
ENV MAIN_CLASS=no.nav.pensjon_samhandler_proxy.PensjonSamhandlerProxyApplication
ENV TZ="Europe/Oslo"

COPY target/pensjon-samhandler-proxy*jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]

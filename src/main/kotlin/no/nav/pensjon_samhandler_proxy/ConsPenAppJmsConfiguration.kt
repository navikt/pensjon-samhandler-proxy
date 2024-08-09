package no.nav.pensjon_samhandler_proxy

import com.ibm.mq.jakarta.jms.MQQueue
import com.ibm.mq.jakarta.jms.MQQueueConnectionFactory
import com.ibm.msg.client.jakarta.wmq.WMQConstants
import com.ibm.msg.client.jakarta.wmq.common.CommonConstants.WMQ_CLIENT_NONJMS_MQ
import jakarta.jms.ConnectionFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jms.connection.CachingConnectionFactory
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter
import org.springframework.jms.core.JmsTemplate

@Configuration
class ConsPenAppJmsConfiguration {
    @Bean
    fun samhandlerXmlJmsTemplate(
        @Qualifier("cons.pen.connectionFactory") connectionFactory: ConnectionFactory,
        @Value("\${samhandler.xml.queueName}") destination: String,
    ) = JmsTemplate(connectionFactory).apply {
        defaultDestination = MQQueue(destination).apply {
            targetClient = WMQ_CLIENT_NONJMS_MQ
        }
        receiveTimeout = 10_000
    }

    @Bean("cons.pen.connectionFactory")
    fun userCredentialsConnectionFactoryAdapter(
        @Value("\${SRVPENMQ_USERNAME}") username: String,
        @Value("\${SRVPENMQ_PASSWORD}") password: String,
        @Value("\${mqGateway01.hostname}") hostName: String,
        @Value("\${mqGateway01.queueManager}") queueManager: String,
        @Value("\${mqGateway01.channel}") channel: String,
        @Value("\${mqGateway01.port}") port: Int,
        @Value("\${mqGateway01.temporaryModel}") temporaryModel: String,
    ): ConnectionFactory {
        return CachingConnectionFactory(UserCredentialsConnectionFactoryAdapter().apply {
            setTargetConnectionFactory(MQQueueConnectionFactory().also {
                it.hostName = hostName
                it.queueManager = queueManager
                it.channel = channel
                it.port = port
                it.transportType = WMQConstants.WMQ_CM_CLIENT
                it.temporaryModel = "DEV.APP.MODEL.QUEUE"
            })
            setUsername(username)
            setPassword(password)
        })
    }
}

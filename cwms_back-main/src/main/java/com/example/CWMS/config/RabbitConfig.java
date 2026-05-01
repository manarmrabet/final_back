package com.example.CWMS.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitConfig — version avec reconnexion automatique et tolérance aux pannes.
 *
 * CORRECTION : Spring Boot ne plante plus au démarrage si RabbitMQ
 * n'est pas lancé. Il tente de se reconnecter en arrière-plan.
 *
 * Pour lancer RabbitMQ (Docker) :
 *   docker run -d --name cwms-rabbit -p 5672:5672 -p 15672:15672
 *     -e RABBITMQ_DEFAULT_USER=cwms
 *     -e RABBITMQ_DEFAULT_PASS=cwms_secret
 *     rabbitmq:3.13-management
 */
@Configuration
public class RabbitConfig {

    @Value("${spring.rabbitmq.host:localhost}")
    private String host;

    @Value("${spring.rabbitmq.port:5672}")
    private int port;

    @Value("${spring.rabbitmq.username:cwms}")
    private String username;

    @Value("${spring.rabbitmq.password:cwms_secret}")
    private String password;

    @Value("${cwms.rabbit.exchange:cwms.topic}")
    private String exchange;

    @Value("${cwms.rabbit.queue.movements:stock.movements}")
    private String movementsQueue;

    @Value("${cwms.rabbit.queue.alerts:stock.alerts}")
    private String alertsQueue;

    @Value("${cwms.rabbit.routing.movement:movement.new}")
    private String movementRoutingKey;

    @Value("${cwms.rabbit.routing.alert:alert.anomaly}")
    private String alertRoutingKey;

    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory(host, port);
        factory.setUsername(username);
        factory.setPassword(password);
        // Reconnexion automatique toutes les 5 secondes si RabbitMQ est indisponible
        factory.getRabbitConnectionFactory().setAutomaticRecoveryEnabled(true);
        factory.getRabbitConnectionFactory().setNetworkRecoveryInterval(5000);
        return factory;
    }

    @Bean
    public TopicExchange cwmsExchange() {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue stockMovementsQueue() {
        return QueueBuilder.durable(movementsQueue).build();
    }

    @Bean
    public Queue stockAlertsQueue() {
        return QueueBuilder.durable(alertsQueue).build();
    }

    @Bean
    public Binding movementsBinding() {
        return BindingBuilder.bind(stockMovementsQueue()).to(cwmsExchange()).with(movementRoutingKey);
    }

    @Bean
    public Binding alertsBinding() {
        return BindingBuilder.bind(stockAlertsQueue()).to(cwmsExchange()).with(alertRoutingKey);
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf) {
        RabbitTemplate tpl = new RabbitTemplate(cf);
        tpl.setMessageConverter(jsonMessageConverter());
        return tpl;
    }
}
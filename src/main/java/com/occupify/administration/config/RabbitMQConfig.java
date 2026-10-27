package com.occupify.administration.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.occupify.administration.event.ContractAcceptedEvent;
import com.occupify.administration.event.ContractCompensationRequestedEvent;
import com.occupify.administration.event.ContractRejectedEvent;
import com.occupify.administration.event.UserActivatedEvent;
import java.util.HashMap;
import java.util.Map;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

  @Value("${app.rabbitmq.user-exchange:occupify.user.exchange}")
  private String userExchangeName;

  @Value("${app.rabbitmq.user-queue:occupify.administration.user.queue}")
  private String userQueueName;

  @Value("${app.rabbitmq.user-activated-routing-key:user.activated}")
  private String userActivatedRoutingKey;

  @Value("${app.rabbitmq.dlx-exchange:occupify.administration.dlx}")
  private String dlxExchangeName;

  @Value("${app.rabbitmq.dlq-queue:occupify.administration.user.dlq}")
  private String dlqQueueName;

  @Value("${app.rabbitmq.dlq-routing-key:user.dlq}")
  private String dlqRoutingKey;

  @Value("${app.rabbitmq.contract-exchange:occupify.contract.exchange}")
  private String contractExchangeName;

  @Value("${app.rabbitmq.contract-queue:occupify.administration.contract.queue}")
  private String contractQueueName;

  @Value("${app.rabbitmq.contract-accepted-routing-key:contract.accepted}")
  private String contractAcceptedRoutingKey;

  @Value("${app.rabbitmq.contract-rejected-routing-key:contract.rejected}")
  private String contractRejectedRoutingKey;

  @Value("${app.rabbitmq.contract-compensation-routing-key:contract.compensation.#}")
  private String contractCompensationRoutingKey;

  @Bean
  public TopicExchange contractExchange() {
    return new TopicExchange(contractExchangeName, true, false);
  }

  @Bean
  public Queue adminContractQueue() {
    Map<String, Object> args = new HashMap<>();
    args.put("x-dead-letter-exchange", dlxExchangeName);
    args.put("x-dead-letter-routing-key", dlqRoutingKey);
    return QueueBuilder.durable(contractQueueName).withArguments(args).build();
  }

  @Bean
  public Binding adminContractAcceptedBinding(Queue adminContractQueue, TopicExchange contractExchange) {
    return BindingBuilder.bind(adminContractQueue).to(contractExchange).with(contractAcceptedRoutingKey);
  }

  @Bean
  public Binding adminContractRejectedBinding(Queue adminContractQueue, TopicExchange contractExchange) {
    return BindingBuilder.bind(adminContractQueue).to(contractExchange).with(contractRejectedRoutingKey);
  }

  @Bean
  public Binding adminContractCompensationBinding(Queue adminContractQueue, TopicExchange contractExchange) {
    return BindingBuilder.bind(adminContractQueue).to(contractExchange).with(contractCompensationRoutingKey);
  }

  @Bean
  public TopicExchange userExchange() {
    return new TopicExchange(userExchangeName, true, false);
  }

  @Bean
  public DirectExchange adminDlxExchange() {
    return new DirectExchange(dlxExchangeName, true, false);
  }

  @Bean
  public Queue adminUserDlq() {
    return QueueBuilder.durable(dlqQueueName).build();
  }

  @Bean
  public Binding adminUserDlqBinding(Queue adminUserDlq, DirectExchange adminDlxExchange) {
    return BindingBuilder.bind(adminUserDlq).to(adminDlxExchange).with(dlqRoutingKey);
  }

  @Bean
  public Queue adminUserQueue() {
    Map<String, Object> args = new HashMap<>();
    args.put("x-dead-letter-exchange", dlxExchangeName);
    args.put("x-dead-letter-routing-key", dlqRoutingKey);
    return QueueBuilder.durable(userQueueName).withArguments(args).build();
  }

  @Bean
  public Binding adminUserBinding(Queue adminUserQueue, TopicExchange userExchange) {
    return BindingBuilder.bind(adminUserQueue)
        .to(userExchange)
        .with(userActivatedRoutingKey);
  }

  @Bean
  public MessageConverter jackson2JsonMessageConverter() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);

    DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
    typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
    typeMapper.setTrustedPackages("*");

    Map<String, Class<?>> idClassMapping = new HashMap<>();
    idClassMapping.put(
        "com.occupify.identity.event.UserActivatedEvent", UserActivatedEvent.class);
    idClassMapping.put(
        "com.occupify.payment.event.UserActivatedEvent", UserActivatedEvent.class);
    idClassMapping.put(
        "com.occupify.administration.event.UserActivatedEvent", UserActivatedEvent.class);
    idClassMapping.put(
        "com.occupify.notification.dto.event.UserActivatedEvent", UserActivatedEvent.class);
    idClassMapping.put("UserActivatedEvent", UserActivatedEvent.class);

    idClassMapping.put("com.occupify.core.event.ContractAcceptedEvent", ContractAcceptedEvent.class);
    idClassMapping.put("com.occupify.administration.event.ContractAcceptedEvent", ContractAcceptedEvent.class);
    idClassMapping.put("ContractAcceptedEvent", ContractAcceptedEvent.class);

    idClassMapping.put("com.occupify.core.event.ContractRejectedEvent", ContractRejectedEvent.class);
    idClassMapping.put("com.occupify.administration.event.ContractRejectedEvent", ContractRejectedEvent.class);
    idClassMapping.put("ContractRejectedEvent", ContractRejectedEvent.class);

    idClassMapping.put("com.occupify.core.event.ContractCompensationRequestedEvent", ContractCompensationRequestedEvent.class);
    idClassMapping.put("com.occupify.administration.event.ContractCompensationRequestedEvent", ContractCompensationRequestedEvent.class);
    idClassMapping.put("ContractCompensationRequestedEvent", ContractCompensationRequestedEvent.class);

    typeMapper.setIdClassMapping(idClassMapping);

    converter.setJavaTypeMapper(typeMapper);
    return converter;
  }

  @Bean
  public RabbitTemplate rabbitTemplate(
      ConnectionFactory connectionFactory, MessageConverter messageConverter) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(messageConverter);
    return rabbitTemplate;
  }
}

package com.github.ivanmikhalchanka.sample.kafkatestcontainersample.config;

import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public interface KafkaIntegrationTest {

  @Container
  KafkaContainer kafkaTestContainer =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.0.1"))
          .withEnv("KAFKA_AUTO_OFFSET_RESET", "earliest")
          .withEnv("KAFKA_MAX_POLL_RECORDS", "1");

  @BeforeAll
  static void initKafkaProperties() {
    System.setProperty("spring.kafka.consumer.bootstrap-servers", kafkaTestContainer.getBootstrapServers());
    System.setProperty("spring.kafka.producer.bootstrap-servers", kafkaTestContainer.getBootstrapServers());
  }
}

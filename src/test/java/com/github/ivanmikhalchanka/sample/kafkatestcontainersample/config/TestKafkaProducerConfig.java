package com.github.ivanmikhalchanka.sample.kafkatestcontainersample.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@TestConfiguration
public class TestKafkaProducerConfig {

  public static final String TEST_KAFKA_TEMPLATE = "testKafkaTemplate";

  @Bean
  public KafkaTemplate<String, Object> testKafkaTemplate(
      @Value("${spring.kafka.producer.bootstrap-servers}") String bootstrapServers,
      @Value("${spring.kafka.producer.key-serializer}") String keySerializer,
      @Value("${spring.kafka.producer.value-serializer}") String valueSerializer) {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer);
    props.put(ProducerConfig.CLIENT_ID_CONFIG, "functional-test-producer");

    DefaultKafkaProducerFactory<String, Object> factory =
        new DefaultKafkaProducerFactory<>(props);

    return new KafkaTemplate<>(factory);
  }
}

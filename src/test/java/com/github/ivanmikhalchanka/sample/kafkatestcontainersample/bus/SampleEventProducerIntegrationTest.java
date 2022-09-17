package com.github.ivanmikhalchanka.sample.kafkatestcontainersample.bus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.config.KafkaIntegrationTest;
import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.model.SampleEvent;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest
class SampleEventProducerIntegrationTest implements KafkaIntegrationTest {

  static KafkaConsumer<String, String> kafkaConsumer;

  @BeforeAll
  static void initKafkaListener() {
    Map<String, Object> consumerConfig =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaTestContainer.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "integration-test-consumer",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true",
            ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "10",
            ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "60000",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    kafkaConsumer = new KafkaConsumer<>(consumerConfig);
  }

  static final String EVENT_ID = "test-event-id";
  static final String MESSAGE = "test-message";
  static final String TOPIC = "sample-event-outbox";

  @Autowired
  KafkaTemplate<String, Object> kafkaTemplate;

  @Test
  void testSendMessage() throws JsonProcessingException {
    SampleEvent event = new SampleEvent(EVENT_ID, MESSAGE);

    kafkaTemplate.send(TOPIC, event.getEventId(), event);
    kafkaTemplate.flush();

    // subscribe to topic & poll events with timeout
    kafkaConsumer.subscribe(List.of(TOPIC));
    ConsumerRecords<String, String> records = kafkaConsumer.poll(
        Duration.of(3, ChronoUnit.SECONDS));
    // verify sent event presented
    assertEquals(1, records.count());
    ConsumerRecord<String, String> record = records.iterator().next();
    // verify event was sent with correct key
    assertEquals(EVENT_ID, record.key());
    // verify event body
    SampleEvent result = new ObjectMapper().readValue(record.value(), SampleEvent.class);
    assertEquals(MESSAGE, result.getMessage());
  }
}

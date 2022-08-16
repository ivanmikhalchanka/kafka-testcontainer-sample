package com.github.ivanmikhalchanka.sample.kafkatestcontainersample.bus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.config.TestKafkaConsumersSpiesBeanPostProcessor;
import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.config.TestKafkaProducerConfig;
import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.model.SampleEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@ContextConfiguration(classes = {
    TestKafkaConsumersSpiesBeanPostProcessor.class,
    TestKafkaProducerConfig.class})
class SampleEventConsumerIntegrationTest {

  @Container
  public static final KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.0.1"))
          .withEnv("KAFKA_AUTO_OFFSET_RESET", "earliest")
          .withEnv("KAFKA_MAX_POLL_RECORDS", "1");

  @BeforeAll
  public static void initKafkaProperties() {
    System.setProperty("spring.kafka.consumer.bootstrap-servers", kafka.getBootstrapServers());
    System.setProperty("spring.kafka.producer.bootstrap-servers", kafka.getBootstrapServers());
  }

  @Autowired
  @Qualifier(TestKafkaProducerConfig.TEST_KAFKA_TEMPLATE)
  KafkaTemplate<String, Object> kafkaTemplate;

  @Autowired
  SampleEventConsumer consumer;

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Test
  void testSampleEventReceived() {
    SampleEvent event = new SampleEvent("id-1", "sample message");

    kafkaTemplate.send("sample-message", event);
    kafkaTemplate.flush();

    Mockito.verify(consumer, Mockito.timeout(3000).times(1)).process(event);
  }

  @Test
  void testSampleEventProcessed() throws InterruptedException {
    SampleEvent event = new SampleEvent("id-1", "sample message");

    kafkaTemplate.send("sample-message", event);
    kafkaTemplate.flush();

    CountDownLatch latch = new CountDownLatch(1);
    Mockito.doAnswer(invocation -> {
          Object result = invocation.callRealMethod();
          latch.countDown();
          return result;
        })
        .when(consumer).process(any());
    latch.await(3, TimeUnit.SECONDS);
    Mockito.verify(consumer, Mockito.timeout(3000).times(1)).process(event);
    Integer messagesAdded = jdbcTemplate.queryForObject("SELECT count(*) FROM sample_message",
        Integer.class);
    assertEquals(1, messagesAdded);
  }
}

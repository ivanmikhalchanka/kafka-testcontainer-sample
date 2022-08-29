package com.github.ivanmikhalchanka.sample.kafkatestcontainersample.bus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.config.KafkaIntegrationTest;
import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.model.SampleEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest
class SampleEventConsumerIntegrationTest implements KafkaIntegrationTest {

  @Autowired
  KafkaTemplate<String, Object> kafkaTemplate;

  @SpyBean
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

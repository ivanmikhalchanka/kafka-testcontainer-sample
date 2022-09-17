# Kafka TestContainer sample

Sample project illustrating testing
of [@KafkaListener](https://docs.spring.io/spring-kafka/docs/current/api/org/springframework/kafka/annotation/KafkaListener.html)
with [Kafka Testcontainer](https://www.testcontainers.org/modules/kafka/)
and [JUnit 5](https://junit.org/junit5/)

## Configuration step-by-step guide

1. #### Add maven dependencies

- [Kafka TestContainer](https://www.testcontainers.org/modules/kafka/)
- [JUnit 5 TestContainers](https://www.testcontainers.org/test_framework_integration/junit_5/)

```xml

<dependencies>
  ...
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
  </dependency>
  ...
</dependencies>
  ...
<dependencyManagement>
<dependencies>
  ...
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-bom</artifactId>
    <version>${testcontainers.version}</version>
    <type>pom</type>
    <scope>import</scope>
  </dependency>
  ...
</dependencies>
</dependencyManagement>
```

2. #### Configure Kafka TestContainer:

- Annotate config with
  [@Testcontainers](https://javadoc.io/doc/org.testcontainers/junit-jupiter/latest/org/testcontainers/junit/jupiter/Testcontainers.html)
  in order to enable tracking of lifecycle of all TestContainers annotated with
  [@Container](https://javadoc.io/doc/org.testcontainers/junit-jupiter/latest/org/testcontainers/junit/jupiter/Container.html)
- Init
  [KafkaContainer](https://www.javadoc.io/doc/org.testcontainers/kafka/latest/org/testcontainers/containers/KafkaContainer.html)
  field with required configs
- Init kafka connection properties with
  [@BeforeAll](https://junit.org/junit5/docs/5.0.0/api/org/junit/jupiter/api/BeforeAll.html)

```java
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
    System.setProperty("spring.kafka.consumer.bootstrap-servers",
        kafkaTestContainer.getBootstrapServers());
    System.setProperty("spring.kafka.producer.bootstrap-servers",
        kafkaTestContainer.getBootstrapServers());
  }
}
```

3. #### Consumer test class configuration:

- Add
  [@SpringBootTest](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/test/context/SpringBootTest.html)
- Implement interface with Kafka TestContainer
  config: [KafkaIntegrationTest](#configure-kafka-testcontainer)
- Autowire consumer bean using
  [@SpyBean](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/test/mock/mockito/SpyBean.html)
  in order to verify that message was actually consumed from Kafka

```java
import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.config.KafkaIntegrationTest;
import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.config.TestKafkaConsumersSpiesBeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
class SampleEventConsumerIntegrationTest implements KafkaIntegrationTest {

  @SpyBean
  SampleEventConsumer consumer;
  ...
}
```

4. #### Producer test class configuration:

- Init instance of
  [KafkaConsumer](https://kafka.apache.org/26/javadoc/index.html?org/apache/kafka/clients/consumer/KafkaConsumer.html)

```java
import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.config.KafkaIntegrationTest;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SampleEventProducerIntegrationTest implements KafkaIntegrationTest {

  static KafkaConsumer<String, String> kafkaConsumer;

  @BeforeAll
  static void initKafkaListener() {
    Map<String, Object> consumerConfig =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafkaTestContainer.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "integration-test-consumer",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true",
            ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "10",
            ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "60000",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    kafkaConsumer = new KafkaConsumer<>(consumerConfig);
  }
}
```

5. #### Serialization/deserialization config used in these examples:

```yaml
spring:
  kafka:
    consumer:
      ...
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.ByteArrayDeserializer
    producer:
      ...
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

Next additional config required for app
[ConcurrentKafkaListenerContainerFactory](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/config/ConcurrentKafkaListenerContainerFactory.html)
to successfully deserialize messages:

```java
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.support.converter.BytesJsonMessageConverter;

@Configuration
public class KafkaListenerBeanPostProcessor implements BeanPostProcessor {

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    if (bean instanceof ConcurrentKafkaListenerContainerFactory factory) {
      factory.setMessageConverter(new BytesJsonMessageConverter());
    }

    return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
  }
}
```

## Test examples:

### Consumer:

- Verify consumer received event:

```java
import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.config.KafkaIntegrationTest;
import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.model.SampleEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest
class SampleEventConsumerIntegrationTest implements KafkaIntegrationTest {

  @Autowired
  KafkaTemplate<String, Object> kafkaTemplate;

  @SpyBean
  SampleEventConsumer consumer;

  @Test
  void testSampleEventReceived() {
    SampleEvent event = new SampleEvent("id-1", "sample message");

    kafkaTemplate.send("sample-message", event);
    kafkaTemplate.flush();

    Mockito.verify(consumer, Mockito.timeout(3000).times(1)).process(event);
  }
}
```

- verify app state changes after event processed:

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.model.SampleEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.config.KafkaIntegrationTest;

@SpringBootTest
class SampleEventConsumerIntegrationTest implements KafkaIntegrationTest {

  @Autowired
  KafkaTemplate<String, Object> kafkaTemplate;

  @SpyBean
  SampleEventConsumer consumer;

  @Autowired
  JdbcTemplate jdbcTemplate;

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
```

Please note, that `Mockito.verify` will be triggered right after method called, so it may not be
fully processed yet.\
As a result, verifications of application state changes (e.g. db state) could fail since they simply
not executed yet.\
In order to fix this behaviour it is required to use some mechanisms like
[CountDownLatch](https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/CountDownLatch.html)
.

### Producer:

- Verify event sent:

```java
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.config.KafkaIntegrationTest;
import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.model.SampleEvent;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest
class SampleEventProducerIntegrationTest implements KafkaIntegrationTest {

  // initialization of static KafkaConsumer<String, String> kafkaConsumer;

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
```

`KafkaConsumer<String, String> kafkaConsumer` initialization details available described
in [Producer test class configuration](#producer-test-class-configuration) chapter. 

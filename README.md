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
  KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.0.1"))
          .withEnv("KAFKA_AUTO_OFFSET_RESET", "earliest")
          .withEnv("KAFKA_MAX_POLL_RECORDS", "1");

  @BeforeAll
  static void initKafkaProperties() {
    System.setProperty("spring.kafka.consumer.bootstrap-servers", kafka.getBootstrapServers());
    System.setProperty("spring.kafka.producer.bootstrap-servers", kafka.getBootstrapServers());
  }
}
```

3. #### Test class configuration:

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

4. #### Serialization/deserialization config used in these examples:

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

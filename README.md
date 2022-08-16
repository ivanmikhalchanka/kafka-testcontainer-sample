# Kafka TestContainer sample

Sample project illustrating testing
of [@KafkaListener](https://docs.spring.io/spring-kafka/docs/current/api/org/springframework/kafka/annotation/KafkaListener.html)
with [Kafka Testcontainer](https://www.testcontainers.org/modules/kafka/)
and [JUnit 5](https://junit.org/junit5/)

## Configuration step-by-step guide

1. #### Add [Kafka TestContainer](https://www.testcontainers.org/modules/kafka/) maven dependencies

<details>
  <summary>pom.xml changes example</summary>

```xml

<dependencies>
  ...
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <scope>test</scope>
  </dependency>
  ...
</dependencies>
  ...
<dependencyManagement>
...
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
...
</dependencyManagement>
```

</details>

2. #### Add [JUnit 5 TestContainers](https://www.testcontainers.org/test_framework_integration/junit_5/) maven dependency:

```xml

<dependencies>
  ...
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
  </dependency>
  ...
</dependencies>
```

3. #### Annotate test class with [@Testcontainers](https://javadoc.io/doc/org.testcontainers/junit-jupiter/latest/org/testcontainers/junit/jupiter/Testcontainers.html)

This would enable tracking of lifecycle of all TestContainers annotated
as [@Container](https://javadoc.io/doc/org.testcontainers/junit-jupiter/latest/org/testcontainers/junit/jupiter/Container.html)

```java

@Testcontainers
public abstract class SampleEventConsumerIntegrationTest {
  ...
}
```

4. #### Init [KafkaContainer](https://www.javadoc.io/doc/org.testcontainers/kafka/latest/org/testcontainers/containers/KafkaContainer.html) field with required configs:

```java

@Container
public static final KafkaContainer kafka =
    new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.0.1"))
        .withEnv("KAFKA_AUTO_OFFSET_RESET", "earliest")
        .withEnv("KAFKA_MAX_POLL_RECORDS", "1");
```

5. #### Init kafka connection properties

e.g. default spring:

```java

@BeforeAll
public static void initKafkaProperties(){
    System.setProperty("spring.kafka.consumer.bootstrap-servers",kafka.getBootstrapServers());
    System.setProperty("spring.kafka.producer.bootstrap-servers",kafka.getBootstrapServers());
}
```

6. #### Create test configuration for KafkaTemplate

[KafkaTemplate javadoc](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/core/KafkaTemplate.html)

Will be used to send messages to Kafka TestContainer.\
It is recommended to
use [@TestConfiguration](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/test/context/TestConfiguration.html)
so it applied for tests only:

```java

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
```

7. #### Spy on all beans with @KafkaListener annotation

That would allow to verify that consumer actually called, e.g.:

- add test
  [BeanPostProcessor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/config/BeanPostProcessor.html)
  implementation that wraps all beans with
  [Mockito.spy](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Spy.html)
  and annotate it with
  [@TestConfiguration](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/test/context/TestConfiguration.html)
  so it applied for tests only:

```java

@TestConfiguration
public static class TestKafkaConsumersSpiesBeanPostProcessor implements BeanPostProcessor {

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName)
      throws BeansException {
    boolean isKafkaConsumer =
        Stream.of(bean.getClass().getDeclaredMethods())
            .map(method -> method.getAnnotation(KafkaListener.class))
            .anyMatch(Objects::nonNull);
    if (isKafkaConsumer) {
      bean = Mockito.spy(bean);
    }

    return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
  }
}
```

8. #### Add required spring test annotations:

- [@SpringBootTest](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/test/context/SpringBootTest.html)
- [@ContextConfiguration](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/test/context/ContextConfiguration.html)
  with [TestKafkaProducerConfig](#create-test-configuration-for-kafkatemplate)
  and [TestKafkaConsumersSpiesBeanPostProcessor](#spy-on-all-beans-with-kafkalistener-annotation)

```java

@Testcontainers
@SpringBootTest
@ContextConfiguration(classes = {
    TestKafkaConsumersSpiesBeanPostProcessor.class,
    TestKafkaProducerConfig.class})
class SampleEventConsumerIntegrationTest {
   ...
}
```

9. #### Serialization/deserialization config used in these examples:

```yaml
spring:
  kafka:
    consumer:
      auto-offset-reset: earliest
      group-id: kafka-testcontainer-sample
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.ByteArrayDeserializer
      enable-auto-commit: false
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

Next additional config required for app
[ConcurrentKafkaListenerContainerFactory](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/config/ConcurrentKafkaListenerContainerFactory.html)
to successfully deserialize messages:

```java

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
...

class SampleEventConsumerIntegrationTest {
  ...
  @Autowired
  @Qualifier(TestKafkaProducerConfig.TEST_KAFKA_TEMPLATE)
  KafkaTemplate<String, Object> kafkaTemplate;

  @Autowired
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

- verify consumer received event and processing completed:

```java
...

class SampleEventConsumerIntegrationTest {
  ...
  @Autowired
  @Qualifier(TestKafkaProducerConfig.TEST_KAFKA_TEMPLATE)
  KafkaTemplate<String, Object> kafkaTemplate;

  @Autowired
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



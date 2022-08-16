package com.github.ivanmikhalchanka.sample.kafkatestcontainersample.config;

import java.util.Objects;
import java.util.stream.Stream;
import org.mockito.Mockito;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.kafka.annotation.KafkaListener;

@TestConfiguration
public class TestKafkaConsumersSpiesBeanPostProcessor implements BeanPostProcessor {

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

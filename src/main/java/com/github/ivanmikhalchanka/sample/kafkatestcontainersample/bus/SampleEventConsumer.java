package com.github.ivanmikhalchanka.sample.kafkatestcontainersample.bus;

import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.entity.Message;
import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.model.SampleEvent;
import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SampleEventConsumer {

  private final MessageRepository messageRepository;

  public SampleEventConsumer(
      MessageRepository messageRepository) {
    this.messageRepository = messageRepository;
  }

  @KafkaListener(topics = "sample-message")
  public void process(SampleEvent event) {
    log.info("SampleEvent: {}", event);

    Message message = new Message();
    BeanUtils.copyProperties(event, message);

    messageRepository.save(message);
  }
}

package com.github.ivanmikhalchanka.sample.kafkatestcontainersample.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class SampleEvent {

  private String eventId;
  private String message;
}

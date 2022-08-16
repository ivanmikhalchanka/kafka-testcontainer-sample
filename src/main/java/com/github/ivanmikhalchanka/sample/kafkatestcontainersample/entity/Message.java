package com.github.ivanmikhalchanka.sample.kafkatestcontainersample.entity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "sample_message")
@Getter
@Setter
@ToString
public class Message {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long id;
  private String eventId;
  private String message;
}

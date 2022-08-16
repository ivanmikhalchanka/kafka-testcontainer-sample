package com.github.ivanmikhalchanka.sample.kafkatestcontainersample.repository;

import com.github.ivanmikhalchanka.sample.kafkatestcontainersample.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

}

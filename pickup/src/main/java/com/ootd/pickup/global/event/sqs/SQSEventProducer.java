package com.ootd.pickup.global.event.sqs;

import com.ootd.pickup.global.event.EventProducer;
import com.ootd.pickup.global.event.MessageQueueEvent;
import org.springframework.stereotype.Component;

@Component
public class SQSEventProducer implements EventProducer {
  @Override
  public void produce(MessageQueueEvent event) {}
}

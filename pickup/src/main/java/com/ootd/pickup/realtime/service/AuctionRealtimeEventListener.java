package com.ootd.pickup.realtime.service;

import com.ootd.pickup.auction.event.AuctionEndedEvent;
import com.ootd.pickup.auction.event.AuctionExtendedEvent;
import com.ootd.pickup.auction.event.AuctionStartedEvent;
import com.ootd.pickup.bid.event.BidPlacedEvent;
import com.ootd.pickup.realtime.dto.AuctionEndedMessage;
import com.ootd.pickup.realtime.dto.AuctionExtendedMessage;
import com.ootd.pickup.realtime.dto.AuctionRealtimeMessageType;
import com.ootd.pickup.realtime.dto.AuctionStartedMessage;
import com.ootd.pickup.realtime.dto.BidPlacedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionRealtimeEventListener {

  private static final String AUCTION_TOPIC_PREFIX = "/topic/auctions/";

  private final SimpMessagingTemplate messagingTemplate;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(AuctionStartedEvent event) {
    AuctionRealtimeMessageType type = AuctionRealtimeMessageType.AUCTION_STARTED;
    send(
        event.auctionId(),
        type,
        new AuctionStartedMessage(
            event.eventId(),
            type,
            event.auctionId(),
            event.occurredAt(),
            event.startedAt(),
            event.endedAt()));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(BidPlacedEvent event) {
    AuctionRealtimeMessageType type = AuctionRealtimeMessageType.BID_PLACED;
    send(
        event.auctionId(),
        type,
        new BidPlacedMessage(
            event.eventId(),
            type,
            event.auctionId(),
            event.occurredAt(),
            event.bidId(),
            event.nicknameMasked(),
            event.bidPrice(),
            event.createdAt()));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(AuctionExtendedEvent event) {
    AuctionRealtimeMessageType type = AuctionRealtimeMessageType.AUCTION_EXTENDED;
    send(
        event.auctionId(),
        type,
        new AuctionExtendedMessage(
            event.eventId(),
            type,
            event.auctionId(),
            event.occurredAt(),
            event.previousEndedAt(),
            event.endedAt()));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(AuctionEndedEvent event) {
    AuctionRealtimeMessageType type = AuctionRealtimeMessageType.AUCTION_ENDED;
    send(
        event.auctionId(),
        type,
        new AuctionEndedMessage(
            event.eventId(),
            type,
            event.auctionId(),
            event.occurredAt(),
            event.status().name(),
            event.finalPrice(),
            event.endedAt()));
  }

  private void send(Long auctionId, AuctionRealtimeMessageType eventType, Object message) {
    try {
      messagingTemplate.convertAndSend(AUCTION_TOPIC_PREFIX + auctionId, message);
    } catch (RuntimeException e) {
      log.warn("실시간 경매 이벤트 전송에 실패했습니다. auctionId={}, eventType={}", auctionId, eventType, e);
    }
  }
}

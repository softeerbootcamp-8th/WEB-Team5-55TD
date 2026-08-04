package com.ootd.pickup.realtime.service;

import com.ootd.pickup.auction.event.AuctionEndedEvent;
import com.ootd.pickup.auction.event.AuctionExtendedEvent;
import com.ootd.pickup.auction.event.AuctionStartedEvent;
import com.ootd.pickup.bid.event.BidPlacedEvent;
import com.ootd.pickup.realtime.dto.AuctionEndedMessage;
import com.ootd.pickup.realtime.dto.AuctionExtendedMessage;
import com.ootd.pickup.realtime.dto.AuctionStartedMessage;
import com.ootd.pickup.realtime.dto.BidPlacedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
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
    send(
        event.auctionId(),
        "AUCTION_STARTED",
        new AuctionStartedMessage(
            event.eventId(),
            "AUCTION_STARTED",
            event.auctionId(),
            event.occurredAt(),
            event.startedAt(),
            event.endedAt()));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(BidPlacedEvent event) {
    send(
        event.auctionId(),
        "BID_PLACED",
        new BidPlacedMessage(
            event.eventId(),
            "BID_PLACED",
            event.auctionId(),
            event.occurredAt(),
            event.bidId(),
            event.nicknameMasked(),
            event.bidPrice(),
            event.createdAt()));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(AuctionExtendedEvent event) {
    send(
        event.auctionId(),
        "AUCTION_EXTENDED",
        new AuctionExtendedMessage(
            event.eventId(),
            "AUCTION_EXTENDED",
            event.auctionId(),
            event.occurredAt(),
            event.previousEndedAt(),
            event.endedAt()));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(AuctionEndedEvent event) {
    send(
        event.auctionId(),
        "AUCTION_ENDED",
        new AuctionEndedMessage(
            event.eventId(),
            "AUCTION_ENDED",
            event.auctionId(),
            event.occurredAt(),
            event.status().name(),
            event.finalPrice(),
            event.endedAt()));
  }

  private void send(Long auctionId, String eventType, Object message) {
    try {
      messagingTemplate.convertAndSend(AUCTION_TOPIC_PREFIX + auctionId, message);
    } catch (MessagingException e) {
      log.warn("실시간 경매 이벤트 전송에 실패했습니다. auctionId={}, eventType={}", auctionId, eventType, e);
    }
  }
}

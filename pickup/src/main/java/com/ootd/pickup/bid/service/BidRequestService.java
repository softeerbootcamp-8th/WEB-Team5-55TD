package com.ootd.pickup.bid.service;

import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_NOT_FOUND;
import static com.ootd.pickup.global.exception.ExceptionCode.BID_REQUEST_NOT_FOUND;

import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.bid.domain.BidRequest;
import com.ootd.pickup.bid.dto.response.BidRequestResultResponse;
import com.ootd.pickup.bid.dto.response.CreateBidRequestResponse;
import com.ootd.pickup.bid.event.BidRequestCreatedMessageQueueEvent;
import com.ootd.pickup.bid.repository.BidRequestRepository;
import com.ootd.pickup.global.event.EventProducer;
import com.ootd.pickup.global.exception.PickUpException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입찰 요청 접수. 가벼운 검증만 하고 실제 입찰 처리(락·업무 규칙 검증)는 SQS로 넘겨 비동기로 수행한다.
 *
 * <p>경매 상태·최소 증가폭·판매자 본인 여부·포인트 한도 같은 검증은 여기서 하지 않는다 — 바로 이 검증들이 {@code Auction} 행 락을 두고 경합을 일으키는
 * 부분이라, 동시 요청이 몰릴 때 이 API가 대기하지 않도록 전부 비동기 처리기({@link BidRequestProcessingService})로 미룬다.
 */
@Service
@RequiredArgsConstructor
public class BidRequestService {

  private final AuctionRepository auctionRepository;
  private final BidRequestRepository bidRequestRepository;
  private final EventProducer eventProducer;

  @Transactional
  public CreateBidRequestResponse createBidRequest(Long auctionId, Long memberId, Long bidPrice) {
    auctionRepository.findById(auctionId).orElseThrow(() -> new PickUpException(AUCTION_NOT_FOUND));

    BidRequest bidRequest =
        bidRequestRepository.save(BidRequest.create(auctionId, memberId, bidPrice));
    eventProducer.produce(BidRequestCreatedMessageQueueEvent.fromEntity(bidRequest));

    return CreateBidRequestResponse.from(bidRequest);
  }

  @Transactional(readOnly = true)
  public BidRequestResultResponse getBidRequestResult(
      Long auctionId, Long bidRequestId, Long memberId) {
    BidRequest bidRequest =
        bidRequestRepository
            .findById(bidRequestId)
            .filter(
                request ->
                    request.getAuctionId().equals(auctionId)
                        && request.getMemberId().equals(memberId))
            .orElseThrow(() -> new PickUpException(BID_REQUEST_NOT_FOUND));
    return BidRequestResultResponse.from(bidRequest);
  }
}

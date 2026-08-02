package com.ootd.pickup.auction.controller;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.repository.auction.AuctionJpaRepository;
import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.cards.repository.CardJpaRepository;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentJpaRepository;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberJpaRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;

abstract class SellerAuctionFixtureSupport {

  @Autowired private MemberJpaRepository memberJpaRepository;
  @Autowired private CardJpaRepository cardJpaRepository;
  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;
  @Autowired private AuctionJpaRepository auctionJpaRepository;

  protected Member createMember(String nickname) {
    Member member = Member.create("login-" + nickname + System.nanoTime(), "password", nickname);
    return memberJpaRepository.save(member);
  }

  protected Consignment createConsignment(Member seller, ConsignmentStatus status) {
    Card card =
        Card.builder()
            .cardName("리자몽")
            .cardNumber("4/102")
            .setName("Base Set")
            .language(Language.JAPANESE)
            .rarity(Rarity.MINT)
            .imageUrl("https://image.example.com/card.png")
            .build();
    cardJpaRepository.save(card);

    Consignment consignment =
        Consignment.builder().card(card).sellerMember(seller).status(status).build();
    return consignmentJpaRepository.save(consignment);
  }

  protected Auction createAuction(
      Consignment consignment, AuctionStatus status, Long startingPrice, LocalDateTime endedAt) {
    Auction auction =
        Auction.builder()
            .consignment(consignment)
            .startedAt(LocalDateTime.now().minusDays(1))
            .endedAt(endedAt)
            .auctionStatus(status)
            .startingPrice(startingPrice)
            .reservePrice(startingPrice)
            .bidIncrement(Math.round(startingPrice * 0.05))
            .build();
    return auctionJpaRepository.save(auction);
  }
}

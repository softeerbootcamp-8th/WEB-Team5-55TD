package com.ootd.pickup.cards.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@SQLDelete(sql = "UPDATE card SET is_deleted = true WHERE card_id = ?")
@SQLRestriction("is_deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Card {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "card_id", nullable = false)
  private Long cardId;

  @Column(name = "tcgdex_id", unique = true)
  private String tcgdexId;

  @Column(name = "tcgdex_set_id")
  private String tcgdexSetId;

  @Column(name = "is_deleted", nullable = false)
  private boolean deleted;

  @Column(name = "card_name", nullable = false)
  private String cardName;

  @Column(name = "card_number", nullable = false)
  private String cardNumber;

  @Column(name = "set_name", nullable = false)
  private String setName;

  @Enumerated(EnumType.STRING)
  @Column(name = "language", nullable = false)
  private Language language;

  @Column(name = "rarity", nullable = false)
  @Enumerated(EnumType.STRING)
  private Rarity rarity;

  @Column(name = "image_url", nullable = false)
  private String imageUrl;

  @Builder
  public Card(
      String cardName,
      String cardNumber,
      String setName,
      Language language,
      Rarity rarity,
      String imageUrl) {
    this.cardName = cardName;
    this.cardNumber = cardNumber;
    this.setName = setName;
    this.language = language;
    this.rarity = rarity;
    this.imageUrl = imageUrl;
  }

  public static Card fromTcgdex(
      String tcgdexId,
      String tcgdexSetId,
      String cardName,
      String cardNumber,
      String setName,
      String imageUrl) {
    Card card =
        Card.builder()
            .cardName(cardName)
            .cardNumber(cardNumber)
            .setName(setName)
            .language(Language.ENGLISH)
            .rarity(Rarity.MINT)
            .imageUrl(imageUrl)
            .build();
    card.tcgdexId = tcgdexId;
    card.tcgdexSetId = tcgdexSetId;
    return card;
  }

  public void updateFromTcgdex(
      String cardName, String cardNumber, String setName, String imageUrl) {
    this.cardName = cardName;
    this.cardNumber = cardNumber;
    this.setName = setName;
    this.imageUrl = imageUrl;
  }
}

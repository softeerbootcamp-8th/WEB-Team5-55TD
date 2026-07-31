package com.ootd.pickup.cards.controller;

import static com.ootd.pickup.global.exception.ExceptionCode.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.cards.repository.CardJpaRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CardSearchIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private CardJpaRepository cardJpaRepository;

  @Test
  void 검색조건과_커서로_카드를_조회하면_ID_내림차순으로_페이지를_반환한다() throws Exception {
    // given
    Card oldMatchedCard = createCard("리자몽 V", "001/100", "Base Set", Language.KOREAN);
    Card differentLanguageCard = createCard("리자몽 EX", "002/100", "Base Set", Language.JAPANESE);
    Card differentSetCard = createCard("리자몽 GX", "003/100", "Jungle", Language.KOREAN);
    Card differentKeywordCard = createCard("피카츄", "025/102", "Base Set", Language.KOREAN);
    Card newMatchedCard = createCard("리자몽 1st Edition Holo", "4/102", "Base Set", Language.KOREAN);
    cardJpaRepository.saveAll(
        List.of(
            oldMatchedCard,
            differentLanguageCard,
            differentSetCard,
            differentKeywordCard,
            newMatchedCard));
    cardJpaRepository.flush();

    // when & then
    mockMvc
        .perform(
            get("/cards")
                .param("keyword", "리자몽")
                .param("setName", "Base Set")
                .param("language", "한국어")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.cursor").value(newMatchedCard.getCardId()))
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.items[0].cardId").value(newMatchedCard.getCardId()))
        .andExpect(jsonPath("$.items[0].cardName").value("리자몽 1st Edition Holo"))
        .andExpect(jsonPath("$.items[0].setName").value("Base Set"))
        .andExpect(jsonPath("$.items[0].cardNumber").value("4/102"))
        .andExpect(jsonPath("$.items[0].language").value("한국어"))
        .andExpect(jsonPath("$.items[0].rarity").value("MINT"));

    mockMvc
        .perform(
            get("/cards")
                .param("keyword", "리자몽")
                .param("setName", "Base Set")
                .param("language", "한국어")
                .param("cursor", String.valueOf(newMatchedCard.getCardId()))
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.cursor").doesNotExist())
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.items[0].cardId").value(oldMatchedCard.getCardId()))
        .andExpect(jsonPath("$.items[0].cardName").value("리자몽 V"));
  }

  @Test
  void 검색조건을_생략하면_전체_카드를_조회한다() throws Exception {
    // given
    Card oldCard = createCard("피카츄", "025/102", "Base Set", Language.KOREAN);
    Card newCard = createCard("리자몽", "4/102", "Base Set", Language.JAPANESE);
    cardJpaRepository.saveAll(List.of(oldCard, newCard));
    cardJpaRepository.flush();

    // when & then
    mockMvc
        .perform(get("/cards").param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.cursor").doesNotExist())
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.items[0].cardId").value(newCard.getCardId()))
        .andExpect(jsonPath("$.items[1].cardId").value(oldCard.getCardId()));
  }

  @Test
  void 검색어와_세트명은_대소문자를_구분하지_않는다() throws Exception {
    // given
    Card card = createCard("Charizard 1st Edition", "4/102", "Base Set", Language.ENGLISH);
    cardJpaRepository.save(card);
    cardJpaRepository.flush();

    // when & then
    mockMvc
        .perform(
            get("/cards")
                .param("keyword", "charizard")
                .param("setName", "base set")
                .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].cardId").value(card.getCardId()));
  }

  @Test
  void 지원하지_않는_언어로_검색하면_400을_반환한다() throws Exception {
    // when & then
    mockMvc
        .perform(get("/cards").param("language", "중국어").param("size", "20"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(ILLEGAL_ARGUMENT.getMessage()));
  }

  private Card createCard(String cardName, String cardNumber, String setName, Language language) {
    return Card.builder()
        .cardName(cardName)
        .cardNumber(cardNumber)
        .setName(setName)
        .language(language)
        .rarity(Rarity.MINT)
        .imageUrl("https://image.example.com/" + cardNumber + ".png")
        .build();
  }
}

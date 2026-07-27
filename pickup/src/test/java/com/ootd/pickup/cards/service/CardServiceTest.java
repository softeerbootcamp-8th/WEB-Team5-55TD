package com.ootd.pickup.cards.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.List;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.cards.dto.request.SearchCardsRequest;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.cards.dto.response.SearchCardsResponse;
import com.ootd.pickup.cards.repository.CardRepository;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.exception.PickUpException;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    private CardService cardService;

    @BeforeEach
    void setUp() {
        cardService = new CardService(cardRepository);
    }

    @Test
    void 존재하는_카드ID로_조회하면_카드_상세정보를_반환한다() {
        // given
        Long cardId = 1L;
        Card card = Card.builder()
                .cardName("피카츄")
                .setName("베이스셋")
                .language(Language.KOREAN)
                .rarity(Rarity.MINT)
                .imageUrl("https://image.example.com/1.png")
                .build();
        given(cardRepository.findCardById(cardId)).willReturn(Optional.of(card));

        // when
        GetCardDetailResponse response = cardService.getCardDetail(cardId);

        // then
        assertThat(response.cardName()).isEqualTo("피카츄");
        assertThat(response.setName()).isEqualTo("베이스셋");
        assertThat(response.language()).isEqualTo(Language.KOREAN.getDisplayName());
        assertThat(response.rarity()).isEqualTo("MINT");
        assertThat(response.imageUrl()).isEqualTo("https://image.example.com/1.png");
    }

    @Test
    void 존재하지_않는_카드ID로_조회하면_예외가_발생한다() {
        // given
        Long notExistCardId = 999L;
        given(cardRepository.findCardById(notExistCardId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> cardService.getCardDetail(notExistCardId))
                .isInstanceOf(PickUpException.class);
    }

    @Test
    void 검색결과가_요청크기보다_많으면_다음페이지_커서를_반환한다() {
        // given
        SearchCardsRequest request = new SearchCardsRequest(
                "리자몽", "Base Set", "한국어", 10L, 2
        );
        Card firstCard = createCard(9L, "리자몽 V", "001/100");
        Card secondCard = createCard(8L, "리자몽 EX", "002/100");
        Card nextCard = createCard(7L, "리자몽 GX", "003/100");
        given(cardRepository.searchCards(
                "리자몽", "Base Set", Language.KOREAN, 10L, 3
        )).willReturn(List.of(firstCard, secondCard, nextCard));

        // when
        CursorPageResponse<SearchCardsResponse, Long> response = cardService.searchCards(request);

        // then
        assertThat(response.hasNext()).isTrue();
        assertThat(response.cursor()).isEqualTo(8L);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.items())
                .extracting(SearchCardsResponse::cardId)
                .containsExactly(9L, 8L);
        then(cardRepository).should().searchCards(
                "리자몽", "Base Set", Language.KOREAN, 10L, 3
        );
    }

    @Test
    void 검색결과가_요청크기_이하면_다음페이지_커서를_반환하지_않는다() {
        // given
        SearchCardsRequest request = new SearchCardsRequest(null, null, null, null, 2);
        Card firstCard = createCard(2L, "피카츄", "025/102");
        Card secondCard = createCard(1L, "라이츄", "026/102");
        given(cardRepository.searchCards(null, null, null, null, 3))
                .willReturn(List.of(firstCard, secondCard));

        // when
        CursorPageResponse<SearchCardsResponse, Long> response = cardService.searchCards(request);

        // then
        assertThat(response.hasNext()).isFalse();
        assertThat(response.cursor()).isNull();
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.items())
                .extracting(SearchCardsResponse::cardName)
                .containsExactly("피카츄", "라이츄");
    }

    @Test
    void 크기가_1보다_작으면_예외가_발생한다() {
        // given
        SearchCardsRequest request = new SearchCardsRequest(null, null, null, null, 0);

        // when & then
        assertThatThrownBy(() -> cardService.searchCards(request))
                .isInstanceOf(PickUpException.class);
        then(cardRepository).shouldHaveNoInteractions();
    }

    @Test
    void 지원하지_않는_언어면_예외가_발생한다() {
        // given
        SearchCardsRequest request = new SearchCardsRequest(null, null, "중국어", null, 20);

        // when & then
        assertThatThrownBy(() -> cardService.searchCards(request))
                .isInstanceOf(PickUpException.class);
        then(cardRepository).shouldHaveNoInteractions();
    }

    private Card createCard(Long cardId, String cardName, String cardNumber) {
        Card card = Card.builder()
                .cardName(cardName)
                .cardNumber(cardNumber)
                .setName("Base Set")
                .language(Language.KOREAN)
                .rarity(Rarity.MINT)
                .imageUrl("https://image.example.com/" + cardId + ".png")
                .build();
        ReflectionTestUtils.setField(card, "cardId", cardId);
        return card;
    }
}

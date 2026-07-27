package com.ootd.pickup.cards.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.cards.repository.CardRepository;
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
        assertThat(response.language()).isEqualTo(Language.KOREAN);
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
}

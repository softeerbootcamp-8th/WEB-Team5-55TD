package com.ootd.pickup.cards.controller;

import static com.ootd.pickup.global.exception.ExceptionCode.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.cards.service.CardService;
import com.ootd.pickup.global.exception.PickUpException;

@WebMvcTest(CardController.class)
class CardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CardService cardService;

    @Test
    void 존재하는_카드ID로_조회하면_카드_상세정보를_반환한다() throws Exception {
        // given
        Long cardId = 1L;
        GetCardDetailResponse response = new GetCardDetailResponse(
                cardId, "피카츄", "베이스셋", "025/102", Language.KOREAN, "MINT", "https://image.example.com/1.png"
        );
        given(cardService.getCardDetail(cardId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/cards/{cardId}", cardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value(cardId))
                .andExpect(jsonPath("$.cardName").value("피카츄"))
                .andExpect(jsonPath("$.setName").value("베이스셋"))
                .andExpect(jsonPath("$.cardNumber").value("025/102"))
                .andExpect(jsonPath("$.language").value("KOREAN"))
                .andExpect(jsonPath("$.rarity").value("MINT"))
                .andExpect(jsonPath("$.imageUrl").value("https://image.example.com/1.png"));
    }

    @Test
    void 존재하지_않는_카드ID로_조회하면_404를_반환한다() throws Exception {
        // given
        Long notExistCardId = 999L;
        given(cardService.getCardDetail(notExistCardId))
                .willThrow(new PickUpException(CARD_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/cards/{cardId}", notExistCardId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(CARD_NOT_FOUND.getMessage()));
    }
}

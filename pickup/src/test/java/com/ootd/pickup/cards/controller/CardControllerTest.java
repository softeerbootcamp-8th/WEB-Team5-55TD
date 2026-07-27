package com.ootd.pickup.cards.controller;

import static com.ootd.pickup.global.exception.ExceptionCode.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ootd.pickup.cards.dto.request.SearchCardsRequest;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.cards.dto.response.SearchCardsResponse;
import com.ootd.pickup.cards.service.CardService;
import com.ootd.pickup.global.dto.response.CursorPageResponse;

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
                cardId, "피카츄", "베이스셋", "025/102", "한국어", "MINT", "https://image.example.com/1.png"
        );
        given(cardService.getCardDetail(cardId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/cards/{cardId}", cardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value(cardId))
                .andExpect(jsonPath("$.cardName").value("피카츄"))
                .andExpect(jsonPath("$.setName").value("베이스셋"))
                .andExpect(jsonPath("$.cardNumber").value("025/102"))
                .andExpect(jsonPath("$.language").value("한국어"))
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

    @Test
    void 검색조건으로_카드를_조회하면_커서페이지를_반환한다() throws Exception {
        // given
        SearchCardsResponse item = new SearchCardsResponse(
                9L,
                "리자몽 1st Edition Holo",
                "Base Set",
                "4/102",
                "한국어",
                "MINT",
                "https://image.example.com/9.png"
        );
        CursorPageResponse<SearchCardsResponse, Long> response =
                new CursorPageResponse<>(true, 9L, 1, List.of(item));
        given(cardService.searchCards(any(SearchCardsRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(get("/cards")
                        .param("keyword", "리자몽")
                        .param("setName", "Base Set")
                        .param("language", "한국어")
                        .param("cursor", "10")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.cursor").value(9L))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.items[0].cardId").value(9L))
                .andExpect(jsonPath("$.items[0].cardName").value("리자몽 1st Edition Holo"))
                .andExpect(jsonPath("$.items[0].setName").value("Base Set"))
                .andExpect(jsonPath("$.items[0].cardNumber").value("4/102"))
                .andExpect(jsonPath("$.items[0].language").value("한국어"))
                .andExpect(jsonPath("$.items[0].rarity").value("MINT"))
                .andExpect(jsonPath("$.items[0].imageUrl").value("https://image.example.com/9.png"));

        then(cardService).should().searchCards(argThat(request ->
                request.keyword().equals("리자몽")
                        && request.setName().equals("Base Set")
                        && request.language().equals("한국어")
                        && request.cursor().equals(10L)
                        && request.size().equals(1)
        ));
    }

    @Test
    void 크기를_생략하고_카드를_검색하면_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/cards")
                        .param("keyword", "리자몽"))
                .andExpect(status().isBadRequest());

        then(cardService).shouldHaveNoInteractions();
    }
}

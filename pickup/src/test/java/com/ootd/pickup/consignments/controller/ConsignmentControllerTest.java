package com.ootd.pickup.consignments.controller;

import com.ootd.pickup.cards.dto.response.SearchCardsResponse;
import com.ootd.pickup.consignments.domain.CertificationBody;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.dto.request.CertificateRequest;
import com.ootd.pickup.consignments.dto.request.ConsignmentImageRequest;
import com.ootd.pickup.consignments.dto.request.RegisterConsignmentRequest;
import com.ootd.pickup.consignments.dto.response.CertificateResponse;
import com.ootd.pickup.consignments.dto.response.RegisterConsignmentResponse;
import com.ootd.pickup.consignments.service.ConsignmentService;
import com.ootd.pickup.global.exception.PickUpException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;

import static com.ootd.pickup.global.exception.ExceptionCode.CARD_NOT_FOUND;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsignmentController.class)
class ConsignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ConsignmentService consignmentService;

    @Test
    void 유효한_요청으로_상품을_등록하면_201과_상품_상세정보를_반환한다() throws Exception {
        // given
        RegisterConsignmentRequest request = createRequest();
        RegisterConsignmentResponse response = new RegisterConsignmentResponse(
                100L,
                new SearchCardsResponse(
                        10L,
                        "리자몽 1st Edition Holo",
                        "Base Set",
                        "4/102",
                        "일본어",
                        "홀로 레어",
                        "https://image.example.com/card.png"
                ),
                1L,
                "모서리에 약간의 마모",
                ConsignmentStatus.REGISTERABLE,
                new CertificateResponse(
                        200L, "PSA-84213907", CertificationBody.PSA, "10", "GEM_MINT", LocalDate.of(2026, 6, 30)
                )
        );
        given(consignmentService.registerConsignment(eq(1L), any(RegisterConsignmentRequest.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(post("/consignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consignmentId").value(100L))
                .andExpect(jsonPath("$.sellerMemberId").value(1L))
                .andExpect(jsonPath("$.majorDefect").value("모서리에 약간의 마모"))
                .andExpect(jsonPath("$.status").value("REGISTERABLE"))
                .andExpect(jsonPath("$.card.cardId").value(10L))
                .andExpect(jsonPath("$.certificate.certificateId").value(200L))
                .andExpect(jsonPath("$.certificate.certificationBody").value("PSA"))
                .andExpect(jsonPath("$.certificate.grade").value("10"))
                .andExpect(jsonPath("$.certificate.gradeCode").value("GEM_MINT"));
    }

    @Test
    void 카드ID가_없으면_400을_반환한다() throws Exception {
        // given
        RegisterConsignmentRequest request = new RegisterConsignmentRequest(
                null,
                1L,
                null,
                new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
                List.of(
                        new ConsignmentImageRequest("https://image.example.com/front.png"),
                        new ConsignmentImageRequest("https://image.example.com/back.png")
                )
        );

        // when & then
        mockMvc.perform(post("/consignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        then(consignmentService).shouldHaveNoInteractions();
    }

    @Test
    void 이미지가_2장_미만이면_400을_반환한다() throws Exception {
        // given
        RegisterConsignmentRequest request = new RegisterConsignmentRequest(
                10L,
                1L,
                null,
                new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
                List.of(new ConsignmentImageRequest("https://image.example.com/front.png"))
        );

        // when & then
        mockMvc.perform(post("/consignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        then(consignmentService).shouldHaveNoInteractions();
    }

    @Test
    void 존재하지_않는_카드ID로_등록하면_404를_반환한다() throws Exception {
        // given
        RegisterConsignmentRequest request = createRequest();
        given(consignmentService.registerConsignment(eq(1L), any(RegisterConsignmentRequest.class)))
                .willThrow(new PickUpException(CARD_NOT_FOUND));

        // when & then
        mockMvc.perform(post("/consignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(CARD_NOT_FOUND.getMessage()));
    }

    private RegisterConsignmentRequest createRequest() {
        return new RegisterConsignmentRequest(
                10L,
                1L,
                "모서리에 약간의 마모",
                new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
                List.of(
                        new ConsignmentImageRequest("https://image.example.com/front.png"),
                        new ConsignmentImageRequest("https://image.example.com/back.png")
                )
        );
    }
}

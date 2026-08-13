package com.ootd.pickup.consignments.controller;

import static com.ootd.pickup.global.exception.ExceptionCode.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.cards.dto.response.SearchCardsResponse;
import com.ootd.pickup.consignments.domain.CardState;
import com.ootd.pickup.consignments.domain.CertificationBody;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.dto.request.CertificateRequest;
import com.ootd.pickup.consignments.dto.request.ConsignmentImageRequest;
import com.ootd.pickup.consignments.dto.request.GetMyConsignmentsRequest;
import com.ootd.pickup.consignments.dto.request.ModifyConsignmentRequest;
import com.ootd.pickup.consignments.dto.request.RegisterConsignmentRequest;
import com.ootd.pickup.consignments.dto.response.CertificateResponse;
import com.ootd.pickup.consignments.dto.response.ConsignmentImageResponse;
import com.ootd.pickup.consignments.dto.response.GetConsignmentDetailResponse;
import com.ootd.pickup.consignments.dto.response.GetMyConsignmentsResponse;
import com.ootd.pickup.consignments.dto.response.RegisterConsignmentResponse;
import com.ootd.pickup.consignments.service.ConsignmentApplicationService;
import com.ootd.pickup.consignments.service.ConsignmentService;
import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.global.slack.SlackErrorNotifier;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(ConsignmentController.class)
class ConsignmentControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private ConsignmentService consignmentService;

  @MockitoBean private ConsignmentApplicationService consignmentApplicationService;

  @MockitoBean private SlackErrorNotifier slackErrorNotifier;

  @Test
  void 유효한_요청으로_상품을_등록하면_201과_상품_상세정보를_반환한다() throws Exception {
    // given
    RegisterConsignmentRequest request = createRequest();
    RegisterConsignmentResponse response =
        new RegisterConsignmentResponse(
            100L,
            new SearchCardsResponse(
                10L,
                "리자몽 1st Edition Holo",
                "Base Set",
                "4/102",
                "일본어",
                "홀로 레어",
                "https://image.example.com/card.png"),
            1L,
            CardState.HIGH,
            "모서리에 약간의 마모",
            ConsignmentStatus.REGISTERABLE,
            new CertificateResponse(
                200L,
                "PSA-84213907",
                CertificationBody.PSA,
                "10",
                "GEM_MINT",
                LocalDate.of(2026, 6, 30)));
    given(
            consignmentApplicationService.registerConsignment(
                eq(1L), any(RegisterConsignmentRequest.class)))
        .willReturn(response);

    // when & then
    mockMvc
        .perform(
            post("/consignments")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.consignmentId").value(100L))
        .andExpect(jsonPath("$.sellerMemberId").value(1L))
        .andExpect(jsonPath("$.cardState").value("HIGH"))
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
    RegisterConsignmentRequest request =
        new RegisterConsignmentRequest(
            null,
            CardState.HIGH,
            null,
            new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")));

    // when & then
    mockMvc
        .perform(
            post("/consignments")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());

    then(consignmentService).shouldHaveNoInteractions();
  }

  @Test
  void 카드상태가_없으면_400을_반환한다() throws Exception {
    RegisterConsignmentRequest request =
        new RegisterConsignmentRequest(
            10L,
            null,
            null,
            new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")));

    mockMvc
        .perform(
            post("/consignments")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());

    then(consignmentApplicationService).shouldHaveNoInteractions();
  }

  @Test
  void 감정일이_현재보다_이후면_상품_등록은_400을_반환한다() throws Exception {
    // given
    RegisterConsignmentRequest request =
        new RegisterConsignmentRequest(
            10L,
            CardState.HIGH,
            null,
            new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.now().plusDays(1)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")));

    // when & then
    mockMvc
        .perform(
            post("/consignments")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());

    then(consignmentApplicationService).shouldHaveNoInteractions();
  }

  @Test
  void 정의되지_않은_카드상태이면_400을_반환한다() throws Exception {
    String request =
        objectMapper.writeValueAsString(createRequest()).replace("\"HIGH\"", "\"INVALID\"");

    mockMvc
        .perform(
            post("/consignments")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isBadRequest());

    then(consignmentApplicationService).shouldHaveNoInteractions();
  }

  @Test
  void 이미지가_2장_미만이면_400을_반환한다() throws Exception {
    // given
    RegisterConsignmentRequest request =
        new RegisterConsignmentRequest(
            10L,
            CardState.HIGH,
            null,
            new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
            List.of(new ConsignmentImageRequest("https://image.example.com/front.png")));

    // when & then
    mockMvc
        .perform(
            post("/consignments")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());

    then(consignmentService).shouldHaveNoInteractions();
  }

  @Test
  void 이미지가_5장을_초과하면_400을_반환한다() throws Exception {
    // given
    RegisterConsignmentRequest request =
        new RegisterConsignmentRequest(
            10L,
            CardState.HIGH,
            null,
            new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageRequest("uploads/1/consignments/1.jpg"),
                new ConsignmentImageRequest("uploads/1/consignments/2.jpg"),
                new ConsignmentImageRequest("uploads/1/consignments/3.jpg"),
                new ConsignmentImageRequest("uploads/1/consignments/4.jpg"),
                new ConsignmentImageRequest("uploads/1/consignments/5.jpg"),
                new ConsignmentImageRequest("uploads/1/consignments/6.jpg")));

    // when & then
    mockMvc
        .perform(
            post("/consignments")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());

    then(consignmentService).shouldHaveNoInteractions();
  }

  @Test
  void 카드ID가_BIGINT_범위를_초과하면_500이_아닌_400을_반환한다() throws Exception {
    // given
    String requestBody =
        """
        {
          "cardId": 99999999999999999999,
          "majorDefect": null,
          "certificate": {
            "serialNumber": "PSA-84213907",
            "certificationBody": "PSA",
            "grade": "10",
            "inspectedAt": "2026-06-30"
          },
          "images": [
            {"temporaryObjectKey": "https://image.example.com/front.png"},
            {"temporaryObjectKey": "https://image.example.com/back.png"}
          ]
        }
        """;

    // when & then
    mockMvc
        .perform(
            post("/consignments")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("cardId")));

    then(consignmentApplicationService).shouldHaveNoInteractions();
    then(slackErrorNotifier).shouldHaveNoInteractions();
  }

  @Test
  void 배열_안의_필드가_BIGINT_범위를_초과하면_어떤_이미지인지_메시지에_포함한다() throws Exception {
    // given
    String requestBody =
        """
        {
          "cardId": 10,
          "majorDefect": null,
          "certificate": {
            "serialNumber": "PSA-84213907",
            "certificationBody": "PSA",
            "grade": "10",
            "inspectedAt": "2026-06-30"
          },
          "images": [
            {"temporaryObjectKey": "https://image.example.com/front.png"},
            {"consignmentImageId": 99999999999999999999, "temporaryObjectKey": null}
          ]
        }
        """;

    // when & then
    mockMvc
        .perform(
            post("/consignments")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("images[1].consignmentImageId")));

    then(consignmentApplicationService).shouldHaveNoInteractions();
    then(slackErrorNotifier).shouldHaveNoInteractions();
  }

  @Test
  void 존재하지_않는_카드ID로_등록하면_404를_반환한다() throws Exception {
    // given
    RegisterConsignmentRequest request = createRequest();
    given(
            consignmentApplicationService.registerConsignment(
                eq(1L), any(RegisterConsignmentRequest.class)))
        .willThrow(new PickUpException(CARD_NOT_FOUND));

    // when & then
    mockMvc
        .perform(
            post("/consignments")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value(CARD_NOT_FOUND.getMessage()));
  }

  @Test
  void 유효한_요청으로_내_상품_목록을_조회하면_200과_커서페이지를_반환한다() throws Exception {
    // given
    GetMyConsignmentsResponse item =
        new GetMyConsignmentsResponse(
            100L,
            500L,
            new GetCardDetailResponse(
                10L,
                "리자몽 1st Edition Holo",
                "Base Set",
                "4/102",
                "일본어",
                "레어 홀로",
                "https://image.example.com/card.png"),
            1L,
            CardState.HIGH,
            "모서리에 약간의 마모",
            ConsignmentStatus.REGISTERABLE,
            null,
            null,
            null,
            new CertificateResponse(
                200L,
                "PSA-84213907",
                CertificationBody.PSA,
                "10",
                "GEM_MINT",
                LocalDate.of(2026, 6, 30)),
            "https://image.example.com/consignment-thumbnail.png");
    CursorPageResponse<GetMyConsignmentsResponse, Long> response =
        new CursorPageResponse<>(true, 100L, 1, List.of(item));
    given(consignmentService.getMyConsignments(eq(1L), any(GetMyConsignmentsRequest.class)))
        .willReturn(response);

    // when & then
    mockMvc
        .perform(
            get("/consignments")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .param("status", "REGISTERABLE")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.cursor").value(100L))
        .andExpect(jsonPath("$.items[0].consignmentId").value(100L))
        .andExpect(jsonPath("$.items[0].auctionId").value(500L))
        .andExpect(jsonPath("$.items[0].sellerMemberId").value(1L))
        .andExpect(jsonPath("$.items[0].status").value("REGISTERABLE"))
        .andExpect(jsonPath("$.items[0].card.cardId").value(10L))
        .andExpect(jsonPath("$.items[0].certificate.certificateId").value(200L));
  }

  @Test
  void 인증_없이_내_상품_목록을_조회하면_401을_반환한다() throws Exception {
    // when & then
    mockMvc
        .perform(get("/consignments").param("status", "REGISTERABLE").param("size", "20"))
        .andExpect(status().isUnauthorized());

    then(consignmentService).shouldHaveNoInteractions();
  }

  @Test
  void 크기를_생략하고_내_상품_목록을_조회하면_400을_반환한다() throws Exception {
    // when & then
    mockMvc
        .perform(
            get("/consignments")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .param("status", "REGISTERABLE"))
        .andExpect(status().isBadRequest());

    then(consignmentService).shouldHaveNoInteractions();
  }

  @Test
  void status를_생략하고_내_상품_목록을_조회하면_400을_반환한다() throws Exception {
    // when & then
    mockMvc
        .perform(
            get("/consignments")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .param("size", "20"))
        .andExpect(status().isBadRequest());

    then(consignmentService).shouldHaveNoInteractions();
  }

  @Test
  void 유효하지_않은_status로_내_상품_목록을_조회하면_400을_반환한다() throws Exception {
    // given
    given(consignmentService.getMyConsignments(eq(1L), any(GetMyConsignmentsRequest.class)))
        .willThrow(new PickUpException(INVALID_CONSIGNMENT_STATUS));

    // when & then
    mockMvc
        .perform(
            get("/consignments")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .param("status", "존재하지않는상태")
                .param("size", "20"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(INVALID_CONSIGNMENT_STATUS.getMessage()));
  }

  @Test
  void 존재하는_상품ID로_조회하면_200과_상품_상세정보를_반환한다() throws Exception {
    // given
    Long consignmentId = 100L;
    GetConsignmentDetailResponse response =
        new GetConsignmentDetailResponse(
            consignmentId,
            new GetCardDetailResponse(
                10L,
                "리자몽 1st Edition Holo",
                "Base Set",
                "4/102",
                "일본어",
                "레어 홀로",
                "https://image.example.com/card.png"),
            "피카츄",
            CardState.HIGH,
            "모서리에 약간의 마모",
            ConsignmentStatus.REGISTERABLE,
            null,
            null,
            null,
            new CertificateResponse(
                200L,
                "PSA-84213907",
                CertificationBody.PSA,
                "10",
                "GEM_MINT",
                LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageResponse(1L, 1, "https://image.example.com/front.png"),
                new ConsignmentImageResponse(2L, 2, "https://image.example.com/back.png")),
            false);
    given(consignmentService.getConsignment(consignmentId)).willReturn(response);

    // when & then
    mockMvc
        .perform(get("/consignments/{consignmentId}", consignmentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.consignmentId").value(100L))
        .andExpect(jsonPath("$.card.cardId").value(10L))
        .andExpect(jsonPath("$.sellerMemberNickname").value("피카츄"))
        .andExpect(jsonPath("$.status").value("REGISTERABLE"))
        .andExpect(jsonPath("$.certificate.certificateId").value(200L))
        .andExpect(jsonPath("$.images[0].consignmentImageId").value(1L))
        .andExpect(jsonPath("$.images[0].imageOrder").value(1))
        .andExpect(jsonPath("$.images[1].imageOrder").value(2))
        .andExpect(jsonPath("$.auctionRegistered").value(false));
  }

  @Test
  void 존재하지_않는_상품ID로_조회하면_404를_반환한다() throws Exception {
    // given
    Long notExistConsignmentId = 999L;
    given(consignmentService.getConsignment(notExistConsignmentId))
        .willThrow(new PickUpException(CONSIGNMENT_NOT_FOUND));

    // when & then
    mockMvc
        .perform(get("/consignments/{consignmentId}", notExistConsignmentId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value(CONSIGNMENT_NOT_FOUND.getMessage()));
  }

  @Test
  void 유효한_요청으로_상품을_수정하면_200과_수정된_상세정보를_반환한다() throws Exception {
    // given
    Long consignmentId = 100L;
    ModifyConsignmentRequest request = createModifyRequest();
    GetConsignmentDetailResponse response =
        new GetConsignmentDetailResponse(
            consignmentId,
            new GetCardDetailResponse(
                10L,
                "리자몽 1st Edition Holo",
                "Base Set",
                "4/102",
                "일본어",
                "레어 홀로",
                "https://image.example.com/card.png"),
            "피카츄",
            CardState.HIGH,
            "새로운 흠집 설명",
            ConsignmentStatus.REGISTERABLE,
            null,
            null,
            null,
            new CertificateResponse(
                201L,
                "PSA-84213907",
                CertificationBody.PSA,
                "10",
                "GEM_MINT",
                LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageResponse(3L, 1, "https://image.example.com/front.png"),
                new ConsignmentImageResponse(4L, 2, "https://image.example.com/back.png")),
            false);
    given(
            consignmentApplicationService.modifyConsignment(
                eq(consignmentId), eq(1L), any(ModifyConsignmentRequest.class)))
        .willReturn(response);

    // when & then
    mockMvc
        .perform(
            patch("/consignments/{consignmentId}", consignmentId)
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.consignmentId").value(100L))
        .andExpect(jsonPath("$.cardState").value("HIGH"))
        .andExpect(jsonPath("$.majorDefect").value("새로운 흠집 설명"))
        .andExpect(jsonPath("$.certificate.certificateId").value(201L));
  }

  @Test
  void 이미지가_2장_미만이면_수정_요청은_400을_반환한다() throws Exception {
    // given
    Long consignmentId = 100L;
    ModifyConsignmentRequest request =
        new ModifyConsignmentRequest(
            CardState.HIGH,
            null,
            new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
            List.of(new ConsignmentImageRequest("https://image.example.com/front.png")));

    // when & then
    mockMvc
        .perform(
            patch("/consignments/{consignmentId}", consignmentId)
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());

    then(consignmentService).shouldHaveNoInteractions();
  }

  @Test
  void 감정일이_현재보다_이후면_상품_수정은_400을_반환한다() throws Exception {
    // given
    Long consignmentId = 100L;
    ModifyConsignmentRequest request =
        new ModifyConsignmentRequest(
            CardState.HIGH,
            null,
            new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.now().plusDays(1)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")));

    // when & then
    mockMvc
        .perform(
            patch("/consignments/{consignmentId}", consignmentId)
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());

    then(consignmentApplicationService).shouldHaveNoInteractions();
  }

  @Test
  void 인증_없이_수정을_요청하면_401을_반환한다() throws Exception {
    // given
    Long consignmentId = 100L;
    ModifyConsignmentRequest request = createModifyRequest();

    // when & then
    mockMvc
        .perform(
            patch("/consignments/{consignmentId}", consignmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());

    then(consignmentService).shouldHaveNoInteractions();
  }

  @Test
  void 본인이_등록한_상품이_아니면_403을_반환한다() throws Exception {
    // given
    Long consignmentId = 100L;
    ModifyConsignmentRequest request = createModifyRequest();
    given(
            consignmentApplicationService.modifyConsignment(
                eq(consignmentId), eq(1L), any(ModifyConsignmentRequest.class)))
        .willThrow(new PickUpException(CONSIGNMENT_MODIFY_OWNER_MISMATCH));

    // when & then
    mockMvc
        .perform(
            patch("/consignments/{consignmentId}", consignmentId)
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value(CONSIGNMENT_MODIFY_OWNER_MISMATCH.getMessage()));
  }

  @Test
  void 존재하지_않는_상품을_수정하면_404를_반환한다() throws Exception {
    // given
    Long notExistConsignmentId = 999L;
    ModifyConsignmentRequest request = createModifyRequest();
    given(
            consignmentApplicationService.modifyConsignment(
                eq(notExistConsignmentId), eq(1L), any(ModifyConsignmentRequest.class)))
        .willThrow(new PickUpException(CONSIGNMENT_NOT_FOUND));

    // when & then
    mockMvc
        .perform(
            patch("/consignments/{consignmentId}", notExistConsignmentId)
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value(CONSIGNMENT_NOT_FOUND.getMessage()));
  }

  @Test
  void 경매_진행중인_상품을_수정하면_409를_반환한다() throws Exception {
    // given
    Long consignmentId = 100L;
    ModifyConsignmentRequest request = createModifyRequest();
    given(
            consignmentApplicationService.modifyConsignment(
                eq(consignmentId), eq(1L), any(ModifyConsignmentRequest.class)))
        .willThrow(new PickUpException(CONSIGNMENT_NOT_MODIFIABLE));

    // when & then
    mockMvc
        .perform(
            patch("/consignments/{consignmentId}", consignmentId)
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(CONSIGNMENT_NOT_MODIFIABLE.getMessage()));
  }

  @Test
  void 유효한_요청으로_상품을_삭제하면_204를_반환한다() throws Exception {
    // given
    Long consignmentId = 100L;

    // when & then
    mockMvc
        .perform(
            delete("/consignments/{consignmentId}", consignmentId)
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isNoContent());

    then(consignmentApplicationService).should().deleteConsignment(consignmentId, 1L);
  }

  @Test
  void 인증_없이_삭제를_요청하면_401을_반환한다() throws Exception {
    // given
    Long consignmentId = 100L;

    // when & then
    mockMvc
        .perform(delete("/consignments/{consignmentId}", consignmentId))
        .andExpect(status().isUnauthorized());

    then(consignmentService).shouldHaveNoInteractions();
  }

  @Test
  void 본인이_등록한_상품이_아니면_삭제_요청은_403을_반환한다() throws Exception {
    // given
    Long consignmentId = 100L;
    willThrow(new PickUpException(CONSIGNMENT_DELETE_OWNER_MISMATCH))
        .given(consignmentApplicationService)
        .deleteConsignment(consignmentId, 1L);

    // when & then
    mockMvc
        .perform(
            delete("/consignments/{consignmentId}", consignmentId)
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value(CONSIGNMENT_DELETE_OWNER_MISMATCH.getMessage()));
  }

  @Test
  void 존재하지_않는_상품을_삭제하면_404를_반환한다() throws Exception {
    // given
    Long notExistConsignmentId = 999L;
    willThrow(new PickUpException(CONSIGNMENT_NOT_FOUND))
        .given(consignmentApplicationService)
        .deleteConsignment(notExistConsignmentId, 1L);

    // when & then
    mockMvc
        .perform(
            delete("/consignments/{consignmentId}", notExistConsignmentId)
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value(CONSIGNMENT_NOT_FOUND.getMessage()));
  }

  @Test
  void 경매가_시작된_이후인_상품을_삭제하면_409를_반환한다() throws Exception {
    // given
    Long consignmentId = 100L;
    willThrow(new PickUpException(CONSIGNMENT_NOT_DELETABLE))
        .given(consignmentApplicationService)
        .deleteConsignment(consignmentId, 1L);

    // when & then
    mockMvc
        .perform(
            delete("/consignments/{consignmentId}", consignmentId)
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(CONSIGNMENT_NOT_DELETABLE.getMessage()));
  }

  private ModifyConsignmentRequest createModifyRequest() {
    return new ModifyConsignmentRequest(
        CardState.HIGH,
        "새로운 흠집 설명",
        new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
        List.of(
            new ConsignmentImageRequest("https://image.example.com/front.png"),
            new ConsignmentImageRequest("https://image.example.com/back.png")));
  }

  private RegisterConsignmentRequest createRequest() {
    return new RegisterConsignmentRequest(
        10L,
        CardState.HIGH,
        "모서리에 약간의 마모",
        new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
        List.of(
            new ConsignmentImageRequest("https://image.example.com/front.png"),
            new ConsignmentImageRequest("https://image.example.com/back.png")));
  }
}

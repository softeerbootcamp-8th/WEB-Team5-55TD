package com.ootd.pickup.member.controller;

import static com.ootd.pickup.global.exception.ExceptionCode.MEMBER_WITHDRAW_NOT_ALLOWED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.dto.request.GetMyWatchesRequest;
import com.ootd.pickup.auction.dto.response.AuctionListItemResponse;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.global.slack.SlackErrorNotifier;
import com.ootd.pickup.member.dto.MyProfileResponse;
import com.ootd.pickup.member.dto.PointBalanceResponse;
import com.ootd.pickup.member.dto.UpdateMyProfileRequest;
import com.ootd.pickup.member.dto.WithdrawMemberRequest;
import com.ootd.pickup.member.service.MemberService;
import com.ootd.pickup.member.service.ProfileApplicationService;
import com.ootd.pickup.point.domain.PointTransactionType;
import com.ootd.pickup.point.dto.request.GetPointTransactionsRequest;
import com.ootd.pickup.point.dto.response.PointChargeResponse;
import com.ootd.pickup.point.dto.response.PointTransactionItemResponse;
import com.ootd.pickup.point.service.PointChargeService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(MemberController.class)
class MemberControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private MemberService memberService;

  @MockitoBean private ProfileApplicationService profileApplicationService;

  @MockitoBean private PointChargeService pointChargeService;

  @MockitoBean private SlackErrorNotifier slackErrorNotifier;

  @Test
  void 가입_입력이_형식에_맞지_않으면_회원을_생성하지_않는다() throws Exception {
    // given — 아이디 5자 미만, 닉네임 1자, 비밀번호 한 종류 8자 미만
    String request =
        """
        {
          "loginId": "abc",
          "nickname": "닉",
          "password": "1234"
        }
        """;

    // when & then
    mockMvc
        .perform(post("/members").contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(memberService);
  }

  @Test
  void 가입_닉네임의_앞뒤_공백을_제외하면_2자_미만일_경우_회원을_생성하지_않는다() throws Exception {
    String request =
        """
        {
          "loginId": "pickup-user",
          "nickname": " 가 ",
          "password": "password1"
        }
        """;

    mockMvc
        .perform(post("/members").contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(memberService);
  }

  @Test
  void 인증된_회원이_내_정보를_조회하면_200과_회원정보를_반환한다() throws Exception {
    // given
    MyProfileResponse response = new MyProfileResponse(1L, "pickup-user", "피카츄", null);
    given(memberService.getMyProfile(1L)).willReturn(response);

    // when & then
    mockMvc
        .perform(
            get("/members/me")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberId").value(1L))
        .andExpect(jsonPath("$.loginId").value("pickup-user"))
        .andExpect(jsonPath("$.nickname").value("피카츄"))
        .andExpect(jsonPath("$.profileImageUrl").doesNotExist())
        .andExpect(jsonPath("$.joinedAt").doesNotExist())
        .andExpect(jsonPath("$.password").doesNotExist());
  }

  @Test
  void 인증된_회원이_일부_정보를_수정하면_200과_수정된_회원정보를_반환한다() throws Exception {
    // given
    UpdateMyProfileRequest request = new UpdateMyProfileRequest("라이츄회원", null, null, null);
    MyProfileResponse response = new MyProfileResponse(1L, "pickup-user", "라이츄회원", null);
    given(profileApplicationService.updateMyProfile(1L, request)).willReturn(response);

    // when & then
    mockMvc
        .perform(
            patch("/members/me")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nickname").value("라이츄회원"))
        .andExpect(jsonPath("$.joinedAt").doesNotExist());

    then(profileApplicationService).should().updateMyProfile(1L, request);
  }

  @Test
  void 수정할_필드가_없으면_400을_반환한다() throws Exception {
    // given
    String request = "{}";

    // when & then
    mockMvc
        .perform(
            patch("/members/me")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isBadRequest());

    then(memberService).shouldHaveNoInteractions();
  }

  @Test
  void 현재비밀번호_없이_비밀번호를_변경하면_400을_반환한다() throws Exception {
    // given
    String request = "{\"password\":\"new-password\"}";

    // when & then
    mockMvc
        .perform(
            patch("/members/me")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isBadRequest());

    then(memberService).shouldHaveNoInteractions();
  }

  @Test
  void 닉네임이_공백이면_400을_반환한다() throws Exception {
    // given
    String request = "{\"nickname\":\"    \"}";

    // when & then
    mockMvc
        .perform(
            patch("/members/me")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isBadRequest());

    then(memberService).shouldHaveNoInteractions();
  }

  @Test
  void 닉네임의_앞뒤_공백을_제외하면_2자_미만일_경우_400을_반환한다() throws Exception {
    String request = "{\"nickname\":\" 가 \"}";

    mockMvc
        .perform(
            patch("/members/me")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isBadRequest());

    then(profileApplicationService).shouldHaveNoInteractions();
  }

  @Test
  void 새_비밀번호가_공백이면_400을_반환한다() throws Exception {
    // given
    String request = "{\"currentPassword\":\"old-password\",\"password\":\"    \"}";

    // when & then
    mockMvc
        .perform(
            patch("/members/me")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isBadRequest());

    then(memberService).shouldHaveNoInteractions();
  }

  @Test
  void 인증된_회원이_포인트를_조회하면_200과_잔액을_반환한다() throws Exception {
    // given
    given(memberService.getMyPointBalance(1L))
        .willReturn(new PointBalanceResponse(3_000_000L, 500_000L, 2_500_000L));

    // when & then
    mockMvc
        .perform(
            get("/members/me/points")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pointBalance").value(3_000_000L))
        .andExpect(jsonPath("$.reservedPointBalance").value(500_000L))
        .andExpect(jsonPath("$.availablePointBalance").value(2_500_000L));
  }

  @Test
  void 인증된_회원이_포인트_거래내역을_조회하면_커서페이지를_반환한다() throws Exception {
    // given
    PointTransactionItemResponse item =
        new PointTransactionItemResponse(
            3L,
            PointTransactionType.AUCTION_PAYOUT,
            10_500L,
            20_500L,
            1L,
            LocalDateTime.of(2026, 8, 8, 10, 0));
    given(memberService.getMyPointTransactions(eq(1L), any(GetPointTransactionsRequest.class)))
        .willReturn(CursorPageResponse.from(List.of(item), false, null));

    // when & then
    mockMvc
        .perform(
            get("/members/me/point-transactions")
                .param("size", "20")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].transactionType").value("AUCTION_PAYOUT"))
        .andExpect(jsonPath("$.items[0].amount").value(10_500L))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  void 인증된_회원이_포인트를_충전하면_201과_충전결과를_반환한다() throws Exception {
    // given
    String request = "{\"amount\":300000,\"idempotencyKey\":\"req-1\"}";
    PointChargeResponse response =
        new PointChargeResponse(
            1L, 300_000L, 500_000L, 0L, 500_000L, LocalDateTime.of(2026, 8, 8, 10, 0));
    given(pointChargeService.chargePoint(1L, 300_000L, "req-1")).willReturn(response);

    // when & then
    mockMvc
        .perform(
            post("/members/me/point-charges")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.chargedAmount").value(300_000L))
        .andExpect(jsonPath("$.pointBalance").value(500_000L));
  }

  @Test
  void 충전_금액이_0이하면_400을_반환한다() throws Exception {
    // given
    String request = "{\"amount\":0,\"idempotencyKey\":\"req-1\"}";

    // when & then
    mockMvc
        .perform(
            post("/members/me/point-charges")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isBadRequest());

    then(pointChargeService).shouldHaveNoInteractions();
  }

  @Test
  void idempotencyKey가_없으면_400을_반환한다() throws Exception {
    // given
    String request = "{\"amount\":300000}";

    // when & then
    mockMvc
        .perform(
            post("/members/me/point-charges")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isBadRequest());

    then(pointChargeService).shouldHaveNoInteractions();
  }

  @Test
  void 같은_idempotencyKey로_재요청하면_200과_기존결과를_반환한다() throws Exception {
    // given
    String request = "{\"amount\":300000,\"idempotencyKey\":\"req-1\"}";
    PointChargeResponse response =
        new PointChargeResponse(
            1L, 300_000L, 500_000L, 0L, 500_000L, LocalDateTime.of(2026, 8, 8, 10, 0));
    given(pointChargeService.chargePoint(1L, 300_000L, "req-1"))
        .willThrow(new DataIntegrityViolationException("uk_point_transaction_idempotency_key"));
    given(pointChargeService.getChargeResult(1L, "req-1")).willReturn(response);

    // when & then
    mockMvc
        .perform(
            post("/members/me/point-charges")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chargedAmount").value(300_000L));

    then(pointChargeService).should().getChargeResult(1L, "req-1");
  }

  @Test
  void 인증정보가_없으면_포인트_충전은_401을_반환한다() throws Exception {
    // given & when & then
    mockMvc
        .perform(
            post("/members/me/point-charges")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":300000,\"idempotencyKey\":\"req-1\"}"))
        .andExpect(status().isUnauthorized());

    then(pointChargeService).shouldHaveNoInteractions();
  }

  @Test
  void 인증정보가_없으면_내_정보_조회는_401을_반환한다() throws Exception {
    // given & when & then
    mockMvc.perform(get("/members/me")).andExpect(status().isUnauthorized());

    then(memberService).shouldHaveNoInteractions();
  }

  @Test
  void 인증된_회원이_관심목록을_조회하면_200과_관심경매목록을_반환한다() throws Exception {
    // given
    AuctionListItemResponse item =
        new AuctionListItemResponse(
            10L,
            2L,
            "Test Title",
            new GetCardDetailResponse(
                1L,
                "리자몽 1st Edition Holo",
                "Base Set",
                "4/102",
                "일본어",
                "홀로 레어",
                "https://example.com/card.png"),
            "PSA 10",
            AuctionStatus.SCHEDULED,
            10_000L,
            null,
            LocalDateTime.of(2026, 8, 10, 12, 0),
            null,
            null,
            3L,
            true,
            "https://example.com/thumb.png");
    CursorPageResponse<AuctionListItemResponse, String> response =
        CursorPageResponse.from(List.of(item), false, null);
    given(memberService.getMyWatches(1L, new GetMyWatchesRequest(null, 20))).willReturn(response);

    // when & then
    mockMvc
        .perform(
            get("/members/me/watches")
                .param("size", "20")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.items[0].auctionId").value(10L))
        .andExpect(jsonPath("$.items[0].auctionStatus").value("SCHEDULED"))
        .andExpect(jsonPath("$.items[0].watched").value(true));
  }

  @Test
  void 인증정보가_없으면_관심목록_조회는_401을_반환한다() throws Exception {
    // given & when & then
    mockMvc.perform(get("/members/me/watches")).andExpect(status().isUnauthorized());

    then(memberService).shouldHaveNoInteractions();
  }

  @Test
  void 인증된_회원이_비밀번호와_함께_탈퇴를_요청하면_204를_반환한다() throws Exception {
    // given
    WithdrawMemberRequest request = new WithdrawMemberRequest("password1234");

    // when & then
    mockMvc
        .perform(
            delete("/members/me")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNoContent());

    then(memberService).should().withdrawMember(1L, request);
  }

  @Test
  void 비밀번호_없이_탈퇴를_요청하면_400을_반환한다() throws Exception {
    // given
    String request = "{}";

    // when & then
    mockMvc
        .perform(
            delete("/members/me")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isBadRequest());

    then(memberService).shouldHaveNoInteractions();
  }

  @Test
  void 진행중인_경매나_입찰이_있으면_탈퇴는_409를_반환한다() throws Exception {
    // given
    WithdrawMemberRequest request = new WithdrawMemberRequest("password1234");
    willThrow(new PickUpException(MEMBER_WITHDRAW_NOT_ALLOWED))
        .given(memberService)
        .withdrawMember(1L, request);

    // when & then
    mockMvc
        .perform(
            delete("/members/me")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict());
  }

  @Test
  void 인증정보가_없으면_탈퇴_요청은_401을_반환한다() throws Exception {
    // given
    WithdrawMemberRequest request = new WithdrawMemberRequest("password1234");

    // when & then
    mockMvc
        .perform(
            delete("/members/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());

    then(memberService).shouldHaveNoInteractions();
  }
}

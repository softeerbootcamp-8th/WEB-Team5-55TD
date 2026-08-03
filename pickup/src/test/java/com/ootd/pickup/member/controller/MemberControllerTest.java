package com.ootd.pickup.member.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.slack.SlackErrorNotifier;
import com.ootd.pickup.member.dto.MyProfileResponse;
import com.ootd.pickup.member.dto.PointBalanceResponse;
import com.ootd.pickup.member.dto.UpdateMyProfileRequest;
import com.ootd.pickup.member.service.MemberService;
import com.ootd.pickup.member.service.ProfileApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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

  @MockitoBean private SlackErrorNotifier slackErrorNotifier;

  @Test
  void 아이디_닉네임_비밀번호가_4자_미만이면_회원을_생성하지_않는다() throws Exception {
    // given
    String request =
        """
        {
          "loginId": "abc",
          "nickname": "닉넴",
          "password": "123"
        }
        """;

    // when & then
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
    given(memberService.getMyPointBalance(1L)).willReturn(new PointBalanceResponse(3_000_000L));

    // when & then
    mockMvc
        .perform(
            get("/members/me/points")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pointBalance").value(3_000_000L));
  }

  @Test
  void 인증정보가_없으면_내_정보_조회는_401을_반환한다() throws Exception {
    // given & when & then
    mockMvc.perform(get("/members/me")).andExpect(status().isUnauthorized());

    then(memberService).shouldHaveNoInteractions();
  }
}

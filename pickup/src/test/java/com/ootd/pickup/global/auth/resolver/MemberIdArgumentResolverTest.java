package com.ootd.pickup.global.auth.resolver;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.auth.annotation.MemberId;
import com.ootd.pickup.global.auth.annotation.RequireAuthentication;
import com.ootd.pickup.global.exception.PickUpException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;

@ExtendWith(MockitoExtension.class)
class MemberIdArgumentResolverTest {

  private final MemberIdArgumentResolver resolver = new MemberIdArgumentResolver();

  @Mock private NativeWebRequest webRequest;

  @Test
  void 인증_어노테이션이_없으면_지원하지_않는다() throws NoSuchMethodException {
    // given
    MethodParameter parameter = parameterOf("withoutAuth", Long.class);

    // when & then
    assertThat(resolver.supportsParameter(parameter)).isFalse();
  }

  @Test
  void 회원ID_어노테이션이_없으면_지원하지_않는다() throws NoSuchMethodException {
    // given
    MethodParameter parameter = parameterOf("withAuthNoMemberIdAnnotation", Long.class);

    // when & then
    assertThat(resolver.supportsParameter(parameter)).isFalse();
  }

  @Test
  void 파라미터_타입이_Long이_아니면_지원하지_않는다() throws NoSuchMethodException {
    // given
    MethodParameter parameter = parameterOf("withAuthWrongType", String.class);

    // when & then
    assertThat(resolver.supportsParameter(parameter)).isFalse();
  }

  @Test
  void 인증_어노테이션과_회원ID_어노테이션이_모두_있고_타입이_Long이면_지원한다() throws NoSuchMethodException {
    // given
    MethodParameter parameter = parameterOf("withAuth", Long.class);

    // when & then
    assertThat(resolver.supportsParameter(parameter)).isTrue();
  }

  @Test
  void 인증_정보가_있으면_회원ID를_반환한다() throws NoSuchMethodException {
    // given
    MethodParameter parameter = parameterOf("withAuth", Long.class);
    given(
            webRequest.getAttribute(
                AuthenticationAttributes.ATTRIBUTE_NAME, RequestAttributes.SCOPE_REQUEST))
        .willReturn(new Authentication(1L));

    // when
    Object memberId = resolver.resolveArgument(parameter, null, webRequest, null);

    // then
    assertThat(memberId).isEqualTo(1L);
  }

  @Test
  void 인증_정보가_없으면_예외가_발생한다() throws NoSuchMethodException {
    // given
    MethodParameter parameter = parameterOf("withAuth", Long.class);
    given(
            webRequest.getAttribute(
                AuthenticationAttributes.ATTRIBUTE_NAME, RequestAttributes.SCOPE_REQUEST))
        .willReturn(null);

    // when & then
    assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, webRequest, null))
        .isInstanceOf(PickUpException.class);
  }

  private MethodParameter parameterOf(String methodName, Class<?> parameterType)
      throws NoSuchMethodException {
    return new MethodParameter(DummyController.class.getMethod(methodName, parameterType), 0);
  }

  private static class DummyController {
    @RequireAuthentication
    public void withAuth(@MemberId Long memberId) {}

    @RequireAuthentication
    public void withAuthNoMemberIdAnnotation(Long memberId) {}

    @RequireAuthentication
    public void withAuthWrongType(@MemberId String memberId) {}

    public void withoutAuth(@MemberId Long memberId) {}
  }
}

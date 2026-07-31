package com.ootd.pickup.global.auth.resolver;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.auth.annotation.OptionalMemberId;
import com.ootd.pickup.global.auth.annotation.RequireAuthentication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;

@ExtendWith(MockitoExtension.class)
class OptionalMemberIdArgumentResolverTest {

  private final OptionalMemberIdArgumentResolver resolver = new OptionalMemberIdArgumentResolver();

  @Mock private NativeWebRequest webRequest;

  @Test
  void 인증_정보가_있으면_회원ID를_반환한다() throws NoSuchMethodException {
    // given
    MethodParameter parameter = parameterOf("withOptionalMemberId", Long.class);
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
  void 인증_정보가_없으면_null을_반환한다() throws NoSuchMethodException {
    // given
    MethodParameter parameter = parameterOf("withOptionalMemberId", Long.class);
    given(
            webRequest.getAttribute(
                AuthenticationAttributes.ATTRIBUTE_NAME, RequestAttributes.SCOPE_REQUEST))
        .willReturn(null);

    // when
    Object memberId = resolver.resolveArgument(parameter, null, webRequest, null);

    // then
    assertThat(memberId).isNull();
  }

  @Test
  void 인증_필수_애노테이션이_없어도_지원한다() throws NoSuchMethodException {
    // given
    MethodParameter withoutRequireAuth = parameterOf("withOptionalMemberId", Long.class);
    MethodParameter withRequireAuth = parameterOf("withRequireAuthAndOptionalMemberId", Long.class);

    // when & then
    assertThat(resolver.supportsParameter(withoutRequireAuth)).isTrue();
    assertThat(resolver.supportsParameter(withRequireAuth)).isTrue();
  }

  @Test
  void OptionalMemberId_애노테이션이_없으면_지원하지_않는다() throws NoSuchMethodException {
    // given
    MethodParameter parameter = parameterOf("withoutAnnotation", Long.class);

    // when & then
    assertThat(resolver.supportsParameter(parameter)).isFalse();
  }

  private MethodParameter parameterOf(String methodName, Class<?> parameterType)
      throws NoSuchMethodException {
    return new MethodParameter(DummyController.class.getMethod(methodName, parameterType), 0);
  }

  private static class DummyController {
    public void withOptionalMemberId(@OptionalMemberId Long memberId) {}

    @RequireAuthentication
    public void withRequireAuthAndOptionalMemberId(@OptionalMemberId Long memberId) {}

    public void withoutAnnotation(Long memberId) {}
  }
}

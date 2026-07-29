package com.ootd.pickup.global.auth.interceptor;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.auth.annotation.RequireAuthentication;
import com.ootd.pickup.global.exception.PickUpException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class AuthenticationInterceptorTest {

  private final AuthenticationInterceptor interceptor = new AuthenticationInterceptor();

  @Test
  void 핸들러가_HandlerMethod가_아니면_통과한다() {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    boolean result = interceptor.preHandle(request, response, new Object());

    // then
    assertThat(result).isTrue();
  }

  @Test
  void 인증이_필요하지_않은_핸들러는_통과한다() throws NoSuchMethodException {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    HandlerMethod handler = new HandlerMethod(new DummyController(), "open");

    // when
    boolean result = interceptor.preHandle(request, response, handler);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void 인증이_필요한_핸들러이고_인증_정보가_있으면_통과한다() throws NoSuchMethodException {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L));
    MockHttpServletResponse response = new MockHttpServletResponse();
    HandlerMethod handler = new HandlerMethod(new DummyController(), "secured");

    // when
    boolean result = interceptor.preHandle(request, response, handler);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void 인증이_필요한_핸들러이고_인증_정보가_없으면_예외가_발생한다() throws NoSuchMethodException {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    HandlerMethod handler = new HandlerMethod(new DummyController(), "secured");

    // when & then
    assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
        .isInstanceOf(PickUpException.class);
  }

  private static class DummyController {
    @RequireAuthentication
    public void secured() {}

    public void open() {}
  }
}

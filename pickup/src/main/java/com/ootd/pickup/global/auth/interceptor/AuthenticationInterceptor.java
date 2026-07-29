package com.ootd.pickup.global.auth.interceptor;

import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.auth.annotation.RequireAuthentication;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    // handler가 HandlerMethod 인지 확인
    if (!(handler instanceof HandlerMethod method)) {
      return true;
    }
    // HandlerMethod에 @RequireAuthentication 어노테이션 보유 확인
    RequireAuthentication annotation = method.getMethodAnnotation(RequireAuthentication.class);
    if (annotation == null) {
      return true;
    }
    // attribute 가지고 있는지 확인
    Object obj = request.getAttribute(AuthenticationAttributes.ATTRIBUTE_NAME);
    if (!(obj instanceof Authentication authentication)) {
      throw new PickUpException(ExceptionCode.AUTHENTICATION_REQUIRED);
    }
    return true;
  }
}

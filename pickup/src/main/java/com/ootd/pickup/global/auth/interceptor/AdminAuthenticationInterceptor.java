package com.ootd.pickup.global.auth.interceptor;

import com.ootd.pickup.global.auth.AdminAuthentication;
import com.ootd.pickup.global.auth.AdminAuthenticationAttributes;
import com.ootd.pickup.global.auth.annotation.RequireAdminAuthentication;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthenticationInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod method)) {
      return true;
    }
    RequireAdminAuthentication annotation =
        method.getMethodAnnotation(RequireAdminAuthentication.class);
    if (annotation == null) {
      return true;
    }
    Object obj = request.getAttribute(AdminAuthenticationAttributes.ATTRIBUTE_NAME);
    if (!(obj instanceof AdminAuthentication authentication)) {
      throw new PickUpException(ExceptionCode.ADMIN_AUTHENTICATION_REQUIRED);
    }
    return true;
  }
}

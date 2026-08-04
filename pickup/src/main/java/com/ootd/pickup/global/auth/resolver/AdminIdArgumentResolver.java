package com.ootd.pickup.global.auth.resolver;

import com.ootd.pickup.global.auth.AdminAuthentication;
import com.ootd.pickup.global.auth.AdminAuthenticationAttributes;
import com.ootd.pickup.global.auth.annotation.AdminId;
import com.ootd.pickup.global.auth.annotation.RequireAdminAuthentication;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class AdminIdArgumentResolver implements HandlerMethodArgumentResolver {
  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.getMethodAnnotation(RequireAdminAuthentication.class) != null
        && parameter.getParameterAnnotation(AdminId.class) != null
        && parameter.getParameterType().equals(Long.class);
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    Object attribute =
        webRequest.getAttribute(
            AdminAuthenticationAttributes.ATTRIBUTE_NAME, RequestAttributes.SCOPE_REQUEST);
    if (!(attribute instanceof AdminAuthentication authentication)) {
      throw new PickUpException(ExceptionCode.ADMIN_AUTHENTICATION_REQUIRED);
    }
    return authentication.adminId();
  }
}

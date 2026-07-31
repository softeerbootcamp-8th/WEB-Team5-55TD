package com.ootd.pickup.global.auth.resolver;

import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.auth.annotation.OptionalMemberId;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class OptionalMemberIdArgumentResolver implements HandlerMethodArgumentResolver {
  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.getParameterAnnotation(OptionalMemberId.class) != null
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
            AuthenticationAttributes.ATTRIBUTE_NAME, RequestAttributes.SCOPE_REQUEST);
    return attribute instanceof Authentication authentication ? authentication.memberId() : null;
  }
}

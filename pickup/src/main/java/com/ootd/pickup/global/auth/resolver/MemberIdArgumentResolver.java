package com.ootd.pickup.global.auth.resolver;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.auth.annotation.MemberId;
import com.ootd.pickup.global.auth.annotation.RequireAuthentication;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;

@Component
public class MemberIdArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getMethodAnnotation(RequireAuthentication.class) != null
            && parameter.getParameterAnnotation(MemberId.class) != null
            && parameter.getParameterType().equals(Long.class);
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        Object attribute = webRequest.getAttribute(
            AuthenticationAttributes.ATTRIBUTE_NAME,
            RequestAttributes.SCOPE_REQUEST
        );
        if (!(attribute instanceof Authentication authentication)) {
            throw new PickUpException(ExceptionCode.AUTHENTICATION_REQUIRED);
        }
        return authentication.memberId();
    }
}

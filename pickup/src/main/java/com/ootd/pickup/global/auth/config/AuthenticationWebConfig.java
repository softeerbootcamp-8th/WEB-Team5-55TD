package com.ootd.pickup.global.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ootd.pickup.auth.token.AccessTokenVerifier;
import com.ootd.pickup.global.auth.filter.AuthenticationFilter;
import com.ootd.pickup.global.auth.interceptor.AuthenticationInterceptor;
import com.ootd.pickup.global.auth.resolver.MemberIdArgumentResolver;
import com.ootd.pickup.global.filter.ExceptionHandlingFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class AuthenticationWebConfig implements WebMvcConfigurer {

    private final AuthenticationInterceptor authenticationInterceptor;
    private final MemberIdArgumentResolver memberIdArgumentResolver;

    @Bean
    @ConditionalOnBean(AccessTokenVerifier.class)
    public AuthenticationFilter authenticationFilter(AccessTokenVerifier accessTokenVerifier) {
        return new AuthenticationFilter(accessTokenVerifier);
    }

    @Bean
    @ConditionalOnBean(AccessTokenVerifier.class)
    public ExceptionHandlingFilter exceptionHandlingFilter(ObjectMapper objectMapper) {
        return new ExceptionHandlingFilter(objectMapper);
    }

    @Bean
    @ConditionalOnBean(AccessTokenVerifier.class)
    public FilterRegistrationBean<ExceptionHandlingFilter> exceptionHandlingFilterRegistration(
            ExceptionHandlingFilter exceptionHandlingFilter
    ) {
        FilterRegistrationBean<ExceptionHandlingFilter> filterRegistrationBean =
                new FilterRegistrationBean<>(exceptionHandlingFilter);
        filterRegistrationBean.addUrlPatterns("/*");
        filterRegistrationBean.setOrder(0);
        return filterRegistrationBean;
    }

    @Bean
    @ConditionalOnBean(AccessTokenVerifier.class)
    public FilterRegistrationBean<AuthenticationFilter> authenticationFilterRegistration(AuthenticationFilter authenticationFilter) {
        FilterRegistrationBean<AuthenticationFilter> filterRegistrationBean = new FilterRegistrationBean<>(authenticationFilter);
        filterRegistrationBean.addUrlPatterns("/*");
        filterRegistrationBean.setOrder(1);
        return filterRegistrationBean;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authenticationInterceptor);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(memberIdArgumentResolver);
    }

}

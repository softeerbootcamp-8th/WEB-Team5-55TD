package com.ootd.pickup.global.auth.config;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.ootd.pickup.auth.token.AccessTokenVerifier;
import com.ootd.pickup.global.auth.TokenCookieProperties;
import com.ootd.pickup.global.auth.filter.AuthenticationFilter;
import com.ootd.pickup.global.auth.interceptor.AuthenticationInterceptor;
import com.ootd.pickup.global.auth.resolver.MemberIdArgumentResolver;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(TokenCookieProperties.class)
public class AuthenticationWebConfig implements WebMvcConfigurer {

    private final AuthenticationInterceptor authenticationInterceptor;
    private final MemberIdArgumentResolver memberIdArgumentResolver;

    @Bean
    @ConditionalOnBean(AccessTokenVerifier.class)
    public FilterRegistrationBean<AuthenticationFilter> authenticationFilterRegistration(
        AccessTokenVerifier accessTokenVerifier
    ) {
        AuthenticationFilter authenticationFilter =
            new AuthenticationFilter(accessTokenVerifier);
        FilterRegistrationBean<AuthenticationFilter> filterRegistrationBean =
            new FilterRegistrationBean<>(authenticationFilter);
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

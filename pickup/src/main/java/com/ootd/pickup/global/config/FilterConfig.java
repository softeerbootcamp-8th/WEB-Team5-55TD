package com.ootd.pickup.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ootd.pickup.auth.token.AccessTokenVerifier;
import com.ootd.pickup.global.filter.ExceptionHandlingFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    @ConditionalOnBean(AccessTokenVerifier.class)
    public FilterRegistrationBean<ExceptionHandlingFilter> exceptionHandlingFilterRegistration(
            ObjectMapper objectMapper
    ) {
        ExceptionHandlingFilter exceptionHandlingFilter =
                new ExceptionHandlingFilter(objectMapper);
        FilterRegistrationBean<ExceptionHandlingFilter> filterRegistrationBean =
                new FilterRegistrationBean<>(exceptionHandlingFilter);
        filterRegistrationBean.addUrlPatterns("/*");
        filterRegistrationBean.setOrder(0);
        return filterRegistrationBean;
    }
}

package com.ootd.pickup.global.config;

import com.ootd.pickup.auth.token.AccessTokenVerifier;
import com.ootd.pickup.global.auth.TokenCookieManager;
import com.ootd.pickup.global.auth.filter.CsrfProtectionFilter;
import com.ootd.pickup.global.filter.ExceptionHandlingFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class FilterConfig {

  @Bean
  @ConditionalOnBean(AccessTokenVerifier.class)
  public FilterRegistrationBean<ExceptionHandlingFilter> exceptionHandlingFilterRegistration(
      ObjectMapper objectMapper, TokenCookieManager tokenCookieManager) {
    ExceptionHandlingFilter exceptionHandlingFilter =
        new ExceptionHandlingFilter(objectMapper, tokenCookieManager);
    FilterRegistrationBean<ExceptionHandlingFilter> filterRegistrationBean =
        new FilterRegistrationBean<>(exceptionHandlingFilter);
    filterRegistrationBean.addUrlPatterns("/*");
    filterRegistrationBean.setOrder(0);
    return filterRegistrationBean;
  }

  /**
   * ExceptionHandlingFilter(0) 다음, AuthenticationFilter(2)보다 먼저 돈다. 인증 여부와 무관하게 검증하므로 순서상 앞이어야 한다.
   */
  @Bean
  @ConditionalOnBean(AccessTokenVerifier.class)
  public FilterRegistrationBean<CsrfProtectionFilter> csrfProtectionFilterRegistration() {
    FilterRegistrationBean<CsrfProtectionFilter> filterRegistrationBean =
        new FilterRegistrationBean<>(new CsrfProtectionFilter());
    filterRegistrationBean.addUrlPatterns("/*");
    filterRegistrationBean.setOrder(1);
    return filterRegistrationBean;
  }
}

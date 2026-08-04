package com.ootd.pickup.global.config;

import com.ootd.pickup.auth.token.AdminAccessTokenVerifier;
import com.ootd.pickup.global.auth.filter.AdminAuthenticationFilter;
import com.ootd.pickup.global.auth.interceptor.AdminAuthenticationInterceptor;
import com.ootd.pickup.global.auth.resolver.AdminIdArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AdminAuthenticationWebConfig implements WebMvcConfigurer {

  private final AdminAuthenticationInterceptor adminAuthenticationInterceptor;
  private final AdminIdArgumentResolver adminIdArgumentResolver;

  @Bean
  @ConditionalOnBean(AdminAccessTokenVerifier.class)
  public FilterRegistrationBean<AdminAuthenticationFilter> adminAuthenticationFilterRegistration(
      AdminAccessTokenVerifier adminAccessTokenVerifier) {
    AdminAuthenticationFilter adminAuthenticationFilter =
        new AdminAuthenticationFilter(adminAccessTokenVerifier);
    FilterRegistrationBean<AdminAuthenticationFilter> filterRegistrationBean =
        new FilterRegistrationBean<>(adminAuthenticationFilter);
    filterRegistrationBean.addUrlPatterns("/admin/*");
    filterRegistrationBean.setOrder(1);
    return filterRegistrationBean;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(adminAuthenticationInterceptor).addPathPatterns("/admin/**");
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(adminIdArgumentResolver);
  }
}

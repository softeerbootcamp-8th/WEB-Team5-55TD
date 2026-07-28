package com.ootd.pickup.global.filter;

import com.ootd.pickup.auth.token.InvalidAccessTokenException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class ExceptionHandlingFilterTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();
    private final ExceptionHandlingFilter exceptionHandlingFilter =
            new ExceptionHandlingFilter(objectMapper);

    @Test
    void 액세스_토큰이_유효하지_않으면_401_응답을_반환한다() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/members/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        doThrow(new InvalidAccessTokenException())
                .when(filterChain)
                .doFilter(request, response);

        // when
        exceptionHandlingFilter.doFilter(request, response, filterChain);

        // then
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.get("status").asInt()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(body.get("error").asText()).isEqualTo("INVALID_ACCESS_TOKEN");
        assertThat(body.get("message").asText()).isEqualTo("유효하지 않은 액세스 토큰입니다.");
        assertThat(body.get("path").asText()).isEqualTo("/members/me");
        assertThat(body.get("timestamp").asText()).isNotBlank();
    }

    @Test
    void 예상하지_못한_예외가_발생하면_그대로_전파한다() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/members/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        doThrow(new IllegalStateException("unexpected"))
                .when(filterChain)
                .doFilter(request, response);

        // when & then
        assertThatThrownBy(() -> exceptionHandlingFilter.doFilter(request, response, filterChain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("unexpected");
    }
}

package com.ootd.pickup.health.api;

import com.ootd.pickup.health.dto.response.HealthCheckResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Health Check", description = "서버 상태 확인 API")
public interface HealthCheckApi {

  @Operation(
      summary = "헬스체크",
      description = "서버가 정상적으로 동작 중인지 확인합니다.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "서버 정상 동작",
            content = @Content(schema = @Schema(implementation = HealthCheckResponse.class)))
      })
  ResponseEntity<HealthCheckResponse> healthCheck();
}

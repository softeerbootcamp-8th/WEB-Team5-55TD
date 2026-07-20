package com.ootd.pickup.health.controller;

import com.ootd.pickup.health.api.HealthCheckApi;
import com.ootd.pickup.health.dto.response.HealthCheckResponse;
import com.ootd.pickup.health.service.HealthCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/healthcheck")
@RequiredArgsConstructor
public class HealthCheckController implements HealthCheckApi {

    private final HealthCheckService healthCheckService;

    @GetMapping
    @Override
    public ResponseEntity<HealthCheckResponse> healthCheck(){
        return ResponseEntity.ok(healthCheckService.getHealthCheckStatus());
    }
}

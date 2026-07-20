package com.ootd.pickup.healthCheck.controller;

import com.ootd.pickup.healthCheck.controller.dto.response.HealthCheckResponse;
import com.ootd.pickup.healthCheck.service.HealthCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/healthcheck")
@RequiredArgsConstructor
public class HealthCheckController {

    private final HealthCheckService healthCheckService;

    @GetMapping
    public ResponseEntity<HealthCheckResponse> healthCheck(){
        return ResponseEntity.ok(healthCheckService.getHealthCheckStatus());
    }
}

package com.ootd.pickup.health.service;

import org.springframework.stereotype.Service;

import com.ootd.pickup.health.dto.response.HealthCheckResponse;

@Service
public class HealthCheckService {

    public HealthCheckResponse getHealthCheckStatus() {
        return new HealthCheckResponse("OK");
    }
}

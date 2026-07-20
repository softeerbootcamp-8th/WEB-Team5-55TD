package com.ootd.pickup.healthCheck.service;

import com.ootd.pickup.healthCheck.controller.dto.response.HealthCheckResponse;
import org.springframework.stereotype.Service;

@Service
public class HealthCheckService {

    public HealthCheckResponse getHealthCheckStatus() {
        return new HealthCheckResponse("OK");
    }
}

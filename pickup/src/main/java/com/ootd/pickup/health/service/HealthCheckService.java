package com.ootd.pickup.health.service;

import com.ootd.pickup.health.dto.response.HealthCheckResponse;
import org.springframework.stereotype.Service;

@Service
public class HealthCheckService {

  public HealthCheckResponse getHealthCheckStatus() {
    return new HealthCheckResponse("OK");
  }
}

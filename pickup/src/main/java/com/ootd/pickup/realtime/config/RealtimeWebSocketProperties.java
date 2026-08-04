package com.ootd.pickup.realtime.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("realtime.websocket")
public record RealtimeWebSocketProperties(List<String> allowedOrigins) {}

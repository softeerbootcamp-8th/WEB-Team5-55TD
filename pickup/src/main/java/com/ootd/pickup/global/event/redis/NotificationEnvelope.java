package com.ootd.pickup.global.event.redis;

import com.ootd.pickup.global.event.EventType;
import tools.jackson.databind.JsonNode;

public record NotificationEnvelope(EventType eventType, JsonNode payload) {}

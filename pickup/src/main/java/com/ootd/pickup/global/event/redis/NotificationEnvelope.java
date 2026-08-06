package com.ootd.pickup.global.event.redis;

import tools.jackson.databind.JsonNode;

public record NotificationEnvelope(String eventType, JsonNode payload) {}

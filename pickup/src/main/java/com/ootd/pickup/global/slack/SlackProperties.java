package com.ootd.pickup.global.slack;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "slack")
public record SlackProperties(
    @DefaultValue("false") boolean enabled, String botToken, String channel) {}

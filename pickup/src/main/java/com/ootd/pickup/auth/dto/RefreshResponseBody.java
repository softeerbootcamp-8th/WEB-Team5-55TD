package com.ootd.pickup.auth.dto;

import java.time.Instant;

public record RefreshResponseBody(Instant expiresAt) {}

package com.ootd.pickup.admin.dto.response;

public record AdminPointGrantResultResponse(
    Long memberId, long balance, AdminPointGrantResponse grant) {}

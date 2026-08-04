package com.ootd.pickup.admin.dto.response;

import com.ootd.pickup.point.domain.PointGrant;
import java.time.LocalDateTime;

public record AdminPointGrantResponse(
    Long pointGrantId,
    Long memberId,
    Long adminId,
    long amount,
    String reason,
    LocalDateTime createdAt) {

  public static AdminPointGrantResponse fromEntity(PointGrant pointGrant) {
    return new AdminPointGrantResponse(
        pointGrant.getPointGrantId(),
        pointGrant.getMemberId(),
        pointGrant.getAdminId(),
        pointGrant.getAmount(),
        pointGrant.getReason(),
        pointGrant.getCreatedAt());
  }
}

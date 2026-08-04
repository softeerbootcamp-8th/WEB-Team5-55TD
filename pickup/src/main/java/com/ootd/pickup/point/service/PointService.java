package com.ootd.pickup.point.service;

import static com.ootd.pickup.global.exception.ExceptionCode.INVALID_POINT_GRANT_AMOUNT;
import static com.ootd.pickup.global.exception.ExceptionCode.POINT_NOT_FOUND;

import com.ootd.pickup.admin.dto.request.AdminGrantPointRequest;
import com.ootd.pickup.admin.dto.response.AdminPointGrantResponse;
import com.ootd.pickup.admin.dto.response.AdminPointGrantResultResponse;
import com.ootd.pickup.global.dto.response.PageResponse;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.domain.PointGrant;
import com.ootd.pickup.point.repository.PointGrantRepository;
import com.ootd.pickup.point.repository.PointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

  private final PointRepository pointRepository;
  private final PointGrantRepository pointGrantRepository;

  @Transactional
  public AdminPointGrantResultResponse grantPoint(
      Long adminId, Long memberId, AdminGrantPointRequest request) {
    if (request.amount() == 0) {
      throw new PickUpException(INVALID_POINT_GRANT_AMOUNT);
    }

    Point point =
        pointRepository
            .findByMemberId(memberId)
            .orElseThrow(() -> new PickUpException(POINT_NOT_FOUND));

    point.adjustBalance(request.amount());

    PointGrant pointGrant =
        pointGrantRepository.save(
            PointGrant.create(memberId, adminId, request.amount(), request.reason()));

    log.info("포인트 발급 - adminId={}, memberId={}, amount={}", adminId, memberId, request.amount());

    return new AdminPointGrantResultResponse(
        memberId, point.getBalance(), AdminPointGrantResponse.fromEntity(pointGrant));
  }

  public PageResponse<AdminPointGrantResponse> getGrantHistory(Long memberId, Pageable pageable) {
    Page<PointGrant> page = pointGrantRepository.findAllByMemberId(memberId, pageable);
    return PageResponse.from(page, AdminPointGrantResponse::fromEntity);
  }
}

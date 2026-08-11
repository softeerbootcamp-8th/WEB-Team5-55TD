package com.ootd.pickup.point.service;

import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.repository.PointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PointService {

  private final PointRepository pointRepository;

  public void initializePoint(Long memberId) {
    pointRepository.save(Point.create(memberId));
    log.info("포인트 계좌를 생성했습니다 - memberId={}", memberId);
  }
}

package com.ootd.pickup.point.service;

import static com.ootd.pickup.global.exception.ExceptionCode.INVALID_CHARGE_AMOUNT;
import static com.ootd.pickup.global.exception.ExceptionCode.POINT_NOT_FOUND;

import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.service.MemberManageService;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.domain.PointTransaction;
import com.ootd.pickup.point.dto.response.PointChargeResponse;
import com.ootd.pickup.point.repository.PointRepository;
import com.ootd.pickup.point.repository.PointTransactionRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 결제 연동 없이 클릭 즉시 포인트를 적립하는 목업 충전 서비스.
 *
 * <p>같은 idempotencyKey로 재요청(더블 클릭, 네트워크 재시도)해도 중복 적립되지 않도록 {@code point_transaction}의 유니크 제약({@code
 * idempotency_key})을 최종 안전장치로 쓴다. {@link #chargePoint}는 사전 조회로 대부분의 중복 요청을 락 없이 걸러내지만, 드문 경쟁
 * 상황에서 두 요청이 모두 사전 조회를 통과하면 뒤늦게 저장을 시도하는 쪽이 {@code GenerationType.IDENTITY}의 즉시 flush로 유니크 제약 위반을
 * 받는다. {@code SettlementService}와 같은 이유로 이 예외를 여기서 잡지 않고 그대로 던진다 — flush 실패는 트랜잭션을 rollback-only로
 * 확정시키므로, 같은 트랜잭션 안에서 잡아도 결국 커밋 시점에 {@code UnexpectedRollbackException}으로 터진다. 트랜잭션 경계 밖의 호출자(컨트롤러)가
 * 이 예외를 "이미 처리됨"으로 해석해 {@link #getChargeResult}로 기존 결과를 반환해야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PointChargeService {

  public static final long MIN_CHARGE_AMOUNT = 100_000L;
  public static final long MAX_CHARGE_AMOUNT = 10_000_000L;

  private final MemberManageService memberManageService;
  private final PointLockService pointLockService;
  private final PointRepository pointRepository;
  private final PointTransactionRepository pointTransactionRepository;

  @Transactional
  public PointChargeResponse chargePoint(Long memberId, long amount, String idempotencyKeySuffix) {
    validateAmount(amount);

    Optional<PointTransaction> existing =
        pointTransactionRepository.findByIdempotencyKey(
            PointTransaction.chargeIdempotencyKey(idempotencyKeySuffix));
    if (existing.isPresent()) {
      return getChargeResult(memberId, idempotencyKeySuffix);
    }

    Member member = memberManageService.getMemberById(memberId);
    Point point = pointLockService.getPointForUpdate(memberId);
    point.increaseBalance(amount);
    pointRepository.save(point);

    PointTransaction transaction =
        PointTransaction.forCharge(member, amount, point.getBalance(), idempotencyKeySuffix);
    pointTransactionRepository.save(transaction);

    log.info("포인트 충전 완료 - memberId={}, amount={}", memberId, amount);
    return PointChargeResponse.fromEntity(transaction, point);
  }

  public PointChargeResponse getChargeResult(Long memberId, String idempotencyKeySuffix) {
    PointTransaction transaction =
        pointTransactionRepository
            .findByIdempotencyKey(PointTransaction.chargeIdempotencyKey(idempotencyKeySuffix))
            .orElseThrow(() -> new PickUpException(POINT_NOT_FOUND));
    Point point =
        pointRepository.findByMemberId(memberId).orElseThrow(() -> new PickUpException(POINT_NOT_FOUND));
    return PointChargeResponse.fromEntity(transaction, point);
  }

  private void validateAmount(long amount) {
    if (amount < MIN_CHARGE_AMOUNT || amount > MAX_CHARGE_AMOUNT) {
      throw new PickUpException(INVALID_CHARGE_AMOUNT);
    }
  }
}

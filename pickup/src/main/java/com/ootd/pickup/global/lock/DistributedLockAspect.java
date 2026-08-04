package com.ootd.pickup.global.lock;

import static com.ootd.pickup.global.exception.ExceptionCode.BID_LOCK_ACQUISITION_FAILED;

import com.ootd.pickup.global.exception.PickUpException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * {@link DistributedLock}이 붙은 메서드를 Redisson 분산 락으로 감싼다.
 *
 * <p>트랜잭션 어드바이스(기본 {@link Ordered#LOWEST_PRECEDENCE})보다 먼저 실행되도록 낮은 순서 값을 부여해, 락이 트랜잭션 시작 전에 걸리고
 * 트랜잭션 커밋 이후에 풀리도록 한다. 그렇지 않으면 락 해제와 커밋 사이의 틈에서 다른 스레드가 커밋 전 데이터를 읽을 수 있다.
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@RequiredArgsConstructor
public class DistributedLockAspect {

  private final RedissonClient redissonClient;
  private final LockKeyParser lockKeyParser;

  @Around("@annotation(com.ootd.pickup.global.lock.DistributedLock)")
  public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    DistributedLock distributedLock = signature.getMethod().getAnnotation(DistributedLock.class);

    String key = lockKeyParser.parse(joinPoint, distributedLock.key());
    RLock rLock = redissonClient.getLock(key);

    boolean acquired = tryLock(rLock, distributedLock);
    if (!acquired) {
      log.warn("분산 락 획득 실패 - key={}", key);
      throw new PickUpException(BID_LOCK_ACQUISITION_FAILED);
    }

    try {
      return joinPoint.proceed();
    } finally {
      if (rLock.isHeldByCurrentThread()) {
        rLock.unlock();
      }
    }
  }

  private boolean tryLock(RLock rLock, DistributedLock distributedLock)
      throws InterruptedException {
    long waitTime = distributedLock.waitTime();
    long leaseTime = distributedLock.leaseTime();
    TimeUnit timeUnit = distributedLock.timeUnit();
    return rLock.tryLock(waitTime, leaseTime, timeUnit);
  }
}

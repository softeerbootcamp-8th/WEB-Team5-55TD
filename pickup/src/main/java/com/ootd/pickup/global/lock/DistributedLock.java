package com.ootd.pickup.global.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 분산 락으로 메서드 실행을 동시성 제어한다. {@link DistributedLockAspect}가 Redisson 기반 락으로 처리한다.
 *
 * <p>{@code key}는 메서드 파라미터를 바인딩한 SpEL 표현식이다. 예: {@code "'auction:' + #auctionId"}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DistributedLock {

  String key();

  long waitTime() default 3;

  long leaseTime() default 5;

  TimeUnit timeUnit() default TimeUnit.SECONDS;
}

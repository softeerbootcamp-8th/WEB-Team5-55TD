package com.ootd.pickup.global.lock;

import static com.ootd.pickup.global.exception.ExceptionCode.BID_LOCK_ACQUISITION_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.ootd.pickup.global.exception.PickUpException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class DistributedLockAspectTest {

  @Mock private RedissonClient redissonClient;

  @Mock private LockKeyParser lockKeyParser;

  @Mock private ProceedingJoinPoint joinPoint;

  @Mock private MethodSignature methodSignature;

  @Mock private RLock rLock;

  private DistributedLockAspect distributedLockAspect;

  @BeforeEach
  void setUp() {
    distributedLockAspect = new DistributedLockAspect(redissonClient, lockKeyParser);
  }

  @Test
  void 락_획득에_실패하면_예외가_발생하고_원본_메서드는_실행되지_않는다() throws Throwable {
    // given
    given(joinPoint.getSignature()).willReturn(methodSignature);
    given(methodSignature.getMethod()).willReturn(targetMethod());
    given(lockKeyParser.parse(joinPoint, "'auction:' + #auctionId")).willReturn("auction:1");
    given(redissonClient.getLock("auction:1")).willReturn(rLock);
    given(rLock.tryLock(3, 5, TimeUnit.SECONDS)).willReturn(false);

    // when & then
    assertThatThrownBy(() -> distributedLockAspect.lock(joinPoint))
        .isInstanceOf(PickUpException.class)
        .satisfies(
            exception ->
                assertThat(((PickUpException) exception).getExceptionCodeName())
                    .isEqualTo(BID_LOCK_ACQUISITION_FAILED.getClientExceptionCode().name()));
    then(joinPoint).should(never()).proceed();
    then(rLock).should(never()).unlock();
  }

  @Test
  void 락_획득에_성공하면_원본_메서드를_실행하고_락을_해제한다() throws Throwable {
    // given
    Object expectedResult = "결과";
    given(joinPoint.getSignature()).willReturn(methodSignature);
    given(methodSignature.getMethod()).willReturn(targetMethod());
    given(lockKeyParser.parse(joinPoint, "'auction:' + #auctionId")).willReturn("auction:1");
    given(redissonClient.getLock("auction:1")).willReturn(rLock);
    given(rLock.tryLock(3, 5, TimeUnit.SECONDS)).willReturn(true);
    given(rLock.isHeldByCurrentThread()).willReturn(true);
    given(joinPoint.proceed()).willReturn(expectedResult);

    // when
    Object result = distributedLockAspect.lock(joinPoint);

    // then
    assertThat(result).isEqualTo(expectedResult);
    then(joinPoint).should().proceed();
    then(rLock).should().unlock();
  }

  @DistributedLock(key = "'auction:' + #auctionId")
  private void annotatedTarget(Long auctionId) {}

  private Method targetMethod() throws NoSuchMethodException {
    return DistributedLockAspectTest.class.getDeclaredMethod("annotatedTarget", Long.class);
  }
}

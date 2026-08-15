package com.ootd.pickup.global.observability;

import datadog.trace.api.CorrelationIdentifier;
import datadog.trace.api.DDSpanId;
import datadog.trace.api.DDTraceId;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.lang.Nullable;

/**
 * Outbox→SQS, 실행기 스레드 전환, Redis Pub/Sub처럼 스레드·프로세스 경계를 넘는 지점에서 트레이스를 W3C {@code traceparent} 문자열
 * 하나로 옮겨 이어 붙이기 위한 캐리어.
 *
 * <p>OpenTelemetry API로 만든 스팬은 dd-java-agent가 자체 트레이서로 브릿지해 문제없이 이어진다. 다만 <b>Spring MVC 같은 자동 계측이
 * 만든 네이티브 스팬은 {@link Context#current()}로 보이지 않는다</b> — 이 에이전트 버전에서 OTel 브릿지가 그 방향으로는 동작하지 않는 것으로 실측
 * 확인됐다. 그래서 {@link #captureCurrentTraceParent()}는 OTel 컨텍스트가 비어 있으면 {@link
 * CorrelationIdentifier}(dd-trace-api의 공개 API, 로그 상관관계용으로 정식 제공되며 네이티브 스팬도 정확히 읽는다)로 한 번 더 시도한다.
 *
 * <p>경계를 넘을 때마다 <b>보내는 쪽에서 {@link #captureCurrentTraceParent()}로 지금 활성 트레이스를 문자열로 떠서 함께 실어 보내고</b>,
 * <b>받는 쪽에서 {@link #runWithExtractedContext}로 그 문자열을 부모로 하는 새 스팬을 열어 안에서 실제 처리를 실행</b>한다.
 */
public final class TraceContextCarrier {

  private static final String TRACEPARENT_KEY = "traceparent";
  private static final Tracer TRACER = GlobalOpenTelemetry.getTracer("pickup-async-boundary");

  /** W3C traceparent의 trace-id는 32자리 16진수(128비트)여야 한다. */
  private static final int TRACE_ID_HEX_LENGTH = 32;

  /** W3C traceparent의 parent-id(span-id)는 16자리 16진수(64비트)여야 한다. */
  private static final int SPAN_ID_HEX_LENGTH = 16;

  private static final TextMapSetter<Map<String, String>> SETTER = Map::put;
  private static final TextMapGetter<Map<String, String>> GETTER =
      new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
          return carrier.keySet();
        }

        @Override
        @Nullable
        public String get(@Nullable Map<String, String> carrier, String key) {
          return carrier == null ? null : carrier.get(key);
        }
      };

  private TraceContextCarrier() {}

  /**
   * 지금 활성 트레이스를 W3C {@code traceparent} 문자열로 떠낸다.
   *
   * @return 활성 스팬이 전혀 없으면 {@code null}
   */
  @Nullable
  public static String captureCurrentTraceParent() {
    Map<String, String> carrier = new HashMap<>();
    GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .inject(Context.current(), carrier, SETTER);
    String traceParent = carrier.get(TRACEPARENT_KEY);
    return traceParent != null ? traceParent : captureFromCorrelationIdentifier();
  }

  /**
   * {@link CorrelationIdentifier}가 보는 네이티브 활성 스팬을 수동으로 W3C 형식 문자열로 조립한다.
   *
   * <p>이 클래스가 만들지 않은(자동 계측이 만든) 스팬은 {@link Context#current()}로 보이지 않을 때가 있다는 게 실측으로 확인된 이 경로의 존재
   * 이유다 — {@link CorrelationIdentifier}는 그 경우에도 정확한 값을 준다.
   */
  @Nullable
  private static String captureFromCorrelationIdentifier() {
    try {
      String traceId = CorrelationIdentifier.getTraceId();
      String spanId = CorrelationIdentifier.getSpanId();
      if (isBlankOrZero(traceId) || isBlankOrZero(spanId)) {
        return null;
      }
      String traceIdHex = DDTraceId.from(traceId).toHexStringPadded(TRACE_ID_HEX_LENGTH);
      String spanIdHex = DDSpanId.toHexStringPadded(DDSpanId.from(spanId));
      String paddedSpanIdHex =
          spanIdHex.length() < SPAN_ID_HEX_LENGTH
              ? "0".repeat(SPAN_ID_HEX_LENGTH - spanIdHex.length()) + spanIdHex
              : spanIdHex;
      return "00-" + traceIdHex + "-" + paddedSpanIdHex + "-01";
    } catch (NumberFormatException exception) {
      return null;
    } catch (NoClassDefFoundError error) {
      // dd-trace-api는 compileOnly라, 에이전트 없는 로컬·테스트 실행에는 이 클래스 자체가 없다.
      return null;
    }
  }

  private static boolean isBlankOrZero(@Nullable String value) {
    return value == null || value.isEmpty() || "0".equals(value);
  }

  /**
   * {@code traceParent}를 부모로 하는 새 스팬 {@code spanName}을 열고 그 안에서 {@code work}를 실행한다.
   *
   * <p>{@code traceParent}가 {@code null}이면(옛 배포에서 만들어진 메시지, 활성 스팬이 없던 시점에 적재된 이벤트, 또는 진짜 원점인 최초 호출
   * 등) {@link Context#current()}를 기준으로 추출한다 — 이 스레드에 이미 다른 활성 스팬(예: 같은 배치를 처리 중인 바깥 스팬)이 있으면 그 아래에
   * 자연스럽게 이어지고, 전혀 없으면 새 트레이스로 시작한다. {@link Context#root()}를 기준으로 쓰면 이미 활성 중인 스팬까지 강제로 끊어 버려서 안 된다.
   */
  public static void runWithExtractedContext(
      @Nullable String traceParent, String spanName, Runnable work) {
    callWithExtractedContext(
        traceParent,
        spanName,
        () -> {
          work.run();
          return null;
        });
  }

  /** {@link #runWithExtractedContext(String, String, Runnable)}의 값을 돌려주는 버전. */
  public static <T> T callWithExtractedContext(
      @Nullable String traceParent, String spanName, Supplier<T> work) {
    Context base = Context.current();
    Context extracted =
        traceParent == null
            ? base
            : GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(base, Map.of(TRACEPARENT_KEY, traceParent), GETTER);
    try (Scope ignored = extracted.makeCurrent()) {
      Span span = TRACER.spanBuilder(spanName).setSpanKind(SpanKind.CONSUMER).startSpan();
      try (Scope spanScope = span.makeCurrent()) {
        return work.get();
      } finally {
        span.end();
      }
    }
  }
}

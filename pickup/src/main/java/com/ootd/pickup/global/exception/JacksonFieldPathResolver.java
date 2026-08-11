package com.ootd.pickup.global.exception;

import java.util.List;
import tools.jackson.core.JacksonException;

/**
 * Jackson 역직렬화 실패 시 {@code cause} 체인에서 문제가 된 필드 경로를 찾아 {@code images[1].consignmentImageId} 형태의
 * 문자열로 만든다.
 *
 * <p>{@code GlobalExceptionHandler}가 HTTP 상태 코드를 매핑하는 역할에만 집중하도록, Jackson 예외 내부 구조를 해석하는 책임을 이 클래스로
 * 분리했다.
 */
public final class JacksonFieldPathResolver {

  private JacksonFieldPathResolver() {}

  /** 어떤 필드에서 실패했는지 알 수 없는 경우(JSON 문법 자체가 깨진 경우 등)에는 {@code null}을 반환한다. */
  public static String resolve(Throwable exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof JacksonException jacksonException) {
        String path = formatPath(jacksonException.getPath());
        if (path != null) {
          return path;
        }
      }
    }
    return null;
  }

  static String formatPath(List<JacksonException.Reference> references) {
    StringBuilder path = new StringBuilder();
    for (JacksonException.Reference reference : references) {
      if (reference.getPropertyName() != null) {
        if (!path.isEmpty()) {
          path.append('.');
        }
        path.append(reference.getPropertyName());
      } else if (reference.getIndex() >= 0) {
        path.append('[').append(reference.getIndex()).append(']');
      }
    }
    return path.isEmpty() ? null : path.toString();
  }
}
